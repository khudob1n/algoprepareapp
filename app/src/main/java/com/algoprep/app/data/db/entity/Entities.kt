package com.algoprep.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey
import com.algoprep.app.domain.model.BatchStatus
import com.algoprep.app.domain.model.Bucket
import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.DuplicateDecision
import com.algoprep.app.domain.model.ErrorType
import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.model.PlannedKind
import com.algoprep.app.domain.model.PlannedStatus
import com.algoprep.app.domain.model.SessionType
import com.algoprep.app.domain.model.SolveOutcome
import com.algoprep.app.domain.model.TaskOrigin
import com.algoprep.app.domain.model.TaskStatus
import com.algoprep.app.domain.model.UserLevel

// Conventions: enums are stored by name (TEXT); instants as epoch millis; dates as ISO yyyy-MM-dd.

@Entity(tableName = "topic")
data class TopicEntity(
    @PrimaryKey val id: String,
    val title: String,
    val parentId: String?,
    val orderIndex: Int,
)

@Entity(tableName = "pattern")
data class PatternEntity(
    @PrimaryKey val id: String,
    val title: String,
    val topicHint: String?,
)

@Entity(
    tableName = "task",
    indices = [
        Index("canonicalKey"),
        Index("status"),
        Index(value = ["seedKey"], unique = true),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val originalText: String,
    val canonicalKey: String,
    val difficulty: Difficulty? = null,
    val estimatedSolveMin: Int? = null,
    val constraintsJson: String? = null,
    val examplesJson: String? = null,
    val hintsJson: String? = null,
    val solutionIdea: String? = null,
    val timeComplexity: String? = null,
    val spaceComplexity: String? = null,
    val roleLevel: String? = null,
    val personalNotes: String = "",
    val status: TaskStatus = TaskStatus.NEW,
    val timesSolved: Int = 0,
    val lastSolvedAt: Long? = null,
    val nextReviewAt: Long? = null,
    val confidence: Int? = null,
    val origin: TaskOrigin,
    /** Stable id of built-in tasks; makes seeding idempotent. Null for user tasks. */
    val seedKey: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "import_batch")
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val sourceName: String,
    val format: String,
    val rawSize: Int,
    val candidatesFound: Int,
    val saved: Int,
    val merged: Int,
    val skipped: Int,
    val status: BatchStatus,
)

@Entity(
    tableName = "task_mention",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = CASCADE,
        ),
        ForeignKey(
            entity = ImportBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = SET_NULL,
        ),
    ],
    indices = [Index("taskId"), Index("batchId"), Index("companyTag")],
)
data class TaskMentionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val batchId: Long? = null,
    val rawTitle: String,
    val rawText: String,
    val source: String? = null,
    val sourceUrl: String? = null,
    val companyTag: String? = null,
    val interviewStage: String? = null,
    val roleLevel: String? = null,
    val reportedDate: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "task_topic",
    primaryKeys = ["taskId", "topicId"],
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = CASCADE),
        ForeignKey(entity = TopicEntity::class, parentColumns = ["id"], childColumns = ["topicId"], onDelete = CASCADE),
    ],
    indices = [Index("topicId")],
)
data class TaskTopicEntity(
    val taskId: Long,
    val topicId: String,
    val isUserEdited: Boolean = false,
)

@Entity(
    tableName = "task_pattern",
    primaryKeys = ["taskId", "patternId"],
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = CASCADE),
        ForeignKey(entity = PatternEntity::class, parentColumns = ["id"], childColumns = ["patternId"], onDelete = CASCADE),
    ],
    indices = [Index("patternId")],
)
data class TaskPatternEntity(
    val taskId: Long,
    val patternId: String,
    val isUserEdited: Boolean = false,
)

