package com.algoprep.app.domain.repository

import com.algoprep.app.domain.model.ErrorEntry
import com.algoprep.app.domain.model.Pattern
import com.algoprep.app.domain.model.ReminderSettings
import com.algoprep.app.domain.model.PlanDay
import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.model.PlannedStatus
import com.algoprep.app.domain.model.ReviewState
import com.algoprep.app.domain.model.RoadmapDay
import com.algoprep.app.domain.model.SessionType
import com.algoprep.app.domain.model.SolveSession
import com.algoprep.app.domain.model.Task
import com.algoprep.app.domain.model.TaskStatus
import com.algoprep.app.domain.model.Topic
import com.algoprep.app.domain.model.TopicSkill
import com.algoprep.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface CatalogRepository {
    fun observeTopics(): Flow<List<Topic>>
    fun observePatterns(): Flow<List<Pattern>>
    fun observeRoadmap(): Flow<List<RoadmapDay>>
    suspend fun getRoadmap(): List<RoadmapDay>
}

interface TaskRepository {
    fun observeAll(): Flow<List<Task>>
    fun observe(id: Long): Flow<Task?>
    fun observeByIds(ids: Collection<Long>): Flow<List<Task>>
    suspend fun get(id: Long): Task?
    suspend fun getByTopics(topicIds: Set<String>): List<Task>
    /** Inserts a new task with its topic/pattern links and mentions in one transaction. */
    suspend fun insertNew(task: Task): Long
    suspend fun updateNotes(id: Long, notes: String)
    /** Moves LEARNING/MASTERED tasks whose review date has arrived to REVIEW. */
    suspend fun markDueTasksForReview(now: Instant)
    suspend fun updateProgress(
        id: Long,
        status: TaskStatus,
        timesSolved: Int,
        lastSolvedAt: Instant?,
        nextReviewAt: Instant?,
        confidence: Int?,
    )
}

interface ProfileRepository {
    fun observeProfile(): Flow<UserProfile?>
    fun observeSkills(): Flow<List<TopicSkill>>
    suspend fun getSkills(): List<TopicSkill>
    /** Saves the profile and creates initial topic skills from self ratings (1..5) atomically. */
    suspend fun saveOnboarding(profile: UserProfile, selfRatings: Map<String, Int>)
    suspend fun upsertSkills(skills: List<TopicSkill>)
}

interface PlanRepository {
    fun observeDays(): Flow<List<PlanDay>>
    fun observeDay(dayIndex: Int): Flow<PlanDay?>
    suspend fun getDay(dayIndex: Int): PlanDay?
    suspend fun hasPlan(): Boolean
    /** Replaces the whole plan (days, day topics and items). Item ids of 0 are auto-generated. */
    suspend fun replacePlan(days: List<PlanDay>)
    suspend fun replaceDayItems(day: PlanDay)
    suspend fun setItemStatus(itemId: Long, status: PlannedStatus, sessionId: Long?)
    suspend fun setDayStatus(dayIndex: Int, status: PlanDayStatus)
}

interface TrainingRepository {
    suspend fun startSession(taskId: Long, plannedItemId: Long?, type: SessionType): Long
    suspend fun getSession(id: Long): SolveSession?
    fun observeSession(id: Long): Flow<SolveSession?>
    suspend fun getActiveSession(): SolveSession?
    fun observeActiveSession(): Flow<SolveSession?>
    suspend fun deleteSession(id: Long)
    suspend fun saveSession(session: SolveSession)
    fun observeFinishedSessions(): Flow<List<SolveSession>>
    suspend fun sessionsForTask(taskId: Long): List<SolveSession>

    suspend fun addErrors(errors: List<ErrorEntry>)
    fun observeUnresolvedErrors(): Flow<List<ErrorEntry>>
    fun observeAllErrors(): Flow<List<ErrorEntry>>
    suspend fun resolveError(id: Long)
    suspend fun resolveErrorsForTask(taskId: Long)

    suspend fun getReviewState(taskId: Long): ReviewState?
    suspend fun upsertReviewState(state: ReviewState)
    suspend fun getDueReviews(now: Instant): List<ReviewState>
}

/** Runs several repository calls atomically (backed by a Room transaction). */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

interface SettingsRepository {
    fun observeReminders(): Flow<ReminderSettings>
    suspend fun saveReminders(settings: ReminderSettings)
}
