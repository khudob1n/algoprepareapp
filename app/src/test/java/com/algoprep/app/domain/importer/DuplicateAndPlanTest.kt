package com.algoprep.app.domain.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateAndPlanTest {
    private fun index(vararg titles: Pair<String, String>) = DuplicateIndex().apply { titles.forEach { (id, t) -> add(id, t, "") } }

    @Test fun exactTitleMatchIgnoresCaseAndPunctuation() {
        val m = index("bank:1" to "Two Sum").matches("two-sum!", "")
        assertEquals("bank:1", m.single().id)
        assertEquals(1.0, m.single().score, 1e-9)
    }

    @Test fun similarTitlesAreFlaggedAndDifferentOnesAreNot() {
        val idx = index("a" to "Longest Substring Without Repeating Characters", "b" to "Merge Two Sorted Lists")
        val m = idx.matches("Longest substring without repeating characters (variant)", "")
        assertEquals(listOf("a"), m.map { it.id })
        assertTrue(idx.matches("Validate Binary Search Tree", "").isEmpty())
    }

    @Test fun shortOrSingleWordTitlesDoNotFuzzyMatch() {
        assertTrue(index("a" to "Sum").matches("Sum of digits", "").isEmpty())
    }

    @Test fun statementOverlapFindsRewordedTitles() {
        val text = "Given an array of integers nums and an integer target return indices of the two numbers such that they add up to target"
        val idx = DuplicateIndex().apply { add("bank:7", "Two Sum", text) }
        val m = idx.matches("Pair that adds up to a target", "$text. You may assume exactly one solution.")
        assertEquals("bank:7", m.first().id)
    }

    @Test fun unrelatedTextsDoNotMatch() {
        val idx = DuplicateIndex().apply { add("x", "Reverse Linked List", "Reverse a singly linked list in place and return the new head of the list without extra memory") }
        assertTrue(idx.matches("Number of Islands", "Count connected groups of land cells in a grid of water and land using any traversal you like").isEmpty())
    }

    // ---- plan builder -------------------------------------------------------------------------

    private val source = ImportSource("f.md", "x".repeat(10), ImportFormat.MARKDOWN)

    private fun draft(
        id: String,
        selected: Boolean = true,
        dup: DuplicateMatch? = null,
        choice: DuplicateChoice = DuplicateChoice.UNDECIDED,
        companyTag: String? = null,
    ) = CandidateDraft(
        tempId = id, selected = selected, title = "T $id", text = "text $id", topics = setOf("arrays"), patterns = emptySet(),
        difficulty = null, source = "src $id", sourceUrl = null, companyTag = companyTag, interviewStage = null, roleLevel = null,
        reportedDate = null, notes = null, confidence = ParserConfidence.HIGH, titleGuessed = false, duplicate = dup, choice = choice,
    )

    private fun bank(id: Long) = DuplicateMatch(DuplicateTarget.Bank(id, "bank $id"), 0.9)
    private fun batch(id: String) = DuplicateMatch(DuplicateTarget.Batch(id, "T $id"), 0.9)

    @Test fun unselectedAreSkippedAndCounted() {
        val plan = ImportPlanBuilder.build(source, listOf(draft("a"), draft("b", selected = false), draft("c")))
        assertEquals(2, plan.newTasks)
        assertEquals(1, plan.skipped)
        assertEquals(3, plan.candidatesFound)
    }

    @Test fun undecidedAndKeepSeparateBecomeNewTasksNeverMerges() {
        val plan = ImportPlanBuilder.build(source, listOf(
            draft("a", dup = bank(5)),
            draft("b", dup = bank(6), choice = DuplicateChoice.KEEP_SEPARATE),
        ))
        assertTrue(plan.entries.all { it is SaveEntry.NewTask })
        assertEquals(emptyList<SeparatePair>(), (plan.entries[0] as SaveEntry.NewTask).keptSeparateFrom)
        assertEquals(listOf(SeparatePair(6L, 0.9)), (plan.entries[1] as SaveEntry.NewTask).keptSeparateFrom)
    }

    @Test fun mergeIntoBankAddsAMention() {
        val plan = ImportPlanBuilder.build(source, listOf(draft("a", dup = bank(5), choice = DuplicateChoice.MERGE, companyTag = "Acme")))
        val e = plan.entries.single() as SaveEntry.MergeIntoTask
        assertEquals(5L, e.taskId)
        assertEquals("Acme", e.mention.companyTag)
        assertEquals("src a", e.mention.source)
        assertEquals(1, plan.merged)
    }

    @Test fun batchMergeGroupsUnderTheEarliestDraft() {
        val plan = ImportPlanBuilder.build(source, listOf(
            draft("a"),
            draft("b", dup = batch("a"), choice = DuplicateChoice.MERGE),
            draft("c", dup = batch("b"), choice = DuplicateChoice.MERGE),
        ))
        assertEquals(1, plan.newTasks)
        val followers = plan.entries.filterIsInstance<SaveEntry.MergeIntoBatch>()
        assertEquals(listOf("a", "a"), followers.map { it.targetTempId })
        assertTrue("roots come first", plan.entries.first() is SaveEntry.NewTask)
    }

    @Test fun mergeIntoAnUnselectedDraftFallsBackToANewTask() {
        val plan = ImportPlanBuilder.build(source, listOf(
            draft("a", selected = false),
            draft("b", dup = batch("a"), choice = DuplicateChoice.MERGE),
        ))
        assertTrue(plan.entries.single() is SaveEntry.NewTask)
    }

    @Test fun groupMergingIntoABankTaskSendsEveryMemberThere() {
        val plan = ImportPlanBuilder.build(source, listOf(
            draft("a", dup = bank(9), choice = DuplicateChoice.MERGE),
            draft("b", dup = batch("a"), choice = DuplicateChoice.MERGE),
        ))
        assertTrue(plan.entries.all { it is SaveEntry.MergeIntoTask && it.taskId == 9L })
        assertFalse(plan.entries.any { it is SaveEntry.NewTask })
    }
}
