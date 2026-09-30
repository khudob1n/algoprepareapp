package com.algoprep.app.domain.importer

import com.algoprep.app.domain.model.Difficulty
import java.time.LocalDate

data class NewMention(
    val rawTitle: String,
    val rawText: String,
    val source: String?,
    val sourceUrl: String?,
    val companyTag: String?,
    val interviewStage: String?,
    val roleLevel: String?,
    val reportedDate: LocalDate?,
)

data class NewTaskDraft(
    val title: String,
    val text: String,
    val difficulty: Difficulty?,
    val topics: Set<String>,
    val patterns: Set<String>,
    val roleLevel: String?,
    val notes: String,
)

sealed interface SaveEntry {
    val mention: NewMention

    /** Creates a task. [keptSeparateFrom] lists bank tasks the user explicitly said are different. */
    data class NewTask(
        val tempId: String,
        val draft: NewTaskDraft,
        override val mention: NewMention,
        val keptSeparateFrom: List<Long> = emptyList(),
    ) : SaveEntry

    /** Adds one more mention to a task that is already in the bank. */
    data class MergeIntoTask(val taskId: Long, override val mention: NewMention) : SaveEntry

    /** Adds one more mention to a task created by another entry of the same import. */
    data class MergeIntoBatch(val targetTempId: String, override val mention: NewMention) : SaveEntry
}

data class ImportSavePlan(
    val sourceName: String,
    val format: ImportFormat,
    val rawSize: Int,
    val candidatesFound: Int,
    val entries: List<SaveEntry>,
) {
    val skipped: Int get() = candidatesFound - entries.size
    val newTasks: Int get() = entries.count { it is SaveEntry.NewTask }
    val merged: Int get() = entries.count { it !is SaveEntry.NewTask }
}

/**
 * Turns the reviewed drafts into a save plan.
 *  - unselected drafts are skipped;
 *  - "merge" into a bank task adds a mention to it;
 *  - "merge" into another draft of the same import groups them: the earliest one creates the task
 *    and the rest become its mentions; if the target is not selected the draft is saved as its own task;
 *  - "keep separate" and undecided drafts become new tasks (nothing is ever merged silently).
 */
object ImportPlanBuilder {
    fun build(source: ImportSource, drafts: List<CandidateDraft>): ImportSavePlan {
        val selected = drafts.filter { it.selected }
        val byId = selected.associateBy { it.tempId }
        val order = selected.withIndex().associate { it.value.tempId to it.index }

        // Union-find over "merge into an earlier draft of this import".
        val parent = HashMap<String, String>()
        fun find(x: String): String {
            var r = x
            while (parent[r] != null && parent[r] != r) r = parent.getValue(r)
            return r
        }
        for (d in selected) {
            val dup = d.duplicate
            val target = dup?.target
            if (d.choice == DuplicateChoice.MERGE && target is DuplicateTarget.Batch && target.tempId in byId) {
                val a = find(d.tempId)
                val b = find(target.tempId)
                if (a != b) {
                    // the earliest draft in the group becomes the root
                    if (order.getValue(a) < order.getValue(b)) parent[b] = a else parent[a] = b
                }
            }
        }

        val entries = ArrayList<SaveEntry>()
        for (d in selected) {
            val root = find(d.tempId)
            val rootDraft = byId.getValue(root)
            val rootMerge = (rootDraft.duplicate?.target as? DuplicateTarget.Bank)
                ?.takeIf { rootDraft.choice == DuplicateChoice.MERGE }
            val mention = mentionOf(d)
            entries += when {
                rootMerge != null -> SaveEntry.MergeIntoTask(rootMerge.taskId, mention)
                root != d.tempId -> SaveEntry.MergeIntoBatch(root, mention)
                else -> {
                    val bank = d.duplicate?.target as? DuplicateTarget.Bank
                    SaveEntry.NewTask(
                        tempId = d.tempId,
                        draft = taskOf(d),
                        mention = mention,
                        keptSeparateFrom = if (bank != null && d.choice == DuplicateChoice.KEEP_SEPARATE) listOf(bank.taskId) else emptyList(),
                    )
                }
            }
        }
        // Roots must be created before their followers.
        val sorted = entries.sortedBy { if (it is SaveEntry.MergeIntoBatch) 1 else 0 }
        return ImportSavePlan(source.name, source.format, source.text.length, drafts.size, sorted)
    }

    private fun mentionOf(d: CandidateDraft) = NewMention(
        rawTitle = d.title,
        rawText = d.text,
        source = d.source?.takeIf { it.isNotBlank() },
        sourceUrl = d.sourceUrl?.takeIf { it.isNotBlank() },
        companyTag = d.companyTag?.takeIf { it.isNotBlank() },
        interviewStage = d.interviewStage?.takeIf { it.isNotBlank() },
        roleLevel = d.roleLevel?.takeIf { it.isNotBlank() },
        reportedDate = d.reportedDate,
    )

    private fun taskOf(d: CandidateDraft) = NewTaskDraft(
        title = d.title.trim(),
        text = d.text.trim().ifEmpty { d.title.trim() },
        difficulty = d.difficulty,
        topics = d.topics,
        patterns = d.patterns,
        roleLevel = d.roleLevel?.takeIf { it.isNotBlank() },
        notes = d.notes.orEmpty(),
    )
}
