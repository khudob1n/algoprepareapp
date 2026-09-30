package com.algoprep.app.domain.usecase

import com.algoprep.app.domain.model.InterviewGoal
import com.algoprep.app.domain.model.ReminderSettings
import com.algoprep.app.domain.model.UserLevel
import com.algoprep.app.domain.model.UserProfile
import com.algoprep.app.domain.planning.PlanGenerator
import com.algoprep.app.domain.repository.CatalogRepository
import com.algoprep.app.domain.repository.PlanRepository
import com.algoprep.app.domain.repository.ProfileRepository
import com.algoprep.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class OnboardingInput(
    val level: UserLevel,
    val goal: InterviewGoal,
    val dailyMinutes: Int,
    val startDate: LocalDate,
    /** Effective rating (1..5) for every topic. */
    val selfRatings: Map<String, Int>,
    val reminders: ReminderSettings,
)

class CompleteOnboarding @Inject constructor(
    private val catalog: CatalogRepository,
    private val profiles: ProfileRepository,
    private val plans: PlanRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    /**
     * Order matters: the profile row is what marks onboarding as done, so it is written last.
     * If anything fails earlier the user simply sees onboarding again and the plan is replaced.
     */
    suspend operator fun invoke(input: OnboardingInput) {
        // Seeding runs at app start; wait (bounded) in case the user was faster than the first import.
        val roadmap = withTimeout(SEED_WAIT_MS) { catalog.observeRoadmap().first { it.isNotEmpty() } }

        settings.saveReminders(input.reminders)

        val days = PlanGenerator.generateSkeleton(
            roadmap = roadmap,
            startDate = input.startDate,
            dailyMinutes = input.dailyMinutes,
            today = LocalDate.now(clock),
        )
        plans.replacePlan(days)

        profiles.saveOnboarding(
            profile = UserProfile(
                level = input.level,
                goal = input.goal.name,
                dailyMinutes = input.dailyMinutes,
                startDate = input.startDate,
                targetDate = days.last().date,
                onboardedAt = clock.instant(),
            ),
            selfRatings = input.selfRatings,
        )
    }

    private companion object {
        const val SEED_WAIT_MS = 10_000L
    }
}
