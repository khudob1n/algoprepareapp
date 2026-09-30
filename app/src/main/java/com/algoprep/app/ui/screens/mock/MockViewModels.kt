package com.algoprep.app.ui.screens.mock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.algoprep.app.domain.mock.MockFormat
import com.algoprep.app.domain.model.ErrorType
import com.algoprep.app.domain.model.SolveOutcome
import com.algoprep.app.domain.model.Task
import com.algoprep.app.domain.repository.CatalogRepository
import com.algoprep.app.domain.repository.TaskRepository
import com.algoprep.app.domain.repository.TrainingRepository
import com.algoprep.app.domain.usecase.CompleteMock
import com.algoprep.app.domain.usecase.CompleteSessionInput
import com.algoprep.app.domain.usecase.FinishMock
import com.algoprep.app.domain.usecase.MockTaskTime
import com.algoprep.app.domain.usecase.PrepareMock
import com.algoprep.app.ui.navigation.MockResultRoute
import com.algoprep.app.ui.navigation.MockSessionRoute
import com.algoprep.app.ui.navigation.MockSetupRoute
import com.algoprep.app.ui.screens.result.ResultForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

// ---- setup -----------------------------------------------------------------------------------

data class MockSetupUiState(
    val format: MockFormat = MockFormat.QUICK,
    val bankSize: Int = 0,
    val starting: Boolean = false,
    val notEnoughTasks: Boolean = false,
)

sealed interface MockSetupEvent {
    data class Start(val taskIds: List<Long>, val limitMinutes: Int, val startedAtMillis: Long) : MockSetupEvent
}

