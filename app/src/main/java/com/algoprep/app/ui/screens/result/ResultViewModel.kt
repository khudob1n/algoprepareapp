package com.algoprep.app.ui.screens.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.algoprep.app.domain.model.ErrorType
import com.algoprep.app.domain.model.SessionPhase
import com.algoprep.app.domain.model.SolveOutcome
import com.algoprep.app.domain.model.phase
import com.algoprep.app.domain.repository.TaskRepository
import com.algoprep.app.domain.repository.TrainingRepository
import com.algoprep.app.domain.usecase.CompleteSession
import com.algoprep.app.domain.usecase.CompleteSessionInput
import com.algoprep.app.ui.navigation.ResultRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val training: TrainingRepository,
    private val tasks: TaskRepository,
    private val completeSession: CompleteSession,
) : ViewModel() {
    private val sessionId = savedStateHandle.toRoute<ResultRoute>().sessionId

    private val _state = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _state.asStateFlow()

    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    init {
        viewModelScope.launch {
            val session = training.getSession(sessionId)
            val task = session?.let { tasks.get(it.taskId) }
            if (session == null || task == null || session.phase == SessionPhase.FINISHED) {
                _done.send(Unit)
                return@launch
            }
            _state.value = ResultUiState(
                loading = false,
                taskTitle = task.title,
                durationSec = session.durationSec.toLong(),
                hintsUsed = session.hintsUsed,
                solutionIdea = task.solutionIdea,
                complexity = task.complexity,
                form = ResultForm(outcome = suggestOutcome(session.hintsUsed)),
            )
        }
    }

    fun setOutcome(outcome: SolveOutcome) = _state.update { it.copy(form = it.form.copy(outcome = outcome)) }
    fun setConfidence(value: Int) = _state.update { it.copy(form = it.form.copy(confidence = value.coerceIn(1, 5))) }
    fun toggleError(type: ErrorType) = _state.update { it.copy(form = it.form.toggleError(type)) }
    fun setOtherNote(text: String) = _state.update { it.copy(form = it.form.copy(otherNote = text)) }

    fun complete() {
        val s = _state.value
        val outcome = s.form.outcome ?: return
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            completeSession(
                CompleteSessionInput(
                    sessionId = sessionId,
                    outcome = outcome,
                    confidence = s.form.confidence,
                    errorTypes = s.form.errors,
                    otherNote = s.form.otherNote.takeIf { ErrorType.OTHER in s.form.errors },
                ),
            )
            _done.send(Unit)
        }
    }
}
