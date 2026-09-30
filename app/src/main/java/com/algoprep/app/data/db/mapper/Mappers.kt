package com.algoprep.app.data.db.mapper

import com.algoprep.app.core.AppJson
import com.algoprep.app.data.db.entity.ErrorEntryEntity
import com.algoprep.app.data.db.entity.ImportBatchEntity
import com.algoprep.app.data.db.entity.PatternEntity
import com.algoprep.app.data.db.entity.PlanDayEntity
import com.algoprep.app.data.db.entity.PlanDayWithDetails
import com.algoprep.app.data.db.entity.PlannedItemEntity
import com.algoprep.app.data.db.entity.ReviewStateEntity
import com.algoprep.app.data.db.entity.RoadmapTemplateEntity
import com.algoprep.app.data.db.entity.SolveSessionEntity
import com.algoprep.app.data.db.entity.TaskEntity
import com.algoprep.app.data.db.entity.TaskMentionEntity
import com.algoprep.app.data.db.entity.TaskWithRelations
import com.algoprep.app.data.db.entity.TopicEntity
import com.algoprep.app.data.db.entity.TopicSkillEntity
import com.algoprep.app.data.db.entity.UserProfileEntity
import com.algoprep.app.domain.model.Complexity
import com.algoprep.app.domain.model.ErrorEntry
import com.algoprep.app.domain.model.Example
import com.algoprep.app.domain.model.ImportBatch
import com.algoprep.app.domain.model.Mention
import com.algoprep.app.domain.model.Pattern
import com.algoprep.app.domain.model.PlanDay
import com.algoprep.app.domain.model.PlanReason
import com.algoprep.app.domain.model.PlannedItem
import com.algoprep.app.domain.model.ReviewState
import com.algoprep.app.domain.model.RoadmapDay
import com.algoprep.app.domain.model.SolveSession
import com.algoprep.app.domain.model.Task
import com.algoprep.app.domain.model.Topic
import com.algoprep.app.domain.model.TopicSkill
import com.algoprep.app.domain.model.UserProfile
import com.algoprep.app.domain.util.TitleNormalizer
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.time.LocalDate

private fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)

private inline fun <reified T> String?.decodeList(): List<T> =
    if (this == null) emptyList() else AppJson.decodeFromString<List<T>>(this)

private inline fun <reified T> List<T>.encodeOrNull(): String? =
    if (isEmpty()) null else AppJson.encodeToString(this)

// ---- catalog -------------------------------------------------------------------------------

fun TopicEntity.toDomain() = Topic(id, title, parentId, orderIndex)
fun PatternEntity.toDomain() = Pattern(id, title, topicHint)
fun RoadmapTemplateEntity.toDomain() =
    RoadmapDay(dayIndex, title, topicIdsJson.decodeList<String>(), theory, notes)

// ---- tasks ---------------------------------------------------------------------------------

fun TaskMentionEntity.toDomain() = Mention(
    id = id,
    source = source,
    sourceUrl = sourceUrl,
    companyTag = companyTag,
    interviewStage = interviewStage,
    roleLevel = roleLevel,
    reportedDate = reportedDate?.let(LocalDate::parse),
)

fun TaskWithRelations.toDomain() = Task(
    id = task.id,
    title = task.title,
    originalText = task.originalText,
    difficulty = task.difficulty,
    estimatedSolveMin = task.estimatedSolveMin,
    topics = topicLinks.mapTo(linkedSetOf()) { it.topicId },
    patterns = patternLinks.mapTo(linkedSetOf()) { it.patternId },
    constraints = task.constraintsJson.decodeList<String>(),
    examples = task.examplesJson.decodeList<Example>(),
    hints = task.hintsJson.decodeList<String>(),
    solutionIdea = task.solutionIdea,
    complexity = if (task.timeComplexity == null && task.spaceComplexity == null) null
    else Complexity(task.timeComplexity, task.spaceComplexity),
    roleLevel = task.roleLevel,
    personalNotes = task.personalNotes,
    status = task.status,
    timesSolved = task.timesSolved,
    lastSolvedAt = task.lastSolvedAt?.toInstant(),
    nextReviewAt = task.nextReviewAt?.toInstant(),
    confidence = task.confidence,
    origin = task.origin,
    mentions = mentions.sortedBy { it.id }.map { it.toDomain() },
)

/** [now] is used for created/updated stamps of a brand-new row. */
fun Task.toEntity(now: Long, seedKey: String? = null) = TaskEntity(
    id = id,
    title = title,
    originalText = originalText,
    canonicalKey = TitleNormalizer.canonicalKey(title),
    difficulty = difficulty,
    estimatedSolveMin = estimatedSolveMin,
    constraintsJson = constraints.encodeOrNull(),
    examplesJson = examples.encodeOrNull(),
    hintsJson = hints.encodeOrNull(),
    solutionIdea = solutionIdea,
    timeComplexity = complexity?.time,
    spaceComplexity = complexity?.space,
    roleLevel = roleLevel,
    personalNotes = personalNotes,
    status = status,
    timesSolved = timesSolved,
    lastSolvedAt = lastSolvedAt?.toEpochMilli(),
    nextReviewAt = nextReviewAt?.toEpochMilli(),
    confidence = confidence,
    origin = origin,
    seedKey = seedKey,
    createdAt = now,
    updatedAt = now,
)