@HiltViewModel
class MockSetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    tasks: TaskRepository,
    private val prepareMock: PrepareMock,
    private val clock: Clock,
) : ViewModel() {
    val plannedItemId: Long? = savedStateHandle.toRoute<MockSetupRoute>().plannedItemId

    private val local = MutableStateFlow(MockSetupUiState())

    val uiState: StateFlow<MockSetupUiState> = combine(local, tasks.observeAll()) { s, all ->
        s.copy(bankSize = all.size, notEnoughTasks = all.size < s.format.taskCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MockSetupUiState())

    private val _events = Channel<MockSetupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun setFormat(format: MockFormat) = local.update { it.copy(format = format) }

    fun start() {
        val format = local.value.format
        if (local.value.starting) return
        local.update { it.copy(starting = true) }
        viewModelScope.launch {
            val picked = prepareMock(format)
            local.update { it.copy(starting = false) }
            if (picked.isNotEmpty()) {
                _events.send(MockSetupEvent.Start(picked.map { it.id }, format.minutes, clock.millis()))
            }
        }
    }
}

// ---- running ---------------------------------------------------------------------------------

sealed interface MockSessionEvent {
    data class Finished(val sessionIds: List<Long>, val limitMinutes: Int, val totalSeconds: Long) : MockSessionEvent
}

/**
 * The interview clock and the working state live in SavedStateHandle, so rotation and even process death
 * do not lose progress; the clock is wall-clock based (the route carries the start time).
 */
@HiltViewModel
class MockSessionViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    tasks: TaskRepository,
    catalog: CatalogRepository,
    private val finishMock: FinishMock,
    private val clock: Clock,
) : ViewModel() {
    private val route = handle.toRoute<MockSessionRoute>()
    private val taskIds = decodeIds(route.taskIds)

    private val index = handle.getStateFlow(KEY_INDEX, 0)
    private val segmentStart = handle.getStateFlow(KEY_SEGMENT, route.startedAtMillis)
    private val spentMs = handle.getStateFlow(KEY_SPENT, LongArray(taskIds.size))
    private val notes = handle.getStateFlow(KEY_NOTES, ArrayList(List(taskIds.size) { "" }))
    private var finishing = false

    private val _events = Channel<MockSessionEvent>(Channel.BUFFERED)
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

    val uiState: StateFlow<MockUiState> = combine(tasks.observeByIds(taskIds), index, notes, ticker) { loaded, i, n, _ ->
        val byId = loaded.associateBy { it.id }
        MockUiState(
            loading = loaded.size < taskIds.size,
            tasks = taskIds.mapNotNull { byId[it] },
            index = i,
            limitSec = route.limitMinutes * 60L,
            elapsedSec = ((clock.millis() - route.startedAtMillis) / 1000).coerceAtLeast(0),
            notes = n.toList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MockUiState())

    fun select(newIndex: Int) {
        val current = index.value
        if (newIndex == current || newIndex !in taskIds.indices) return
        val now = clock.millis()
        val spent = spentMs.value.copyOf()
        spent[current] += (now - segmentStart.value).coerceAtLeast(0)
        handle[KEY_SPENT] = spent
        handle[KEY_SEGMENT] = now
        handle[KEY_INDEX] = newIndex
    }

    fun setNotes(text: String) {
        val updated = ArrayList(notes.value)
        updated[index.value] = text
        handle[KEY_NOTES] = updated
    }

    fun finish() {
        if (finishing) return
        finishing = true
        val now = clock.millis()
        val seconds = secondsPerTask(spentMs.value, index.value, segmentStart.value, now)
        val times = taskIds.mapIndexed { i, id -> MockTaskTime(id, seconds[i], notes.value[i]) }
        viewModelScope.launch {
            try {
                val ids = finishMock(times, route.plannedItemId)
                _events.send(MockSessionEvent.Finished(ids, route.limitMinutes, ((now - route.startedAtMillis) / 1000).coerceAtLeast(0)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                finishing = false
            }
        }
    }

    private companion object {
        const val KEY_INDEX = "index"
        const val KEY_SEGMENT = "segmentStart"
        const val KEY_SPENT = "spentMs"
        const val KEY_NOTES = "notes"
    }
}

// ---- result ----------------------------------------------------------------------------------

data class MockResultRow(
    val sessionId: Long,
    val task: Task,
    val durationSec: Long,
    val form: ResultForm,
)

data class MockResultUiState(
    val loading: Boolean = true,
    val rows: List<MockResultRow> = emptyList(),
    val limitMinutes: Int = 0,
    val totalSeconds: Long = 0,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val failed: Boolean = false,
) {
    val canComplete: Boolean get() = rows.isNotEmpty() && rows.all { it.form.canComplete }
    val overtimeSeconds: Long get() = (totalSeconds - limitMinutes * 60L).coerceAtLeast(0)
}

@HiltViewModel
class MockResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val training: TrainingRepository,
    private val tasks: TaskRepository,
    private val completeMock: CompleteMock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<MockResultRoute>()

    private val _state = MutableStateFlow(MockResultUiState(limitMinutes = route.limitMinutes, totalSeconds = route.totalSeconds))
    val uiState: StateFlow<MockResultUiState> = _state

    init {
        viewModelScope.launch {
            val rows = decodeIds(route.sessionIds).mapNotNull { id ->
                val session = training.getSession(id) ?: return@mapNotNull null
                val task = tasks.get(session.taskId) ?: return@mapNotNull null
                MockResultRow(id, task, session.durationSec.toLong(), ResultForm())
            }
            _state.update { it.copy(loading = false, rows = rows, saved = rows.isEmpty()) }
        }
    }

    private fun update(sessionId: Long, f: (ResultForm) -> ResultForm) = _state.update { s ->
        s.copy(rows = s.rows.map { if (it.sessionId == sessionId) it.copy(form = f(it.form)) else it })
    }

    fun setOutcome(id: Long, o: SolveOutcome) = update(id) { it.copy(outcome = o) }
    fun setConfidence(id: Long, v: Int) = update(id) { it.copy(confidence = v.coerceIn(1, 5)) }
    fun toggleError(id: Long, t: ErrorType) = update(id) { it.toggleError(t) }
    fun setOtherNote(id: Long, text: String) = update(id) { it.copy(otherNote = text) }

    fun complete() {
        val s = _state.value
        if (!s.canComplete || s.saving) return
        _state.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            try {
                completeMock(
                    s.rows.map { row ->
                        CompleteSessionInput(
                            sessionId = row.sessionId,
                            outcome = checkNotNull(row.form.outcome),
                            confidence = row.form.confidence,
                            errorTypes = row.form.errors,
                            otherNote = row.form.otherNote.takeIf { ErrorType.OTHER in row.form.errors },
                        )
                    },
                )
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, failed = true) }
            }
        }
    }
}
