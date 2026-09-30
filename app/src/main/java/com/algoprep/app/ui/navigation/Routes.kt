package com.algoprep.app.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe routes (Navigation Compose 2.8+). Top-level destinations are objects;
 * destinations with arguments (session/{taskId}, ...) are added as data classes in later phases.
 */
@Serializable data object TodayRoute
@Serializable data object PlanRoute
@Serializable data object TasksRoute
@Serializable data object StatsRoute
@Serializable data object ProfileRoute
