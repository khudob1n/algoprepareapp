package com.algoprep.app.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algoprep.app.domain.model.PlannedStatus
import com.algoprep.app.domain.repository.CatalogRepository
import com.algoprep.app.domain.repository.PlanRepository
import com.algoprep.app.domain.repository.TaskRepository
import com.algoprep.app.domain.usecase.EnsureTodayPlan
import com.algoprep.app.domain.usecase.TodayPlanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val ensureTodayPlan: EnsureTodayPlan,
    private val plans: PlanRepository,
    private val tasks: TaskRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0)

    val topicTitles: StateFlow<Map<String, String>> = catalog.observeTopics()
        .map { topics -> topics.associate { it.id to it.title } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val uiState: StateFlow<TodayUiState> = refreshTick
        .flatMapLatest {
            flow<TodayUiState> {
                when (val result = ensureTodayPlan()) {
                    TodayPlanResult.NoProfile -> emit(TodayUiState.NoPlan)
                    is TodayPlanResult.NotStarted -> emit(TodayUiState.NotStarted(result.startDate))
                    TodayPlanResult.Finished -> emit(TodayUiState.Finished)
                    is TodayPlanResult.Ready -> emitAll(contentFlow(result.dayIndex))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)

    /** Call when the screen becomes visible again (the date may have changed). */
    fun refresh() {
        refreshTick.value++
    }

    fun setItemDone(itemId: Long, done: Boolean) {
        viewModelScope.launch {
            plans.setItemStatus(itemId, if (done) PlannedStatus.DONE else PlannedStatus.TODO, null)
        }
    }

    private fun contentFlow(dayIndex: Int): Flow<TodayUiState> =
        plans.observeDay(dayIndex).filterNotNull().flatMapLatest { day ->
            val ids = day.items.mapNotNull { it.taskId }.distinct()
            combine(
                tasks.observeByIds(ids),
                catalog.observeTopics(),
                catalog.observeRoadmap(),
                plans.observeDays(),
            ) { taskList, topics, roadmap, days ->
                TodayUiState.Active(
                    buildTodayContent(
                        day = day,
                        totalDays = days.size,
                        tasks = taskList.associateBy { it.id },
                        topicTitles = topics.associate { it.id to it.title },
                        theoryText = roadmap.firstOrNull { it.dayIndex == day.dayIndex }?.theory,
                    ),
                )
            }
        }
}
