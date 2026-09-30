package com.algoprep.app.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.algoprep.app.domain.model.ReminderKind
import com.algoprep.app.domain.model.ReminderSettings
import com.algoprep.app.domain.reminders.ReminderTiming
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One self-rescheduling one-time job per reminder: unlike PeriodicWork, it can target a wall-clock time
 * chosen by the user. Android may delay delivery by a few minutes (Doze); the UI says so.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    fun apply(settings: ReminderSettings) {
        for (kind in ReminderKind.entries) {
            val slot = settings[kind]
            if (slot.enabled) schedule(kind, slot.time) else cancel(kind)
        }
    }

    fun schedule(kind: ReminderKind, time: LocalTime) {
        val delay = ReminderTiming.delayUntilNext(time, ZonedDateTime.now(clock))
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_KIND to kind.name))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(kind), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(kind: ReminderKind) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(kind))
    }

    private fun uniqueName(kind: ReminderKind) = "reminder_${kind.name.lowercase()}"
}
