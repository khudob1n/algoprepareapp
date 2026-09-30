package com.algoprep.app

import android.app.Application
import com.algoprep.app.data.seed.SeedLoader
import com.algoprep.app.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AlgoPrepApp : Application() {
    @Inject lateinit var seedLoader: SeedLoader
    @Inject @field:ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // UI observes Room flows, so screens fill in as soon as seeding commits.
        appScope.launch { seedLoader.seedIfNeeded() }
    }
}
