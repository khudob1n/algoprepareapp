package com.algoprep.app.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algoprep.app.domain.repository.CatalogRepository
import com.algoprep.app.domain.repository.PlanRepository
import com.algoprep.app.domain.repository.ProfileRepository
import com.algoprep.app.domain.repository.TaskRepository
import com.algoprep.app.domain.repository.TrainingRepository
import com.algoprep.app.domain.stats.Stats
import com.algoprep.app.domain.stats.StatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data class Ready(val stats: Stats) : StatsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    training: TrainingRepository,
    tasks: TaskRepository,
    profiles: ProfileRepository,
    catalog: CatalogRepository,
    plans: PlanRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = training.observeFinishedSessions()
        .flatMapLatest { sessions ->
            val taskIds = sessions.map { it.taskId }.distinct()
            combine(
                tasks.observeByIds(taskIds),
                profiles.observeSkills(),
                catalog.observeTopics(),
                plans.observeDays(),
                training.observeAllErrors(),
            ) { taskList, skills, topics, days, errors ->
                val stats = StatsCalculator.compute(
                    sessions = sessions,
                    tasks = taskList.associateBy { it.id },
                    skills = skills,
                    topics = topics,
                    planDays = days,
                    errors = errors,
                    today = LocalDate.now(clock),
                )
                StatsUiState.Ready(stats) as StatsUiState
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState.Loading)
}
