package com.algoprep.app.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoprep.app.R
import com.algoprep.app.domain.stats.DayActivity
import com.algoprep.app.domain.stats.Stats
import com.algoprep.app.domain.stats.TopicLevel
import com.algoprep.app.domain.stats.TopicStat
import com.algoprep.app.domain.stats.Trend
import com.algoprep.app.ui.components.formatDuration
import com.algoprep.app.ui.screens.result.errorTypeLabel

@Composable
fun StatsScreen(onOpenErrors: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        StatsUiState.Loading -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }
        is StatsUiState.Ready -> StatsContent(s.stats, onOpenErrors)
    }
}

@Composable
private fun StatsContent(stats: Stats, onOpenErrors: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.headlineMedium) }
        item { OverviewCard(stats) }
        item { ProgressCard(stats) }
        item { AdherenceCard(stats) }
        item { ActivityCard(stats.activity) }
        item {
            Text(stringResource(R.string.stats_topics), style = MaterialTheme.typography.titleMedium)
        }
        items(stats.topics, key = { it.topicId }) { TopicRow(it) }
        item { ErrorsCard(stats, onOpenErrors) }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun BigNumber(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OverviewCard(stats: Stats) {
    val o = stats.overview
    SectionCard(stringResource(R.string.stats_overview)) {
        if (o.attempts == 0) {
            Text(stringResource(R.string.stats_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val independent = o.independentPercent?.let { stringResource(R.string.percent_format, it) } ?: "—"
            val avg = o.avgSolveMinutes?.let { formatDuration(it) } ?: "—"
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigNumber(o.currentStreak.toString(), stringResource(R.string.stats_streak))
                BigNumber(o.solved.toString(), stringResource(R.string.stats_solved))
                BigNumber(independent, stringResource(R.string.stats_independent))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigNumber(avg, stringResource(R.string.stats_avg_time))
                BigNumber(formatDuration(o.totalMinutes), stringResource(R.string.stats_total_time))
                BigNumber(o.longestStreak.toString(), stringResource(R.string.stats_longest_streak))
            }
            Text(
                stringResource(R.string.stats_attempts_unique, o.attempts, o.uniqueSolved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!o.practicedToday) {
                Text(stringResource(R.string.stats_not_practiced_today), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ProgressCard(stats: Stats) {
    val p = stats.progress
    SectionCard(stringResource(R.string.stats_progress_title)) {
        val recent = p.recentIndependentPercent
        val previous = p.previousIndependentPercent
        if (recent == null || previous == null) {
            Text(stringResource(R.string.stats_progress_unknown), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                stringResource(R.string.stats_progress_compare, recent, previous),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(
                    when (p.trend) {
                        Trend.UP -> R.string.stats_trend_up
                        Trend.DOWN -> R.string.stats_trend_down
                        else -> R.string.stats_trend_flat
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdherenceCard(stats: Stats) {
    val a = stats.adherence
    SectionCard(stringResource(R.string.stats_adherence_title)) {
        if (a.totalDays == 0) {
            Text(stringResource(R.string.stats_no_plan), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            a.currentDayIndex?.let {
                Text(stringResource(R.string.today_day_of, it, a.totalDays), style = MaterialTheme.typography.labelLarge)
            }
            val percent = a.percent
            if (percent != null) {
                Text(stringResource(R.string.stats_adherence_days, a.doneDays, a.evaluatedDays, percent))
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            } else {
                Text(stringResource(R.string.stats_adherence_none_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (a.plannedMinutes > 0) {
                Text(
                    stringResource(
                        R.string.stats_adherence_minutes,
                        formatDuration(a.actualMinutes),
                        formatDuration(a.plannedMinutes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActivityCard(activity: List<DayActivity>) {
    SectionCard(stringResource(R.string.stats_activity_title)) {
        val max = activity.maxOfOrNull { it.minutes } ?: 0
        if (max == 0) {
            Text(stringResource(R.string.stats_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val barColor = MaterialTheme.colorScheme.primary
            val emptyColor = MaterialTheme.colorScheme.surfaceVariant
            Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
                val gap = 3.dp.toPx()
                val barWidth = (size.width - gap * (activity.size - 1)) / activity.size
                activity.forEachIndexed { i, day ->
                    val h = if (day.minutes == 0) 3.dp.toPx() else size.height * day.minutes / max
                    drawRoundRect(
                        color = if (day.minutes == 0) emptyColor else barColor,
                        topLeft = Offset(i * (barWidth + gap), size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
            Text(
                stringResource(R.string.stats_activity_caption, max),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TopicRow(topic: TopicStat) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(topic.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                stringResource(levelLabel(topic.level)) + trendMark(topic.trend),
                style = MaterialTheme.typography.labelMedium,
                color = if (topic.level == TopicLevel.WEAK) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (topic.level != TopicLevel.NO_DATA) {
            LinearProgressIndicator(
                progress = { topic.scorePercent / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Text(
                stringResource(R.string.stats_topic_detail, topic.solved, topic.attempts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorsCard(stats: Stats, onOpenErrors: () -> Unit) {
    SectionCard(stringResource(R.string.errors_title)) {
        if (stats.openErrors == 0) {
            Text(stringResource(R.string.stats_no_open_errors), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(stringResource(R.string.errors_open_summary, stats.openErrors))
            stats.openErrorsByType.forEach { (type, n) ->
                Text("• " + stringResource(errorTypeLabel(type)) + " — " + n, style = MaterialTheme.typography.bodyMedium)
            }
        }
        OutlinedButton(onClick = onOpenErrors) { Text(stringResource(R.string.stats_open_error_log)) }
    }
}

private fun levelLabel(l: TopicLevel) = when (l) {
    TopicLevel.WEAK -> R.string.topic_level_weak
    TopicLevel.DEVELOPING -> R.string.topic_level_developing
    TopicLevel.STRONG -> R.string.topic_level_strong
    TopicLevel.NO_DATA -> R.string.topic_level_no_data
}

private fun trendMark(t: Trend) = when (t) {
    Trend.UP -> "  ↑"
    Trend.DOWN -> "  ↓"
    Trend.FLAT -> "  →"
    Trend.UNKNOWN -> ""
}
