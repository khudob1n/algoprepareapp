package com.algoprep.app.ui.screens.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoprep.app.BuildConfig
import com.algoprep.app.R
import com.algoprep.app.domain.model.ReminderKind
import com.algoprep.app.ui.screens.onboarding.ReminderRow
import com.algoprep.app.ui.screens.onboarding.reminderDescription
import com.algoprep.app.ui.screens.onboarding.reminderTitle

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineMedium) }
        item {
            Text(
                stringResource(R.string.profile_reminders),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.profile_reminders_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!notificationsEnabled && reminders.anyEnabled) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.profile_notifications_off), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.profile_notifications_off_body), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Button(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                    Text(stringResource(R.string.profile_allow))
                                }
                            }
                            OutlinedButton(onClick = { openNotificationSettings(context) }) {
                                Text(stringResource(R.string.profile_open_settings))
                            }
                        }
                    }
                }
            }
        }
        items(ReminderKind.entries.toList(), key = { it.name }) { kind ->
            ReminderRow(
                title = stringResource(reminderTitle(kind)),
                description = stringResource(reminderDescription(kind)),
                slot = reminders[kind],
                onChange = { viewModel.setReminder(kind, it) },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = viewModel::sendTestNotification, enabled = notificationsEnabled) {
                    Text(stringResource(R.string.profile_test_notification))
                }
                TextButton(onClick = viewModel::disableAll, enabled = reminders.anyEnabled) {
                    Text(stringResource(R.string.profile_disable_all))
                }
            }
        }
        item {
            Text(
                stringResource(R.string.profile_about_local, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
