package com.algoprep.app.ui.screens.today

import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.PlanDay
import com.algoprep.app.domain.model.PlanReason
import com.algoprep.app.domain.model.PlannedItem
import com.algoprep.app.domain.model.PlannedKind
import com.algoprep.app.domain.model.PlannedStatus
import com.algoprep.app.domain.model.Task
import java.time.LocalDate
import kotlin.math.roundToInt

data class TodayItem(
    val id: Long,
    val kind: PlannedKind,
    val taskId: Long?,
    val taskTitle: String?,
    val difficulty: Difficulty?,
    /** Theory text from the roadmap (THEORY items only). */
    val theoryText: String?,
    val estimatedMin: Int,
    val done: Boolean,
    val reasons: List<PlanReason>,
)

data class TodayContent(
    val dayIndex: Int,
    val totalDays: Int,
    val title: String,
    val topicTitles: List<String>,
    val targetMinutes: Int,
    val plannedMinutes: Int,
    val remainingMinutes: Int,
    val progress: Float,
    val intro: List<TodayItem>,
    val main: List<TodayItem>,
    val review: List<TodayItem>,
    val errorReview: List<TodayItem>,
    /** First unfinished item that can be started (a task, or the mock interview), in plan order. */
    val nextTaskItem: TodayItem?,
    /** Plan days that ended unfinished; drives the "you are behind" hint. */
    val behindDays: Int = 0,
) {
    val progressPercent: Int get() = (progress * 100).roundToInt()
    val hasItems: Boolean get() = intro.isNotEmpty() || main.isNotEmpty() || review.isNotEmpty() || errorReview.isNotEmpty()
}

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object NoPlan : TodayUiState
    data class NotStarted(val startDate: LocalDate) : TodayUiState
    data object Finished : TodayUiState
    data class Active(val content: TodayContent) : TodayUiState
}

/** Pure mapping from plan data to what the screen shows (no Android, unit-tested). */
fun buildTodayContent(
    day: PlanDay,
    totalDays: Int,
    tasks: Map<Long, Task>,
    topicTitles: Map<String, String>,
    theoryText: String?,
    behindDays: Int = 0,
): TodayContent {
    val items = day.items.sortedBy { it.orderIndex }.map { it.toTodayItem(tasks, theoryText) }
    val planned = items.sumOf { it.estimatedMin }
    val doneMin = items.filter { it.done }.sumOf { it.estimatedMin }
    val progress = if (planned == 0) 0f else doneMin.toFloat() / planned
    return TodayContent(
        dayIndex = day.dayIndex,
        totalDays = totalDays,
        title = day.title,
        topicTitles = day.topicIds.map { topicTitles[it] ?: it },
        targetMinutes = day.targetMinutes,
        plannedMinutes = planned,
        remainingMinutes = planned - doneMin,
        progress = progress,
        intro = items.filter { it.kind == PlannedKind.THEORY || it.kind == PlannedKind.WARMUP },
        main = items.filter { it.kind == PlannedKind.MAIN || it.kind == PlannedKind.MOCK },
        review = items.filter { it.kind == PlannedKind.REVIEW },
        errorReview = items.filter { it.kind == PlannedKind.ERROR_REVIEW },
        nextTaskItem = items.firstOrNull { !it.done && (it.taskId != null || it.kind == PlannedKind.MOCK) },
        behindDays = behindDays,
    )
}

private fun PlannedItem.toTodayItem(tasks: Map<Long, Task>, theoryText: String?): TodayItem {
    val task = taskId?.let { tasks[it] }
    return TodayItem(
        id = id,
        kind = kind,
        taskId = taskId,
        taskTitle = task?.title,
        difficulty = task?.difficulty,
        theoryText = if (kind == PlannedKind.THEORY) theoryText else null,
        estimatedMin = estimatedMin,
        done = status == PlannedStatus.DONE,
        reasons = reasons,
    )
}
