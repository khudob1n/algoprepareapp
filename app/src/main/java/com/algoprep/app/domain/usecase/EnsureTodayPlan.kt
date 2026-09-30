package com.algoprep.app.domain.usecase

import com.algoprep.app.domain.model.PlanDay
import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.model.PlannedKind
import com.algoprep.app.domain.model.PlannedStatus
import com.algoprep.app.domain.planning.AdaptivePolicy
import com.algoprep.app.domain.planning.AdjustReason
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
        val carryOverIds = unfinishedOfLatestPastDay(days, today)
        syncStatuses(days, today)
        skipLeftovers(days, today)

        val day = days.firstOrNull { it.date == today }
            ?: return if (today.isBefore(days.first().date)) {
                TodayPlanResult.NotStarted(days.first().date)
            } else {
                TodayPlanResult.Finished
            }

        if (day.items.isEmpty()) generate(day, today, carryOverIds)
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

    /** Tasks that were planned for the most recent earlier day and never finished. */
    private fun unfinishedOfLatestPastDay(days: List<PlanDay>, today: LocalDate): List<Long> =
        days.filter { it.date.isBefore(today) && it.items.isNotEmpty() }
            .maxByOrNull { it.date }
            ?.items
            ?.filter { it.status == PlannedStatus.TODO && it.taskId != null && it.kind != PlannedKind.THEORY }
            ?.sortedBy { it.orderIndex }
            ?.mapNotNull { it.taskId }
            .orEmpty()

    /** Items of days that are over and still open are closed, so nothing stays "to do" in the past. */
    private suspend fun skipLeftovers(days: List<PlanDay>, today: LocalDate) {
        for (d in days.filter { it.date.isBefore(today) }) {
            d.items.filter { it.status == PlannedStatus.TODO }.forEach { plans.setItemStatus(it.id, PlannedStatus.SKIPPED, null) }
        }
    }

    private suspend fun generate(day: PlanDay, today: LocalDate, carryOverIds: List<Long>) {
        val roadmap = catalog.getRoadmap()
        val skills = profiles.getSkills().associateBy { it.topicId }
        val introduced = roadmap.filter { it.dayIndex <= day.dayIndex }.flatMap { it.topicIds }.toSet()
        val allocation = AdaptivePolicy.allocate(day.topicIds, skills, introduced)
        val carried = carryOverIds.mapNotNull { tasks.get(it) }
        val input = PlannerInput(
            day = day,
            roadmapDay = roadmap.firstOrNull { it.dayIndex == day.dayIndex },
            tasks = tasks.observeAll().first(),
            skills = skills,
            introducedTopicIds = introduced,
            unresolvedErrors = training.observeUnresolvedErrors().first(),
            recentTaskIds = training.observeFinishedSessions().first()
                .filter { !it.localDate.isBefore(today.minusDays(RECENT_DAYS)) }
                .map { it.taskId }
                .toSet(),
            now = clock.instant(),
            allocation = allocation,
            carryOver = carried,
        )
        // Keep a move the user chose (shift / compress) unless an adaptation of this day replaces it.
        val existing = AdjustReason.decode(day.adjustReason)
        val reason = allocation.reason?.encode()
            ?: day.adjustReason.takeIf { existing is AdjustReason.Shifted || existing is AdjustReason.Compressed }
        plans.replaceDayItems(
            day.copy(
                status = PlanDayStatus.TODAY,
                isAdjusted = reason != null,
                adjustReason = reason,
                items = DayPlanner.plan(input),
            ),
        )
    }

    private companion object {
        const val RECENT_DAYS = 2L
    }
}
