package com.algoprep.app.domain.stats

import com.algoprep.app.domain.model.ErrorType
import java.time.LocalDate

enum class Trend { UP, FLAT, DOWN, UNKNOWN }

enum class TopicLevel { WEAK, DEVELOPING, STRONG, NO_DATA }

data class OverviewStats(
    val attempts: Int,
    val solved: Int,
    val uniqueSolved: Int,
    /** Share of attempts solved without hints; null when there are no attempts. */
    val independentPercent: Int?,
    val avgSolveMinutes: Int?,
    val totalMinutes: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val practicedToday: Boolean,
)

data class TopicStat(
    val topicId: String,
    val title: String,
    val attempts: Int,
    val solved: Int,
    val scorePercent: Int,
    val level: TopicLevel,
    val trend: Trend,
)

data class DayActivity(val date: LocalDate, val minutes: Int, val attempts: Int)

data class PlanAdherence(
    val currentDayIndex: Int?,
    val totalDays: Int,
    val doneDays: Int,
    val missedDays: Int,
    /** Days that have ended or were completed; the denominator of [percent]. */
    val evaluatedDays: Int,
    val percent: Int?,
    val plannedMinutes: Int,
    val actualMinutes: Int,
)

data class ProgressTrend(
    val recentIndependentPercent: Int?,
    val previousIndependentPercent: Int?,
    val trend: Trend,
)

data class Stats(
    val overview: OverviewStats,
    val progress: ProgressTrend,
    val adherence: PlanAdherence,
    val activity: List<DayActivity>,
    val topics: List<TopicStat>,
    val openErrorsByType: List<Pair<ErrorType, Int>>,
    val openErrors: Int,
)
