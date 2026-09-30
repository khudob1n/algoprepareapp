package com.algoprep.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.algoprep.app.R
import com.algoprep.app.ui.components.PlaceholderScreen
import com.algoprep.app.ui.navigation.PlanRoute
import com.algoprep.app.ui.navigation.ProfileRoute
import com.algoprep.app.ui.navigation.StatsRoute
import com.algoprep.app.ui.navigation.TasksRoute
import com.algoprep.app.ui.navigation.TodayRoute
import com.algoprep.app.ui.navigation.TopLevelDestination

@Composable
fun AlgoPrepAppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            // Full-screen flows (session, result, onboarding) will hide the bar in later phases:
            // show it only when the current destination is one of the top-level tabs.
            val isTopLevel = TopLevelDestination.entries.any { dest ->
                currentDestination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
            }
            if (isTopLevel) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected =
                            currentDestination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<TodayRoute> {
                PlaceholderScreen(R.string.placeholder_today_title, R.string.placeholder_today_body)
            }
            composable<PlanRoute> {
                PlaceholderScreen(R.string.placeholder_plan_title, R.string.placeholder_plan_body)
            }
            composable<TasksRoute> {
                PlaceholderScreen(R.string.placeholder_tasks_title, R.string.placeholder_tasks_body)
            }
            composable<StatsRoute> {
                PlaceholderScreen(R.string.placeholder_stats_title, R.string.placeholder_stats_body)
            }
            composable<ProfileRoute> {
                PlaceholderScreen(R.string.placeholder_profile_title, R.string.placeholder_profile_body)
            }
        }
    }
}
