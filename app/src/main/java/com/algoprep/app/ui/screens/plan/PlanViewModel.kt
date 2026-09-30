package com.algoprep.app.ui.screens.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.algoprep.app.domain.model.PlanDay
import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.model.PlannedStatus
import com.algoprep.app.domain.repository.CatalogRepository
import com.algoprep.app.domain.repository.PlanRepository
import com.algoprep.app.domain.repository.TaskRepository
import com.algoprep.app.ui.navigation.PlanDayRoute
import com.algoprep.app.ui.screens.today.TodayContent
import com.algoprep.app.ui.screens.today.buildTodayContent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class PlanRow(
    val day: PlanDay,
    val topicTitles: List<String>,
    val doneItems: Int,
    val totalItems: Int,
)

data class PlanUiState(
    val rows: List<PlanRow> = emptyList(),
    val doneDays: Int = 0,
    val missedDays: Int = 0,
    val totalDays: Int = 0,
    val loading: Boolean = true,
)

@HiltViewModel
class PlanViewModel @Inject constructor(
    plans: PlanRepository,
    catalog: CatalogRepository,
) : ViewModel() {
    val uiState: StateFlow<PlanUiState> = combine(plans.observeDays(), catalog.observeTopics()) { days, topics ->
        val titles = topics.associate { it.id to it.title }
        PlanUiState(
            rows = days.map { d ->
                PlanRow(
                    day = d,
                    topicTitles = d.topicIds.map { titles[it] ?: it },
                    doneItems = d.items.count { it.status == PlannedStatus.DONE },
                    totalItems = d.items.size,
                )
            },
            doneDays = days.count { it.status == PlanDayStatus.DONE },
            missedDays = days.count { it.status == PlanDayStatus.MISSED },
            totalDays = days.size,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())
}

data class PlanDayUiState(
    val content: TodayContent? = null,
    val date: LocalDate? = null,
    val status: PlanDayStatus? = null,
    val isAdjusted: Boolean = false,
    val adjustReason: String? = null,
    val topicTitles: Map<String, String> = emptyMap(),
    val topicsOfDay: List<String> = emptyList(),
    val targetMinutes: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanDayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    plans: PlanRepository,
    tasks: TaskRepository,
    catalog: CatalogRepository,
) : ViewModel() {
    private val dayIndex = savedStateHandle.toRoute<PlanDayRoute>().dayIndex

    val uiState: StateFlow<PlanDayUiState> = plans.observeDay(dayIndex).filterNotNull().flatMapLatest { day ->
        val ids = day.items.mapNotNull { it.taskId }.distinct()
        combine(
            tasks.observeByIds(ids),
            catalog.observeTopics(),
            catalog.observeRoadmap(),
            plans.observeDays().map { it.size },
        ) { taskList, topics, roadmap, total ->
            val titles = topics.associate { it.id to it.title }
            PlanDayUiState(
                content = buildTodayContent(
                    day = day,
                    totalDays = total,
                    tasks = taskList.associateBy { it.id },
                    topicTitles = titles,
                    theoryText = roadmap.firstOrNull { it.dayIndex == day.dayIndex }?.theory,
                ),
                date = day.date,
                status = day.status,
                isAdjusted = day.isAdjusted,
                adjustReason = day.adjustReason,
                topicTitles = titles,
                topicsOfDay = day.topicIds.map { titles[it] ?: it },
                targetMinutes = day.targetMinutes,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanDayUiState())
}
