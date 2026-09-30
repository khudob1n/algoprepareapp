package com.algoprep.app.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoprep.app.R
import com.algoprep.app.domain.model.PlannedKind
import com.algoprep.app.domain.model.SessionPhase
import com.algoprep.app.domain.model.SolveSession
import com.algoprep.app.domain.model.phase
import com.algoprep.app.ui.components.PlaceholderScreen
import com.algoprep.app.ui.components.PlannedItemRow
import com.algoprep.app.ui.components.WhyDialog
import com.algoprep.app.ui.components.formatDuration
import com.algoprep.app.ui.theme.HeroBrush
import com.algoprep.app.ui.theme.Lime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TodayScreen(
    onOpenTask: (TodayItem) -> Unit,
    onOpenSession: (SolveSession) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenMock: (plannedItemId: Long?) -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val topicTitles by viewModel.topicTitles.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    when (val s = state) {
        TodayUiState.Loading -> CenteredLoading()
        TodayUiState.NoPlan -> PlaceholderScreen(R.string.today_no_plan_title, R.string.today_no_plan_body)
        TodayUiState.Finished -> PlaceholderScreen(R.string.today_finished_title, R.string.today_finished_body)
        is TodayUiState.NotStarted -> {
            val date = s.startDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            PlaceholderMessage(stringResource(R.string.today_not_started, date))
        }
        is TodayUiState.Active -> TodayContentView(
            content = s.content,
            topicTitles = topicTitles,
            activeSession = activeSession,
            onOpenTask = onOpenTask,
            onOpenSession = onOpenSession,
            onOpenPlan = onOpenPlan,
            onOpenMock = onOpenMock,
            onToggleDone = viewModel::setItemDone,
        )
    }
}

@Composable
private fun TodayContentView(
    content: TodayContent,
    topicTitles: Map<String, String>,
    activeSession: SolveSession?,
    onOpenTask: (TodayItem) -> Unit,
    onOpenSession: (SolveSession) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenMock: (Long?) -> Unit,
    onToggleDone: (Long, Boolean) -> Unit,
) {
    var whyItem by remember { mutableStateOf<TodayItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        ) {
            item { TodayHeader(content) }
            item {
                androidx.compose.material3.TextButton(onClick = { onOpenMock(null) }) { Text(stringResource(R.string.today_mock_button)) }
            }
            if (content.behindDays >= BEHIND_HINT_DAYS) {
                item { BehindBanner(content.behindDays, onOpenPlan) }
            }
            section(R.string.today_section_intro, content.intro, onOpenTask, onOpenMock, onToggleDone) { whyItem = it }
            section(R.string.today_section_main, content.main, onOpenTask, onOpenMock, onToggleDone) { whyItem = it }
            section(R.string.today_section_review, content.review, onOpenTask, onOpenMock, onToggleDone) { whyItem = it }
            section(R.string.today_section_error, content.errorReview, onOpenTask, onOpenMock, onToggleDone) { whyItem = it }
        }
        val next = content.nextTaskItem
        Button(
            onClick = {
                when {
                    activeSession != null -> onOpenSession(activeSession)
                    next != null && next.kind == PlannedKind.MOCK -> onOpenMock(next.id)
                    else -> next?.let(onOpenTask)
                }
            },
            enabled = activeSession != null || next != null,
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 20.dp),
            shape = CircleShape,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Lime, contentColor = androidx.compose.ui.graphics.Color(0xFF1B2900)),
        ) {
            Text(
                stringResource(
                    when {
                        activeSession != null && activeSession.phase == SessionPhase.AWAITING_RESULT -> R.string.today_enter_result
                        activeSession != null -> R.string.today_resume_session
                        next != null -> R.string.today_start_next
                        content.hasItems -> R.string.today_all_done
                        else -> R.string.today_nothing_planned
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }

    whyItem?.let { WhyDialog(it, topicTitles, onDismiss = { whyItem = null }) }
}

@Composable
private fun TodayHeader(content: TodayContent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(HeroBrush)
            .padding(24.dp),
    ) {
        Text(
            stringResource(R.string.today_day_of, content.dayIndex, content.totalDays).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.8f),
        )
        Text(
            content.title,
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LinearProgressIndicator(
                progress = { content.progress },
                modifier = Modifier.weight(1f).height(10.dp).clip(CircleShape),
                color = Lime,
                trackColor = Color.White.copy(alpha = 0.25f),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Text(
                stringResource(R.string.percent_format, content.progressPercent),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
        }
        Text(
            text = stringResource(
                R.string.today_time_summary,
                formatDuration(content.plannedMinutes),
                formatDuration(content.remainingMinutes),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

private fun LazyListScope.section(
    titleRes: Int,
    list: List<TodayItem>,
    onOpenTask: (TodayItem) -> Unit,
    onOpenMock: (Long?) -> Unit,
    onToggleDone: (Long, Boolean) -> Unit,
    onWhy: (TodayItem) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "title-$titleRes") {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        )
    }
    items(list, key = { it.id }) { item ->
        PlannedItemRow(
            item = item,
            onClick = when {
                item.kind == PlannedKind.THEORY -> ({ onToggleDone(item.id, !item.done) })
                item.kind == PlannedKind.MOCK && !item.done -> ({ onOpenMock(item.id) })
                item.taskId != null && !item.done -> ({ onOpenTask(item) })
                else -> null
            },
            onWhy = { onWhy(item) },
        )
    }
}

@Composable
private fun CenteredLoading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { CircularProgressIndicator() }
}

@Composable
private fun PlaceholderMessage(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

private const val BEHIND_HINT_DAYS = 2

@Composable
private fun BehindBanner(days: Int, onOpenPlan: () -> Unit) {
    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                androidx.compose.ui.platform.LocalContext.current.resources.getQuantityString(R.plurals.recovery_title, days, days),
                style = MaterialTheme.typography.titleSmall,
            )
            androidx.compose.material3.TextButton(onClick = onOpenPlan) { Text(stringResource(R.string.today_behind_open_plan)) }
        }
    }
}
