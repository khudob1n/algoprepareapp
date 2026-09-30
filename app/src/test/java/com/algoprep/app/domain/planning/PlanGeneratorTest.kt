package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.model.RoadmapDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanGeneratorTest {
    private val roadmap = (1..30).map { RoadmapDay(it, "Day $it", listOf("arrays"), null, null) }
    private val start = LocalDate.of(2026, 10, 5)

    @Test fun producesOneDayPerRoadmapEntryWithConsecutiveDates() {
        val days = PlanGenerator.generateSkeleton(roadmap.shuffled(), start, 90, today = start)
        assertEquals((1..30).toList(), days.map { it.dayIndex })
        assertEquals(start, days.first().date)
        assertEquals(start.plusDays(29), days.last().date)
        assertTrue(days.all { it.targetMinutes == 90 && it.items.isEmpty() && !it.isAdjusted })
    }

    @Test fun marksOnlyTodayAsToday() {
        val days = PlanGenerator.generateSkeleton(roadmap, start, 60, today = start.plusDays(2))
        assertEquals(listOf(3), days.filter { it.status == PlanDayStatus.TODAY }.map { it.dayIndex })
    }

    @Test fun futureStartHasNoTodayDay() {
        val days = PlanGenerator.generateSkeleton(roadmap, start, 60, today = start.minusDays(1))
        assertTrue(days.none { it.status == PlanDayStatus.TODAY })
    }
}
