package com.algoprep.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.algoprep.app.R
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home(TodayRoute, TodayRoute::class, R.string.nav_home, Icons.Filled.Home),
    Plan(PlanRoute, PlanRoute::class, R.string.nav_plan, Icons.Filled.CalendarMonth),
    Tasks(TasksRoute, TasksRoute::class, R.string.nav_tasks, Icons.AutoMirrored.Filled.ListAlt),
    Stats(StatsRoute, StatsRoute::class, R.string.nav_stats, Icons.Filled.BarChart),
    Profile(ProfileRoute, ProfileRoute::class, R.string.nav_profile, Icons.Filled.Person),
}
