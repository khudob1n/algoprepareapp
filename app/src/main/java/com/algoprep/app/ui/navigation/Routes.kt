package com.algoprep.app.ui.navigation

import com.algoprep.app.domain.model.SessionType
import kotlinx.serialization.Serializable

/**
 * Type-safe routes (Navigation Compose 2.8+). Destinations with arguments
 * (session/{taskId}, ...) are added as data classes in later phases.
 */

// Graphs
@Serializable data object OnboardingGraphRoute
@Serializable data object MainGraphRoute

// Onboarding
@Serializable data object WelcomeRoute
@Serializable data object GoalsRoute
@Serializable data object TopicsRoute
@Serializable data object RemindersRoute

// Main tabs
@Serializable data object TodayRoute
@Serializable data object PlanRoute
@Serializable data object TasksRoute
@Serializable data object StatsRoute
@Serializable data object ProfileRoute

// Detail screens (hide the bottom bar)
@Serializable data class PlanDayRoute(val dayIndex: Int)
@Serializable data class SessionRoute(
    val taskId: Long,
    val plannedItemId: Long? = null,
    val type: SessionType = SessionType.PRACTICE,
)
@Serializable data class ResultRoute(val sessionId: Long)
@Serializable data object ErrorLogRoute
@Serializable data class TaskDetailRoute(val taskId: Long)
@Serializable data object ImportReviewRoute
@Serializable data class ImportDoneRoute(val saved: Int, val merged: Int, val skipped: Int)
