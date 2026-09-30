package com.algoprep.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

enum class InterviewGoal { BIG_TECH, ANY_COMPANY, REFRESH }

enum class StartOption {
    TODAY, TOMORROW, NEXT_MONDAY;

    fun resolve(today: LocalDate): LocalDate = when (this) {
        TODAY -> today
        TOMORROW -> today.plusDays(1)
        NEXT_MONDAY -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    }
}

/** Default self-rating (1..5) for topics the user did not rate explicitly. */
fun defaultSelfRating(level: UserLevel): Int = when (level) {
    UserLevel.BEGINNER -> 2
    UserLevel.INTERMEDIATE -> 3
    UserLevel.ADVANCED -> 4
}

enum class ReminderKind { MORNING, EVENING, REVIEW, STREAK }

data class ReminderSlot(val enabled: Boolean, val time: LocalTime)

data class ReminderSettings(
    val morning: ReminderSlot,
    val evening: ReminderSlot,
    val review: ReminderSlot,
    val streak: ReminderSlot,
) {
    operator fun get(kind: ReminderKind): ReminderSlot = when (kind) {
        ReminderKind.MORNING -> morning
        ReminderKind.EVENING -> evening
        ReminderKind.REVIEW -> review
        ReminderKind.STREAK -> streak
    }

    fun withSlot(kind: ReminderKind, slot: ReminderSlot): ReminderSettings = when (kind) {
        ReminderKind.MORNING -> copy(morning = slot)
        ReminderKind.EVENING -> copy(evening = slot)
        ReminderKind.REVIEW -> copy(review = slot)
        ReminderKind.STREAK -> copy(streak = slot)
    }

    val anyEnabled: Boolean get() = ReminderKind.entries.any { get(it).enabled }

    companion object {
        val DEFAULT = ReminderSettings(
            morning = ReminderSlot(true, LocalTime.of(8, 0)),
            evening = ReminderSlot(true, LocalTime.of(20, 0)),
            review = ReminderSlot(true, LocalTime.of(12, 0)),
            streak = ReminderSlot(true, LocalTime.of(21, 0)),
        )
    }
}
