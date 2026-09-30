package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.ReviewState
import com.algoprep.app.domain.model.SolveOutcome
import com.algoprep.app.domain.model.TaskStatus
import com.algoprep.app.domain.model.isSolved

data class Attempt(val outcome: SolveOutcome, val confidence: Int?)

object TaskProgress {
    const val MASTERED_MIN_REPETITIONS = 4
    const val MASTERED_MIN_CONFIDENCE = 4

    /**
     * Status right after an attempt ([recent] is newest first and includes that attempt):
     *  - FAILED_RECENTLY when it was not solved;
     *  - MASTERED after at least four successful repetitions and two independent, confident solves in a row;
     *  - otherwise LEARNING. REVIEW is applied later, when the review date arrives
     *    (see TaskRepository.markDueTasksForReview).
     */
    fun statusAfter(state: ReviewState, recent: List<Attempt>): TaskStatus = when {
        recent.firstOrNull()?.outcome?.isSolved != true -> TaskStatus.FAILED_RECENTLY
        state.repetitions >= MASTERED_MIN_REPETITIONS && recent.size >= 2 &&
            recent.take(2).all {
                it.outcome == SolveOutcome.INDEPENDENT && (it.confidence ?: 0) >= MASTERED_MIN_CONFIDENCE
            } -> TaskStatus.MASTERED
        else -> TaskStatus.LEARNING
    }
}
