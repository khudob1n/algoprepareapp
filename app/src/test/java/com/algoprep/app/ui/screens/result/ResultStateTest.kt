package com.algoprep.app.ui.screens.result

import com.algoprep.app.domain.model.ErrorType
import com.algoprep.app.domain.model.SolveOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultStateTest {
    @Test fun outcomeIsSuggestedFromOpenedHints() {
        assertEquals(SolveOutcome.INDEPENDENT, suggestOutcome(0))
        assertEquals(SolveOutcome.SMALL_HINT, suggestOutcome(1))
        assertEquals(SolveOutcome.BIG_HINT, suggestOutcome(2))
        assertEquals(SolveOutcome.BIG_HINT, suggestOutcome(3))
    }

    @Test fun cannotCompleteWithoutAnOutcome() {
        assertFalse(ResultForm().canComplete)
        assertTrue(ResultForm(outcome = SolveOutcome.NOT_SOLVED).canComplete)
    }

    @Test fun errorsToggleOnAndOff() {
        val form = ResultForm().toggleError(ErrorType.EDGE_CASES).toggleError(ErrorType.OTHER)
        assertEquals(setOf(ErrorType.EDGE_CASES, ErrorType.OTHER), form.errors)
        assertEquals(setOf(ErrorType.OTHER), form.toggleError(ErrorType.EDGE_CASES).errors)
    }
}
