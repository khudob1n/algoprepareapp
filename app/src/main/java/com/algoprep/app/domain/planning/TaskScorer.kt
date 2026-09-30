package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.PlanReason
import com.algoprep.app.domain.model.ReasonCode
import com.algoprep.app.domain.model.Task
import com.algoprep.app.domain.model.TaskStatus
import com.algoprep.app.domain.model.TopicSkill
import java.time.Duration
import java.time.Instant
import kotlin.math.ln
import kotlin.math.roundToInt

class ScoringContext(
    val now: Instant,
    val skills: Map<String, TopicSkill>,
    val weakTopicIds: Set<String>,
)

data class ScoredTask(val task: Task, val score: Int, val reasons: List<PlanReason>)

/**
 * Transparent priority heuristic: a sum of named terms, each with a human-readable reason.
 *
 *  - review due          +25 (+1 per overdue day, max +10)
 *  - recent failure      +25
 *  - not solved yet      +30
 *  - weak topic          +20 * (1 - topic score)
 *  - difficulty fit      +15 / +8 / +0  (exact / neighbouring / far level for the topic's current score)
 *  - frequency in the user's imported set: up to +10, only if mentioned at least twice.
 *    Deliberately small: how often a task appears in imported data says nothing about the future.
 *
 * Unknown difficulty gives no fit points instead of a guess.
 */
object TaskScorer {
    fun score(task: Task, ctx: ScoringContext): ScoredTask {
        val terms = mutableListOf<Pair<Int, PlanReason>>()

        val due = task.nextReviewAt
        if (due != null && !due.isAfter(ctx.now)) {
            val overdueDays = Duration.between(due, ctx.now).toDays().toInt().coerceIn(0, 10)
            terms += (25 + overdueDays) to PlanReason(ReasonCode.REVIEW_DUE, overdueDays.toString())
        }
        if (task.status == TaskStatus.FAILED_RECENTLY) {
            terms += 25 to PlanReason(ReasonCode.RECENT_FAILURE)
        }
        val weakHere = task.topics.filter { it in ctx.weakTopicIds }
        if (weakHere.isNotEmpty()) {
            val worst = weakHere.minWith(compareBy({ ctx.skills[it]?.score ?: 0.0 }, { it }))
            val points = (20 * (1 - (ctx.skills[worst]?.score ?: 0.0))).roundToInt()
            terms += points to PlanReason(ReasonCode.WEAK_TOPIC, worst)
        }
        if (task.timesSolved == 0) {
            terms += 30 to PlanReason(ReasonCode.NOT_SOLVED_YET)
        }
        val mentions = task.mentions.size
        if (mentions >= 2) {
            terms += (4 * ln(mentions.toDouble())).roundToInt().coerceAtMost(10) to
                PlanReason(ReasonCode.FREQUENT_IN_DATASET, mentions.toString())
        }

        var total = terms.sumOf { it.first }
        val fit = difficultyFit(task, ctx)
        total += fit.points
        val reasons = terms.sortedByDescending { it.first }.map { it.second }.toMutableList()
        if (fit.exact) reasons += PlanReason(ReasonCode.DIFFICULTY_FIT)

        return ScoredTask(task, total, reasons)
    }

    private class Fit(val points: Int, val exact: Boolean)

    private fun difficultyFit(task: Task, ctx: ScoringContext): Fit {
        val difficulty = task.difficulty ?: return Fit(0, false)
        val scores = task.topics.mapNotNull { ctx.skills[it]?.score }
        val topicScore = if (scores.isEmpty()) 0.5 else scores.average()
        val preferred = when {
            topicScore < 0.4 -> Difficulty.EASY
            topicScore < 0.75 -> Difficulty.MEDIUM
            else -> Difficulty.HARD
        }
        return when (kotlin.math.abs(difficulty.ordinal - preferred.ordinal)) {
            0 -> Fit(15, true)
            1 -> Fit(8, false)
            else -> Fit(0, false)
        }
    }
}
