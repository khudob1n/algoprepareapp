package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.Bucket
import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.ErrorEntry
import com.algoprep.app.domain.model.PlanDay
import com.algoprep.app.domain.model.PlanReason
import com.algoprep.app.domain.model.PlannedItem
import com.algoprep.app.domain.model.PlannedKind
import com.algoprep.app.domain.model.PlannedStatus
import com.algoprep.app.domain.model.ReasonCode
import com.algoprep.app.domain.model.RoadmapDay
import com.algoprep.app.domain.model.Task
import com.algoprep.app.domain.model.TaskStatus
import com.algoprep.app.domain.model.TopicSkill
import java.time.Instant

data class PlannerInput(
    val day: PlanDay,
    val roadmapDay: RoadmapDay?,
    val tasks: List<Task>,
    val skills: Map<String, TopicSkill>,
    /** Topics already introduced by the roadmap up to and including this day. */
    val introducedTopicIds: Set<String>,
    val unresolvedErrors: List<ErrorEntry>,
    /** Tasks solved in the last couple of days; not offered again as fresh practice. */
    val recentTaskIds: Set<Long>,
    val now: Instant,
)

/**
 * Builds the items of one day.
 *
 * Fixed parts first (theory, warm-up, error review), then the remaining time is split roughly
 * 70 / 20 / 10:
 *  - SPACED (10%, up to 30% when many reviews are due): due reviews, else one mixed task from topics
 *    already studied;
 *  - WEAK   (20%): weak topics outside today's roadmap topics;
 *  - ROADMAP: everything that is left, from today's roadmap topics (so unused SPACED/WEAK time
 *    goes back to the curriculum instead of being wasted).
 */
object DayPlanner {
    const val THEORY_MIN = 10
    const val ERROR_REVIEW_MIN = 10
    const val WARMUP_MAX_MIN = 20
    const val WARMUP_MIN_DAY_BUDGET = 60

    fun estimate(task: Task): Int = task.estimatedSolveMin ?: when (task.difficulty) {
        Difficulty.EASY -> 15
        Difficulty.HARD -> 40
        Difficulty.MEDIUM, null -> 25
    }

