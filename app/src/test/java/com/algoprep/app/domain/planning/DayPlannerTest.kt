package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.Bucket
import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.ErrorEntry
import com.algoprep.app.domain.model.ErrorType
import com.algoprep.app.domain.model.PlannedItem
import com.algoprep.app.domain.model.PlannedKind
import com.algoprep.app.domain.model.ReasonCode
import com.algoprep.app.domain.model.RoadmapDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class DayPlannerTest {
    private val roadmapDay = RoadmapDay(12, "Trees + DFS", listOf("trees"), "Recursive DFS", null)
    private val introduced = setOf("arrays", "graphs", "trees")

    private val bank = listOf(
        task(1, "Max Depth", setOf("trees"), Difficulty.EASY, 10),
        task(2, "Validate BST", setOf("trees"), Difficulty.MEDIUM, 20),
        task(3, "LCA", setOf("trees"), Difficulty.MEDIUM, 25),
        task(4, "Path Sum", setOf("trees"), Difficulty.MEDIUM, 25),
        task(5, "Serialize Tree", setOf("trees"), Difficulty.HARD, 40),
        task(6, "Number of Islands", setOf("graphs"), Difficulty.MEDIUM, 25),
        task(7, "Clone Graph", setOf("graphs"), Difficulty.MEDIUM, 30),
        task(8, "Two Sum", setOf("arrays"), Difficulty.EASY, 15, solved = 1),
    )

    private fun input(
        tasks: List<com.algoprep.app.domain.model.Task> = bank,
        skills: Map<String, com.algoprep.app.domain.model.TopicSkill> = emptyMap(),
        errors: List<ErrorEntry> = emptyList(),
        minutes: Int = 120,
        recent: Set<Long> = emptySet(),
        roadmap: RoadmapDay? = roadmapDay,
    ) = PlannerInput(
        day = planDay(12, listOf("trees"), minutes), roadmapDay = roadmap, tasks = tasks,
        skills = skills, introducedTopicIds = introduced, unresolvedErrors = errors,
        recentTaskIds = recent, now = NOW,
    )

    private fun List<PlannedItem>.kinds() = map { it.kind }

    @Test fun firstDayWithNoHistoryHasTheoryWarmupAndRoadmapTasks() {
        val items = DayPlanner.plan(input(skills = emptyMap()))
        assertEquals(PlannedKind.THEORY, items[0].kind)
        assertEquals(PlannedKind.WARMUP, items[1].kind)
        assertEquals(1L, items[1].taskId) // easiest unsolved task of today's topics
        val main = items.filter { it.kind == PlannedKind.MAIN && it.bucket == Bucket.ROADMAP }
        assertTrue(main.isNotEmpty())
        assertTrue(main.all { it.reasons.first().code == ReasonCode.ROADMAP_TOPIC })
        assertEquals(items.indices.toList(), items.map { it.orderIndex })
    }

    @Test fun noTaskAppearsTwice() {
        val items = DayPlanner.plan(input(skills = mapOf("graphs" to skill("graphs", 0.2, selfRating = 1))))
        val ids = items.mapNotNull { it.taskId }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun weakTopicOutsideTodayFillsTheWeakBucket() {
        val skills = mapOf("graphs" to skill("graphs", 0.2, attempts = 4), "trees" to skill("trees", 0.8))
        val items = DayPlanner.plan(input(skills = skills))
        val weak = items.filter { it.bucket == Bucket.WEAK }
        assertTrue(weak.isNotEmpty())
        assertTrue(weak.all { it.taskId in setOf(6L, 7L) })
        assertTrue(weak.all { item -> item.reasons.any { it.code == ReasonCode.WEAK_TOPIC && it.arg == "graphs" } })
    }

    @Test fun weakTopicThatIsNotIntroducedYetIsIgnored() {
        val skills = mapOf("dp" to skill("dp", 0.1, attempts = 0, selfRating = 1))
        val withDp = bank + task(20, "Coin Change", setOf("dp"), Difficulty.MEDIUM, 30)
        val items = DayPlanner.plan(input(tasks = withDp, skills = skills))
        assertFalse(items.any { it.taskId == 20L })
    }

    @Test fun dueReviewBecomesReviewItemWithReason() {
        val due = task(9, "Old Problem", setOf("arrays"), Difficulty.MEDIUM, 20, solved = 2,
            nextReviewAt = NOW.minus(Duration.ofDays(2)))
        val items = DayPlanner.plan(input(tasks = bank + due))
        val review = items.single { it.kind == PlannedKind.REVIEW }
        assertEquals(9L, review.taskId)
        assertEquals(Bucket.SPACED, review.bucket)
        assertEquals(ReasonCode.REVIEW_DUE, review.reasons.first().code)
    }

    @Test fun mixedTaskUsedWhenNothingIsDue() {
        val items = DayPlanner.plan(input())
        val spaced = items.filter { it.bucket == Bucket.SPACED }
        assertEquals(1, spaced.size)
        assertEquals(PlannedKind.MAIN, spaced.single().kind)
        assertTrue(spaced.single().reasons.any { it.code == ReasonCode.MIXED })
    }

    @Test fun errorReviewIsLastAndPointsToTheFailedTask() {
        val err = ErrorEntry(1, 5, 3, ErrorType.EDGE_CASES, null, false, NOW.minus(Duration.ofHours(20)))
        val items = DayPlanner.plan(input(errors = listOf(err)))
        assertEquals(PlannedKind.ERROR_REVIEW, items.last().kind)
        assertEquals(3L, items.last().taskId)
    }

    @Test fun smallDailyBudgetSkipsWarmup() {
        val items = DayPlanner.plan(input(minutes = 45))
        assertFalse(PlannedKind.WARMUP in items.kinds())
        assertNotNull(items.firstOrNull { it.kind == PlannedKind.MAIN })
    }

    @Test fun recentlySolvedAndMasteredTasksAreNotOffered() {
        val mastered = task(30, "Mastered", setOf("trees"), status = com.algoprep.app.domain.model.TaskStatus.MASTERED, solved = 5)
        val items = DayPlanner.plan(input(tasks = bank + mastered, recent = setOf(1L)))
        val ids = items.mapNotNull { it.taskId }
        assertFalse(30L in ids)
        assertFalse(1L in ids)
    }

    @Test fun totalPlannedTimeStaysNearTheBudget() {
        val items = DayPlanner.plan(input(minutes = 120))
        val total = items.sumOf { it.estimatedMin }
        assertTrue("total=$total", total in 60..150)
    }

    @Test fun emptyBankStillProducesTheory() {
        val items = DayPlanner.plan(input(tasks = emptyList()))
        assertEquals(listOf(PlannedKind.THEORY), items.kinds())
    }
}
