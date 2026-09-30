package com.algoprep.app.domain.mock

import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.Task
import com.algoprep.app.domain.model.TaskStatus
import com.algoprep.app.domain.model.TopicSkill
import com.algoprep.app.domain.planning.ScoringContext
import com.algoprep.app.domain.planning.TaskScorer
import com.algoprep.app.domain.planning.WeakTopics
import java.time.Instant
import kotlin.random.Random

enum class MockFormat(val minutes: Int, val difficulties: List<Difficulty>) {
    /** One medium task in 45 minutes. */
    QUICK(45, listOf(Difficulty.MEDIUM)),

    /** A medium and a hard task in 90 minutes. */
    FULL(90, listOf(Difficulty.MEDIUM, Difficulty.HARD)),
    ;

    val taskCount: Int get() = difficulties.size
}

/**
 * Picks tasks for a mock interview from the user's own bank: tasks of the wanted difficulty that were not
 * solved lately, preferring unsolved/failed ones and weak topics, with a little randomness so that two mocks differ.
 * When there is no task of the wanted difficulty, any remaining task is used rather than shortening the interview.
 */
object MockPlanner {
    private const val TOP_CHOICES = 3
    private const val SCORE_MARGIN = 10

    fun pick(
        format: MockFormat,
        tasks: List<Task>,
        skills: Map<String, TopicSkill>,
        recentTaskIds: Set<Long>,
        now: Instant,
        random: Random,
    ): List<Task> {
        val weak = WeakTopics.pick(skills.values).map { it.topicId }.toSet()
        val ctx = ScoringContext(now, skills, weak)
        var pool = tasks.filter { it.id !in recentTaskIds && it.status != TaskStatus.MASTERED }
        if (pool.size < format.taskCount) pool = tasks
        val picked = ArrayList<Task>()
        for (wanted in format.difficulties) {
            val remaining = pool.filter { candidate -> picked.none { it.id == candidate.id } }
            val exact = remaining.filter { it.difficulty == wanted }
            val candidates = exact.ifEmpty { remaining }
            val best = candidates.map { TaskScorer.score(it, ctx) }
                .sortedWith(compareByDescending<com.algoprep.app.domain.planning.ScoredTask> { it.score }.thenBy { it.task.id })
                .take(TOP_CHOICES)
            if (best.isEmpty()) break
            // Randomise only among near-equal candidates, so a clearly better task is never skipped.
            val close = best.filter { it.score >= best.first().score - SCORE_MARGIN }
            picked += close[random.nextInt(close.size)].task
        }
        return picked
    }
}
