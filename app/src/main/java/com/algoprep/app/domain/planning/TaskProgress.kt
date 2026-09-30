package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.SolveOutcome
import com.algoprep.app.domain.model.TaskStatus
import com.algoprep.app.domain.model.isSolved

object TaskProgress {
    /**
     * Status right after an attempt. A failed attempt always wins ("failed recently");
     * a solved one keeps REVIEW/MASTERED, otherwise the task is being learned.
     * The spaced-repetition scheduler refines this once it has computed the next review.
     */
    fun statusAfter(current: TaskStatus, outcome: SolveOutcome): TaskStatus = when {
        !outcome.isSolved -> TaskStatus.FAILED_RECENTLY
        current == TaskStatus.REVIEW || current == TaskStatus.MASTERED -> current
        else -> TaskStatus.LEARNING
    }
}
