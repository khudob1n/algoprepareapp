package com.algoprep.app.ui.screens.session

import com.algoprep.app.domain.planning.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateTest {
    @Test fun timerFormat() {
        assertEquals("00:00", formatTimer(0))
        assertEquals("23:41", formatTimer(23 * 60 + 41))
        assertEquals("1:05:09", formatTimer(3600 + 5 * 60 + 9))
        assertEquals("00:00", formatTimer(-5))
    }

    @Test fun hintsAreRevealedProgressively() {
        val s = SessionUiState(loading = false, task = task(1), running = true, allHints = listOf("a", "b", "c"), hintsRevealed = 1)
        assertEquals(listOf("a"), s.revealedHints)
        assertTrue(s.canRevealHint)
        assertFalse(s.copy(hintsRevealed = 3).canRevealHint)
        assertFalse(s.copy(running = false).canRevealHint)
    }

    @Test fun overtimeOnlyWhenRunningPastEstimate() {
        val t = task(1, minutes = 10)
        assertFalse(SessionUiState(task = t, running = true, elapsedSec = 600).isOvertime)
        assertTrue(SessionUiState(task = t, running = true, elapsedSec = 601).isOvertime)
        assertFalse(SessionUiState(task = t, running = false, elapsedSec = 9999).isOvertime)
        assertFalse(SessionUiState(task = task(2, minutes = null), running = true, elapsedSec = 9999).isOvertime)
    }
}
