package com.algoprep.app.ui.screens

import com.algoprep.app.domain.planning.task
import com.algoprep.app.ui.screens.mock.MockUiState
import com.algoprep.app.ui.screens.mock.decodeIds
import com.algoprep.app.ui.screens.mock.encodeIds
import com.algoprep.app.ui.screens.mock.formatCountdown
import com.algoprep.app.ui.screens.mock.secondsPerTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockStateTest {
    @Test fun countdownShowsRemainingTimeAndThenOvertime() {
        assertEquals("45:00", formatCountdown(45 * 60))
        assertEquals("00:01", formatCountdown(1))
        assertEquals("00:00", formatCountdown(0))
        assertEquals("+02:10", formatCountdown(-130))
        assertEquals("1:30:00", formatCountdown(90 * 60))
    }

    @Test fun overtimeAndNavigationFlags() {
        val s = MockUiState(loading = false, tasks = listOf(task(1), task(2)), index = 1, limitSec = 100, elapsedSec = 101)
        assertTrue(s.overtime)
        assertTrue(s.isLast)
        assertEquals(2L, s.current?.id)
        assertFalse(s.copy(elapsedSec = 100, index = 0).overtime)
        assertFalse(s.copy(index = 0).isLast)
    }

    @Test fun idsSurviveTheRoundTripAndBadInputIsIgnored() {
        assertEquals(listOf(3L, 14L), decodeIds(encodeIds(listOf(3, 14))))
        assertEquals(listOf(1L, 2L), decodeIds("1, x ,2,"))
        assertTrue(decodeIds("").isEmpty())
    }

    @Test fun runningSegmentIsCountedForTheCurrentTaskOnly() {
        val spent = longArrayOf(60_000, 5_000)
        assertEquals(listOf(60L, 35L), secondsPerTask(spent, currentIndex = 1, segmentStartMs = 1_000, nowMs = 31_000))
        assertEquals(listOf(90L, 5L), secondsPerTask(spent, currentIndex = 0, segmentStartMs = 0, nowMs = 30_000))
        assertEquals("a clock that went backwards adds nothing", listOf(60L, 5L), secondsPerTask(spent, 0, 10_000, 5_000))
    }
}
