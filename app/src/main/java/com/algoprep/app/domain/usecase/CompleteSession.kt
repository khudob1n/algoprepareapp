package com.algoprep.app.domain.usecase

import com.algoprep.app.domain.model.ErrorEntry
import com.algoprep.app.domain.model.ErrorType
import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.model.PlannedStatus
import com.algoprep.app.domain.model.SessionPhase
import com.algoprep.app.domain.model.SessionType
import com.algoprep.app.domain.model.SolveOutcome
import com.algoprep.app.domain.model.isSolved
import com.algoprep.app.domain.model.phase
import com.algoprep.app.domain.planning.SkillTracker
import com.algoprep.app.domain.planning.TaskProgress
import com.algoprep.app.domain.repository.PlanRepository
import com.algoprep.app.domain.repository.ProfileRepository
import com.algoprep.app.domain.repository.TaskRepository
import com.algoprep.app.domain.repository.TrainingRepository
import com.algoprep.app.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

data class CompleteSessionInput(
    val sessionId: Long,
    val outcome: SolveOutcome,
    val confidence: Int,
    val errorTypes: Set<ErrorType>,
    /** Free text, only kept for ErrorType.OTHER. */
    val otherNote: String?,
)

/**
 * Saves the result of a session and updates everything that depends on it, atomically:
 * the session itself, the error log, the plan item and day, the task's progress and the topic skills
 * (which drive weak topics and the next days' plan).
 */
class CompleteSession @Inject constructor(
    private val training: TrainingRepository,
    private val tasks: TaskRepository,
    private val plans: PlanRepository,
    private val profiles: ProfileRepository,
    private val tx: TransactionRunner,
    private val clock: Clock,
) {
    /** @return false when the session does not exist or has already been completed. */
    suspend operator fun invoke(input: CompleteSessionInput): Boolean = tx.run {
        val session = training.getSession(input.sessionId) ?: return@run false
        if (session.phase == SessionPhase.FINISHED) return@run false
        val now = clock.instant()
        val confidence = input.confidence.coerceIn(1, 5)

        training.saveSession(
            session.copy(
                finishedAt = now,
                durationSec = session.durationSec.coerceAtLeast(1),
                outcome = input.outcome,
                confidence = confidence,
            ),
        )

        if (input.errorTypes.isNotEmpty()) {
            training.addErrors(
                input.errorTypes.sorted().map { type ->
                    ErrorEntry(
                        id = 0,
                        sessionId = session.id,
                        taskId = session.taskId,
                        type = type,
                        note = if (type == ErrorType.OTHER) input.otherNote?.trim()?.ifEmpty { null } else null,
                        resolved = false,
                        createdAt = now,
                    )
                },
            )
        }
        if (session.type == SessionType.ERROR_REVIEW && input.outcome.isSolved) {
            training.resolveErrorsForTask(session.taskId)
        }

        session.plannedItemId?.let { markItemDone(it, session.id) }

        val task = tasks.get(session.taskId)
        if (task != null) {
            val solved = input.outcome.isSolved
            tasks.updateProgress(
                id = task.id,
                status = TaskProgress.statusAfter(task.status, input.outcome),
                timesSolved = task.timesSolved + if (solved) 1 else 0,
                lastSolvedAt = if (solved) now else task.lastSolvedAt,
                nextReviewAt = task.nextReviewAt,
                confidence = confidence,
            )

            val skills = profiles.getSkills().associateBy { it.topicId }
            val duration = Duration.ofSeconds(session.durationSec.toLong().coerceAtLeast(1))
            profiles.upsertSkills(
                task.topics.map { topic ->
                    SkillTracker.update(
                        current = skills[topic],
                        topicId = topic,
                        outcome = input.outcome,
                        confidence = confidence,
                        duration = duration,
                        estimatedMin = task.estimatedSolveMin,
                        now = now,
                    )
                },
            )
        }
        true
    }

    private suspend fun markItemDone(itemId: Long, sessionId: Long) {
        val day = plans.observeDays().first().firstOrNull { d -> d.items.any { it.id == itemId } }
        plans.setItemStatus(itemId, PlannedStatus.DONE, sessionId)
        if (day != null && day.items.all { it.id == itemId || it.status == PlannedStatus.DONE }) {
            plans.setDayStatus(day.dayIndex, PlanDayStatus.DONE)
        }
    }
}
