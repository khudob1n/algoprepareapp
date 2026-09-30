package com.algoprep.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algoprep.app.domain.model.InterviewGoal
import com.algoprep.app.domain.model.ReminderKind
import com.algoprep.app.domain.model.ReminderSettings
import com.algoprep.app.domain.model.ReminderSlot
import com.algoprep.app.domain.model.StartOption
import com.algoprep.app.domain.model.Topic
import com.algoprep.app.domain.model.UserLevel
import com.algoprep.app.domain.model.defaultSelfRating
import com.algoprep.app.domain.repository.CatalogRepository
import com.algoprep.app.domain.usecase.CompleteOnboarding
import com.algoprep.app.domain.usecase.OnboardingInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class OnboardingUiState(
    val level: UserLevel = UserLevel.INTERMEDIATE,
    val goal: InterviewGoal = InterviewGoal.BIG_TECH,
    val dailyMinutes: Int = 120,
    val startOption: StartOption = StartOption.TODAY,
    val topics: List<Topic> = emptyList(),
    /** Only ratings the user set explicitly; the rest fall back to the level default. */
    val explicitRatings: Map<String, Int> = emptyMap(),
    val reminders: ReminderSettings = ReminderSettings.DEFAULT,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    fun ratingFor(topicId: String): Int = explicitRatings[topicId] ?: defaultSelfRating(level)
}

private data class Form(
    val level: UserLevel = UserLevel.INTERMEDIATE,
    val goal: InterviewGoal = InterviewGoal.BIG_TECH,
    val dailyMinutes: Int = 120,
    val startOption: StartOption = StartOption.TODAY,
    val explicitRatings: Map<String, Int> = emptyMap(),
    val reminders: ReminderSettings = ReminderSettings.DEFAULT,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    catalog: CatalogRepository,
    private val completeOnboarding: CompleteOnboarding,
    private val clock: Clock,
) : ViewModel() {

    private val form = MutableStateFlow(Form())

    val uiState: StateFlow<OnboardingUiState> =
        combine(form, catalog.observeTopics()) { f, topics ->
            OnboardingUiState(
                level = f.level,
                goal = f.goal,
                dailyMinutes = f.dailyMinutes,
                startOption = f.startOption,
                topics = topics,
                explicitRatings = f.explicitRatings,
                reminders = f.reminders,
                isSaving = f.isSaving,
                saveFailed = f.saveFailed,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    private val _finished = Channel<Unit>(Channel.BUFFERED)
    val finished = _finished.receiveAsFlow()

    fun setLevel(level: UserLevel) = form.update { it.copy(level = level) }
    fun setGoal(goal: InterviewGoal) = form.update { it.copy(goal = goal) }
    fun setDailyMinutes(minutes: Int) = form.update { it.copy(dailyMinutes = minutes.coerceIn(30, 240)) }
    fun setStartOption(option: StartOption) = form.update { it.copy(startOption = option) }

    fun setRating(topicId: String, rating: Int) =
        form.update { it.copy(explicitRatings = it.explicitRatings + (topicId to rating.coerceIn(1, 5))) }

    fun setReminder(kind: ReminderKind, slot: ReminderSlot) =
        form.update { it.copy(reminders = it.reminders.withSlot(kind, slot)) }

    fun finish() {
        val state = uiState.value
        if (state.isSaving) return
        form.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                completeOnboarding(
                    OnboardingInput(
                        level = state.level,
                        goal = state.goal,
                        dailyMinutes = state.dailyMinutes,
                        startDate = state.startOption.resolve(LocalDate.now(clock)),
                        selfRatings = state.topics.associate { it.id to state.ratingFor(it.id) },
                        reminders = state.reminders,
                    ),
                )
                _finished.send(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                form.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }
}
