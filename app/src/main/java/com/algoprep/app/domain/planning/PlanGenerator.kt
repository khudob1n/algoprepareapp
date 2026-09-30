package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.PlanDay
import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.model.RoadmapDay
import java.time.LocalDate

/**
 * Builds the initial plan skeleton from the roadmap template: one PlanDay per roadmap day with a date,
 * topics and time budget. Concrete tasks (items) are chosen later by the daily planner (Phase 4),
 * because they depend on the task bank and on what the user has already solved.
 */
object PlanGenerator {
    const val INITIAL_VERSION = 1

    fun generateSkeleton(
        roadmap: List<RoadmapDay>,
        startDate: LocalDate,
        dailyMinutes: Int,
        today: LocalDate,
    ): List<PlanDay> = roadmap.sortedBy { it.dayIndex }.map { day ->
        val date = startDate.plusDays((day.dayIndex - 1).toLong())
        PlanDay(
            dayIndex = day.dayIndex,
            date = date,
            title = day.title,
            targetMinutes = dailyMinutes,
            isAdjusted = false,
            adjustReason = null,
            generatedVersion = INITIAL_VERSION,
            status = if (date == today) PlanDayStatus.TODAY else PlanDayStatus.UPCOMING,
            topicIds = day.topicIds,
            items = emptyList(),
        )
    }
}
