package com.algoprep.app.domain.stats

import com.algoprep.app.domain.model.ErrorEntry
import com.algoprep.app.domain.model.ErrorType
import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.model.SessionType
import com.algoprep.app.domain.model.SolveOutcome
import com.algoprep.app.domain.model.SolveSession
import com.algoprep.app.domain.model.Topic
import com.algoprep.app.domain.planning.NOW
import com.algoprep.app.domain.planning.planDay
import com.algoprep.app.domain.planning.skill
import com.algoprep.app.domain.planning.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class StatsCalculatorTest {
    private val today = LocalDate.of(2026, 10, 12)
    private var nextId = 1L

    private fun session(
        daysAgo: Long,
        outcome: SolveOutcome,
        taskId: Long = 1,
        minutes: Int = 20,
        order: Long = 0,
    ) = SolveSession(
        id = nextId++, taskId = taskId, plannedItemId = null, type = SessionType.PRACTICE,
        startedAt = NOW.minus(Duration.ofDays(daysAgo)).plusSeconds(order), finishedAt = NOW.minus(Duration.ofDays(daysAgo)),
        durationSec = minutes * 60, hintsUsed = 0, outcome = outcome, confidence = 3, notes = "",
        localDate = today.minusDays(daysAgo),
    )

    private val tasks = mapOf(
        1L to task(1, "A", setOf("arrays")),
        2L to task(2, "B", setOf("graphs")),
    )
    private val topics = listOf(Topic("arrays", "Arrays", null, 0), Topic("graphs", "Graphs", null, 1), Topic("dp", "DP", null, 2))

    private fun compute(
        sessions: List<SolveSession>,
        skills: List<com.algoprep.app.domain.model.TopicSkill> = emptyList(),
        planDays: List<com.algoprep.app.domain.model.PlanDay> = emptyList(),
        errors: List<ErrorEntry> = emptyList(),
    ) = StatsCalculator.compute(sessions, tasks, skills, topics, planDays, errors, today)

    @Test fun emptyDataHasNoInventedNumbers() {
        val s = compute(emptyList())
        assertEquals(0, s.overview.attempts)
        assertNull(s.overview.independentPercent)
        assertNull(s.overview.avgSolveMinutes)
        assertEquals(0, s.overview.currentStreak)
        assertEquals(Trend.UNKNOWN, s.progress.trend)
        assertNull(s.adherence.percent)
        assertEquals(30, s.activity.size)
        assertEquals(today, s.activity.last().date)
    }

    @Test fun overviewCountsAttemptsSolvedAndIndependentShare() {
        val s = compute(
            listOf(
                session(0, SolveOutcome.INDEPENDENT, 1, 10),
                session(0, SolveOutcome.SMALL_HINT, 2, 30),
                session(1, SolveOutcome.NOT_SOLVED, 2, 40),
                session(2, SolveOutcome.INDEPENDENT, 1, 20),
            ),
        )
        assertEquals(4, s.overview.attempts)
        assertEquals(3, s.overview.solved)
        assertEquals(2, s.overview.uniqueSolved)
        assertEquals(50, s.overview.independentPercent)
        assertEquals(20, s.overview.avgSolveMinutes) // (10 + 30 + 20) / 3
        assertEquals(100, s.overview.totalMinutes)
        assertTrue(s.overview.practicedToday)
    }

    @Test fun unfinishedSessionsAreIgnored() {
        val running = session(0, SolveOutcome.INDEPENDENT).copy(finishedAt = null, outcome = null)
        assertEquals(0, compute(listOf(running)).overview.attempts)
    }

    @Test fun streakSurvivesUntilTheDayEnds() {
        val days = setOf(today.minusDays(1), today.minusDays(2), today.minusDays(3))
        assertEquals(3, StatsCalculator.currentStreak(days, today))
        assertEquals(4, StatsCalculator.currentStreak(days + today, today))
        assertEquals(0, StatsCalculator.currentStreak(setOf(today.minusDays(2)), today))
    }

    @Test fun longestStreakLooksAtTheWholeHistory() {
        val days = setOf(1L, 2L, 3L, 4L, 10L, 11L).map { today.minusDays(it + 20) }.toSet()
        assertEquals(4, StatsCalculator.longestStreak(days))
        assertEquals(0, StatsCalculator.longestStreak(emptySet()))
    }

    @Test fun progressTrendComparesTheLastTwoWeeks() {
        val recent = List(4) { session(it.toLong(), SolveOutcome.INDEPENDENT) }
        val previous = List(4) { session(8L + it, if (it < 3) SolveOutcome.NOT_SOLVED else SolveOutcome.INDEPENDENT) }
        val s = compute(recent + previous)
        assertEquals(100, s.progress.recentIndependentPercent)
        assertEquals(25, s.progress.previousIndependentPercent)
        assertEquals(Trend.UP, s.progress.trend)

        val worse = compute(previous.map { it.copy(outcome = SolveOutcome.INDEPENDENT) } + recent.map { it.copy(outcome = SolveOutcome.NOT_SOLVED) })
        assertEquals(Trend.DOWN, worse.progress.trend)
    }

    @Test fun trendNeedsEnoughAttemptsInBothWindows() {
        val few = listOf(session(0, SolveOutcome.INDEPENDENT), session(9, SolveOutcome.INDEPENDENT))
        assertEquals(Trend.UNKNOWN, compute(few).progress.trend)
    }

    @Test fun activityListsThirtyDaysWithMinutes() {
        val s = compute(listOf(session(0, SolveOutcome.INDEPENDENT, minutes = 15), session(0, SolveOutcome.NOT_SOLVED, minutes = 25), session(29, SolveOutcome.INDEPENDENT, minutes = 5)))
        assertEquals(40, s.activity.last().minutes)
        assertEquals(2, s.activity.last().attempts)
        assertEquals(5, s.activity.first().minutes)
        assertEquals(today.minusDays(29), s.activity.first().date)
    }

    @Test fun topicsAreSortedWeakFirstAndNoDataLast() {
        val skills = listOf(
            skill("arrays", 0.9, attempts = 5),
            skill("graphs", 0.3, attempts = 4),
        )
        val s = compute(listOf(session(0, SolveOutcome.NOT_SOLVED, 2)), skills)
        assertEquals(listOf("graphs", "arrays", "dp"), s.topics.map { it.topicId })
        assertEquals(TopicLevel.WEAK, s.topics[0].level)
        assertEquals(TopicLevel.STRONG, s.topics[1].level)
        assertEquals(TopicLevel.NO_DATA, s.topics[2].level)
        assertEquals(30, s.topics[0].scorePercent)
    }

    @Test fun topicTrendUsesRecentOutcomes() {
        val improving = listOf(
            SolveOutcome.NOT_SOLVED, SolveOutcome.NOT_SOLVED, SolveOutcome.SAW_SOLUTION,
            SolveOutcome.INDEPENDENT, SolveOutcome.INDEPENDENT, SolveOutcome.SMALL_HINT,
        ).mapIndexed { i, o -> session(10L - i, o, 1) }
        val s = compute(improving, listOf(skill("arrays", 0.6, attempts = 6)))
        assertEquals(Trend.UP, s.topics.first { it.topicId == "arrays" }.trend)
        assertEquals(Trend.UNKNOWN, s.topics.first { it.topicId == "graphs" }.trend)
    }

    @Test fun adherenceCountsEndedDaysAndPlannedVersusActualMinutes() {
        val days = listOf(
            planDay(1, minutes = 60).copy(date = today.minusDays(2), status = PlanDayStatus.DONE),
            planDay(2, minutes = 60).copy(date = today.minusDays(1), status = PlanDayStatus.MISSED),
            planDay(3, minutes = 60).copy(date = today, status = PlanDayStatus.TODAY),
            planDay(4, minutes = 60).copy(date = today.plusDays(1), status = PlanDayStatus.UPCOMING),
        )
        val s = compute(listOf(session(2, SolveOutcome.INDEPENDENT, minutes = 50), session(0, SolveOutcome.INDEPENDENT, minutes = 10)), planDays = days)
        assertEquals(3, s.adherence.currentDayIndex)
        assertEquals(1, s.adherence.doneDays)
        assertEquals(1, s.adherence.missedDays)
        assertEquals(2, s.adherence.evaluatedDays)
        assertEquals(50, s.adherence.percent)
        assertEquals(180, s.adherence.plannedMinutes)
        assertEquals(60, s.adherence.actualMinutes)
    }

    @Test fun openErrorsAreGroupedByType() {
        fun err(id: Long, t: ErrorType, resolved: Boolean = false) = ErrorEntry(id, 1, 1, t, null, resolved, NOW)
        val s = compute(emptyList(), errors = listOf(err(1, ErrorType.EDGE_CASES), err(2, ErrorType.EDGE_CASES), err(3, ErrorType.PATTERN), err(4, ErrorType.COMPLEXITY, true)))
        assertEquals(3, s.openErrors)
        assertEquals(listOf(ErrorType.EDGE_CASES to 2, ErrorType.PATTERN to 1), s.openErrorsByType)
        assertFalse(s.openErrorsByType.any { it.first == ErrorType.COMPLEXITY })
    }
}
