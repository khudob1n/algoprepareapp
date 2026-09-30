package com.algoprep.app.data.repository

import androidx.room.withTransaction
import com.algoprep.app.data.db.AppDatabase
import com.algoprep.app.data.db.entity.PlanDayTopicEntity
import com.algoprep.app.data.db.entity.TaskPatternEntity
import com.algoprep.app.data.db.entity.TaskTopicEntity
import com.algoprep.app.data.db.mapper.toDomain
import com.algoprep.app.data.db.mapper.toEntity
import com.algoprep.app.domain.model.ErrorEntry
import com.algoprep.app.domain.model.Pattern
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
import com.algoprep.app.domain.repository.CatalogRepository
import com.algoprep.app.domain.repository.PlanRepository
import com.algoprep.app.domain.repository.ProfileRepository
import com.algoprep.app.domain.repository.TaskRepository
import com.algoprep.app.domain.repository.TrainingRepository
import com.algoprep.app.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepositoryImpl @Inject constructor(db: AppDatabase) : CatalogRepository {
    private val dao = db.catalogDao()

    override fun observeTopics(): Flow<List<Topic>> = dao.observeTopics().map { l -> l.map { it.toDomain() } }
    override fun observePatterns(): Flow<List<Pattern>> = dao.observePatterns().map { l -> l.map { it.toDomain() } }
    override fun observeRoadmap(): Flow<List<RoadmapDay>> = dao.observeRoadmap().map { l -> l.map { it.toDomain() } }
    override suspend fun getRoadmap(): List<RoadmapDay> = dao.getRoadmap().map { it.toDomain() }
}

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) : TaskRepository {
    private val dao = db.taskDao()

    override fun observeAll(): Flow<List<Task>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    override fun observe(id: Long): Flow<Task?> = dao.observe(id).map { it?.toDomain() }
    override fun observeByIds(ids: Collection<Long>): Flow<List<Task>> =
        dao.observeByIds(ids).map { l -> l.map { it.toDomain() } }
    override suspend fun get(id: Long): Task? = dao.get(id)?.toDomain()

    override suspend fun getByTopics(topicIds: Set<String>): List<Task> =
        if (topicIds.isEmpty()) emptyList() else dao.getByTopics(topicIds).map { it.toDomain() }

    override suspend fun insertNew(task: Task): Long = db.withTransaction {
        val now = clock.millis()
        val id = dao.insert(task.copy(id = 0).toEntity(now))
        dao.insertTopicLinks(task.topics.map { TaskTopicEntity(id, it) })
        dao.insertPatternLinks(task.patterns.map { TaskPatternEntity(id, it) })
        dao.insertMentions(task.mentions.map { it.toEntity(id, now) })
        id
    }

    override suspend fun updateNotes(id: Long, notes: String) = dao.updateNotes(id, notes, clock.millis())

    override suspend fun updateProgress(
        id: Long,
        status: TaskStatus,
        timesSolved: Int,
        lastSolvedAt: Instant?,
        nextReviewAt: Instant?,
        confidence: Int?,
    ) = dao.updateProgress(
        id, status, timesSolved, lastSolvedAt?.toEpochMilli(), nextReviewAt?.toEpochMilli(), confidence, clock.millis(),
    )
}

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) : ProfileRepository {
    private val dao = db.profileDao()

    override fun observeProfile(): Flow<UserProfile?> = dao.observeProfile().map { it?.toDomain() }
    override fun observeSkills(): Flow<List<TopicSkill>> = dao.observeSkills().map { l -> l.map { it.toDomain() } }
    override suspend fun getSkills(): List<TopicSkill> = dao.getSkills().map { it.toDomain() }

    override suspend fun saveOnboarding(profile: UserProfile, selfRatings: Map<String, Int>) {
        db.withTransaction {
            dao.upsertProfile(profile.toEntity())
            val now = clock.millis()
            val existing = dao.getSkills().associateBy { it.topicId }
            // Never overwrite skills that already have attempts (e.g. onboarding re-run).
            val fresh = selfRatings.filterKeys { existing[it]?.attempts.let { a -> a == null || a == 0 } }
                .map { (topicId, rating) ->
                    val r = rating.coerceIn(1, 5)
                    TopicSkill(topicId, r, (r - 1) / 4.0, 0, 0, null).toEntity(now)
                }
            dao.upsertSkills(fresh)
        }
    }

    override suspend fun upsertSkills(skills: List<TopicSkill>) {
        val now = clock.millis()
        dao.upsertSkills(skills.map { it.toEntity(now) })
    }
}

