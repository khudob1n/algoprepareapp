package com.algoprep.app.ui.screens.errors

import com.algoprep.app.domain.model.ErrorEntry
import com.algoprep.app.domain.model.ErrorType
import com.algoprep.app.domain.model.Task

enum class ErrorFilter { OPEN, RESOLVED, ALL }

data class ErrorLogRow(val error: ErrorEntry, val taskTitle: String?)

data class ErrorLogUiState(
    val filter: ErrorFilter = ErrorFilter.OPEN,
    val rows: List<ErrorLogRow> = emptyList(),
    /** Open errors per type, most frequent first: what to work on. */
    val openByType: List<Pair<ErrorType, Int>> = emptyList(),
    val openCount: Int = 0,
    val loading: Boolean = true,
)

fun buildErrorLog(errors: List<ErrorEntry>, tasks: Map<Long, Task>, filter: ErrorFilter): ErrorLogUiState {
    val open = errors.filter { !it.resolved }
    val visible = when (filter) {
        ErrorFilter.OPEN -> open
        ErrorFilter.RESOLVED -> errors.filter { it.resolved }
        ErrorFilter.ALL -> errors
    }
    return ErrorLogUiState(
        filter = filter,
        rows = visible.sortedByDescending { it.createdAt }.map { ErrorLogRow(it, tasks[it.taskId]?.title) },
        openByType = open.groupingBy { it.type }.eachCount().toList()
            .sortedWith(compareByDescending<Pair<ErrorType, Int>> { it.second }.thenBy { it.first }),
        openCount = open.size,
        loading = false,
    )
}
