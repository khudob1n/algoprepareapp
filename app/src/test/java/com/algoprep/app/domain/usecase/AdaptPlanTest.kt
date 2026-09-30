package com.algoprep.app.domain.usecase

import com.algoprep.app.domain.model.PlanDay
import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.model.PlannedStatus
import com.algoprep.app.domain.model.RoadmapDay
import com.algoprep.app.domain.model.UserLevel
import com.algoprep.app.domain.model.UserProfile
import com.algoprep.app.domain.planning.AdjustReason
import com.algoprep.app.domain.planning.DayPlannerFixtures
import com.algoprep.app.domain.planning.planDay
import com.algoprep.app.domain.planning.skill
import com.algoprep.app.domain.planning.task
import com.algoprep.app.fakes.FakeCatalogRepository
import com.algoprep.app.fakes.FakePlanRepository
import com.algoprep.app.fakes.FakeProfileRepository
import com.algoprep.app.fakes.FakeTaskRepository
import com.algoprep.app.fakes.FakeTrainingRepository
import com.algoprep.app.fakes.FakeTransactionRunner
import com.algoprep.app.fakes.fixedClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AdaptPlanTest {
    private val clock = fixedClock("2026-10-06T08:00:00Z")
    private val today = LocalDate.of(2026, 10, 6)
    private val start = LocalDate.of(2026, 10, 1)

    private val roadmap = (1..10).map {
        RoadmapDay(it, "Day $it", listOf(if (it <= 5) "arrays" else "graphs"), "theory", if (it in setOf(7, 9)) AdaptPlan.SKIPPABLE else null)
    }

    private fun days(): List<PlanDay> = (1..10).map { i ->
        planDay(i, listOf(if (i <= 5) "arrays" else "graphs"), 60).copy(
            date = start.plusDays(i - 1L),
            status = when {
                i <= 3 -> PlanDayStatus.DONE
                i <= 5 -> PlanDayStatus.MISSED
                i == 6 -> PlanDayStatus.TODAY
                else -> PlanDayStatus.UPCOMING
            },
        )
    }

    private val profile = UserProfile(UserLevel.INTERMEDIATE, "BIG_TECH", 60, start, start.plusDays(9), Instant.EPOCH)

    private class Env(val plans: FakePlanRepository, val profiles: FakeProfileRepository, val adapt: AdaptPlan, val replan: ReplanRemainingDays)

    private fun env(skills: List<com.algoprep.app.domain.model.TopicSkill> = emptyList(), days: List<PlanDay> = days()): Env {
        val plans = FakePlanRepository(days)
        val profiles = FakeProfileRepository(profile, skills)
        val catalog = FakeCatalogRepository(roadmap = roadmap)
        return Env(
            plans, profiles,
            AdaptPlan(plans, profiles, catalog, FakeTransactionRunner(), clock),
            ReplanRemainingDays(plans, profiles, catalog, clock),
        )
    }

    @Test fun shiftMovesUnfinishedDaysAndTheTargetDate() = runTest {
        val e = env()
        e.adapt.apply(AdjustMode.SHIFT)
        val byIndex = e.plans.days.value.associateBy { it.dayIndex }
        assertEquals(today, byIndex.getValue(4).date)
        assertEquals(PlanDayStatus.TODAY, byIndex.getValue(4).status)
        assertEquals(today.plusDays(6), byIndex.getValue(10).date)
        assertEquals(today.plusDays(6), e.profiles.profile.value!!.targetDate)
        assertEquals(PlanDayStatus.DONE, byIndex.getValue(2).status)
        assertEquals(AdjustReason.Shifted(2), AdjustReason.decode(byIndex.getValue(4).adjustReason))
        assertTrue(byIndex.getValue(4).isAdjusted)
    }

    @Test fun compressDropsSkippableDaysAndKeepsTheTargetDate() = runTest {
        val e = env()
        e.adapt.apply(AdjustMode.COMPRESS)
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 8, 10), e.plans.days.value.map { it.dayIndex }.toSet())
        assertEquals(LocalDate.of(2026, 10, 10), e.profiles.profile.value!!.targetDate)
        assertEquals(AdjustReason.Compressed(2), AdjustReason.decode(e.plans.getDay(4)!!.adjustReason))
    }

    @Test fun previewDoesNotChangeAnything() = runTest {
        val e = env()
        val before = e.plans.days.value
        val preview = e.adapt.preview(AdjustMode.SHIFT)
        assertEquals(2, preview.endDateShiftDays)
        assertEquals(before, e.plans.days.value)
    }

    @Test fun movedDaysAreClearedSoTheyAreReplanned() = runTest {
        val withItems = days().map { if (it.dayIndex == 5) it.copy(items = listOf(DayPlannerFixtures.item(9))) else it }
        val e = env(days = withItems)
        e.adapt.apply(AdjustMode.SHIFT)
        assertTrue(e.plans.getDay(5)!!.items.isEmpty())
    }

    // ---- nightly replan --------------------------------------------------------------------

    @Test fun replanMarksFutureDaysWhenTopicsAreClearlyWeak() = runTest {
        val e = env(skills = listOf(skill("arrays", 0.2, attempts = 6), skill("graphs", 0.6)))
        val changed = e.replan()
        assertTrue(changed > 0)
        val day8 = e.plans.getDay(8)!!
        assertTrue(day8.isAdjusted)
        assertEquals(AdjustReason.MoreWeak(25, listOf("arrays")), AdjustReason.decode(day8.adjustReason))
        assertEquals(2, day8.generatedVersion)
    }

    @Test fun replanLeavesPastAndCompletedDaysAlone() = runTest {
        val e = env(skills = listOf(skill("arrays", 0.2, attempts = 6)))
        e.replan()
        assertNull(e.plans.getDay(2)!!.adjustReason)
        assertNull(e.plans.getDay(5)!!.adjustReason)
        assertNull("today is handled when its items are generated", e.plans.getDay(6)!!.adjustReason)
    }

    @Test fun replanIsIdempotent() = runTest {
        val e = env(skills = listOf(skill("arrays", 0.2, attempts = 6)))
        e.replan()
        val snapshot = e.plans.days.value
        assertEquals(0, e.replan())
        assertEquals(snapshot, e.plans.days.value)
    }

    @Test fun replanClearsAnAdaptationThatNoLongerApplies() = runTest {
        val e = env(skills = listOf(skill("arrays", 0.2, attempts = 6)))
        e.replan()
        e.profiles.skills.value = listOf(skill("arrays", 0.7, attempts = 8))
        e.replan()
        assertFalse(e.plans.getDay(8)!!.isAdjusted)
        assertNull(e.plans.getDay(8)!!.adjustReason)
    }

    @Test fun replanKeepsAShiftReasonUntilSomethingReplacesIt() = runTest {
        val e = env()
        e.adapt.apply(AdjustMode.SHIFT)
        e.replan()
        assertEquals(AdjustReason.Shifted(2), AdjustReason.decode(e.plans.getDay(8)!!.adjustReason))
    }

    // ---- carry-over through EnsureTodayPlan ------------------------------------------------

    @Test fun unfinishedTasksOfYesterdayAreCarriedOverAndPastItemsAreClosed() = runTest {
        val tasks = FakeTaskRepository(
            listOf(task(1, "Left over", setOf("arrays")), task(2, "Fresh A", setOf("graphs")), task(3, "Fresh B", setOf("graphs"))),
        )
        val yesterday = DayPlannerFixtures.item(77).copy(dayIndex = 5, taskId = 1, status = PlannedStatus.TODO)
        val d = days().map {
            when (it.dayIndex) {
                5 -> it.copy(date = today.minusDays(1), items = listOf(yesterday))
                6 -> it.copy(date = today, status = PlanDayStatus.UPCOMING)
                else -> it
            }
        }
        val plans = FakePlanRepository(d)
        val ensure = EnsureTodayPlan(FakeProfileRepository(profile), plans, FakeCatalogRepository(roadmap = roadmap), tasks, FakeTrainingRepository(clock), clock)
        ensure()
        val today6 = plans.getDay(6)!!
        assertTrue(today6.items.any { it.taskId == 1L && it.reasons.any { r -> r.code == com.algoprep.app.domain.model.ReasonCode.CARRIED_OVER } })
        assertEquals(PlannedStatus.SKIPPED, plans.getDay(5)!!.items.single().status)
    }
}
