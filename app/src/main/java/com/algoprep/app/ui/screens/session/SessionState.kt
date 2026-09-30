package com.algoprep.app.ui.screens.session

import com.algoprep.app.domain.model.SolveSession
import com.algoprep.app.domain.model.Task
import java.util.Locale

data class SessionUiState(
    val loading: Boolean = true,
    val task: Task? = null,
    val running: Boolean = false,
    val elapsedSec: Long = 0,
    val allHints: List<String> = emptyList(),
    val hintsRevealed: Int = 0,
    val notes: String = "",
    /** Another unfinished session blocks starting this one until the user resolves it. */
    val conflictingSession: SolveSession? = null,
) {
    val revealedHints: List<String> get() = allHints.take(hintsRevealed)
    val canRevealHint: Boolean get() = running && hintsRevealed < allHints.size
    val estimatedSec: Long? get() = task?.estimatedSolveMin?.let { it * 60L }
    val isOvertime: Boolean get() = running && estimatedSec?.let { elapsedSec > it } == true
}

sealed interface SessionEvent {
    data class ToResult(val sessionId: Long) : SessionEvent
    data class OpenOther(val session: SolveSession) : SessionEvent
    data object Closed : SessionEvent
}

/** mm:ss, or h:mm:ss from one hour on. */
fun formatTimer(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.ROOT, "%02d:%02d", m, sec)
}
