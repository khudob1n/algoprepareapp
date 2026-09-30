package com.algoprep.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algoprep.app.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class AppStart { ONBOARDING, MAIN }

@HiltViewModel
class RootViewModel @Inject constructor(profiles: ProfileRepository) : ViewModel() {
    /** Resolved once: null while loading, then where the NavHost should start. */
    val start: StateFlow<AppStart?> = flow {
        val onboarded = profiles.observeProfile().first() != null
        emit(if (onboarded) AppStart.MAIN else AppStart.ONBOARDING)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
