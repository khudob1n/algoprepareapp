package com.algoprep.app.ui.screens.tasks

import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.Mention
import com.algoprep.app.domain.model.TaskStatus
import com.algoprep.app.domain.planning.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankStateTest {
    private val tasks = listOf(
        task(1, "two sum", setOf("arrays", "hashing"), Difficulty.EASY),
        task(2, "Binary Tree Paths", setOf("trees"), Difficulty.MEDIUM, status = TaskStatus.LEARNING)
            .copy(originalText = "Return all root-to-leaf paths"),
        task(3, "Clone Graph", setOf("graphs"), null)
            .copy(mentions = listOf(Mention(1, "A", null, null, null, null, null))),
    )

    private fun titles(f: BankFilter) = filterTasks(tasks, f).map { it.title }

    @Test fun noFilterReturnsEverythingSortedByTitleIgnoringCase() {
        assertEquals(listOf("Binary Tree Paths", "Clone Graph", "two sum"), titles(BankFilter()))
        assertFalse(BankFilter().isActive)
    }

    @Test fun searchMatchesTitleAndStatement() {
        assertEquals(listOf("two sum"), titles(BankFilter(query = " TWO ")))
        assertEquals(listOf("Binary Tree Paths"), titles(BankFilter(query = "root-to-leaf")))
        assertTrue(titles(BankFilter(query = "zzz")).isEmpty())
    }

    @Test fun exactFiltersCombine() {
        assertEquals(listOf("Binary Tree Paths"), titles(BankFilter(status = TaskStatus.LEARNING)))
        assertEquals(listOf("two sum"), titles(BankFilter(difficulty = Difficulty.EASY, topicId = "hashing")))
        assertEquals(listOf("Clone Graph"), titles(BankFilter(importedOnly = true)))
        assertTrue(titles(BankFilter(difficulty = Difficulty.HARD)).isEmpty())
        assertTrue(BankFilter(importedOnly = true).isActive)
    }
}
