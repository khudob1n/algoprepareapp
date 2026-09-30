package com.algoprep.app.domain.reminders

import com.algoprep.app.domain.model.Bucket
import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.PlanDay
import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.model.PlannedItem
import com.algoprep.app.domain.model.PlannedKind
import com.algoprep.app.domain.model.PlannedStatus
import com.algoprep.app.domain.model.ReminderKind
import com.algoprep.app.domain.model.ReviewState
import com.algoprep.app.domain.model.RoadmapDay
import com.algoprep.app.domain.model.SessionType
import com.algoprep.app.domain.model.SolveOutcome
import com.algoprep.app.domain.model.SolveSession
import com.algoprep.app.domain.model.UserLevel
import com.algoprep.app.domain.model.UserProfile
import com.algoprep.app.domain.planning.planDay
import com.algoprep.app.domain.planning.task
import com.algoprep.app.domain.usecase.EnsureTodayPlan
import com.algoprep.app.fakes.FakeCatalogRepository
import com.algoprep.app.fakes.FakePlanRepository
import com.algoprep.app.fakes.FakeProfileRepository
import com.algoprep.app.fakes.FakeTaskRepository
import com.algoprep.app.fakes.FakeTrainingRepository
import com.algoprep.app.fakes.fixedClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BuildReminderTest {
    private val clock = fixedClock("2026-10-12T08:00:00Z")
    private val today = LocalDate.of(2026, 10, 12)
    private val start = today.minusDays(1) // today is day 2

    private val profile = UserProfile(UserLevel.INTERMEDIATE, "BIG_TECH", 120, start, start.plusDays(2), Instant.EPOCH)
    private val roadmap = (1..3).map { RoadmapDay(it, "Day $it", listOf("arrays"), "theory", null) }
    private val bank = listOf(task(1, "Two Sum", setOf("arrays"), Difficulty.EASY, 10), task(2, "3Sum", setOf("arrays"), Difficulty.MEDIUM, 30))

    private fun days(): List<PlanDay> = (1..3).map {
        planDay(it, listOf("arrays"), 120).copy(date = start.plusDays(it - 1L), status = PlanDayStatus.UPCOMING)
    }

    private class Env(
        val plans: FakePlanRepository,
        val training: FakeTrainingRepository,
        val profiles: FakeProfileRepository,
        val build: BuildReminder,
    )

    private fun env(
        days: List<PlanDay> = days(),
        hasProfile: Boolean = true,
    ): Env {
        val plans = FakePlanRepository(days)
        val training = FakeTrainingRepository(clock)
        val profiles = FakeProfileRepository(if (hasProfile) profile else null)
        val ensure = EnsureTodayPlan(profiles, plans, FakeCatalogRepository(roadmap = roadmap), FakeTaskRepository(bank), training, clock)
        return Env(plans, training, profiles, BuildReminder(ensure, plans, profiles, training, clock))
    }

    private fun session(daysAgo: Long) = SolveSession(
        1, 1, null, SessionType.PRACTICE, Instant.EPOCH, Instant.EPOCH, 600, 0, SolveOutcome.INDEPENDENT, 4, "", today.minusDays(daysAgo),
    )

    @Test fun morningShowsDayTitleAndPlannedMinutes() = runTest {
        val e = env()
        val m = e.build(ReminderKind.MORNING) as ReminderContent.Morning
        assertEquals(2, m.dayIndex)
        assertEquals(3, m.totalDays)
        assertEquals("Day 2", m.title)
        assertEquals(e.plans.getDay(2)!!.items.sumOf { it.estimatedMin }, m.plannedMinutes)
    }

    @Test fun morningSilentWithoutPlanOrWhenDayIsDone() = runTest {
        assertNull(env(hasProfile = false).build(ReminderKind.MORNING))
        val e = env()
        e.build(ReminderKind.MORNING)
        e.plans.setDayStatus(2, PlanDayStatus.DONE)
        assertNull(e.build(ReminderKind.MORNING))
    }

    @Test fun eveningReportsPercentAndTasksLeft() = runTest {
        val e = env()
        e.build(ReminderKind.MORNING) // generates today's items
        val items = e.plans.getDay(2)!!.items
        val firstTask = items.first { it.taskId != null }
        e.plans.setItemStatus(firstTask.id, PlannedStatus.DONE, null)
        val ev = e.build(ReminderKind.EVENING) as ReminderContent.Evening
        val planned = items.sumOf { it.estimatedMin }
        assertEquals(Math.round(100.0 * firstTask.estimatedMin / planned).toInt(), ev.percent)
        assertEquals(items.count { it.taskId != null } - 1, ev.tasksLeft)
    }

    @Test fun eveningSilentWhenNothingLeftOrNotGenerated() = runTest {
        val e = env()
        assertNull(e.build(ReminderKind.EVENING)) // items not generated yet
        e.build(ReminderKind.MORNING)
        e.plans.getDay(2)!!.items.forEach { e.plans.setItemStatus(it.id, PlannedStatus.DONE, null) }
        assertNull(e.build(ReminderKind.EVENING))
    }

    @Test fun reviewCountsDueTasks() = runTest {
        val e = env()
        assertNull(e.build(ReminderKind.REVIEW))
        e.training.upsertReviewState(ReviewState(1, 1, 2.3, 1, 0, Instant.parse("2026-10-11T00:00:00Z"), null))
        e.training.upsertReviewState(ReviewState(2, 1, 2.3, 1, 0, Instant.parse("2026-10-20T00:00:00Z"), null))
        assertEquals(ReminderContent.Review(1), e.build(ReminderKind.REVIEW))
    }

    @Test fun streakWarningOnlyIfNoPracticeToday() = runTest {
        val e = env()
        assertEquals(ReminderContent.Streak(0), e.build(ReminderKind.STREAK))
        e.training.sessions.value = listOf(session(1), session(2))
        assertEquals(ReminderContent.Streak(2), e.build(ReminderKind.STREAK))
        e.training.sessions.value = listOf(session(0), session(1))
        assertNull(e.build(ReminderKind.STREAK))
    }

    @Test fun streakSilentOutsideThePlanWindow() = runTest {
        val shifted = days().map { it.copy(date = it.date.plusDays(30)) }
        assertNull(env(days = shifted).build(ReminderKind.STREAK))
        assertNull(env(hasProfile = false).build(ReminderKind.STREAK))
    }
}
