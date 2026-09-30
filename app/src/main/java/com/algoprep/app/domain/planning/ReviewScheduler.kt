package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.ReviewState
import com.algoprep.app.domain.model.SolveOutcome
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Simplified SM-2. After an attempt:
 *  - independent and confident (>= 4): repetition + 1; interval 1, 3, then previous * ease; ease + 0.1
 *  - independent but unsure, or a small hint: repetition + 1; interval max(2, previous * 1.2); ease unchanged
 *  - significant hint: back to 2 days, repetition 0, ease - 0.15
 *  - saw the solution / not solved: 1 day, repetition 0, lapse + 1, ease - 0.2
 * Intervals are capped at 60 days; ease stays within 1.3..2.8.
 * A review is due from the start of its day (local time), so it lands in that day's plan.
 */
object ReviewScheduler {
    const val START_EASE = 2.3
    const val MIN_EASE = 1.3
    const val MAX_EASE = 2.8
    const val MAX_INTERVAL_DAYS = 60
    const val CONFIDENT = 4

    fun next(
        taskId: Long,
        prev: ReviewState?,
        outcome: SolveOutcome,
        confidence: Int,
        today: LocalDate,
        zone: ZoneId,
    ): ReviewState {
        val lastInterval = prev?.intervalDays ?: 0
        val ease = prev?.ease ?: START_EASE
        val reps = prev?.repetitions ?: 0
        val lapses = prev?.lapses ?: 0

        val interval: Int
        val newEase: Double
        val newReps: Int
        var newLapses = lapses
        when {
            outcome == SolveOutcome.INDEPENDENT && confidence >= CONFIDENT -> {
                newReps = reps + 1
                interval = when (newReps) {
                    1 -> 1
                    2 -> 3
                    else -> maxOf(lastInterval + 1, (lastInterval * ease).roundToInt())
                }
                newEase = (ease + 0.1).coerceAtMost(MAX_EASE)
            }
            outcome == SolveOutcome.INDEPENDENT || outcome == SolveOutcome.SMALL_HINT -> {
                newReps = reps + 1
                interval = maxOf(2, (lastInterval * 1.2).roundToInt())
                newEase = ease
            }
            outcome == SolveOutcome.BIG_HINT -> {
                newReps = 0
                interval = 2
                newEase = (ease - 0.15).coerceAtLeast(MIN_EASE)
            }
            else -> {
                newReps = 0
                interval = 1
                newEase = (ease - 0.2).coerceAtLeast(MIN_EASE)
                newLapses = lapses + 1
            }
        }
        val capped = interval.coerceIn(1, MAX_INTERVAL_DAYS)
        return ReviewState(
            taskId = taskId,
            intervalDays = capped,
            ease = newEase,
            repetitions = newReps,
            lapses = newLapses,
            dueAt = today.plusDays(capped.toLong()).atStartOfDay(zone).toInstant(),
            lastOutcome = outcome,
        )
    }
}
