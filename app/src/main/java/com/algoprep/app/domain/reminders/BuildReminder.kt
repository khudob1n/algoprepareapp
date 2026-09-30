package com.algoprep.app.domain.reminders

import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.model.PlannedStatus
import com.algoprep.app.domain.model.ReminderKind
import com.algoprep.app.domain.repository.PlanRepository
import com.algoprep.app.domain.repository.ProfileRepository
import com.algoprep.app.domain.repository.TrainingRepository
import com.algoprep.app.domain.stats.StatsCalculator
import com.algoprep.app.domain.usecase.EnsureTodayPlan
import com.algoprep.app.domain.usecase.TodayPlanResult
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Decides whether a reminder is worth showing right now and what it should say.
 * Returns null when there is nothing useful to say, so the user is never nagged with empty messages.
 */
class BuildReminder @Inject constructor(
    private val ensureTodayPlan: EnsureTodayPlan,
    private val plans: PlanRepository,
    private val profiles: ProfileRepository,
    private val training: TrainingRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(kind: ReminderKind): ReminderContent? = when (kind) {
        ReminderKind.MORNING -> morning()
        ReminderKind.EVENING -> evening()
        ReminderKind.REVIEW -> review()
        ReminderKind.STREAK -> streak()
    }

    private suspend fun morning(): ReminderContent? {
        val ready = ensureTodayPlan() as? TodayPlanResult.Ready ?: return null
        val day = plans.getDay(ready.dayIndex) ?: return null
        if (day.status == PlanDayStatus.DONE) return null
        val minutes = if (day.items.isEmpty()) day.targetMinutes else day.items.sumOf { it.estimatedMin }
        return ReminderContent.Morning(day.dayIndex, plans.observeDays().first().size, day.title, minutes)
    }

    private suspend fun evening(): ReminderContent? {
        val today = LocalDate.now(clock)
        val day = plans.observeDays().first().firstOrNull { it.date == today } ?: return null
        if (day.items.isEmpty()) return null
        val left = day.items.count { it.taskId != null && it.status != PlannedStatus.DONE }
        if (left == 0) return null
        val planned = day.items.sumOf { it.estimatedMin }
        val done = day.items.filter { it.status == PlannedStatus.DONE }.sumOf { it.estimatedMin }
        val percent = if (planned == 0) 0 else (100.0 * done / planned).roundToInt()
        return ReminderContent.Evening(percent, left)
    }

    private suspend fun review(): ReminderContent? {
        val due = training.getDueReviews(clock.instant()).size
        return if (due > 0) ReminderContent.Review(due) else null
    }

    private suspend fun streak(): ReminderContent? {
        profiles.observeProfile().first() ?: return null
        val today = LocalDate.now(clock)
        val days = plans.observeDays().first()
        if (days.isEmpty() || today.isBefore(days.first().date) || today.isAfter(days.last().date)) return null
        val practiced = training.observeFinishedSessions().first().map { it.localDate }.toSet()
        if (today in practiced) return null
        return ReminderContent.Streak(StatsCalculator.currentStreak(practiced, today))
    }
}