    fun plan(input: PlannerInput): List<PlannedItem> {
        val day = input.day
        val dayTopics = day.topicIds.toSet()
        val weakIds = WeakTopics.pick(input.skills.values.filter { it.topicId in input.introducedTopicIds })
            .map { it.topicId }.toSet()
        val ctx = ScoringContext(input.now, input.skills, weakIds)

        val drafts = mutableListOf<Draft>()
        val used = mutableSetOf<Long>()

        val available = input.tasks.filter { t ->
            t.status != TaskStatus.MASTERED && t.id !in input.recentTaskIds
        }
        val roadmapPool = available.filter { t -> t.topics.any { it in dayTopics } }

        // 1. theory
        val theory = input.roadmapDay?.theory
        var fixed = 0
        if (theory != null) {
            drafts += Draft(
                PlannedKind.THEORY, null, THEORY_MIN, Bucket.ROADMAP,
                listOf(PlanReason(ReasonCode.ROADMAP_TOPIC, day.topicIds.firstOrNull())),
            )
            fixed += THEORY_MIN
        }

        // 2. warm-up: the easiest unsolved task of today's topics
        if (day.targetMinutes >= WARMUP_MIN_DAY_BUDGET) {
            val warmup = roadmapPool
                .filter { it.timesSolved == 0 && estimate(it) <= WARMUP_MAX_MIN }
                .minWithOrNull(compareBy({ it.difficulty?.ordinal ?: Difficulty.MEDIUM.ordinal }, { estimate(it) }, { it.id }))
            if (warmup != null) {
                drafts += Draft(
                    PlannedKind.WARMUP, warmup.id, estimate(warmup), Bucket.ROADMAP,
                    listOf(
                        PlanReason(ReasonCode.ROADMAP_TOPIC, warmup.topics.firstOrNull { it in dayTopics }),
                        PlanReason(ReasonCode.NOT_SOLVED_YET),
                    ),
                )
                used += warmup.id
                fixed += estimate(warmup)
            }
        }

        // 3. error review (placed last in the list, but budgeted now)
        val errorTask = input.unresolvedErrors
            .sortedByDescending { it.createdAt }
            .firstNotNullOfOrNull { e -> input.tasks.firstOrNull { it.id == e.taskId } }
        if (errorTask != null) fixed += ERROR_REVIEW_MIN

        val remaining = (day.targetMinutes - fixed).coerceAtLeast(0)

        // 4. spaced repetition / mixed
        val due = input.tasks
            .filter { t ->
                val due = t.nextReviewAt
                t.id !in used && due != null && !due.isAfter(input.now)
            }
            .map { TaskScorer.score(it, ctx) }
            .sortedWith(ranking)
        val spacedMin = remaining / 10
        val spacedMax = remaining * 3 / 10
        val spacedBudget = if (due.isEmpty()) spacedMin
        else due.take(3).sumOf { estimate(it.task) }.coerceIn(spacedMin, spacedMax)
        val spacedPicked: List<ScoredTask>
        val spacedKind: PlannedKind
        if (due.isNotEmpty()) {
            spacedPicked = fill(due, spacedBudget, maxCount = 3, guaranteeFirst = false)
            spacedKind = PlannedKind.REVIEW
        } else {
            val mixedPool = available
                .filter { t ->
                    t.id !in used &&
                        t.topics.any { it in input.introducedTopicIds } &&
                        t.topics.none { it in dayTopics }
                }
                .map { TaskScorer.score(it, ctx) }
                .sortedWith(ranking)
            spacedPicked = fill(mixedPool, spacedBudget, maxCount = 1, guaranteeFirst = false)
            spacedKind = PlannedKind.MAIN
        }
        used += spacedPicked.map { it.task.id }
        val spacedUsed = spacedPicked.sumOf { estimate(it.task) }

        // 5. weak topics outside today's roadmap topics
        val weakPool = available
            .filter { t ->
                t.id !in used &&
                    t.topics.any { it in weakIds } &&
                    t.topics.none { it in dayTopics }
            }
            .map { TaskScorer.score(it, ctx) }
            .sortedWith(ranking)
        val weakPicked = fill(weakPool, remaining * 2 / 10, maxCount = 2, guaranteeFirst = false)
        used += weakPicked.map { it.task.id }
        val weakUsed = weakPicked.sumOf { estimate(it.task) }

        // 6. roadmap gets everything that is left
        val roadmapBudget = (remaining - spacedUsed - weakUsed).coerceAtLeast(0)
        val roadmapScored = roadmapPool
            .filter { it.id !in used }
            .map { TaskScorer.score(it, ctx) }
            .sortedWith(ranking)
        val roadmapPicked = fill(roadmapScored, roadmapBudget, maxCount = 5, guaranteeFirst = true)

        for (s in roadmapPicked) {
            val topic = s.task.topics.firstOrNull { it in dayTopics }
            drafts += Draft(
                PlannedKind.MAIN, s.task.id, estimate(s.task), Bucket.ROADMAP,
                listOf(PlanReason(ReasonCode.ROADMAP_TOPIC, topic)) + s.reasons,
            )
        }
        for (s in weakPicked) {
            drafts += Draft(PlannedKind.MAIN, s.task.id, estimate(s.task), Bucket.WEAK, s.reasons)
        }
        for (s in spacedPicked) {
            val reasons = if (spacedKind == PlannedKind.MAIN) s.reasons + PlanReason(ReasonCode.MIXED) else s.reasons
            drafts += Draft(spacedKind, s.task.id, estimate(s.task), Bucket.SPACED, reasons)
        }
        if (errorTask != null) {
            drafts += Draft(
                PlannedKind.ERROR_REVIEW, errorTask.id, ERROR_REVIEW_MIN, Bucket.SPACED,
                listOf(PlanReason(ReasonCode.RECENT_FAILURE)),
            )
        }

        return drafts.mapIndexed { index, d ->
            PlannedItem(
                id = 0,
                dayIndex = day.dayIndex,
                kind = d.kind,
                taskId = d.taskId,
                orderIndex = index,
                estimatedMin = d.minutes,
                status = PlannedStatus.TODO,
                bucket = d.bucket,
                reasons = d.reasons,
                completedSessionId = null,
            )
        }
    }

    private class Draft(
        val kind: PlannedKind,
        val taskId: Long?,
        val minutes: Int,
        val bucket: Bucket,
        val reasons: List<PlanReason>,
    )

    private val ranking: Comparator<ScoredTask> =
        compareByDescending<ScoredTask> { it.score }.thenBy { it.task.title }.thenBy { it.task.id }

    /**
     * Greedily takes tasks in ranking order while at least half of the next task fits in the budget.
     * With [guaranteeFirst] the best task is taken even if it does not fit (the curriculum must never be empty).
     */
    private fun fill(scored: List<ScoredTask>, budget: Int, maxCount: Int, guaranteeFirst: Boolean): List<ScoredTask> {
        if (budget <= 0 && !guaranteeFirst) return emptyList()
        val picked = mutableListOf<ScoredTask>()
        var usedMin = 0
        for (s in scored) {
            if (picked.size >= maxCount) break
            val e = estimate(s.task)
            val fits = usedMin + e / 2 <= budget
            if (fits || (guaranteeFirst && picked.isEmpty())) {
                picked += s
                usedMin += e
            }
        }
        return picked
    }
}
