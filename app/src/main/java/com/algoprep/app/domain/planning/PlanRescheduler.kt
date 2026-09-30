package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.PlanDay
import com.algoprep.app.domain.model.PlanDayStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Reschedule(
    /** Days that stay in the plan, re-dated in order. */
    val days: List<PlanDay>,
    /** Day indexes that were dropped (compression only). */
    val dropped: List<Int>,
    /** The plan's new last date. */
    val endDate: LocalDate?,
    /** How many days the end date moved compared with [PlanRescheduler.endOf] of the input. */
    val endDateShiftDays: Int,
)

/**
 * Pure re-dating of the unfinished part of the plan. It never reorders the curriculum.
 *  - [shift]: every unfinished day is re-dated consecutively from today (the end date moves later);
 *  - [compress]: like shift, but first drops skippable (review / mixed) days, earliest first,
 *    as many as needed to keep the original end date. If that is not enough the end date still moves.
 */
object PlanRescheduler {
    /** Days that ended without being completed. */
    fun missedDays(days: List<PlanDay>, today: LocalDate): Int =
        days.count { it.date.isBefore(today) && it.status != PlanDayStatus.DONE }

    fun endOf(days: List<PlanDay>): LocalDate? = days.maxOfOrNull { it.date }

    fun shift(days: List<PlanDay>, today: LocalDate): Reschedule {
        val remaining = remaining(days)
        return build(days, remaining, dropped = emptyList(), today)
    }

    fun compress(days: List<PlanDay>, today: LocalDate, skippable: Set<Int>): Reschedule {
        val remaining = remaining(days)
        val originalEnd = endOf(days) ?: return build(days, remaining, emptyList(), today)
        val calendarDays = (ChronoUnit.DAYS.between(today, originalEnd) + 1).toInt().coerceAtLeast(0)
        val excess = (remaining.size - calendarDays).coerceAtLeast(0)
        val dropped = remaining.filter { it.dayIndex in skippable }.take(excess).map { it.dayIndex }
        return build(days, remaining.filter { it.dayIndex !in dropped }, dropped, today)
    }

    private fun remaining(days: List<PlanDay>): List<PlanDay> =
        days.filter { it.status != PlanDayStatus.DONE }.sortedBy { it.dayIndex }

    private fun build(all: List<PlanDay>, kept: List<PlanDay>, dropped: List<Int>, today: LocalDate): Reschedule {
        val redated = kept.mapIndexed { i, d ->
            val date = today.plusDays(i.toLong())
            d.copy(
                date = date,
                status = if (i == 0) PlanDayStatus.TODAY else PlanDayStatus.UPCOMING,
                // Day content may be stale after a move: the day is planned again when it comes.
                items = if (d.date == date) d.items else emptyList(),
            )
        }
        val oldEnd = endOf(all)
        val newEnd = (all.filter { it.status == PlanDayStatus.DONE } + redated).maxOfOrNull { it.date }
        return Reschedule(
            days = redated,
            dropped = dropped,
            endDate = newEnd,
            endDateShiftDays = if (oldEnd != null && newEnd != null) ChronoUnit.DAYS.between(oldEnd, newEnd).toInt() else 0,
        )
    }
}
