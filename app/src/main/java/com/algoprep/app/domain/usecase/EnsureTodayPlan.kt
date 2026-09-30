package com.algoprep.app.domain.usecase

import com.algoprep.app.domain.model.PlanDay
import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.model.PlannedStatus
import com.algoprep.app.domain.planning.DayPlanner
import com.algoprep.app.domain.planning.PlannerInput
import com.algoprep.app.domain.repository.CatalogRepository
import com.algoprep.app.domain.repository.PlanRepository
import com.algoprep.app.domain.repository.ProfileRepository
import com.algoprep.app.domain.repository.TaskRepository
import com.algoprep.app.domain.repository.TrainingRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

sealed interface TodayPlanResult {
    data object NoProfile : TodayPlanResult
    data class NotStarted(val startDate: LocalDate) : TodayPlanResult
    data object Finished : TodayPlanResult
    data class Ready(val dayIndex: Int) : TodayPlanResult
}

/**
 * Makes sure today's plan day exists and has items. Idempotent: items are generated once per day,
 * so the list stays stable while the user works through it.
 */
class EnsureTodayPlan @Inject constructor(
    private val profiles: ProfileRepository,
    private val plans: PlanRepository,
    private val catalog: CatalogRepository,
    private val tasks: TaskRepository,
    private val training: TrainingRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): TodayPlanResult {
        profiles.observeProfile().first() ?: return TodayPlanResult.NoProfile
        val days = plans.observeDays().first()
        if (days.isEmpty()) return TodayPlanResult.NoProfile

        val today = LocalDate.now(clock)
        tasks.markDueTasksForReview(clock.instant())
        syncStatuses(days, today)

        val day = days.firstOrNull { it.date == today }
            ?: return if (today.isBefore(days.first().date)) {
                TodayPlanResult.NotStarted(days.first().date)
            } else {
                TodayPlanResult.Finished
            }

        if (day.items.isEmpty()) generate(day, today)
        return TodayPlanResult.Ready(day.dayIndex)
    }

    private suspend fun syncStatuses(days: List<PlanDay>, today: LocalDate) {
        for (d in days) {
            val allDone = d.items.isNotEmpty() && d.items.all { it.status == PlannedStatus.DONE }
            val desired = when {
                d.status == PlanDayStatus.DONE || allDone -> PlanDayStatus.DONE
                d.date == today -> PlanDayStatus.TODAY
                d.date.isBefore(today) -> PlanDayStatus.MISSED
                else -> PlanDayStatus.UPCOMING
            }
            if (desired != d.status) plans.setDayStatus(d.dayIndex, desired)
        }
    }

    private suspend fun generate(day: PlanDay, today: LocalDate) {
        val roadmap = catalog.getRoadmap()
        val input = PlannerInput(
            day = day,
            roadmapDay = roadmap.firstOrNull { it.dayIndex == day.dayIndex },
            tasks = tasks.observeAll().first(),
            skills = profiles.getSkills().associateBy { it.topicId },
            introducedTopicIds = roadmap.filter { it.dayIndex <= day.dayIndex }.flatMap { it.topicIds }.toSet(),
            unresolvedErrors = training.observeUnresolvedErrors().first(),
            recentTaskIds = training.observeFinishedSessions().first()
                .filter { !it.localDate.isBefore(today.minusDays(RECENT_DAYS)) }
                .map { it.taskId }
                .toSet(),
            now = clock.instant(),
        )
        plans.replaceDayItems(day.copy(status = PlanDayStatus.TODAY, items = DayPlanner.plan(input)))
    }

    private companion object {
        const val RECENT_DAYS = 2L
    }
}
