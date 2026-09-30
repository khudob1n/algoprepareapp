package com.algoprep.app.domain.usecase

import com.algoprep.app.domain.model.Bucket
import com.algoprep.app.domain.model.ErrorType
import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.model.PlannedItem
import com.algoprep.app.domain.model.PlannedKind
import com.algoprep.app.domain.model.PlannedStatus
import com.algoprep.app.domain.model.SessionType
import com.algoprep.app.domain.model.SolveOutcome
import com.algoprep.app.domain.model.TaskStatus
import com.algoprep.app.domain.planning.planDay
import com.algoprep.app.domain.planning.skill
import com.algoprep.app.domain.planning.task
import com.algoprep.app.fakes.FakePlanRepository
import com.algoprep.app.fakes.FakeProfileRepository
import com.algoprep.app.fakes.FakeTaskRepository
import com.algoprep.app.fakes.FakeTrainingRepository
import com.algoprep.app.fakes.FakeTransactionRunner
import com.algoprep.app.fakes.fixedClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteSessionTest {
    private val clock = fixedClock()
    private val tasks = FakeTaskRepository(listOf(task(1, "Two Sum", setOf("arrays", "hashing"), minutes = 20)))
    private val training = FakeTrainingRepository(clock)
    private val profiles = FakeProfileRepository(skills = listOf(skill("arrays", 0.5), skill("hashing", 0.5)))

    private fun item(id: Long, status: PlannedStatus = PlannedStatus.TODO) = PlannedItem(
        id, 12, PlannedKind.MAIN, 1, id.toInt(), 20, status, Bucket.ROADMAP, emptyList(), null,
    )

    private val plans = FakePlanRepository(listOf(planDay().copy(items = listOf(item(1), item(2, PlannedStatus.DONE)))))

    private val useCase = CompleteSession(training, tasks, plans, profiles, FakeTransactionRunner(), clock)

    private suspend fun startedAndStopped(type: SessionType = SessionType.PRACTICE, itemId: Long? = 1, seconds: Int = 600): Long {
        val id = training.startSession(1, itemId, type)
        training.saveSession(training.getSession(id)!!.copy(durationSec = seconds))
        return id
    }

    @Test fun solvedSessionUpdatesEverything() = runTest {
        val id = startedAndStopped()
        val ok = useCase(CompleteSessionInput(id, SolveOutcome.INDEPENDENT, 4, emptySet(), null))
        assertTrue(ok)

        val session = training.getSession(id)!!
        assertEquals(SolveOutcome.INDEPENDENT, session.outcome)
        assertEquals(4, session.confidence)
        assertNotNull(session.finishedAt)

        val t = tasks.get(1)!!
        assertEquals(1, t.timesSolved)
        assertEquals(TaskStatus.LEARNING, t.status)
        assertEquals(4, t.confidence)
        assertNotNull(t.lastSolvedAt)

        assertEquals(setOf("arrays", "hashing"), profiles.skills.value.filter { it.attempts == 6 }.map { it.topicId }.toSet())
        assertEquals(PlannedStatus.DONE, plans.days.value.single().items.first { it.id == 1L }.status)
        assertEquals(id, plans.days.value.single().items.first { it.id == 1L }.completedSessionId)
        assertEquals(PlanDayStatus.DONE, plans.days.value.single().status)
    }

    @Test fun failedSessionMarksTaskFailedRecentlyAndLogsErrors() = runTest {
        val id = startedAndStopped()
        useCase(CompleteSessionInput(id, SolveOutcome.NOT_SOLVED, 2, setOf(ErrorType.OTHER, ErrorType.EDGE_CASES), " off by one "))

        val t = tasks.get(1)!!
        assertEquals(0, t.timesSolved)
        assertEquals(TaskStatus.FAILED_RECENTLY, t.status)
        assertNull(t.lastSolvedAt)

        val errors = training.errors.value
        assertEquals(setOf(ErrorType.OTHER, ErrorType.EDGE_CASES), errors.map { it.type }.toSet())
        assertEquals("off by one", errors.first { it.type == ErrorType.OTHER }.note)
        assertNull(errors.first { it.type == ErrorType.EDGE_CASES }.note)
        assertEquals(1, profiles.skills.value.first { it.topicId == "arrays" }.failures)
    }

    @Test fun dayIsNotDoneWhileOtherItemsRemain() = runTest {
        val open = FakePlanRepository(listOf(planDay().copy(items = listOf(item(1), item(2)))))
        val id = startedAndStopped()
        CompleteSession(training, tasks, open, profiles, FakeTransactionRunner(), clock)
            .invoke(CompleteSessionInput(id, SolveOutcome.SMALL_HINT, 3, emptySet(), null))
        assertEquals(PlanDayStatus.TODAY, open.days.value.single().status)
    }

    @Test fun successfulErrorReviewResolvesOldErrors() = runTest {
        val first = startedAndStopped()
        useCase(CompleteSessionInput(first, SolveOutcome.NOT_SOLVED, 2, setOf(ErrorType.COMPLEXITY), null))
        assertEquals(1, training.errors.value.count { !it.resolved })

        val review = startedAndStopped(type = SessionType.ERROR_REVIEW, itemId = null)
        useCase(CompleteSessionInput(review, SolveOutcome.INDEPENDENT, 4, emptySet(), null))
        assertEquals(0, training.errors.value.count { !it.resolved })
    }

    @Test fun failedErrorReviewKeepsErrorsOpen() = runTest {
        val first = startedAndStopped()
        useCase(CompleteSessionInput(first, SolveOutcome.NOT_SOLVED, 2, setOf(ErrorType.PATTERN), null))
        val review = startedAndStopped(type = SessionType.ERROR_REVIEW, itemId = null)
        useCase(CompleteSessionInput(review, SolveOutcome.SAW_SOLUTION, 2, emptySet(), null))
        assertEquals(1, training.errors.value.count { !it.resolved })
    }

    @Test fun completingTwiceIsANoOp() = runTest {
        val id = startedAndStopped()
        assertTrue(useCase(CompleteSessionInput(id, SolveOutcome.INDEPENDENT, 5, emptySet(), null)))
        assertFalse(useCase(CompleteSessionInput(id, SolveOutcome.INDEPENDENT, 5, emptySet(), null)))
        assertEquals(1, tasks.get(1)!!.timesSolved)
    }

    @Test fun unknownSessionReturnsFalse() = runTest {
        assertFalse(useCase(CompleteSessionInput(99, SolveOutcome.INDEPENDENT, 3, emptySet(), null)))
    }
}
