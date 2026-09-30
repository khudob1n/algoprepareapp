package com.algoprep.app.ui.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.algoprep.app.ui.navigation.GoalsRoute
import com.algoprep.app.ui.navigation.MainGraphRoute
import com.algoprep.app.ui.navigation.OnboardingGraphRoute
import com.algoprep.app.ui.navigation.RemindersRoute
import com.algoprep.app.ui.navigation.TopicsRoute
import com.algoprep.app.ui.navigation.WelcomeRoute

/** The four onboarding steps share one ViewModel scoped to the onboarding graph. */
fun NavGraphBuilder.onboardingGraph(navController: NavHostController) {
    navigation<OnboardingGraphRoute>(startDestination = WelcomeRoute) {
        composable<WelcomeRoute> {
            WelcomeScreen(onStart = { navController.navigate(GoalsRoute) })
        }
        composable<GoalsRoute> { entry ->
            val vm = entry.onboardingViewModel(navController)
            val state by vm.uiState.collectAsStateWithLifecycle()
            GoalsScreen(
                state = state,
                onLevel = vm::setLevel,
                onGoal = vm::setGoal,
                onMinutes = vm::setDailyMinutes,
                onStart = vm::setStartOption,
                onNext = { navController.navigate(TopicsRoute) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<TopicsRoute> { entry ->
            val vm = entry.onboardingViewModel(navController)
            val state by vm.uiState.collectAsStateWithLifecycle()
            TopicsScreen(
                state = state,
                onRating = vm::setRating,
                onNext = { navController.navigate(RemindersRoute) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<RemindersRoute> { entry ->
            val vm = entry.onboardingViewModel(navController)
            val state by vm.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(vm) {
                vm.finished.collect {
                    navController.navigate(MainGraphRoute) {
                        popUpTo(OnboardingGraphRoute) { inclusive = true }
                    }
                }
            }
            RemindersScreen(
                state = state,
                onReminder = vm::setReminder,
                onFinish = vm::finish,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun NavBackStackEntry.onboardingViewModel(navController: NavHostController): OnboardingViewModel {
    val parent = remember(this) { navController.getBackStackEntry<OnboardingGraphRoute>() }
    return hiltViewModel(parent)
}
