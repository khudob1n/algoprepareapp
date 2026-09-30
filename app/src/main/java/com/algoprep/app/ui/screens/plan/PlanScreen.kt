package com.algoprep.app.ui.screens.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoprep.app.R
import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.ui.components.PlannedItemRow
import com.algoprep.app.ui.components.WhyDialog
import com.algoprep.app.ui.components.adjustReasonText
import com.algoprep.app.ui.components.formatDuration
import com.algoprep.app.domain.usecase.AdjustMode
import com.algoprep.app.ui.screens.today.TodayItem
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun PlanScreen(onOpenDay: (Int) -> Unit, viewModel: PlanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(stringResource(R.string.plan_title), style = MaterialTheme.typography.headlineMedium)
                if (state.totalDays > 0) {
                    Text(
                        stringResource(R.string.plan_adherence, state.doneDays, state.totalDays, state.missedDays),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = viewModel::replanNow, enabled = !state.replanning) {
                        Text(stringResource(if (state.replanning) R.string.plan_replanning else R.string.plan_replan_now))
                    }
                }
            }
        }
        state.recovery?.let { recovery ->
            item { RecoveryCard(recovery, onApply = viewModel::apply) }
        }
        items(state.rows, key = { it.day.dayIndex }) { row ->
            val d = row.day
            val isToday = d.status == PlanDayStatus.TODAY
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenDay(d.dayIndex) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isToday) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.plan_day_label, d.dayIndex),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(statusLabel(d.status)),
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor(d.status),
                        )
                    }
                    Text(d.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        d.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) + " · " +
                            formatDuration(d.targetMinutes),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (row.totalItems > 0) {
                        Text(
                            stringResource(R.string.plan_items_done, row.doneItems, row.totalItems),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (d.isAdjusted) {
                        val reason = adjustReasonText(d.adjustReason, state.topicTitles)
                        Text(
                            stringResource(R.string.plan_adjusted) + (reason?.let { ": $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryCard(recovery: RecoveryOptions, onApply: (AdjustMode) -> Unit) {
    var pending by remember { mutableStateOf<AdjustMode?>(null) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                LocalContext.current.resources.getQuantityString(R.plurals.recovery_title, recovery.missedDays, recovery.missedDays),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(stringResource(R.string.recovery_body), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pending = AdjustMode.SHIFT }) { Text(stringResource(R.string.recovery_shift)) }
                OutlinedButton(onClick = { pending = AdjustMode.COMPRESS }) { Text(stringResource(R.string.recovery_compress)) }
            }
        }
    }
    pending?.let { mode ->
        val res = LocalContext.current.resources
        val text = when (mode) {
            AdjustMode.SHIFT -> res.getQuantityString(R.plurals.recovery_shift_effect, recovery.shiftEndDays, recovery.shiftEndDays)
            AdjustMode.COMPRESS -> buildString {
                append(res.getQuantityString(R.plurals.recovery_compress_dropped, recovery.compressDropped, recovery.compressDropped))
                if (recovery.compressEndDays > 0) {
                    append(' ')
                    append(res.getQuantityString(R.plurals.recovery_compress_still_late, recovery.compressEndDays, recovery.compressEndDays))
                }
            }
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(if (mode == AdjustMode.SHIFT) R.string.recovery_shift else R.string.recovery_compress)) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    onApply(mode)
                    pending = null
                }) { Text(stringResource(R.string.recovery_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun statusColor(status: PlanDayStatus) = when (status) {
    PlanDayStatus.DONE -> MaterialTheme.colorScheme.primary
    PlanDayStatus.MISSED -> MaterialTheme.colorScheme.error
    PlanDayStatus.TODAY -> MaterialTheme.colorScheme.primary
    PlanDayStatus.UPCOMING -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusLabel(status: PlanDayStatus) = when (status) {
    PlanDayStatus.DONE -> R.string.plan_status_done
    PlanDayStatus.MISSED -> R.string.plan_status_missed
    PlanDayStatus.TODAY -> R.string.plan_status_today
    PlanDayStatus.UPCOMING -> R.string.plan_status_upcoming
}

@Composable
fun PlanDayScreen(onBack: () -> Unit, viewModel: PlanDayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var whyItem by remember { mutableStateOf<TodayItem?>(null) }
    val content = state.content

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            if (content != null) {
                Text(stringResource(R.string.plan_day_label, content.dayIndex), style = MaterialTheme.typography.titleMedium)
            }
        }
        if (content != null) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                item {
                    Column(modifier = Modifier.padding(bottom = 12.dp)) {
                        Text(content.title, style = MaterialTheme.typography.headlineSmall)
                        state.date?.let {
                            Text(
                                it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) + " · " +
                                    formatDuration(state.targetMinutes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            stringResource(R.string.plan_day_topics, state.topicsOfDay.joinToString(", ")),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (state.isAdjusted) {
                            Text(
                                stringResource(R.string.plan_adjusted) + (adjustReasonText(state.adjustReason, state.topicTitles)?.let { ": $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
                if (!content.hasItems) {
                    item {
                        Text(
                            stringResource(R.string.plan_day_not_generated),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(content.intro + content.main + content.review + content.errorReview, key = { it.id }) { item ->
                    PlannedItemRow(item = item, onClick = null, onWhy = { whyItem = item })
                }
            }
        }
    }
    whyItem?.let { WhyDialog(it, state.topicTitles, onDismiss = { whyItem = null }) }
}
