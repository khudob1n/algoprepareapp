package com.algoprep.app.fakes

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Fixed clock so tests are deterministic. */
fun fixedClock(iso: String = "2026-10-12T08:00:00Z"): Clock = Clock.fixed(Instant.parse(iso), ZoneOffset.UTC)

class FakeTransactionRunner : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

class FakeCatalogRepository(
    topics: List<Topic> = emptyList(),
    roadmap: List<RoadmapDay> = emptyList(),
) : CatalogRepository {
    val topics = MutableStateFlow(topics)
    val roadmap = MutableStateFlow(roadmap)
    override fun observeTopics(): Flow<List<Topic>> = topics
    override fun observePatterns(): Flow<List<Pattern>> = MutableStateFlow(emptyList())
    override fun observeRoadmap(): Flow<List<RoadmapDay>> = roadmap
    override suspend fun getRoadmap(): List<RoadmapDay> = roadmap.value
}

class FakeTaskRepository(tasks: List<Task> = emptyList()) : TaskRepository {
    val tasks = MutableStateFlow(tasks)
    private var nextId = (tasks.maxOfOrNull { it.id } ?: 0) + 1

    override fun observeAll(): Flow<List<Task>> = tasks
    override fun observe(id: Long): Flow<Task?> = tasks.map { l -> l.firstOrNull { it.id == id } }
    override fun observeByIds(ids: Collection<Long>): Flow<List<Task>> = tasks.map { l -> l.filter { it.id in ids } }
    override suspend fun get(id: Long): Task? = tasks.value.firstOrNull { it.id == id }
    override suspend fun getByTopics(topicIds: Set<String>): List<Task> =
        tasks.value.filter { t -> t.topics.any { it in topicIds } }

    override suspend fun insertNew(task: Task): Long {
        val id = nextId++
        tasks.value = tasks.value + task.copy(id = id)
        return id
    }

    override suspend fun updateNotes(id: Long, notes: String) = modify(id) { it.copy(personalNotes = notes) }

    override suspend fun markDueTasksForReview(now: Instant) {
        tasks.value = tasks.value.map {
            val due = it.nextReviewAt
            if (due != null && !due.isAfter(now) && (it.status == TaskStatus.LEARNING || it.status == TaskStatus.MASTERED)) {
                it.copy(status = TaskStatus.REVIEW)
            } else it
        }
    }

    override suspend fun updateProgress(
        id: Long,
        status: TaskStatus,
        timesSolved: Int,
        lastSolvedAt: Instant?,
        nextReviewAt: Instant?,
        confidence: Int?,
    ) = modify(id) {
        it.copy(
            status = status, timesSolved = timesSolved, lastSolvedAt = lastSolvedAt,
            nextReviewAt = nextReviewAt, confidence = confidence,
        )
    }

    private fun modify(id: Long, f: (Task) -> Task) {
        tasks.value = tasks.value.map { if (it.id == id) f(it) else it }
    }
}

class FakeProfileRepository(
    profile: UserProfile? = null,
    skills: List<TopicSkill> = emptyList(),
) : ProfileRepository {
    val profile = MutableStateFlow(profile)
    val skills = MutableStateFlow(skills)
    override fun observeProfile(): Flow<UserProfile?> = profile
    override fun observeSkills(): Flow<List<TopicSkill>> = skills
    override suspend fun getSkills(): List<TopicSkill> = skills.value
    override suspend fun saveOnboarding(profile: UserProfile, selfRatings: Map<String, Int>) {
        this.profile.value = profile
        val existing = skills.value.associateBy { it.topicId }
        skills.value = selfRatings.map { (t, r) ->
            existing[t]?.takeIf { it.attempts > 0 } ?: TopicSkill(t, r, (r - 1) / 4.0, 0, 0, null)
        }
    }

    override suspend fun updateTargetDate(date: LocalDate?) {
        profile.value = profile.value?.copy(targetDate = date)
    }

    override suspend fun upsertSkills(skills: List<TopicSkill>) {
        val byId = this.skills.value.associateBy { it.topicId }.toMutableMap()
        skills.forEach { byId[it.topicId] = it }
        this.skills.value = byId.values.toList()
    }
}

class FakePlanRepository(days: List<PlanDay> = emptyList()) : PlanRepository {
    val days = MutableStateFlow(days)
    override fun observeDays(): Flow<List<PlanDay>> = days
    override fun observeDay(dayIndex: Int): Flow<PlanDay?> = days.map { l -> l.firstOrNull { it.dayIndex == dayIndex } }
    override suspend fun getDay(dayIndex: Int): PlanDay? = days.value.firstOrNull { it.dayIndex == dayIndex }
    override suspend fun hasPlan(): Boolean = days.value.isNotEmpty()
    override suspend fun replacePlan(days: List<PlanDay>) {
        var nextId = 1L
        this.days.value = days.map { d -> d.copy(items = d.items.map { it.copy(id = nextId++) }) }
    }

