package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.SolveOutcome
import com.algoprep.app.domain.model.TopicSkill
import java.time.Duration
import java.time.Instant

/**
 * Updates per-topic skill after a finished session.
 *
 * score <- 0.7 * score + 0.3 * quality, where quality starts from the outcome
 * (independent 1.0, small hint 0.75, big hint 0.4, saw solution 0.15, not solved 0.0) minus up to 0.15
 * for low confidence and for taking much longer than the estimate.
 */
object SkillTracker {
    private const val KEEP = 0.7
    private const val LEARN = 0.3
    private const val SLOW_FACTOR = 1.5
    const val DEFAULT_SELF_RATING = 3

    fun baseQuality(outcome: SolveOutcome): Double = when (outcome) {
        SolveOutcome.INDEPENDENT -> 1.0
        SolveOutcome.SMALL_HINT -> 0.75
        SolveOutcome.BIG_HINT -> 0.4
        SolveOutcome.SAW_SOLUTION -> 0.15
        SolveOutcome.NOT_SOLVED -> 0.0
    }

    fun quality(outcome: SolveOutcome, confidence: Int, duration: Duration, estimatedMin: Int?): Double {
        val lowConfidence = when {
            confidence <= 1 -> 0.10
            confidence == 2 -> 0.05
            else -> 0.0
        }
        val slow = if (estimatedMin != null && duration.seconds > SLOW_FACTOR * estimatedMin * 60) 0.05 else 0.0
        return (baseQuality(outcome) - lowConfidence - slow).coerceIn(0.0, 1.0)
    }

    fun update(
        current: TopicSkill?,
        topicId: String,
        outcome: SolveOutcome,
        confidence: Int,
        duration: Duration,
        estimatedMin: Int?,
        now: Instant,
    ): TopicSkill {
        val skill = current ?: TopicSkill(topicId, DEFAULT_SELF_RATING, 0.5, 0, 0, null)
        val q = quality(outcome, confidence, duration, estimatedMin)
        val solved = outcome == SolveOutcome.INDEPENDENT || outcome == SolveOutcome.SMALL_HINT || outcome == SolveOutcome.BIG_HINT
        return skill.copy(
            score = (KEEP * skill.score + LEARN * q).coerceIn(0.0, 1.0),
            attempts = skill.attempts + 1,
            failures = skill.failures + if (solved) 0 else 1,
            lastPracticedAt = now,
        )
    }
}
