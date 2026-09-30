package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.PlanReason
import com.algoprep.app.domain.model.ReasonCode
import com.algoprep.app.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class TaskScorerTest {
    private fun ctx(vararg skills: com.algoprep.app.domain.model.TopicSkill, weak: Set<String> = emptySet()) =
        ScoringContext(NOW, skills.associateBy { it.topicId }, weak)

    private fun codes(r: List<PlanReason>) = r.map { it.code }

    @Test fun unsolvedTaskGetsNotSolvedReason() {
        val s = TaskScorer.score(task(1, difficulty = null), ctx())
        assertEquals(30, s.score)
        assertEquals(listOf(ReasonCode.NOT_SOLVED_YET), codes(s.reasons))
    }

    @Test fun solvedTaskWithUnknownDifficultyScoresZero() {
        val s = TaskScorer.score(task(1, solved = 2, difficulty = null), ctx())
        assertEquals(0, s.score)
        assertTrue(s.reasons.isEmpty())
    }

    @Test fun weakTopicAddsPointsAndNamesTheWorstTopic() {
        val c = ctx(skill("graphs", 0.2), skill("trees", 0.4), weak = setOf("graphs", "trees"))
        val s = TaskScorer.score(task(1, topics = setOf("graphs", "trees"), solved = 1, difficulty = null), c)
        assertEquals(16, s.score) // 20 * (1 - 0.2)
        assertEquals(PlanReason(ReasonCode.WEAK_TOPIC, "graphs"), s.reasons.single())
    }

    @Test fun dueReviewGrowsWithOverdueDaysAndIsCapped() {
        val overdue = task(1, solved = 1, difficulty = null, nextReviewAt = NOW.minus(Duration.ofDays(3)))
        assertEquals(28, TaskScorer.score(overdue, ctx()).score)
        val veryOverdue = task(2, solved = 1, difficulty = null, nextReviewAt = NOW.minus(Duration.ofDays(90)))
        assertEquals(35, TaskScorer.score(veryOverdue, ctx()).score)
        val future = task(3, solved = 1, difficulty = null, nextReviewAt = NOW.plus(Duration.ofDays(1)))
        assertEquals(0, TaskScorer.score(future, ctx()).score)
    }

    @Test fun recentFailureIsReported() {
        val s = TaskScorer.score(task(1, solved = 1, difficulty = null, status = TaskStatus.FAILED_RECENTLY), ctx())
        assertEquals(listOf(ReasonCode.RECENT_FAILURE), codes(s.reasons))
        assertEquals(25, s.score)
    }

    @Test fun frequencyCountsOnlyFromTwoMentionsAndIsSmall() {
        val one = TaskScorer.score(task(1, solved = 1, difficulty = null, mentions = 1), ctx())
        assertFalse(ReasonCode.FREQUENT_IN_DATASET in codes(one.reasons))
        val many = TaskScorer.score(task(2, solved = 1, difficulty = null, mentions = 12), ctx())
        assertEquals(10, many.score)
        assertEquals(PlanReason(ReasonCode.FREQUENT_IN_DATASET, "12"), many.reasons.single())
    }

    @Test fun difficultyFitFollowsTopicStrength() {
        val weakTopic = ctx(skill("arrays", 0.2))
        assertEquals(15, TaskScorer.score(task(1, difficulty = Difficulty.EASY, solved = 1), weakTopic).score)
        assertEquals(8, TaskScorer.score(task(2, difficulty = Difficulty.MEDIUM, solved = 1), weakTopic).score)
        assertEquals(0, TaskScorer.score(task(3, difficulty = Difficulty.HARD, solved = 1), weakTopic).score)
        val strongTopic = ctx(skill("arrays", 0.9))
        assertEquals(15, TaskScorer.score(task(4, difficulty = Difficulty.HARD, solved = 1), strongTopic).score)
    }

    @Test fun reasonsAreOrderedByContribution() {
        val c = ctx(skill("graphs", 0.1), weak = setOf("graphs"))
        val s = TaskScorer.score(task(1, topics = setOf("graphs"), difficulty = null), c)
        assertEquals(listOf(ReasonCode.NOT_SOLVED_YET, ReasonCode.WEAK_TOPIC), codes(s.reasons))
    }
}
