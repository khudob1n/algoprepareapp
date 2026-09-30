package com.algoprep.app.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class TaskWithRelations(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val mentions: List<TaskMentionEntity>,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val topicLinks: List<TaskTopicEntity>,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val patternLinks: List<TaskPatternEntity>,
)

data class PlanDayWithDetails(
    @Embedded val day: PlanDayEntity,
    @Relation(parentColumn = "dayIndex", entityColumn = "dayIndex")
    val items: List<PlannedItemEntity>,
    @Relation(parentColumn = "dayIndex", entityColumn = "dayIndex")
    val topics: List<PlanDayTopicEntity>,
)
