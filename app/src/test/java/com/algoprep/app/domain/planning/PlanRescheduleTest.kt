package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.PlanDay
import com.algoprep.app.domain.model.PlanDayStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanRescheduleTest {
    private val start = LocalDate.of(2026, 10, 1)

    /** A 10-day plan, days 1-3 done, 4-5 missed, today is 2026-10-06 (day 6). */
    private fun plan(): List<PlanDay> = (1..10).map { i ->
        planDay(i, listOf("arrays"), 60).copy(
            date = start.plusDays(i - 1L),
            status = when {
                i <= 3 -> PlanDayStatus.DONE
                i <= 5 -> PlanDayStatus.MISSED
                i == 6 -> PlanDayStatus.TODAY
                else -> PlanDayStatus.UPCOMING
            },
        )
    }

    private val today = LocalDate.of(2026, 10, 6)
    private val skippable = setOf(7, 9)

    @Test fun missedDaysAreThoseThatEndedUnfinished() {
        assertEquals(2, PlanRescheduler.missedDays(plan(), today))
        assertEquals(0, PlanRescheduler.missedDays(plan(), start))
    }

    @Test fun shiftRedatesEveryUnfinishedDayFromTodayInOrder() {
        val r = PlanRescheduler.shift(plan(), today)
        assertEquals(listOf(4, 5, 6, 7, 8, 9, 10), r.days.map { it.dayIndex })
        assertEquals((0L..6L).map { today.plusDays(it) }, r.days.map { it.date })
        assertEquals(PlanDayStatus.TODAY, r.days.first().status)
        assertTrue(r.days.drop(1).all { it.status == PlanDayStatus.UPCOMING })
        assertEquals(today.plusDays(6), r.endDate)
        assertEquals(2, r.endDateShiftDays) // original end 2026-10-10, new end 2026-10-12
        assertTrue(r.dropped.isEmpty())
    }

    @Test fun compressDropsSkippableDaysToKeepTheEndDate() {
        val r = PlanRescheduler.compress(plan(), today, skippable)
        // 7 unfinished days, 5 calendar days left (10-06..10-10) -> drop two
        assertEquals(listOf(7, 9), r.dropped)
        assertEquals(listOf(4, 5, 6, 8, 10), r.days.map { it.dayIndex })
        assertEquals(LocalDate.of(2026, 10, 10), r.endDate)
        assertEquals(0, r.endDateShiftDays)
    }

    @Test fun compressDropsOnlyAsManyAsNeededAndOnlySkippableOnes() {
        val allowed = setOf(7)
        val r = PlanRescheduler.compress(plan(), today, allowed)
        assertEquals(listOf(7), r.dropped)
        assertEquals(1, r.endDateShiftDays) // still one day too long
    }

    @Test fun compressWithNothingToDropBehavesLikeShift() {
        val r = PlanRescheduler.compress(plan(), today, emptySet())
        assertTrue(r.dropped.isEmpty())
        assertEquals(PlanRescheduler.shift(plan(), today).endDate, r.endDate)
    }

    @Test fun aPlanThatIsOnTrackIsLeftAlone() {
        val onTrack = (1..5).map { planDay(it, listOf("arrays"), 60).copy(date = today.plusDays(it - 1L), status = if (it == 1) PlanDayStatus.TODAY else PlanDayStatus.UPCOMING) }
        val r = PlanRescheduler.shift(onTrack, today)
        assertEquals(onTrack.map { it.date }, r.days.map { it.date })
        assertEquals(0, r.endDateShiftDays)
    }

    @Test fun movedDaysLoseStaleItemsButUnmovedDaysKeepThem() {
        val item = DayPlannerFixtures.item(1)
        val days = plan().map { if (it.dayIndex == 6 || it.dayIndex == 4) it.copy(items = listOf(item)) else it }
        val r = PlanRescheduler.shift(days, today)
        assertTrue("day 4 moved from 10-04 to 10-06", r.days.first { it.dayIndex == 4 }.items.isEmpty())
        assertTrue("day 6 moved from 10-06 to 10-08", r.days.first { it.dayIndex == 6 }.items.isEmpty())
    }
}

internal object DayPlannerFixtures {
    fun item(id: Long) = com.algoprep.app.domain.model.PlannedItem(
        id, 1, com.algoprep.app.domain.model.PlannedKind.MAIN, 1, 0, 20, com.algoprep.app.domain.model.PlannedStatus.TODO,
        com.algoprep.app.domain.model.Bucket.ROADMAP, emptyList(), null,
    )
}