    override suspend fun replaceDayItems(day: PlanDay) {
        var nextId = (days.value.flatMap { it.items }.maxOfOrNull { it.id } ?: 0) + 1
        val withIds = day.copy(items = day.items.map { if (it.id == 0L) it.copy(id = nextId++) else it })
        days.value = days.value.map { if (it.dayIndex == day.dayIndex) withIds else it }
    }

    override suspend fun setItemStatus(itemId: Long, status: PlannedStatus, sessionId: Long?) {
        days.value = days.value.map { d ->
            d.copy(items = d.items.map { if (it.id == itemId) it.copy(status = status, completedSessionId = sessionId) else it })
        }
    }

    override suspend fun setDayStatus(dayIndex: Int, status: PlanDayStatus) {
        days.value = days.value.map { if (it.dayIndex == dayIndex) it.copy(status = status) else it }
    }

    override suspend fun updateDayMeta(day: PlanDay) {
        days.value = days.value.map { if (it.dayIndex == day.dayIndex) day.copy(items = it.items) else it }
    }

    override suspend fun deleteDays(dayIndexes: List<Int>) {
        days.value = days.value.filter { it.dayIndex !in dayIndexes }
    }
}

class FakeTrainingRepository(private val clock: Clock = fixedClock()) : TrainingRepository {
    val sessions = MutableStateFlow<List<SolveSession>>(emptyList())
    val errors = MutableStateFlow<List<ErrorEntry>>(emptyList())
    val reviews = MutableStateFlow<List<ReviewState>>(emptyList())
    private var nextSessionId = 1L
    private var nextErrorId = 1L

    override suspend fun startSession(taskId: Long, plannedItemId: Long?, type: SessionType): Long {
        val id = nextSessionId++
        sessions.value = sessions.value + SolveSession(
            id, taskId, plannedItemId, type, clock.instant(), null, 0, 0, null, null, "", LocalDate.now(clock),
        )
        return id
    }

    override suspend fun getSession(id: Long): SolveSession? = sessions.value.firstOrNull { it.id == id }
    override fun observeSession(id: Long): Flow<SolveSession?> = sessions.map { l -> l.firstOrNull { it.id == id } }
    override suspend fun getActiveSession(): SolveSession? = sessions.value.lastOrNull { it.finishedAt == null }
    override fun observeActiveSession(): Flow<SolveSession?> = sessions.map { l -> l.lastOrNull { it.finishedAt == null } }
    override suspend fun deleteSession(id: Long) {
        sessions.value = sessions.value.filter { it.id != id }
    }

    override suspend fun saveSession(session: SolveSession) {
        sessions.value = sessions.value.map { if (it.id == session.id) session else it }
    }

    override fun observeFinishedSessions(): Flow<List<SolveSession>> =
        sessions.map { l -> l.filter { it.finishedAt != null }.sortedByDescending { it.startedAt } }

    override suspend fun sessionsForTask(taskId: Long): List<SolveSession> =
        sessions.value.filter { it.taskId == taskId && it.finishedAt != null }
            .sortedWith(compareByDescending<SolveSession> { it.startedAt }.thenByDescending { it.id })

    override suspend fun addErrors(errors: List<ErrorEntry>) {
        this.errors.value = this.errors.value + errors.map { it.copy(id = nextErrorId++) }
    }

    override fun observeUnresolvedErrors(): Flow<List<ErrorEntry>> = errors.map { l -> l.filter { !it.resolved } }
    override fun observeAllErrors(): Flow<List<ErrorEntry>> = errors.map { l -> l.sortedByDescending { it.createdAt } }
    override suspend fun resolveError(id: Long) {
        errors.value = errors.value.map { if (it.id == id) it.copy(resolved = true) else it }
    }

    override suspend fun resolveErrorsForTask(taskId: Long) {
        errors.value = errors.value.map { if (it.taskId == taskId) it.copy(resolved = true) else it }
    }

    override suspend fun getReviewState(taskId: Long): ReviewState? = reviews.value.firstOrNull { it.taskId == taskId }
    override suspend fun upsertReviewState(state: ReviewState) {
        reviews.value = reviews.value.filter { it.taskId != state.taskId } + state
    }

    override suspend fun getDueReviews(now: Instant): List<ReviewState> =
        reviews.value.filter { !it.dueAt.isAfter(now) }.sortedBy { it.dueAt }
}
