package com.algoprep.app.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

data class Topic(val id: String, val title: String, val parentId: String?, val orderIndex: Int)

data class Pattern(val id: String, val title: String, val topicHint: String?)

@Serializable
data class Example(val input: String, val output: String, val explanation: String? = null)

data class Complexity(val time: String?, val space: String?)

/** One report/mention of a task (source of truth for "how often does it appear in my data"). */
data class Mention(
    val id: Long,
    val source: String?,
    val sourceUrl: String?,
    val companyTag: String?,
    val interviewStage: String?,
    val roleLevel: String?,
    val reportedDate: LocalDate?,
)

/** Unknown metadata is null, never guessed. */
data class Task(
    val id: Long,
    val title: String,
    val originalText: String,
    val difficulty: Difficulty?,
    val estimatedSolveMin: Int?,
    val topics: Set<String>,
    val patterns: Set<String>,
    val constraints: List<String>,
    val examples: List<Example>,
    val hints: List<String>,
    val solutionIdea: String?,
    val complexity: Complexity?,
    val roleLevel: String?,
    val personalNotes: String,
    val status: TaskStatus,
    val timesSolved: Int,
    val lastSolvedAt: Instant?,
    val nextReviewAt: Instant?,
    val confidence: Int?,
    val origin: TaskOrigin,
    val mentions: List<Mention>,
)

data class RoadmapDay(
    val dayIndex: Int,
    val title: String,
    val topicIds: List<String>,
    val theory: String?,
    val notes: String?,
)

data class UserProfile(
    val level: UserLevel,
    val goal: String,
    val dailyMinutes: Int,
    val startDate: LocalDate,
    val targetDate: LocalDate?,
    val onboardedAt: Instant,
)

data class TopicSkill(
    val topicId: String,
    val selfRating: Int,
    val score: Double,
    val attempts: Int,
    val failures: Int,
    val lastPracticedAt: Instant?,
)

@Serializable
data class PlanReason(val code: ReasonCode, val arg: String? = null)

data class PlannedItem(
    val id: Long,
    val dayIndex: Int,
    val kind: PlannedKind,
    val taskId: Long?,
    val orderIndex: Int,
    val estimatedMin: Int,
    val status: PlannedStatus,
    val bucket: Bucket,
    val reasons: List<PlanReason>,
    val completedSessionId: Long?,
)

data class PlanDay(
    val dayIndex: Int,
    val date: LocalDate,
    val title: String,
    val targetMinutes: Int,
    val isAdjusted: Boolean,
    val adjustReason: String?,
    val generatedVersion: Int,
    val status: PlanDayStatus,
    val topicIds: List<String>,
    val items: List<PlannedItem>,
)

data class SolveSession(
    val id: Long,
    val taskId: Long,
    val plannedItemId: Long?,
    val type: SessionType,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val durationSec: Int,
    val hintsUsed: Int,
    val outcome: SolveOutcome?,
    val confidence: Int?,
    val notes: String,
    val localDate: LocalDate,
)

data class ErrorEntry(
    val id: Long,
    val sessionId: Long,
    val taskId: Long,
    val type: ErrorType,
    val note: String?,
    val resolved: Boolean,
    val createdAt: Instant,
)

data class ReviewState(
    val taskId: Long,
    val intervalDays: Int,
    val ease: Double,
    val repetitions: Int,
    val lapses: Int,
    val dueAt: Instant,
    val lastOutcome: SolveOutcome?,
)

/**
 * Lifecycle of a session without extra schema:
 *  - RUNNING: started, timer is running (durationSec == 0)
 *  - AWAITING_RESULT: the user pressed "done"; the timer is frozen (durationSec > 0) but the result is not saved yet
 *  - FINISHED: result saved (finishedAt != null)
 */
enum class SessionPhase { RUNNING, AWAITING_RESULT, FINISHED }

val SolveSession.phase: SessionPhase
    get() = when {
        finishedAt != null -> SessionPhase.FINISHED
        durationSec > 0 -> SessionPhase.AWAITING_RESULT
        else -> SessionPhase.RUNNING
    }

fun PlannedKind.toSessionType(): SessionType = when (this) {
    PlannedKind.THEORY, PlannedKind.WARMUP, PlannedKind.MAIN -> SessionType.PRACTICE
    PlannedKind.REVIEW -> SessionType.REVIEW
    PlannedKind.ERROR_REVIEW -> SessionType.ERROR_REVIEW
    PlannedKind.MOCK -> SessionType.MOCK
}

data class ImportBatch(
    val id: Long,
    val createdAt: Instant,
    val sourceName: String,
    val candidatesFound: Int,
    val saved: Int,
    val merged: Int,
    val skipped: Int,
    val status: BatchStatus,
)

/** Just enough of a bank task to look for duplicates. */
data class TaskBrief(val id: Long, val title: String, val text: String)
