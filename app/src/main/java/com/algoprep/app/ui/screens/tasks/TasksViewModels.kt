package com.algoprep.app.ui.screens.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.Task
import com.algoprep.app.domain.model.TaskStatus
import com.algoprep.app.domain.model.Topic
import com.algoprep.app.domain.repository.CatalogRepository
import com.algoprep.app.domain.repository.TaskRepository
import com.algoprep.app.domain.stats.InterviewData
import com.algoprep.app.domain.stats.InterviewDataCalculator
import com.algoprep.app.ui.navigation.TaskDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BankUiState(
    val loading: Boolean = true,
    val tasks: List<Task> = emptyList(),
    val totalCount: Int = 0,
    val topics: List<Topic> = emptyList(),
    val filter: BankFilter = BankFilter(),
)

@HiltViewModel
class BankViewModel @Inject constructor(
    tasks: TaskRepository,
    catalog: CatalogRepository,
) : ViewModel() {
    private val filter = MutableStateFlow(BankFilter())

    val uiState: StateFlow<BankUiState> = combine(tasks.observeAll(), catalog.observeTopics(), filter) { all, topics, f ->
        BankUiState(
            loading = false,
            tasks = filterTasks(all, f),
            totalCount = all.size,
            topics = topics,
            filter = f,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BankUiState())

    fun setQuery(q: String) = filter.update { it.copy(query = q) }
    fun setStatus(s: TaskStatus?) = filter.update { it.copy(status = s) }
    fun setDifficulty(d: Difficulty?) = filter.update { it.copy(difficulty = d) }
    fun setTopic(id: String?) = filter.update { it.copy(topicId = id) }
    fun setImportedOnly(v: Boolean) = filter.update { it.copy(importedOnly = v) }
    fun clearFilters() {
        filter.value = BankFilter()
    }
}

data class InterviewDataUiState(val loading: Boolean = true, val data: InterviewData? = null)

@HiltViewModel
class InterviewDataViewModel @Inject constructor(
    tasks: TaskRepository,
    catalog: CatalogRepository,
) : ViewModel() {
    val uiState: StateFlow<InterviewDataUiState> =
        combine(tasks.observeAll(), catalog.observeTopics(), catalog.observePatterns()) { all, topics, patterns ->
            InterviewDataUiState(loading = false, data = InterviewDataCalculator.compute(all, topics, patterns))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InterviewDataUiState())
}

data class TaskDetailUiState(
    val loading: Boolean = true,
    val task: Task? = null,
    val topicTitles: Map<String, String> = emptyMap(),
    val patternTitles: Map<String, String> = emptyMap(),
    val notes: String = "",
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tasks: TaskRepository,
    catalog: CatalogRepository,
) : ViewModel() {
    private val taskId = savedStateHandle.toRoute<TaskDetailRoute>().taskId

    private val notes = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TaskDetailUiState> = combine(
        tasks.observe(taskId),
        catalog.observeTopics(),
        catalog.observePatterns(),
        notes,
    ) { task, topics, patterns, draft ->
        TaskDetailUiState(
            loading = false,
            task = task,
            topicTitles = topics.associate { it.id to it.title },
            patternTitles = patterns.associate { it.id to it.title },
            notes = draft ?: task?.personalNotes.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailUiState())

    init {
        // Notes are saved shortly after the user stops typing.
        viewModelScope.launch {
            notes.drop(1).debounce(NOTES_DEBOUNCE_MS).collect { draft ->
                if (draft != null && draft != tasks.observe(taskId).first()?.personalNotes) tasks.updateNotes(taskId, draft)
            }
        }
    }

    fun setNotes(text: String) {
        notes.value = text
    }

    private companion object {
        const val NOTES_DEBOUNCE_MS = 700L
    }
}
