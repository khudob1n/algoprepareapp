package com.algoprep.app.ui.screens.errors

import com.algoprep.app.domain.model.ErrorEntry
import com.algoprep.app.domain.model.ErrorType
import com.algoprep.app.domain.planning.NOW
import com.algoprep.app.domain.planning.task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class ErrorLogStateTest {
    private fun err(id: Long, taskId: Long, type: ErrorType, resolved: Boolean = false, hoursAgo: Long = id) =
        ErrorEntry(id, 1, taskId, type, null, resolved, NOW.minus(Duration.ofHours(hoursAgo)))

    private val errors = listOf(
        err(1, 1, ErrorType.EDGE_CASES),
        err(2, 1, ErrorType.EDGE_CASES),
        err(3, 2, ErrorType.PATTERN),
        err(4, 2, ErrorType.COMPLEXITY, resolved = true),
    )
    private val tasks = mapOf(1L to task(1, "A"), 2L to task(2, "B"))

    @Test fun openFilterHidesResolvedAndSortsNewestFirst() {
        val s = buildErrorLog(errors, tasks, ErrorFilter.OPEN)
        assertEquals(listOf(1L, 2L, 3L), s.rows.map { it.error.id })
        assertEquals("A", s.rows.first().taskTitle)
        assertEquals(3, s.openCount)
    }

    @Test fun summaryListsMostFrequentOpenTypeFirst() {
        val s = buildErrorLog(errors, tasks, ErrorFilter.ALL)
        assertEquals(listOf(ErrorType.EDGE_CASES to 2, ErrorType.PATTERN to 1), s.openByType)
        assertEquals(4, s.rows.size)
    }

    @Test fun resolvedFilter() {
        assertEquals(listOf(4L), buildErrorLog(errors, tasks, ErrorFilter.RESOLVED).rows.map { it.error.id })
    }

    @Test fun missingTaskKeepsTheRow() {
        assertEquals(null, buildErrorLog(errors, emptyMap(), ErrorFilter.OPEN).rows.first().taskTitle)
    }
}
