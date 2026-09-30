package com.algoprep.app.ui.navigation

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
@Serializable data class SessionRoute(val taskId: Long, val plannedItemId: Long? = null)
