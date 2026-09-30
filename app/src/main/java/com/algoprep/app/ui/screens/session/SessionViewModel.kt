package com.algoprep.app.ui.screens.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.algoprep.app.domain.hints.HintProvider
import com.algoprep.app.domain.model.SessionPhase
import com.algoprep.app.domain.model.SolveSession
import com.algoprep.app.domain.model.phase
import com.algoprep.app.domain.repository.CatalogRepository
import com.algoprep.app.domain.repository.TaskRepository
import com.algoprep.app.domain.repository.TrainingRepository
import com.algoprep.app.ui.navigation.SessionRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/**
 * The timer is wall-clock based: the session row stores startedAt, so rotation, backgrounding and
 * process death do not lose time. The session keeps running when the user leaves the screen;
 * Today offers "resume".
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    tasks: TaskRepository,
    catalog: CatalogRepository,
    private val training: TrainingRepository,
    private val hintProvider: HintProvider,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<SessionRoute>()

    private val session = MutableStateFlow<SolveSession?>(null)
    private val notes = MutableStateFlow("")
    private val conflict = MutableStateFlow<SolveSession?>(null)
    private val loaded = MutableStateFlow(false)

    private val _events = Channel<SessionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val topicTitles: StateFlow<Map<String, String>> = catalog.observeTopics()
        .map { topics -> topics.associate { it.id to it.title } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(1_000)
        }
    }

    private val elapsed = combine(session, ticker) { s, _ ->
        when {
            s == null -> 0L
            s.durationSec > 0 -> s.durationSec.toLong()
            else -> Duration.between(s.startedAt, clock.instant()).seconds
        }
    }

    private val base = combine(tasks.observe(route.taskId).filterNotNull(), session, notes, conflict, loaded) {
            task, s, n, c, isLoaded ->
        SessionUiState(
            loading = !isLoaded,
            task = task,
            running = s != null,
            allHints = hintProvider.hintsFor(task),
            hintsRevealed = s?.hintsUsed ?: 0,
            notes = n,
            conflictingSession = c,
        )
    }

    val uiState: StateFlow<SessionUiState> = combine(base, elapsed) { state, sec -> state.copy(elapsedSec = sec) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUiState())

    init {
        viewModelScope.launch {
            val active = training.getActiveSession()
            when {
                active == null -> Unit
                active.taskId != route.taskId -> conflict.value = active
                active.phase == SessionPhase.AWAITING_RESULT -> _events.send(SessionEvent.ToResult(active.id))
                else -> {
                    session.value = active
                    notes.value = active.notes
                }
            }
            loaded.value = true
        }
        viewModelScope.launch {
            notes.debounce(NOTES_DEBOUNCE_MS).collect { persistNotes(it) }
        }
    }

    fun setNotes(text: String) {
        notes.value = text
    }

    fun start() {
        if (session.value != null || conflict.value != null) return
        viewModelScope.launch {
            val id = training.startSession(route.taskId, route.plannedItemId, route.type)
            val created = training.getSession(id) ?: return@launch
            val withNotes = created.copy(notes = notes.value)
            if (withNotes.notes.isNotEmpty()) training.saveSession(withNotes)
            session.value = withNotes
        }
    }

    fun revealHint() {
        val cur = session.value ?: return
        val total = uiState.value.allHints.size
        if (cur.hintsUsed >= total) return
        val updated = cur.copy(hintsUsed = cur.hintsUsed + 1)
        session.value = updated
        viewModelScope.launch { training.saveSession(updated) }
    }

    fun finish() {
        val cur = session.value ?: return
        val seconds = Duration.between(cur.startedAt, clock.instant()).seconds.coerceAtLeast(1).toInt()
        val updated = cur.copy(durationSec = seconds, notes = notes.value)
        session.value = updated
        viewModelScope.launch {
            training.saveSession(updated)
            _events.send(SessionEvent.ToResult(updated.id))
        }
    }

    fun cancel() {
        val cur = session.value ?: return
        viewModelScope.launch {
            training.deleteSession(cur.id)
            session.value = null
            _events.send(SessionEvent.Closed)
        }
    }

    fun resumeConflicting() {
        val other = conflict.value ?: return
        viewModelScope.launch { _events.send(SessionEvent.OpenOther(other)) }
    }

    fun discardConflicting() {
        val other = conflict.value ?: return
        viewModelScope.launch {
            training.deleteSession(other.id)
            conflict.value = null
        }
    }

    private suspend fun persistNotes(text: String) {
        val cur = session.value ?: return
        if (cur.notes == text) return
        val updated = cur.copy(notes = text)
        session.value = updated
        training.saveSession(updated)
    }

    private companion object {
        const val NOTES_DEBOUNCE_MS = 600L
    }
}