@Singleton
class PlanRepositoryImpl @Inject constructor(private val db: AppDatabase) : PlanRepository {
    private val dao = db.planDao()

    override fun observeDays(): Flow<List<PlanDay>> = dao.observeDays().map { l -> l.map { it.toDomain() } }
    override fun observeDay(dayIndex: Int): Flow<PlanDay?> = dao.observeDay(dayIndex).map { it?.toDomain() }
    override suspend fun getDay(dayIndex: Int): PlanDay? = dao.getDay(dayIndex)?.toDomain()
    override suspend fun hasPlan(): Boolean = dao.dayCount() > 0

    override suspend fun replacePlan(days: List<PlanDay>) {
        db.withTransaction {
            dao.clearPlan()
            dao.insertDays(days.map { it.toEntity() })
            dao.insertDayTopics(days.flatMap { d -> d.topicIds.map { PlanDayTopicEntity(d.dayIndex, it) } })
            dao.insertItems(days.flatMap { d -> d.items.map { it.copy(dayIndex = d.dayIndex).toEntity() } })
        }
    }

    override suspend fun replaceDayItems(day: PlanDay) {
        db.withTransaction {
            dao.updateDay(day.toEntity())
            dao.deleteTopicsForDay(day.dayIndex)
            dao.insertDayTopics(day.topicIds.map { PlanDayTopicEntity(day.dayIndex, it) })
            dao.deleteItemsForDay(day.dayIndex)
            dao.insertItems(day.items.map { it.copy(dayIndex = day.dayIndex).toEntity() })
        }
    }

    override suspend fun setItemStatus(itemId: Long, status: PlannedStatus, sessionId: Long?) =
        dao.setItemStatus(itemId, status, sessionId)

    override suspend fun setDayStatus(dayIndex: Int, status: PlanDayStatus) = dao.setDayStatus(dayIndex, status)
}

@Singleton
class TrainingRepositoryImpl @Inject constructor(
    db: AppDatabase,
    private val clock: Clock,
) : TrainingRepository {
    private val dao = db.trainingDao()

    override suspend fun startSession(taskId: Long, plannedItemId: Long?, type: SessionType): Long {
        val now = clock.instant()
        val session = SolveSession(
            id = 0, taskId = taskId, plannedItemId = plannedItemId, type = type,
            startedAt = now, finishedAt = null, durationSec = 0, hintsUsed = 0,
            outcome = null, confidence = null, notes = "",
            localDate = LocalDate.now(clock),
        )
        return dao.insertSession(session.toEntity())
    }

    override suspend fun getSession(id: Long): SolveSession? = dao.getSession(id)?.toDomain()
    override fun observeSession(id: Long): Flow<SolveSession?> = dao.observeSession(id).map { it?.toDomain() }
    override suspend fun getActiveSession(): SolveSession? = dao.getActiveSession()?.toDomain()
    override fun observeActiveSession(): Flow<SolveSession?> = dao.observeActiveSession().map { it?.toDomain() }
    override suspend fun deleteSession(id: Long) = dao.deleteSession(id)
    override suspend fun saveSession(session: SolveSession) = dao.updateSession(session.toEntity())

    override fun observeFinishedSessions(): Flow<List<SolveSession>> =
        dao.observeFinishedSessions().map { l -> l.map { it.toDomain() } }

    override suspend fun sessionsForTask(taskId: Long): List<SolveSession> =
        dao.sessionsForTask(taskId).map { it.toDomain() }

    override suspend fun addErrors(errors: List<ErrorEntry>) = dao.insertErrors(errors.map { it.copy(id = 0).toEntity() })

    override fun observeUnresolvedErrors(): Flow<List<ErrorEntry>> =
        dao.observeUnresolvedErrors().map { l -> l.map { it.toDomain() } }

    override suspend fun resolveError(id: Long) = dao.resolveError(id)

    override suspend fun getReviewState(taskId: Long): ReviewState? = dao.getReviewState(taskId)?.toDomain()
    override suspend fun upsertReviewState(state: ReviewState) = dao.upsertReviewState(state.toEntity())

    override suspend fun getDueReviews(now: Instant): List<ReviewState> =
        dao.getDueReviews(now.toEpochMilli()).map { it.toDomain() }
}

@Singleton
class RoomTransactionRunner @Inject constructor(private val db: AppDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}
