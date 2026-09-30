package com.algoprep.app.domain.usecase

import com.algoprep.app.domain.mock.MockFormat
import com.algoprep.app.domain.mock.MockPlanner
import com.algoprep.app.domain.model.SessionType
import com.algoprep.app.domain.model.Task
import com.algoprep.app.domain.repository.ProfileRepository
import com.algoprep.app.domain.repository.TaskRepository
import com.algoprep.app.domain.repository.TrainingRepository
import com.algoprep.app.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

class PrepareMock @Inject constructor(
    private val tasks: TaskRepository,
    private val profiles: ProfileRepository,
    private val training: TrainingRepository,
    private val clock: Clock,
) {
    /** Tasks for the chosen format; fewer than the format needs only when the bank is too small. */
    suspend operator fun invoke(format: MockFormat, random: Random = Random(clock.millis())): List<Task> {
        val today = LocalDate.now(clock)
        val recent = training.observeFinishedSessions().first()
            .filter { !it.localDate.isBefore(today.minusDays(RECENT_DAYS)) }
            .map { it.taskId }.toSet()
        return MockPlanner.pick(
            format = format,
            tasks = tasks.observeAll().first(),
            skills = profiles.getSkills().associateBy { it.topicId },
            recentTaskIds = recent,
            now = clock.instant(),
            random = random,
        )
    }

    private companion object {
        const val RECENT_DAYS = 7L
    }
}

data class MockTaskTime(val taskId: Long, val seconds: Long, val notes: String)

/**
 * Turns the end of a mock interview into one stopped session per task (type MOCK), so nothing is lost if the
 * user leaves before entering results: each one then waits in "enter result" like a normal session.
 */
class FinishMock @Inject constructor(
    private val training: TrainingRepository,
    private val tx: TransactionRunner,
) {
    /** @return the created session ids in task order. */
    suspend operator fun invoke(times: List<MockTaskTime>, plannedItemId: Long?): List<Long> = tx.run {
        times.map { t ->
            val id = training.startSession(t.taskId, plannedItemId, SessionType.MOCK)
            val session = checkNotNull(training.getSession(id)) { "session $id was just created" }
            training.saveSession(session.copy(durationSec = t.seconds.coerceAtLeast(1).toInt(), notes = t.notes))
            id
        }
    }
}

/** Saves the results of all tasks of a mock interview at once. */
class CompleteMock @Inject constructor(
    private val completeSession: CompleteSession,
    private val tx: TransactionRunner,
) {
    suspend operator fun invoke(inputs: List<CompleteSessionInput>) {
        tx.run { inputs.forEach { completeSession(it) } }
    }
}
