package com.algoprep.app.ui.screens.mock

import com.algoprep.app.domain.model.Task
import com.algoprep.app.ui.screens.session.formatTimer
import kotlin.math.abs

data class MockUiState(
    val loading: Boolean = true,
    val tasks: List<Task> = emptyList(),
    val index: Int = 0,
    val limitSec: Long = 0,
    val elapsedSec: Long = 0,
    val notes: List<String> = emptyList(),
) {
    val remainingSec: Long get() = limitSec - elapsedSec
    val overtime: Boolean get() = remainingSec < 0
    val current: Task? get() = tasks.getOrNull(index)
    val isLast: Boolean get() = index >= tasks.lastIndex
}

/** "45:00" while time is left, "+02:10" once the limit is exceeded. */
fun formatCountdown(remainingSec: Long): String =
    (if (remainingSec < 0) "+" else "") + formatTimer(abs(remainingSec))

/** Task ids travel in navigation routes as "1,2,3". */
fun encodeIds(ids: List<Long>): String = ids.joinToString(",")

fun decodeIds(raw: String): List<Long> = raw.split(',').mapNotNull { it.trim().toLongOrNull() }

/**
 * Time per task in whole seconds. The time of the segment that is currently running
 * ([currentIndex], started at [segmentStartMs]) is added to that task.
 */
fun secondsPerTask(spentMs: LongArray, currentIndex: Int, segmentStartMs: Long, nowMs: Long): List<Long> =
    spentMs.mapIndexed { i, ms ->
        val total = ms + if (i == currentIndex) (nowMs - segmentStartMs).coerceAtLeast(0) else 0
        total / 1000
    }
