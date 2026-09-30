package com.algoprep.app.domain.stats

import com.algoprep.app.domain.model.Mention
import com.algoprep.app.domain.model.Pattern
import com.algoprep.app.domain.model.Topic
import com.algoprep.app.domain.planning.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterviewDataTest {
    private fun mentions(vararg pairs: Pair<String?, String?>) =
        pairs.mapIndexed { i, (source, company) -> Mention(i.toLong(), source, null, company, null, null, null) }

    private val topics = listOf(Topic("arrays", "Arrays", null, 0), Topic("graphs", "Graphs", null, 1))
    private val patterns = listOf(Pattern("dfs", "DFS", null))

    private val tasks = listOf(
        task(1, "Two Sum", setOf("arrays")).copy(mentions = mentions("A" to "Acme", "B" to "Acme", null to "Beta")),
        task(2, "Islands", setOf("graphs", "arrays")).copy(patterns = setOf("dfs"), mentions = mentions("A" to null)),
        task(3, "Seed only", setOf("arrays")), // never imported: must not be counted
        task(4, "No topic", emptySet()).copy(mentions = mentions("C" to null, "C" to null)),
    )

    private val data = InterviewDataCalculator.compute(tasks, topics, patterns)

    @Test fun countsOnlyImportedRecords() {
        assertEquals(6, data.totalRecords)
        assertEquals(3, data.uniqueTasks)
    }

    @Test fun topicsCountRecordsAndUniqueTasks() {
        val arrays = data.topics.first { it.id == "arrays" }
        assertEquals(4, arrays.records) // 3 (Two Sum) + 1 (Islands)
        assertEquals(2, arrays.uniqueTasks)
        assertEquals(listOf("arrays", "graphs"), data.topics.map { it.id })
        assertEquals(1, data.patterns.single().records)
        assertEquals(2, data.unclassifiedRecords)
    }

    @Test fun mostMentionedProblemsNeedAtLeastTwoMentions() {
        assertEquals(listOf("Two Sum", "No topic"), data.topProblems.map { it.title })
        assertEquals(3, data.topProblems.first().mentions)
        assertEquals(listOf("A", "B", null), data.topProblems.first().sources)
    }

    @Test fun companiesAreCountedFromMentionsThatNameOne() {
        assertEquals(listOf("Acme" to 2, "Beta" to 1), data.companies.map { it.title to it.records })
        assertEquals(1, data.companies.first().uniqueTasks)
    }

    @Test fun emptyBankGivesEmptyData() {
        val empty = InterviewDataCalculator.compute(emptyList(), topics, patterns)
        assertEquals(0, empty.totalRecords)
        assertTrue(empty.topics.isEmpty() && empty.topProblems.isEmpty())
    }
}
