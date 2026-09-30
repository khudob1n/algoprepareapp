package com.algoprep.app.domain.usecase

import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.PlanDay
import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.model.PlannedKind
import com.algoprep.app.domain.model.PlannedStatus
import com.algoprep.app.domain.model.RoadmapDay
import com.algoprep.app.domain.model.TaskStatus
import com.algoprep.app.domain.model.UserLevel
import com.algoprep.app.domain.model.UserProfile
import com.algoprep.app.domain.planning.planDay
import com.algoprep.app.domain.planning.task
import com.algoprep.app.fakes.FakeCatalogRepository
import com.algoprep.app.fakes.FakePlanRepository
import com.algoprep.app.fakes.FakeProfileRepository
import com.algoprep.app.fakes.FakeTaskRepository
import com.algoprep.app.fakes.FakeTrainingRepository
import com.algoprep.app.fakes.fixedClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class EnsureTodayPlanTest {
    private val clock = fixedClock("2026-10-12T08:00:00Z") // today = 2026-10-12
    private val start = LocalDate.of(2026, 10, 11) // so today is day 2

    private val profile = UserProfile(UserLevel.INTERMEDIATE, "BIG_TECH", 120, start, start.plusDays(2), Instant.EPOCH)
    private val roadmap = (1..3).map { RoadmapDay(it, "Day $it", listOf("arrays"), "theory $it", null) }
    private val bank = listOf(
        task(1, "Two Sum", setOf("arrays"), Difficulty.EASY, 10),
        task(2, "3Sum", setOf("arrays"), Difficulty.MEDIUM, 30),
        task(3, "Container", setOf("arrays"), Difficulty.MEDIUM, 25),
    )

    private fun skeleton(): List<PlanDay> = (1..3).map { i ->
        planDay(i, listOf("arrays"), 120).copy(
            date = start.plusDays(i - 1L),
            status = PlanDayStatus.UPCOMING,
        )
    }

    private fun useCase(
        days: List<PlanDay> = skeleton(),
        tasks: FakeTaskRepository = FakeTaskRepository(bank),
        profileRepo: FakeProfileRepository = FakeProfileRepository(profile),
        plans: FakePlanRepository = FakePlanRepository(days),
    ) = EnsureTodayPlan(profileRepo, plans, FakeCatalogRepository(roadmap = roadmap), tasks, FakeTrainingRepository(clock), clock) to plans

    @Test fun generatesItemsForTodayOnce() = runTest {
        val (uc, plans) = useCase()
        assertEquals(TodayPlanResult.Ready(2), uc())
        val day = plans.getDay(2)!!
        assertTrue(day.items.isNotEmpty())
        assertEquals(PlannedKind.THEORY, day.items.first().kind)
        assertEquals(PlanDayStatus.TODAY, day.status)

        val ids = day.items.map { it.id }
        uc()
        assertEquals("items are stable across calls", ids, plans.getDay(2)!!.items.map { it.id })
    }

    @Test fun otherDaysKeepNoItemsButGetStatuses() = runTest {
        val (uc, plans) = useCase()
        uc()
        assertEquals(PlanDayStatus.MISSED, plans.getDay(1)!!.status)
        assertEquals(PlanDayStatus.UPCOMING, plans.getDay(3)!!.status)
        assertTrue(plans.getDay(3)!!.items.isEmpty())
    }

    @Test fun beforeTheStartDate() = runTest {
        val future = skeleton().map { it.copy(date = it.date.plusDays(10)) }
        val (uc, _) = useCase(days = future)
        assertEquals(TodayPlanResult.NotStarted(future.first().date), uc())
    }

    @Test fun afterTheLastDay() = runTest {
        val past = skeleton().map { it.copy(date = it.date.minusDays(10)) }
        val (uc, plans) = useCase(days = past)
        assertEquals(TodayPlanResult.Finished, uc())
        assertTrue(plans.days.value.all { it.status == PlanDayStatus.MISSED })
    }

    @Test fun withoutProfileThereIsNoPlan() = runTest {
        val (uc, _) = useCase(profileRepo = FakeProfileRepository(null))
        assertEquals(TodayPlanResult.NoProfile, uc())
    }

    @Test fun completedDayStaysDone() = runTest {
        val (uc, plans) = useCase()
        uc()
        plans.days.value.first { it.dayIndex == 2 }.items.forEach { plans.setItemStatus(it.id, PlannedStatus.DONE, null) }
        uc()
        assertEquals(PlanDayStatus.DONE, plans.getDay(2)!!.status)
    }

    @Test fun dueTasksMoveToReviewAndShowUpAsReviewItems() = runTest {
        val due = task(4, "Old", setOf("arrays"), Difficulty.MEDIUM, 20, solved = 2, status = TaskStatus.LEARNING,
            nextReviewAt = Instant.parse("2026-10-11T00:00:00Z"))
        val tasks = FakeTaskRepository(bank + due)
        val (uc, plans) = useCase(tasks = tasks)
        uc()
        assertEquals(TaskStatus.REVIEW, tasks.get(4)!!.status)
        assertTrue(plans.getDay(2)!!.items.any { it.kind == PlannedKind.REVIEW && it.taskId == 4L })
    }
}
