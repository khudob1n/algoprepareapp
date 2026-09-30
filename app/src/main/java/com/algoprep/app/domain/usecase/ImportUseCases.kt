package com.algoprep.app.domain.usecase

import com.algoprep.app.domain.ai.AiAssistant
import com.algoprep.app.domain.importer.CandidateDraft
import com.algoprep.app.domain.importer.DuplicateIndex
import com.algoprep.app.domain.importer.DuplicateMatch
import com.algoprep.app.domain.importer.DuplicateTarget
import com.algoprep.app.domain.importer.ImportParser
import com.algoprep.app.domain.importer.ImportPlanBuilder
import com.algoprep.app.domain.importer.ImportSource
import com.algoprep.app.domain.importer.ParsedImport
import com.algoprep.app.domain.importer.TaskClassifier
import com.algoprep.app.domain.model.ImportBatch
import com.algoprep.app.domain.repository.CatalogRepository
import com.algoprep.app.domain.repository.ImportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Parse step: raw material -> editable drafts with suggested tags and possible duplicates. Saves nothing. */
class ParseImport @Inject constructor(
    private val catalog: CatalogRepository,
    private val imports: ImportRepository,
    private val ai: AiAssistant,
) {
    suspend operator fun invoke(source: ImportSource): ParsedImport = withContext(Dispatchers.Default) {
        val classifier = TaskClassifier(catalog.observeTopics().first(), catalog.observePatterns().first())
        val drafts = ImportParser(classifier, ai).parse(source)

        val index = DuplicateIndex()
        val bank = imports.taskBriefs().associateBy { it.id }
        bank.values.forEach { index.add(BANK + it.id, it.title, it.text) }
        val batchTitles = HashMap<String, String>()

        val withDuplicates = drafts.map { d ->
            val best = index.matches(d.title, d.text)
                .sortedWith(compareByDescending<DuplicateIndex.Match> { it.score }.thenBy { if (it.id.startsWith(BANK)) 0 else 1 })
                .firstOrNull()
            index.add(BATCH + d.tempId, d.title, d.text)
            batchTitles[d.tempId] = d.title
            d.copy(duplicate = best?.let { toMatch(it, bank.mapValues { e -> e.value.title }, batchTitles) })
        }
        ParsedImport(source, withDuplicates)
    }

    private fun toMatch(m: DuplicateIndex.Match, bankTitles: Map<Long, String>, batchTitles: Map<String, String>): DuplicateMatch? =
        when {
            m.id.startsWith(BANK) -> {
                val id = m.id.removePrefix(BANK).toLong()
                DuplicateMatch(DuplicateTarget.Bank(id, bankTitles[id].orEmpty()), m.score)
            }
            else -> {
                val tempId = m.id.removePrefix(BATCH)
                DuplicateMatch(DuplicateTarget.Batch(tempId, batchTitles[tempId].orEmpty()), m.score)
            }
        }

    private companion object {
        const val BANK = "bank:"
        const val BATCH = "batch:"
    }
}

/** Save step: turns the reviewed drafts into a plan and stores it in one transaction. */
class SaveImport @Inject constructor(private val imports: ImportRepository) {
    suspend operator fun invoke(source: ImportSource, drafts: List<CandidateDraft>): ImportBatch =
        imports.save(ImportPlanBuilder.build(source, drafts))
}
