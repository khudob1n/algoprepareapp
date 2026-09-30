package com.algoprep.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.algoprep.app.data.db.AppDatabase
import com.algoprep.app.data.repository.CatalogRepositoryImpl
import com.algoprep.app.data.repository.PlanRepositoryImpl
import com.algoprep.app.data.repository.ProfileRepositoryImpl
import com.algoprep.app.data.repository.RoomTransactionRunner
import com.algoprep.app.data.repository.TaskRepositoryImpl
import com.algoprep.app.data.repository.TrainingRepositoryImpl
import com.algoprep.app.data.seed.SeedLoader
import com.algoprep.app.domain.model.InterviewGoal
import com.algoprep.app.domain.model.PlannedKind
import com.algoprep.app.domain.model.PlannedStatus
import com.algoprep.app.domain.model.ReminderSettings
import com.algoprep.app.domain.model.SessionType
import com.algoprep.app.domain.model.SolveOutcome
import com.algoprep.app.domain.model.TaskStatus
import com.algoprep.app.domain.model.UserLevel
import com.algoprep.app.domain.repository.SettingsRepository
import com.algoprep.app.domain.usecase.CompleteOnboarding
import com.algoprep.app.domain.usecase.CompleteSession
import com.algoprep.app.domain.usecase.CompleteSessionInput
import com.algoprep.app.domain.usecase.EnsureTodayPlan
import com.algoprep.app.domain.usecase.OnboardingInput
import com.algoprep.app.domain.usecase.TodayPlanResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private class InMemorySettings : SettingsRepository {
    private val flow = MutableStateFlow(ReminderSettings.DEFAULT)
    override fun observeReminders(): Flow<ReminderSettings> = flow
    override suspend fun saveReminders(settings: ReminderSettings) {
        flow.value = settings
    }
}

/** The real repositories on a real Room database: onboarding -> today's plan -> one solved task. */
class EndToEndFlowTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-12T08:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 10, 12)

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        runBlocking { SeedLoader(context, db, clock).seedIfNeeded() }
    }

    @After fun tearDown() = db.close()

    @Test fun onboardingPlanSessionAndResultWorkTogether() = runBlocking {
        val catalog = CatalogRepositoryImpl(db)
        val tasks = TaskRepositoryImpl(db, clock)
        val profiles = ProfileRepositoryImpl(db, clock)
        val plans = PlanRepositoryImpl(db)
        val training = TrainingRepositoryImpl(db, clock)
        val tx = RoomTransactionRunner(db)

        // onboarding
        val topics = catalog.observeTopics().first()
        CompleteOnboarding(catalog, profiles, plans, InMemorySettings(), clock)(
            OnboardingInput(UserLevel.INTERMEDIATE, InterviewGoal.BIG_TECH, 120, today, topics.associate { it.id to 3 }, ReminderSettings.DEFAULT),
        )
        assertEquals(30, plans.observeDays().first().size)
        assertNotNull(profiles.observeProfile().first())
        assertEquals(topics.size, profiles.getSkills().size)

        // today's plan
        val ensure = EnsureTodayPlan(profiles, plans, catalog, tasks, training, clock)
        assertEquals(TodayPlanResult.Ready(1), ensure())
        val day = plans.getDay(1)!!
        assertEquals(PlannedKind.THEORY, day.items.first().kind)
        val item = day.items.first { it.taskId != null }
        assertEquals(item.id, plans.getDay(1)!!.items.first { it.taskId != null }.id)
        ensure()
        assertEquals("items stay stable", day.items.map { it.id }, plans.getDay(1)!!.items.map { it.id })

        // a session and its result
        val sessionId = training.startSession(item.taskId!!, item.id, SessionType.PRACTICE)
        training.saveSession(training.getSession(sessionId)!!.copy(durationSec = 600))
        assertTrue(CompleteSession(training, tasks, plans, profiles, tx, clock)(CompleteSessionInput(sessionId, SolveOutcome.INDEPENDENT, 5, emptySet(), null)))

        val task = tasks.get(item.taskId!!)!!
        assertEquals(1, task.timesSolved)
        assertEquals(TaskStatus.LEARNING, task.status)
        assertNotNull(task.nextReviewAt)
        assertEquals(1, training.getReviewState(task.id)!!.repetitions)
        assertEquals(PlannedStatus.DONE, plans.getDay(1)!!.items.first { it.id == item.id }.status)
        assertTrue(profiles.getSkills().filter { it.topicId in task.topics }.all { it.attempts == 1 })
        assertEquals(1, training.observeFinishedSessions().first().size)
    }

    @Test fun savingTheOnboardingTwiceKeepsTheSkillsThatHaveAttempts() = runBlocking {
        val catalog = CatalogRepositoryImpl(db)
        val profiles = ProfileRepositoryImpl(db, clock)
        val plans = PlanRepositoryImpl(db)
        val input = OnboardingInput(UserLevel.BEGINNER, InterviewGoal.REFRESH, 60, today, catalog.observeTopics().first().associate { it.id to 2 }, ReminderSettings.DEFAULT)
        val onboarding = CompleteOnboarding(catalog, profiles, plans, InMemorySettings(), clock)
        onboarding(input)
        onboarding(input)
        assertEquals(30, plans.observeDays().first().size)
        assertEquals(catalog.observeTopics().first().size, profiles.getSkills().size)
    }
}