fun Mention.toEntity(taskId: Long, now: Long) = TaskMentionEntity(
    taskId = taskId,
    rawTitle = "",
    rawText = "",
    source = source,
    sourceUrl = sourceUrl,
    companyTag = companyTag,
    interviewStage = interviewStage,
    roleLevel = roleLevel,
    reportedDate = reportedDate?.toString(),
    createdAt = now,
)

// ---- profile -------------------------------------------------------------------------------

fun UserProfileEntity.toDomain() = UserProfile(
    level = level,
    goal = goal,
    dailyMinutes = dailyMinutes,
    startDate = LocalDate.parse(startDate),
    targetDate = targetDate?.let(LocalDate::parse),
    onboardedAt = onboardedAt.toInstant(),
)

fun UserProfile.toEntity() = UserProfileEntity(
    level = level,
    goal = goal,
    dailyMinutes = dailyMinutes,
    startDate = startDate.toString(),
    targetDate = targetDate?.toString(),
    onboardedAt = onboardedAt.toEpochMilli(),
)

fun TopicSkillEntity.toDomain() = TopicSkill(
    topicId, selfRating, score, attempts, failures, lastPracticedAt?.toInstant(),
)

fun TopicSkill.toEntity(now: Long) = TopicSkillEntity(
    topicId, selfRating, score, attempts, failures, lastPracticedAt?.toEpochMilli(), now,
)

// ---- plan ----------------------------------------------------------------------------------

fun PlannedItemEntity.toDomain() = PlannedItem(
    id = id,
    dayIndex = dayIndex,
    kind = kind,
    taskId = taskId,
    orderIndex = orderIndex,
    estimatedMin = estimatedMin,
    status = status,
    bucket = bucket,
    reasons = reasonJson.decodeList<PlanReason>(),
    completedSessionId = completedSessionId,
)

fun PlannedItem.toEntity() = PlannedItemEntity(
    id = id,
    dayIndex = dayIndex,
    kind = kind,
    taskId = taskId,
    orderIndex = orderIndex,
    estimatedMin = estimatedMin,
    status = status,
    bucket = bucket,
    reasonJson = reasons.encodeOrNull(),
    completedSessionId = completedSessionId,
)

fun PlanDayWithDetails.toDomain() = PlanDay(
    dayIndex = day.dayIndex,
    date = LocalDate.parse(day.date),
    title = day.title,
    targetMinutes = day.targetMinutes,
    isAdjusted = day.isAdjusted,
    adjustReason = day.adjustReason,
    generatedVersion = day.generatedVersion,
    status = day.status,
    topicIds = topics.map { it.topicId }.sorted(),
    items = items.sortedBy { it.orderIndex }.map { it.toDomain() },
)

fun PlanDay.toEntity() = PlanDayEntity(
    dayIndex = dayIndex,
    date = date.toString(),
    title = title,
    targetMinutes = targetMinutes,
    isAdjusted = isAdjusted,
    adjustReason = adjustReason,
    generatedVersion = generatedVersion,
    status = status,
)

// ---- training ------------------------------------------------------------------------------

fun SolveSessionEntity.toDomain() = SolveSession(
    id = id,
    taskId = taskId,
    plannedItemId = plannedItemId,
    type = type,
    startedAt = startedAt.toInstant(),
    finishedAt = finishedAt?.toInstant(),
    durationSec = durationSec,
    hintsUsed = hintsUsed,
    outcome = outcome,
    confidence = confidence,
    notes = notes,
    localDate = LocalDate.parse(localDate),
)

fun SolveSession.toEntity() = SolveSessionEntity(
    id = id,
    taskId = taskId,
    plannedItemId = plannedItemId,
    type = type,
    startedAt = startedAt.toEpochMilli(),
    finishedAt = finishedAt?.toEpochMilli(),
    durationSec = durationSec,
    hintsUsed = hintsUsed,
    outcome = outcome,
    confidence = confidence,
    notes = notes,
    localDate = localDate.toString(),
)

fun ErrorEntryEntity.toDomain() =
    ErrorEntry(id, sessionId, taskId, type, note, resolved, createdAt.toInstant())

fun ErrorEntry.toEntity() =
    ErrorEntryEntity(id, sessionId, taskId, type, note, resolved, createdAt.toEpochMilli())

fun ReviewStateEntity.toDomain() =
    ReviewState(taskId, intervalDays, ease, repetitions, lapses, dueAt.toInstant(), lastOutcome)

fun ReviewState.toEntity() =
    ReviewStateEntity(taskId, intervalDays, ease, repetitions, lapses, dueAt.toEpochMilli(), lastOutcome)

fun ImportBatchEntity.toDomain() =
    ImportBatch(id, createdAt.toInstant(), sourceName, candidatesFound, saved, merged, skipped, status)
