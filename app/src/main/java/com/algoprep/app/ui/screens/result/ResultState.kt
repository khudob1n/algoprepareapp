package com.algoprep.app.ui.screens.result

import com.algoprep.app.domain.model.Complexity
import com.algoprep.app.domain.model.ErrorType
import com.algoprep.app.domain.model.SolveOutcome

data class ResultForm(
    val outcome: SolveOutcome? = null,
    val confidence: Int = 3,
    val errors: Set<ErrorType> = emptySet(),
    val otherNote: String = "",
) {
    val canComplete: Boolean get() = outcome != null

    fun toggleError(type: ErrorType): ResultForm =
        copy(errors = if (type in errors) errors - type else errors + type)
}

data class ResultUiState(
    val loading: Boolean = true,
    val taskTitle: String = "",
    val durationSec: Long = 0,
    val hintsUsed: Int = 0,
    val solutionIdea: String? = null,
    val complexity: Complexity? = null,
    val form: ResultForm = ResultForm(),
    val saving: Boolean = false,
)

/** Pre-selects the answer to "How did you solve it?" from the hints the user actually opened. */
fun suggestOutcome(hintsUsed: Int): SolveOutcome = when {
    hintsUsed <= 0 -> SolveOutcome.INDEPENDENT
    hintsUsed == 1 -> SolveOutcome.SMALL_HINT
    else -> SolveOutcome.BIG_HINT
}
