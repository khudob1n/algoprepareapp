package com.algoprep.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.algoprep.app.R
import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.PlannedKind
import com.algoprep.app.ui.screens.today.TodayItem

@Composable
fun PlannedItemRow(
    item: TodayItem,
    onClick: (() -> Unit)?,
    onWhy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clickable = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Row(
        modifier = clickable.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (item.done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (item.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = itemTitle(item),
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            itemSubtitle(item)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = formatDuration(item.estimatedMin),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.reasons.isNotEmpty()) {
            IconButton(onClick = onWhy) {
                Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.why_button))
            }
        }
    }
}

@Composable
private fun itemTitle(item: TodayItem): String = when {
    item.kind == PlannedKind.THEORY -> stringResource(R.string.today_item_theory)
    item.kind == PlannedKind.MOCK -> stringResource(R.string.today_item_mock)
    else -> item.taskTitle ?: stringResource(R.string.today_item_removed_task)
}

@Composable
private fun itemSubtitle(item: TodayItem): String? = when (item.kind) {
    PlannedKind.THEORY -> item.theoryText
    PlannedKind.WARMUP -> stringResource(R.string.today_item_warmup)
    PlannedKind.ERROR_REVIEW -> stringResource(R.string.today_item_error_review)
    PlannedKind.MOCK -> stringResource(R.string.today_item_mock_desc)
    else -> item.difficulty?.let { stringResource(difficultyLabel(it)) }
}

fun difficultyLabel(d: Difficulty): Int = when (d) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.MEDIUM -> R.string.difficulty_medium
    Difficulty.HARD -> R.string.difficulty_hard
}

/** "Why was I given this task?" — lists the structured reasons stored with the plan item. */
@Composable
fun WhyDialog(item: TodayItem, topicTitles: Map<String, String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.why_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.why_intro))
                item.reasons.forEach { Text("• " + reasonText(it, topicTitles)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}
