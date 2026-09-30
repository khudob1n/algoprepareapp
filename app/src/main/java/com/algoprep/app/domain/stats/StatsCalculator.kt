package com.algoprep.app.domain.stats

import com.algoprep.app.domain.model.ErrorEntry
import com.algoprep.app.domain.model.ErrorType
import com.algoprep.app.domain.model.PlanDay
import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.model.SolveOutcome
import com.algoprep.app.domain.model.SolveSession
import com.algoprep.app.domain.model.Task
import com.algoprep.app.domain.model.Topic
import com.algoprep.app.domain.model.TopicSkill
import com.algoprep.app.domain.model.isSolved
import com.algoprep.app.domain.planning.SkillTracker
import com.algoprep.app.domain.planning.WeakTopics
import kotlin.math.roundToInt
import java.time.LocalDate

/**
 * Everything on the Stats screen is derived from the user's own sessions; nothing is predicted.
 * Pure function of its inputs so it can be unit-tested.
 */
object StatsCalculator {
    const val ACTIVITY_DAYS = 30
    private const val TREND_WINDOW_DAYS = 7
    private const val TREND_MIN_ATTEMPTS = 3
    private const val TREND_DELTA_PERCENT = 10
    private const val TOPIC_TREND_MIN_SESSIONS = 4
    private const val TOPIC_TREND_WINDOW = 3
    private const val TOPIC_TREND_DELTA = 0.15
    private const val STRONG_SCORE = 0.75

    fun compute(
        sessions: List<SolveSession>,
        tasks: Map<Long, Task>,
        skills: List<TopicSkill>,
        topics: List<Topic>,
        planDays: List<PlanDay>,
        errors: List<ErrorEntry>,
        today: LocalDate,
    ): Stats {
        val finished = sessions.filter { it.finishedAt != null && it.outcome != null }
        val openErrors = errors.filter { !it.resolved }
        return Stats(
            overview = overview(finished, today),
            progress = progress(finished, today),
            adherence = adherence(planDays, finished, today),
            activity = activity(finished, today),
            topics = topicStats(finished, tasks, skills, topics),
            openErrorsByType = openErrors.groupingBy { it.type }.eachCount().toList()
                .sortedWith(compareByDescending<Pair<ErrorType, Int>> { it.second }.thenBy { it.first }),
            openErrors = openErrors.size,
        )
    }

    private fun overview(finished: List<SolveSession>, today: LocalDate): OverviewStats {
        val solved = finished.filter { it.outcome?.isSolved == true }
        val independent = finished.count { it.outcome == SolveOutcome.INDEPENDENT }
        val days = finished.map { it.localDate }.toSortedSet()
        return OverviewStats(
            attempts = finished.size,
            solved = solved.size,
            uniqueSolved = solved.map { it.taskId }.toSet().size,
            independentPercent = if (finished.isEmpty()) null else (100.0 * independent / finished.size).roundToInt(),
            avgSolveMinutes = if (solved.isEmpty()) null else (solved.map { it.durationSec }.average() / 60).roundToInt(),
            totalMinutes = (finished.sumOf { it.durationSec } / 60.0).roundToInt(),
            currentStreak = currentStreak(days, today),
            longestStreak = longestStreak(days),
            practicedToday = today in days,
        )
    }

