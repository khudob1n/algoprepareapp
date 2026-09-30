package com.algoprep.app.ui.screens.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TodayScreen(
    onOpenTask: (TodayItem) -> Unit,
    onOpenSession: (SolveSession) -> Unit,
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
    onToggleDone: (Long, Boolean) -> Unit,
) {
    var whyItem by remember { mutableStateOf<TodayItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        ) {
            item { TodayHeader(content) }
            section(R.string.today_section_intro, content.intro, onOpenTask, onToggleDone) { whyItem = it }
            section(R.string.today_section_main, content.main, onOpenTask, onToggleDone) { whyItem = it }
            section(R.string.today_section_review, content.review, onOpenTask, onToggleDone) { whyItem = it }
            section(R.string.today_section_error, content.errorReview, onOpenTask, onToggleDone) { whyItem = it }
        }
        val next = content.nextTaskItem
        Button(
            onClick = {
                if (activeSession != null) onOpenSession(activeSession) else next?.let(onOpenTask)
            },
            enabled = activeSession != null || next != null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
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
            )
        }
    }

    whyItem?.let { WhyDialog(it, topicTitles, onDismiss = { whyItem = null }) }
}

@Composable
private fun TodayHeader(content: TodayContent) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            stringResource(R.string.today_day_of, content.dayIndex, content.totalDays),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(content.title, style = MaterialTheme.typography.headlineMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(progress = { content.progress }, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.percent_format, content.progressPercent), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = stringResource(
                R.string.today_time_summary,
                formatDuration(content.plannedMinutes),
                formatDuration(content.remainingMinutes),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun LazyListScope.section(
    titleRes: Int,
    list: List<TodayItem>,
    onOpenTask: (TodayItem) -> Unit,
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
