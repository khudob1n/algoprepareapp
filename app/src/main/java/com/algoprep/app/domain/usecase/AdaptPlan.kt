package com.algoprep.app.domain.usecase

import com.algoprep.app.domain.model.PlanDay
import com.algoprep.app.domain.model.PlanDayStatus
import com.algoprep.app.domain.planning.AdaptivePolicy
import com.algoprep.app.domain.planning.AdjustReason
import com.algoprep.app.domain.planning.PlanRescheduler
import com.algoprep.app.domain.planning.Reschedule
import com.algoprep.app.domain.repository.CatalogRepository
import com.algoprep.app.domain.repository.PlanRepository
import com.algoprep.app.domain.repository.ProfileRepository
import com.algoprep.app.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * The evening recalculation. The roadmap order and topics never change; only how each remaining day
 * will be balanced does (see [AdaptivePolicy]). Because a day's tasks are chosen on the day itself,
 * this stores the adjustment and its reason so the Plan screen can explain it in advance.
 * Idempotent: running it again with the same data changes nothing.
 */
class ReplanRemainingDays @Inject constructor(
    private val plans: PlanRepository,
    private val profiles: ProfileRepository,
    private val catalog: CatalogRepository,
    private val clock: Clock,
) {
    /** @return how many days were updated. */
    suspend operator fun invoke(): Int {
        val today = LocalDate.now(clock)
        val days = plans.observeDays().first()
        val skills = profiles.getSkills().associateBy { it.topicId }
        val roadmap = catalog.getRoadmap()
        var changed = 0
        for (day in days) {
            if (!day.date.isAfter(today) || day.status == PlanDayStatus.DONE) continue
            val introduced = roadmap.filter { it.dayIndex <= day.dayIndex }.flatMap { it.topicIds }.toSet()
            val fresh = AdaptivePolicy.allocate(day.topicIds, skills, introduced).reason
            val existing = AdjustReason.decode(day.adjustReason)
            // Moves made by the user's choice (shift / compress) stay visible until a new adaptation replaces them.
            val keepExisting = fresh == null && (existing is AdjustReason.Shifted || existing is AdjustReason.Compressed)
            val newReason = if (keepExisting) day.adjustReason else fresh?.encode()
            if (newReason != day.adjustReason) {
                plans.updateDayMeta(day.copy(isAdjusted = newReason != null, adjustReason = newReason, generatedVersion = day.generatedVersion + 1))
                changed++
            }
        }
        return changed
    }
}

enum class AdjustMode { SHIFT, COMPRESS }

/** Shifts or compresses the unfinished part of the plan after missed days. Always an explicit user choice. */
class AdaptPlan @Inject constructor(
    private val plans: PlanRepository,
    private val profiles: ProfileRepository,
    private val catalog: CatalogRepository,
    private val tx: TransactionRunner,
    private val clock: Clock,
) {
    suspend fun preview(mode: AdjustMode): Reschedule {
        val today = LocalDate.now(clock)
        val days = plans.observeDays().first()
        return when (mode) {
            AdjustMode.SHIFT -> PlanRescheduler.shift(days, today)
            AdjustMode.COMPRESS -> PlanRescheduler.compress(days, today, skippableDays())
        }
    }

    suspend fun apply(mode: AdjustMode) {
        val result = preview(mode)
        val original = plans.observeDays().first().associateBy { it.dayIndex }
        tx.run {
            plans.deleteDays(result.dropped)
            for (day in result.days) {
                val moved = original[day.dayIndex]?.date != day.date
                val reason = when {
                    !moved -> day.adjustReason
                    mode == AdjustMode.SHIFT -> AdjustReason.Shifted(result.endDateShiftDays.coerceAtLeast(0)).encode()
                    result.dropped.isNotEmpty() -> AdjustReason.Compressed(result.dropped.size).encode()
                    else -> AdjustReason.Shifted(result.endDateShiftDays.coerceAtLeast(0)).encode()
                }
                val updated = day.copy(isAdjusted = reason != null, adjustReason = reason, generatedVersion = day.generatedVersion + 1)
                // replaceDayItems also clears stale items of days that moved; unmoved days keep theirs.
                if (moved) plans.replaceDayItems(updated) else plans.updateDayMeta(updated)
            }
            profiles.updateTargetDate(result.endDate)
        }
    }

    private suspend fun skippableDays(): Set<Int> =
        catalog.getRoadmap().filter { it.notes == SKIPPABLE }.map { it.dayIndex }.toSet()

    companion object {
        /** Marker in the roadmap template for review / mixed days that may be dropped when compressing. */
        const val SKIPPABLE = "skippable"
    }
}
