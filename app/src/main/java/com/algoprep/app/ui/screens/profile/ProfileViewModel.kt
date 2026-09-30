package com.algoprep.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algoprep.app.domain.model.ReminderKind
import com.algoprep.app.domain.model.ReminderSettings
import com.algoprep.app.domain.model.ReminderSlot
import com.algoprep.app.domain.repository.SettingsRepository
import com.algoprep.app.notifications.ReminderNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val notifier: ReminderNotifier,
) : ViewModel() {
    val reminders: StateFlow<ReminderSettings> = settings.observeReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderSettings.DEFAULT)

    /** Saving is enough: the application observes settings and reschedules the background jobs. */
    fun setReminder(kind: ReminderKind, slot: ReminderSlot) {
        viewModelScope.launch { settings.saveReminders(reminders.value.withSlot(kind, slot)) }
    }

    fun disableAll() {
        viewModelScope.launch {
            val current = reminders.value
            settings.saveReminders(
                ReminderKind.entries.fold(current) { acc, kind -> acc.withSlot(kind, acc[kind].copy(enabled = false)) },
            )
        }
    }

    fun sendTestNotification() = notifier.showTest()
}
