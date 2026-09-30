package com.algoprep.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.algoprep.app.data.db.AppDatabase
import com.algoprep.app.data.db.dao.CatalogDao
import com.algoprep.app.data.db.dao.ImportDao
import com.algoprep.app.data.db.dao.PlanDao
import com.algoprep.app.data.db.dao.ProfileDao
import com.algoprep.app.data.db.dao.TaskDao
import com.algoprep.app.data.db.dao.TrainingDao
import com.algoprep.app.domain.ai.AiAssistant
import com.algoprep.app.domain.ai.NoAiAssistant
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        // No destructive fallback: every schema change must ship a Migration.
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides fun provideCatalogDao(db: AppDatabase): CatalogDao = db.catalogDao()
    @Provides fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()
    @Provides fun providePlanDao(db: AppDatabase): PlanDao = db.planDao()
    @Provides fun provideTrainingDao(db: AppDatabase): TrainingDao = db.trainingDao()
    @Provides fun provideImportDao(db: AppDatabase): ImportDao = db.importDao()

    @Provides @Singleton
    fun provideAiAssistant(): AiAssistant = NoAiAssistant()
}
