package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.SolveOutcome
import com.algoprep.app.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class SkillTrackerTest {
    private val ten = Duration.ofMinutes(10)

    @Test fun independentSolveRaisesTheScore() {
        val s = SkillTracker.update(skill("arrays", 0.5), "arrays", SolveOutcome.INDEPENDENT, 5, ten, 20, NOW)
        assertEquals(0.7 * 0.5 + 0.3 * 1.0, s.score, 1e-9)
        assertEquals(6, s.attempts)
        assertEquals(0, s.failures)
        assertEquals(NOW, s.lastPracticedAt)
    }

    @Test fun failureLowersTheScoreAndCountsAsFailure() {
        val s = SkillTracker.update(skill("graphs", 0.5), "graphs", SolveOutcome.NOT_SOLVED, 1, ten, 20, NOW)
        assertEquals(0.7 * 0.5, s.score, 1e-9)
        assertEquals(1, s.failures)
    }

    @Test fun sawSolutionIsAFailureEvenIfConfidenceIsHigh() {
        val s = SkillTracker.update(skill("graphs", 0.5), "graphs", SolveOutcome.SAW_SOLUTION, 5, ten, 20, NOW)
        assertEquals(1, s.failures)
        assertTrue(s.score < 0.5)
    }

    @Test fun penaltiesForLowConfidenceAndSlowSolveAreBounded() {
        val q = SkillTracker.quality(SolveOutcome.INDEPENDENT, 1, Duration.ofMinutes(60), 20)
        assertEquals(1.0 - 0.10 - 0.05, q, 1e-9)
        assertEquals(0.0, SkillTracker.quality(SolveOutcome.NOT_SOLVED, 1, Duration.ofMinutes(90), 10), 1e-9)
    }

    @Test fun missingSkillStartsNeutral() {
        val s = SkillTracker.update(null, "dp", SolveOutcome.INDEPENDENT, 4, ten, null, NOW)
        assertEquals(1, s.attempts)
        assertNotNull(s.lastPracticedAt)
        assertEquals(0.7 * 0.5 + 0.3, s.score, 1e-9)
    }

    @Test fun repeatedFailuresMakeATopicWeak() {
        var s = skill("graphs", 0.6, attempts = 0, selfRating = 3)
        repeat(3) { s = SkillTracker.update(s, "graphs", SolveOutcome.NOT_SOLVED, 2, ten, 20, NOW) }
        assertTrue(WeakTopics.isWeak(s))
    }
}
