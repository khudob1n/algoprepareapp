package com.algoprep.app.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.algoprep.app.domain.model.ReminderKind
import com.algoprep.app.domain.reminders.BuildReminder
import com.algoprep.app.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepository,
    private val buildReminder: BuildReminder,
    private val notifier: ReminderNotifier,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND)
            ?.let { name -> ReminderKind.entries.firstOrNull { it.name == name } }
            ?: return Result.failure()

        val slot = settings.observeReminders().first()[kind]
        if (!slot.enabled) return Result.success()

        val content = try {
            buildReminder(kind)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // a failed lookup must not stop tomorrow's reminder
        }
        content?.let { notifier.show(kind, it) }

        // Always schedule the next occurrence, even when there was nothing to say today.
        scheduler.schedule(kind, slot.time)
        return Result.success()
    }

    companion object {
        const val KEY_KIND = "kind"
    }
}
