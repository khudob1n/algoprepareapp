package com.algoprep.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.algoprep.app.data.db.entity.ErrorEntryEntity
import com.algoprep.app.data.db.entity.PatternEntity
import com.algoprep.app.data.db.entity.PlanDayEntity
import com.algoprep.app.data.db.entity.PlanDayTopicEntity
import com.algoprep.app.data.db.entity.PlanDayWithDetails
import com.algoprep.app.data.db.entity.PlannedItemEntity
import com.algoprep.app.data.db.entity.ReviewStateEntity
import com.algoprep.app.data.db.entity.RoadmapTemplateEntity
import com.algoprep.app.data.db.entity.SolveSessionEntity
import com.algoprep.app.data.db.entity.TaskEntity
import com.algoprep.app.data.db.entity.TaskMentionEntity
import com.algoprep.app.data.db.entity.TaskPatternEntity
import com.algoprep.app.data.db.entity.TaskTopicEntity
import com.algoprep.app.data.db.entity.TaskWithRelations
import com.algoprep.app.data.db.entity.TopicEntity
import com.algoprep.app.data.db.entity.TopicSkillEntity
import com.algoprep.app.data.db.entity.UserProfileEntity
import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.model.PlannedStatus
import com.algoprep.app.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow

/** Reference data. @Upsert (not REPLACE) so re-seeding never cascades deletes into user data. */
@Dao
interface CatalogDao {
    @Upsert suspend fun upsertTopics(items: List<TopicEntity>)
    @Upsert suspend fun upsertPatterns(items: List<PatternEntity>)
    @Upsert suspend fun upsertRoadmap(items: List<RoadmapTemplateEntity>)

