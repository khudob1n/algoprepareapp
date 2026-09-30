package com.algoprep.app.ui.screens.onboarding

import android.Manifest
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.algoprep.app.R
import com.algoprep.app.domain.model.InterviewGoal
import com.algoprep.app.domain.model.ReminderKind
import com.algoprep.app.domain.model.ReminderSlot
import com.algoprep.app.domain.model.StartOption
import com.algoprep.app.domain.model.UserLevel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private const val TOTAL_STEPS = 3

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("• " + stringResource(R.string.onboarding_welcome_point1))
            Text("• " + stringResource(R.string.onboarding_welcome_point2))
            Text("• " + stringResource(R.string.onboarding_welcome_point3))
        }
        Button(onClick = onStart, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.action_get_started))
        }
    }
}

@Composable
fun GoalsScreen(
    state: OnboardingUiState,
    onLevel: (UserLevel) -> Unit,
    onGoal: (InterviewGoal) -> Unit,
    onMinutes: (Int) -> Unit,
    onStart: (StartOption) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    OnboardingStep(
        step = 1,
        totalSteps = TOTAL_STEPS,
        title = stringResource(R.string.onboarding_goals_title),
        subtitle = null,
        primaryText = stringResource(R.string.action_next),
        onPrimary = onNext,
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel(R.string.onboarding_level_title)
            UserLevel.entries.forEach { level ->
                OptionCard(
                    title = stringResource(levelTitle(level)),
                    subtitle = stringResource(levelDescription(level)),
                    selected = state.level == level,
                    onClick = { onLevel(level) },
                )
            }

            SectionLabel(R.string.onboarding_goal_title)
            InterviewGoal.entries.forEach { goal ->
                OptionCard(
                    title = stringResource(goalTitle(goal)),
                    selected = state.goal == goal,
                    onClick = { onGoal(goal) },
                )
            }

            SectionLabel(R.string.onboarding_daily_title)
            Text(formatDuration(state.dailyMinutes), style = MaterialTheme.typography.titleLarge)
            Slider(
                value = state.dailyMinutes.toFloat(),
                onValueChange = { onMinutes((it / 15f).roundToInt() * 15) },
                valueRange = 30f..240f,
                steps = 13,
            )

            SectionLabel(R.string.onboarding_start_title)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StartOption.entries.forEach { option ->
                    FilterChip(
                        selected = state.startOption == option,
                        onClick = { onStart(option) },
                        label = { Text(stringResource(startTitle(option))) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(textRes: Int) {
    Text(
        stringResource(textRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
fun TopicsScreen(
    state: OnboardingUiState,
    onRating: (String, Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    OnboardingStep(
        step = 2,
        totalSteps = TOTAL_STEPS,
        title = stringResource(R.string.onboarding_topics_title),
        subtitle = stringResource(R.string.onboarding_topics_subtitle),
        primaryText = stringResource(R.string.action_next),
        primaryEnabled = state.topics.isNotEmpty(),
        onPrimary = onNext,
        onBack = onBack,
    ) {
        if (state.topics.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.onboarding_topics_loading),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.topics, key = { it.id }) { topic ->
                    Column {
                        Text(topic.title, style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val current = state.ratingFor(topic.id)
                            (1..5).forEach { value ->
                                FilterChip(
                                    selected = current == value,
                                    onClick = { onRating(topic.id, value) },
                                    label = { Text(value.toString()) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemindersScreen(
    state: OnboardingUiState,
    onReminder: (ReminderKind, ReminderSlot) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onFinish() } // finish regardless of the answer; reminders simply stay silent if denied

    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        state.reminders.anyEnabled &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED

    OnboardingStep(
        step = 3,
        totalSteps = TOTAL_STEPS,
        title = stringResource(R.string.onboarding_reminders_title),
        subtitle = stringResource(R.string.onboarding_reminders_subtitle),
        primaryText = stringResource(R.string.action_build_plan),
        primaryEnabled = !state.isSaving,
        onPrimary = {
            if (needsPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onFinish()
            }
        },
        onBack = if (state.isSaving) null else onBack,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReminderKind.entries.forEach { kind ->
                ReminderRow(
                    title = stringResource(reminderTitle(kind)),
                    description = stringResource(reminderDescription(kind)),
                    slot = state.reminders[kind],
                    onChange = { onReminder(kind, it) },
                )
            }
            if (state.isSaving) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    Text(stringResource(R.string.onboarding_saving))
                }
            }
            if (state.saveFailed) {
                Text(
                    stringResource(R.string.onboarding_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ReminderRow(
    title: String,
    description: String,
    slot: ReminderSlot,
    onChange: (ReminderSlot) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = slot.enabled, onCheckedChange = { onChange(slot.copy(enabled = it)) })
        }
        TextButton(onClick = { showPicker = true }, enabled = slot.enabled) {
            Text(slot.time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
        }
    }
    if (showPicker) {
        TimePickerDialog(
            initial = slot.time,
            onDismiss = { showPicker = false },
            onConfirm = {
                showPicker = false
                onChange(slot.copy(time = it))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val pickerState = rememberTimePickerState(initial.hour, initial.minute, is24h)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = { TimePicker(state = pickerState) },
    )
}

private fun levelTitle(l: UserLevel) = when (l) {
    UserLevel.BEGINNER -> R.string.level_beginner
    UserLevel.INTERMEDIATE -> R.string.level_intermediate
    UserLevel.ADVANCED -> R.string.level_advanced
}

private fun levelDescription(l: UserLevel) = when (l) {
    UserLevel.BEGINNER -> R.string.level_beginner_desc
    UserLevel.INTERMEDIATE -> R.string.level_intermediate_desc
    UserLevel.ADVANCED -> R.string.level_advanced_desc
}

private fun goalTitle(g: InterviewGoal) = when (g) {
    InterviewGoal.BIG_TECH -> R.string.goal_big_tech
    InterviewGoal.ANY_COMPANY -> R.string.goal_any_company
    InterviewGoal.REFRESH -> R.string.goal_refresh
}

private fun startTitle(o: StartOption) = when (o) {
    StartOption.TODAY -> R.string.start_today
    StartOption.TOMORROW -> R.string.start_tomorrow
    StartOption.NEXT_MONDAY -> R.string.start_next_monday
}

private fun reminderTitle(k: ReminderKind) = when (k) {
    ReminderKind.MORNING -> R.string.reminder_morning
    ReminderKind.EVENING -> R.string.reminder_evening
    ReminderKind.REVIEW -> R.string.reminder_review
    ReminderKind.STREAK -> R.string.reminder_streak
}

private fun reminderDescription(k: ReminderKind) = when (k) {
    ReminderKind.MORNING -> R.string.reminder_morning_desc
    ReminderKind.EVENING -> R.string.reminder_evening_desc
    ReminderKind.REVIEW -> R.string.reminder_review_desc
    ReminderKind.STREAK -> R.string.reminder_streak_desc
}
