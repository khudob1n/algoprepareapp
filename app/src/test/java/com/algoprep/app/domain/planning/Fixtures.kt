package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.Mention
import com.algoprep.app.domain.model.PlanDay
import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.model.Task
import com.algoprep.app.domain.model.TaskOrigin
import com.algoprep.app.domain.model.TaskStatus
import com.algoprep.app.domain.model.TopicSkill
import java.time.Instant
import java.time.LocalDate

val NOW: Instant = Instant.parse("2026-10-12T08:00:00Z")

fun task(
    id: Long,
    title: String = "Task $id",
    topics: Set<String> = setOf("arrays"),
    difficulty: Difficulty? = Difficulty.MEDIUM,
    minutes: Int? = 25,
    solved: Int = 0,
    status: TaskStatus = TaskStatus.NEW,
    nextReviewAt: Instant? = null,
    mentions: Int = 0,
) = Task(
    id = id, title = title, originalText = "", difficulty = difficulty, estimatedSolveMin = minutes,
    topics = topics, patterns = emptySet(), constraints = emptyList(), examples = emptyList(),
    hints = emptyList(), solutionIdea = null, complexity = null, roleLevel = null, personalNotes = "",
    status = status, timesSolved = solved, lastSolvedAt = null, nextReviewAt = nextReviewAt,
    confidence = null, origin = TaskOrigin.SEED,
    mentions = List(mentions) { Mention(it.toLong(), "src$it", null, null, null, null, null) },
)

fun skill(topic: String, score: Double, attempts: Int = 5, selfRating: Int = 3) =
    TopicSkill(topic, selfRating, score, attempts, 0, null)

fun planDay(dayIndex: Int = 12, topics: List<String> = listOf("trees"), minutes: Int = 120) = PlanDay(
    dayIndex = dayIndex, date = LocalDate.of(2026, 10, 12), title = "Day $dayIndex",
    targetMinutes = minutes, isAdjusted = false, adjustReason = null, generatedVersion = 1,
    status = PlanDayStatus.TODAY, topicIds = topics, items = emptyList(),
)
