package com.algoprep.app.domain.mock

import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.PlannedKind
import com.algoprep.app.domain.model.ReasonCode
import com.algoprep.app.domain.model.RoadmapDay
import com.algoprep.app.domain.model.SessionType
import com.algoprep.app.domain.model.SolveOutcome
import com.algoprep.app.domain.model.TaskStatus
import com.algoprep.app.domain.planning.DayPlanner
import com.algoprep.app.domain.planning.NOW
import com.algoprep.app.domain.planning.PlannerInput
import com.algoprep.app.domain.planning.planDay
import com.algoprep.app.domain.planning.skill
import com.algoprep.app.domain.planning.task
import com.algoprep.app.domain.usecase.CompleteMock
import com.algoprep.app.domain.usecase.CompleteSession
import com.algoprep.app.domain.usecase.CompleteSessionInput
import com.algoprep.app.domain.usecase.FinishMock
import com.algoprep.app.domain.usecase.MockTaskTime
import com.algoprep.app.domain.usecase.PrepareMock
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
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MockTest {
    private val bank = listOf(
        task(1, "Easy A", setOf("arrays"), Difficulty.EASY),
        task(2, "Medium A", setOf("arrays"), Difficulty.MEDIUM),
        task(3, "Medium B", setOf("graphs"), Difficulty.MEDIUM),
        task(4, "Hard A", setOf("dp"), Difficulty.HARD),
        task(5, "Hard B", setOf("dp"), Difficulty.HARD),
        task(6, "Solved medium", setOf("trees"), Difficulty.MEDIUM, solved = 3, status = TaskStatus.LEARNING),
    )

    private fun pick(format: MockFormat, tasks: List<com.algoprep.app.domain.model.Task> = bank, recent: Set<Long> = emptySet(), seed: Int = 1) =
        MockPlanner.pick(format, tasks, emptyMap(), recent, NOW, Random(seed))

    @Test fun quickIsOneMediumAndFullIsMediumThenHard() {
        assertEquals(Difficulty.MEDIUM, pick(MockFormat.QUICK).single().difficulty)
        val full = pick(MockFormat.FULL)
        assertEquals(listOf(Difficulty.MEDIUM, Difficulty.HARD), full.map { it.difficulty })
        assertEquals(2, full.map { it.id }.toSet().size)
    }

    @Test fun recentlySolvedAndMasteredTasksAreAvoidedWhenThereIsChoice() {
        val mastered = bank + task(7, "Mastered", setOf("arrays"), Difficulty.MEDIUM, status = TaskStatus.MASTERED, solved = 9)
        repeat(20) { seed ->
            val ids = pick(MockFormat.FULL, mastered, recent = setOf(2L), seed = seed).map { it.id }
            assertFalse(2L in ids)
            assertFalse(7L in ids)
        }
    }

    @Test fun unsolvedTasksArePreferredOverSolvedOnes() {
        repeat(20) { seed -> assertFalse(6L in pick(MockFormat.QUICK, seed = seed).map { it.id }) }
    }

    @Test fun missingDifficultyFallsBackToAnyRemainingTask() {
        val noHard = bank.filter { it.difficulty != Difficulty.HARD }
        val full = pick(MockFormat.FULL, noHard)
        assertEquals(2, full.size)
        assertEquals(2, full.map { it.id }.toSet().size)
    }

    @Test fun tinyBankGivesWhatIsAvailable() {
        assertEquals(1, pick(MockFormat.FULL, bank.take(1)).size)
        assertTrue(pick(MockFormat.QUICK, emptyList()).isEmpty())
    }

    @Test fun differentSeedsCanGiveDifferentTasks() {
        val seen = (0..30).map { pick(MockFormat.QUICK, seed = it).single().id }.toSet()
        assertTrue(seen.size >= 2)
    }

    @Test fun mockDayIsTheoryPlusTheMockItselfAndOptionalErrorReview() {
        val input = PlannerInput(
            day = planDay(29, listOf("arrays"), 120), roadmapDay = RoadmapDay(29, "Mock interview", listOf("arrays"), "Simulate a real interview", DayPlanner.MOCK_DAY_NOTE),
            tasks = bank, skills = emptyMap(), introducedTopicIds = setOf("arrays"), unresolvedErrors = emptyList(), recentTaskIds = emptySet(), now = NOW,
        )
        val items = DayPlanner.plan(input)
        assertEquals(listOf(PlannedKind.THEORY, PlannedKind.MOCK), items.map { it.kind })
        val mock = items.last()
        assertEquals(90, mock.estimatedMin)
        assertEquals(ReasonCode.MOCK_DAY, mock.reasons.single().code)
        assertEquals(null, mock.taskId)
    }

    // ---- finishing ---------------------------------------------------------------------------

    @Test fun finishCreatesOneStoppedMockSessionPerTaskAndCompleteSavesThem() = runTest {
        val clock = fixedClock()
        val tasks = FakeTaskRepository(bank)
        val training = FakeTrainingRepository(clock)
        val profiles = FakeProfileRepository(skills = listOf(skill("arrays", 0.5), skill("graphs", 0.5)))
        val plans = FakePlanRepository()
        val tx = FakeTransactionRunner()

        val ids = FinishMock(training, tx)(listOf(MockTaskTime(2, 1500, "idea A"), MockTaskTime(3, 0, "")), plannedItemId = null)
        assertEquals(2, ids.size)
        assertTrue(ids.all { id -> training.getSession(id)!!.type == SessionType.MOCK })
        assertEquals("idea A", training.getSession(ids[0])!!.notes)
        assertEquals("a task left after 0 seconds still has a positive duration", 1, training.getSession(ids[1])!!.durationSec)

        CompleteMock(CompleteSession(training, tasks, plans, profiles, tx, clock), tx)(
            listOf(
                CompleteSessionInput(ids[0], SolveOutcome.INDEPENDENT, 4, emptySet(), null),
                CompleteSessionInput(ids[1], SolveOutcome.NOT_SOLVED, 2, emptySet(), null),
            ),
        )
        assertEquals(1, tasks.get(2)!!.timesSolved)
        assertEquals(TaskStatus.FAILED_RECENTLY, tasks.get(3)!!.status)
        assertNotNull(training.getSession(ids[0])!!.finishedAt)
    }

    @Test fun prepareMockUsesTheBankAndAvoidsRecentlySolvedTasks() = runTest {
        val clock = fixedClock()
        val training = FakeTrainingRepository(clock)
        val id = training.startSession(2, null, SessionType.PRACTICE)
        training.saveSession(training.getSession(id)!!.copy(finishedAt = clock.instant(), durationSec = 60, outcome = SolveOutcome.INDEPENDENT, confidence = 4))
        val prepare = PrepareMock(FakeTaskRepository(bank), FakeProfileRepository(), training, clock)
        repeat(10) { seed ->
            val picked = prepare(MockFormat.FULL, Random(seed))
            assertEquals(2, picked.size)
            assertFalse(2L in picked.map { it.id })
        }
    }
}
