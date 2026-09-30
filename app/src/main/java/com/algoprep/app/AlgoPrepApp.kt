package com.algoprep.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.algoprep.app.background.ReplanScheduler
import com.algoprep.app.data.seed.SeedLoader
import com.algoprep.app.di.ApplicationScope
import com.algoprep.app.domain.repository.SettingsRepository
import com.algoprep.app.notifications.NotificationChannels
import com.algoprep.app.notifications.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AlgoPrepApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var seedLoader: SeedLoader
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var replanScheduler: ReplanScheduler
    @Inject @field:ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        replanScheduler.schedule()
        // UI observes Room flows, so screens fill in as soon as seeding commits.
        appScope.launch { seedLoader.seedIfNeeded() }
        // (Re)schedule reminders at startup and whenever the user changes them.
        appScope.launch {
            settings.observeReminders().distinctUntilChanged().collect { reminderScheduler.apply(it) }
        }
    }
}
