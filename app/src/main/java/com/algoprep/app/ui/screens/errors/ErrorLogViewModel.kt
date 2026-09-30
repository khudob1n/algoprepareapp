package com.algoprep.app.ui.screens.errors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algoprep.app.domain.repository.TaskRepository
import com.algoprep.app.domain.repository.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ErrorLogViewModel @Inject constructor(
    private val training: TrainingRepository,
    tasks: TaskRepository,
) : ViewModel() {
    private val filter = MutableStateFlow(ErrorFilter.OPEN)

    val uiState: StateFlow<ErrorLogUiState> = training.observeAllErrors().flatMapLatest { errors ->
        val ids = errors.map { it.taskId }.distinct()
        combine(tasks.observeByIds(ids), filter) { taskList, f ->
            buildErrorLog(errors, taskList.associateBy { it.id }, f)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ErrorLogUiState())

    fun setFilter(value: ErrorFilter) {
        filter.value = value
    }

    fun resolve(errorId: Long) {
        viewModelScope.launch { training.resolveError(errorId) }
    }
}