    /** A streak survives until the end of the day: with no practice yet today it still counts up to yesterday. */
    fun currentStreak(days: Set<LocalDate>, today: LocalDate): Int {
        var cursor = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun longestStreak(days: Set<LocalDate>): Int {
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (d in days.sorted()) {
            run = if (previous != null && previous.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run)
            previous = d
        }
        return best
    }

    private fun progress(finished: List<SolveSession>, today: LocalDate): ProgressTrend {
        fun window(from: LocalDate, to: LocalDate) = finished.filter { it.localDate in from..to }
        val recent = window(today.minusDays(TREND_WINDOW_DAYS - 1L), today)
        val previous = window(today.minusDays(2L * TREND_WINDOW_DAYS - 1), today.minusDays(TREND_WINDOW_DAYS.toLong()))
        fun rate(l: List<SolveSession>) =
            if (l.size < TREND_MIN_ATTEMPTS) null
            else (100.0 * l.count { it.outcome == SolveOutcome.INDEPENDENT } / l.size).roundToInt()
        val r = rate(recent)
        val p = rate(previous)
        val trend = when {
            r == null || p == null -> Trend.UNKNOWN
            r - p >= TREND_DELTA_PERCENT -> Trend.UP
            p - r >= TREND_DELTA_PERCENT -> Trend.DOWN
            else -> Trend.FLAT
        }
        return ProgressTrend(r, p, trend)
    }

    private fun adherence(planDays: List<PlanDay>, finished: List<SolveSession>, today: LocalDate): PlanAdherence {
        val evaluated = planDays.filter { it.date.isBefore(today) || it.status == PlanDayStatus.DONE }
        val done = planDays.count { it.status == PlanDayStatus.DONE }
        val missed = evaluated.count { it.status != PlanDayStatus.DONE }
        val minutesByDate = finished.groupBy { it.localDate }.mapValues { (_, l) -> l.sumOf { it.durationSec } / 60.0 }
        val dueDays = planDays.filter { !it.date.isAfter(today) }
        return PlanAdherence(
            currentDayIndex = planDays.firstOrNull { it.date == today }?.dayIndex,
            totalDays = planDays.size,
            doneDays = done,
            missedDays = missed,
            evaluatedDays = evaluated.size,
            percent = if (evaluated.isEmpty()) null else (100.0 * (evaluated.size - missed) / evaluated.size).roundToInt(),
            plannedMinutes = dueDays.sumOf { it.targetMinutes },
            actualMinutes = dueDays.sumOf { (minutesByDate[it.date] ?: 0.0) }.roundToInt(),
        )
    }

    private fun activity(finished: List<SolveSession>, today: LocalDate): List<DayActivity> {
        val byDate = finished.groupBy { it.localDate }
        return (ACTIVITY_DAYS - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val list = byDate[date].orEmpty()
            DayActivity(date, (list.sumOf { it.durationSec } / 60.0).roundToInt(), list.size)
        }
    }

    private fun topicStats(
        finished: List<SolveSession>,
        tasks: Map<Long, Task>,
        skills: List<TopicSkill>,
        topics: List<Topic>,
    ): List<TopicStat> {
        val skillById = skills.associateBy { it.topicId }
        val chronological = finished.sortedWith(compareBy({ it.startedAt }, { it.id }))
        val result = topics.map { topic ->
            val own = chronological.filter { s -> tasks[s.taskId]?.topics?.contains(topic.id) == true }
            val skill = skillById[topic.id]
            val score = skill?.score ?: 0.5
            val attempts = skill?.attempts ?: own.size
            val level = when {
                skill != null && WeakTopics.isWeak(skill) -> TopicLevel.WEAK
                attempts == 0 -> TopicLevel.NO_DATA
                attempts >= WeakTopics.MIN_ATTEMPTS && score >= STRONG_SCORE -> TopicLevel.STRONG
                else -> TopicLevel.DEVELOPING
            }
            TopicStat(
                topicId = topic.id,
                title = topic.title,
                attempts = attempts,
                solved = own.count { it.outcome?.isSolved == true },
                scorePercent = (score * 100).roundToInt(),
                level = level,
                trend = topicTrend(own),
            )
        }
        return result.sortedWith(
            compareBy<TopicStat> { it.level.ordinal }.thenBy { it.scorePercent }.thenBy { it.title },
        )
    }

    private fun topicTrend(chronological: List<SolveSession>): Trend {
        if (chronological.size < TOPIC_TREND_MIN_SESSIONS) return Trend.UNKNOWN
        val q = chronological.mapNotNull { it.outcome }.map { SkillTracker.baseQuality(it) }
        val recent = q.takeLast(TOPIC_TREND_WINDOW).average()
        val previous = q.dropLast(TOPIC_TREND_WINDOW).takeLast(TOPIC_TREND_WINDOW).average()
        return when {
            recent - previous >= TOPIC_TREND_DELTA -> Trend.UP
            previous - recent >= TOPIC_TREND_DELTA -> Trend.DOWN
            else -> Trend.FLAT
        }
    }
}