    @Query("SELECT * FROM topic ORDER BY orderIndex")
    fun observeTopics(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM pattern ORDER BY title")
    fun observePatterns(): Flow<List<PatternEntity>>

    @Query("SELECT * FROM roadmap_template ORDER BY dayIndex")
    fun observeRoadmap(): Flow<List<RoadmapTemplateEntity>>

    @Query("SELECT * FROM roadmap_template ORDER BY dayIndex")
    suspend fun getRoadmap(): List<RoadmapTemplateEntity>
}

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(task: TaskEntity): Long

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopicLinks(links: List<TaskTopicEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatternLinks(links: List<TaskPatternEntity>)

    @Insert
    suspend fun insertMentions(mentions: List<TaskMentionEntity>)

    @Transaction
    @Query("SELECT * FROM task ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<TaskWithRelations>>

    @Transaction
    @Query("SELECT * FROM task WHERE id = :id")
    fun observe(id: Long): Flow<TaskWithRelations?>

    @Transaction
    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun get(id: Long): TaskWithRelations?

    @Transaction
    @Query("SELECT * FROM task WHERE id IN (:ids)")
    fun observeByIds(ids: Collection<Long>): Flow<List<TaskWithRelations>>

    @Transaction
    @Query("SELECT * FROM task WHERE id IN (SELECT taskId FROM task_topic WHERE topicId IN (:topicIds))")
    suspend fun getByTopics(topicIds: Collection<String>): List<TaskWithRelations>

    @Query("SELECT * FROM task WHERE canonicalKey = :key LIMIT 1")
    suspend fun findByCanonicalKey(key: String): TaskEntity?

    @Query("UPDATE task SET personalNotes = :notes, updatedAt = :now WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String, now: Long)

    @Query(
        "UPDATE task SET status = :status, timesSolved = :timesSolved, lastSolvedAt = :lastSolvedAt, " +
            "nextReviewAt = :nextReviewAt, confidence = :confidence, updatedAt = :now WHERE id = :id",
    )
    suspend fun updateProgress(
        id: Long,
        status: TaskStatus,
        timesSolved: Int,
        lastSolvedAt: Long?,
        nextReviewAt: Long?,
        confidence: Int?,
        now: Long,
    )

    @Query("SELECT COUNT(*) FROM task")
    suspend fun count(): Int
}

@Dao
interface ProfileDao {
    @Upsert suspend fun upsertProfile(profile: UserProfileEntity)
    @Upsert suspend fun upsertSkills(skills: List<TopicSkillEntity>)

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM topic_skill")
    fun observeSkills(): Flow<List<TopicSkillEntity>>

    @Query("SELECT * FROM topic_skill")
    suspend fun getSkills(): List<TopicSkillEntity>
}

@Dao
interface PlanDao {
    /** Cascades to plan_day_topic and planned_item. */
    @Query("DELETE FROM plan_day")
    suspend fun clearPlan()

    @Insert suspend fun insertDays(days: List<PlanDayEntity>)
    @Insert suspend fun insertDayTopics(topics: List<PlanDayTopicEntity>)
    @Insert suspend fun insertItems(items: List<PlannedItemEntity>)

    @Query("DELETE FROM planned_item WHERE dayIndex = :dayIndex")
    suspend fun deleteItemsForDay(dayIndex: Int)

    @Query("DELETE FROM plan_day_topic WHERE dayIndex = :dayIndex")
    suspend fun deleteTopicsForDay(dayIndex: Int)

    @Update suspend fun updateDay(day: PlanDayEntity)

    @Query("UPDATE planned_item SET status = :status, completedSessionId = :sessionId WHERE id = :id")
    suspend fun setItemStatus(id: Long, status: PlannedStatus, sessionId: Long?)

    @Query("UPDATE plan_day SET status = :status WHERE dayIndex = :dayIndex")
    suspend fun setDayStatus(dayIndex: Int, status: PlanDayStatus)

    @Query("SELECT COUNT(*) FROM plan_day")
    suspend fun dayCount(): Int

    @Transaction
    @Query("SELECT * FROM plan_day ORDER BY dayIndex")
    fun observeDays(): Flow<List<PlanDayWithDetails>>

    @Transaction
    @Query("SELECT * FROM plan_day WHERE dayIndex = :dayIndex")
    fun observeDay(dayIndex: Int): Flow<PlanDayWithDetails?>

    @Transaction
    @Query("SELECT * FROM plan_day WHERE dayIndex = :dayIndex")
    suspend fun getDay(dayIndex: Int): PlanDayWithDetails?
}

@Dao
interface TrainingDao {
    @Insert suspend fun insertSession(session: SolveSessionEntity): Long
    @Update suspend fun updateSession(session: SolveSessionEntity)

    @Query("SELECT * FROM solve_session WHERE id = :id")
    suspend fun getSession(id: Long): SolveSessionEntity?

    @Query("SELECT * FROM solve_session WHERE id = :id")
    fun observeSession(id: Long): Flow<SolveSessionEntity?>

    @Query("SELECT * FROM solve_session WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveSession(): SolveSessionEntity?

    @Query("SELECT * FROM solve_session WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<SolveSessionEntity?>

    @Query("DELETE FROM solve_session WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM solve_session WHERE finishedAt IS NOT NULL ORDER BY startedAt DESC")
    fun observeFinishedSessions(): Flow<List<SolveSessionEntity>>

    @Query("SELECT * FROM solve_session WHERE taskId = :taskId AND finishedAt IS NOT NULL ORDER BY startedAt DESC")
    suspend fun sessionsForTask(taskId: Long): List<SolveSessionEntity>

    @Insert suspend fun insertErrors(errors: List<ErrorEntryEntity>)

    @Query("SELECT * FROM error_entry WHERE resolved = 0 ORDER BY createdAt DESC")
    fun observeUnresolvedErrors(): Flow<List<ErrorEntryEntity>>

    @Query("UPDATE error_entry SET resolved = 1 WHERE id = :id")
    suspend fun resolveError(id: Long)

    @Upsert suspend fun upsertReviewState(state: ReviewStateEntity)

    @Query("SELECT * FROM review_state WHERE taskId = :taskId")
    suspend fun getReviewState(taskId: Long): ReviewStateEntity?

    @Query("SELECT * FROM review_state WHERE dueAt <= :now ORDER BY dueAt")
    suspend fun getDueReviews(now: Long): List<ReviewStateEntity>
}
