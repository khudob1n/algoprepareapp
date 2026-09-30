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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.algoprep.app.R
import com.algoprep.app.domain.model.SessionPhase
import com.algoprep.app.domain.model.phase
import com.algoprep.app.domain.model.toSessionType
import com.algoprep.app.ui.components.PlaceholderScreen
import com.algoprep.app.ui.navigation.ErrorLogRoute
import com.algoprep.app.ui.navigation.MainGraphRoute
import com.algoprep.app.ui.navigation.OnboardingGraphRoute
import com.algoprep.app.ui.navigation.PlanDayRoute
import com.algoprep.app.ui.navigation.PlanRoute
import com.algoprep.app.ui.navigation.ResultRoute
import com.algoprep.app.ui.navigation.ProfileRoute
import com.algoprep.app.ui.navigation.SessionRoute
import com.algoprep.app.ui.navigation.StatsRoute
import com.algoprep.app.ui.navigation.TasksRoute
import com.algoprep.app.ui.navigation.TodayRoute
import com.algoprep.app.ui.navigation.TopLevelDestination
import com.algoprep.app.ui.screens.onboarding.onboardingGraph
import com.algoprep.app.ui.screens.plan.PlanDayScreen
import com.algoprep.app.ui.screens.plan.PlanScreen
import com.algoprep.app.ui.screens.errors.ErrorLogScreen
import com.algoprep.app.ui.screens.result.ResultScreen
import com.algoprep.app.ui.screens.stats.StatsPlaceholderScreen
import com.algoprep.app.ui.screens.session.SessionScreen
import com.algoprep.app.ui.screens.today.TodayScreen

@Composable
fun AlgoPrepAppRoot(rootViewModel: RootViewModel = hiltViewModel()) {
    val start by rootViewModel.start.collectAsStateWithLifecycle()
    // The Surface in MainActivity paints the background while we resolve the start destination.
    val resolvedStart = start ?: return
    AppScaffold(startOnboarding = resolvedStart == AppStart.ONBOARDING)
}

@Composable
private fun AppScaffold(startOnboarding: Boolean) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            // Shown only on the five tabs; onboarding and full-screen flows hide it.
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
                                    popUpTo(TodayRoute) { saveState = true }
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
            startDestination = if (startOnboarding) OnboardingGraphRoute else MainGraphRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            onboardingGraph(navController)
            navigation<MainGraphRoute>(startDestination = TodayRoute) {
                composable<TodayRoute> {
                    TodayScreen(
                        onOpenTask = { item ->
                            item.taskId?.let {
                                navController.navigate(SessionRoute(it, item.id, item.kind.toSessionType()))
                            }
                        },
                        onOpenSession = { session ->
                            if (session.phase == SessionPhase.AWAITING_RESULT) {
                                navController.navigate(ResultRoute(session.id))
                            } else {
                                navController.navigate(
                                    SessionRoute(session.taskId, session.plannedItemId, session.type),
                                )
                            }
                        },
                    )
                }
                composable<PlanRoute> {
                    PlanScreen(onOpenDay = { navController.navigate(PlanDayRoute(it)) })
                }
                composable<PlanDayRoute> {
                    PlanDayScreen(onBack = { navController.popBackStack() })
                }
                composable<SessionRoute> {
                    SessionScreen(
                        onBack = { navController.popBackStack() },
                        onResult = { sessionId ->
                            navController.navigate(ResultRoute(sessionId)) {
                                popUpTo<SessionRoute> { inclusive = true }
                            }
                        },
                        onOpenOther = { other ->
                            navController.navigate(SessionRoute(other.taskId, other.plannedItemId, other.type)) {
                                popUpTo<SessionRoute> { inclusive = true }
                            }
                        },
                    )
                }
                composable<ResultRoute> {
                    ResultScreen(onDone = { navController.popBackStack(TodayRoute, inclusive = false) })
                }
                composable<TasksRoute> {
                    PlaceholderScreen(R.string.placeholder_tasks_title, R.string.placeholder_tasks_body)
                }
                composable<StatsRoute> {
                    StatsPlaceholderScreen(onOpenErrors = { navController.navigate(ErrorLogRoute) })
                }
                composable<ErrorLogRoute> {
                    ErrorLogScreen(onBack = { navController.popBackStack() })
                }
                composable<ProfileRoute> {
                    PlaceholderScreen(R.string.placeholder_profile_title, R.string.placeholder_profile_body)
                }
            }
        }
    }
}