@Entity(
    tableName = "duplicate_candidate",
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskAId"], onDelete = CASCADE),
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskBId"], onDelete = CASCADE),
    ],
    indices = [Index(value = ["taskAId", "taskBId"], unique = true), Index("taskBId")],
)
data class DuplicateCandidateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskAId: Long,
    val taskBId: Long,
    val similarity: Double,
    val decision: DuplicateDecision,
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val level: UserLevel,
    val goal: String,
    val dailyMinutes: Int,
    val startDate: String,
    val targetDate: String?,
    val onboardedAt: Long,
)

@Entity(
    tableName = "topic_skill",
    foreignKeys = [
        ForeignKey(entity = TopicEntity::class, parentColumns = ["id"], childColumns = ["topicId"], onDelete = CASCADE),
    ],
)
data class TopicSkillEntity(
    @PrimaryKey val topicId: String,
    val selfRating: Int,
    val score: Double,
    val attempts: Int,
    val failures: Int,
    val lastPracticedAt: Long?,
    val updatedAt: Long,
)

@Entity(tableName = "roadmap_template")
data class RoadmapTemplateEntity(
    @PrimaryKey val dayIndex: Int,
    val title: String,
    val topicIdsJson: String,
    val theory: String?,
    val notes: String?,
)

@Entity(tableName = "plan_day")
data class PlanDayEntity(
    @PrimaryKey val dayIndex: Int,
    val date: String,
    val title: String,
    val targetMinutes: Int,
    val isAdjusted: Boolean,
    val adjustReason: String?,
    val generatedVersion: Int,
    val status: PlanDayStatus,
)

@Entity(
    tableName = "plan_day_topic",
    primaryKeys = ["dayIndex", "topicId"],
    foreignKeys = [
        ForeignKey(entity = PlanDayEntity::class, parentColumns = ["dayIndex"], childColumns = ["dayIndex"], onDelete = CASCADE),
        ForeignKey(entity = TopicEntity::class, parentColumns = ["id"], childColumns = ["topicId"], onDelete = CASCADE),
    ],
    indices = [Index("topicId")],
)
data class PlanDayTopicEntity(
    val dayIndex: Int,
    val topicId: String,
)

@Entity(
    tableName = "planned_item",
    foreignKeys = [
        ForeignKey(entity = PlanDayEntity::class, parentColumns = ["dayIndex"], childColumns = ["dayIndex"], onDelete = CASCADE),
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = SET_NULL),
    ],
    indices = [Index("dayIndex"), Index("taskId")],
)
data class PlannedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayIndex: Int,
    val kind: PlannedKind,
    val taskId: Long?,
    val orderIndex: Int,
    val estimatedMin: Int,
    val status: PlannedStatus,
    val bucket: Bucket,
    val reasonJson: String?,
    val completedSessionId: Long?,
)

@Entity(
    tableName = "solve_session",
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = CASCADE),
    ],
    indices = [Index("taskId"), Index("localDate")],
)
data class SolveSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val plannedItemId: Long?,
    val type: SessionType,
    val startedAt: Long,
    /** Null while the session is still running. */
    val finishedAt: Long?,
    val durationSec: Int,
    val hintsUsed: Int,
    val outcome: SolveOutcome?,
    val confidence: Int?,
    val notes: String,
    val localDate: String,
)

@Entity(
    tableName = "error_entry",
    foreignKeys = [
        ForeignKey(entity = SolveSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = CASCADE),
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = CASCADE),
    ],
    indices = [Index("sessionId"), Index("taskId")],
)
data class ErrorEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val taskId: Long,
    val type: ErrorType,
    val note: String?,
    val resolved: Boolean,
    val createdAt: Long,
)

@Entity(
    tableName = "review_state",
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = CASCADE),
    ],
    indices = [Index("dueAt")],
)
data class ReviewStateEntity(
    @PrimaryKey val taskId: Long,
    val intervalDays: Int,
    val ease: Double,
    val repetitions: Int,
    val lapses: Int,
    val dueAt: Long,
    val lastOutcome: SolveOutcome?,
)
