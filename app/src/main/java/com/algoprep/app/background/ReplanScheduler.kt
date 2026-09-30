package com.algoprep.app.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.algoprep.app.domain.reminders.ReminderTiming
import com.algoprep.app.domain.usecase.ReplanRemainingDays
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Runs the evening recalculation of the remaining plan once a day, late in the evening. */
@Singleton
class ReplanScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    fun schedule() {
        val delay = ReminderTiming.delayUntilNext(REPLAN_TIME, ZonedDateTime.now(clock))
        val request = OneTimeWorkRequestBuilder<ReplanWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        val REPLAN_TIME: LocalTime = LocalTime.of(22, 30)
        const val UNIQUE_NAME = "nightly_replan"
    }
}

@HiltWorker
class ReplanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val replan: ReplanRemainingDays,
    private val scheduler: ReplanScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            replan()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // never let one failed run stop the next night's run
        }
        scheduler.schedule()
        return Result.success()
    }
}
