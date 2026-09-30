package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.TopicSkill
import kotlin.math.roundToInt

/** Why a day differs from the plain 70 / 20 / 10 split. Stored as a short code, localised by the UI. */
sealed interface AdjustReason {
    /** The share of weak-topic practice was raised because these topics are clearly weak. */
    data class MoreWeak(val sharePercent: Int, val topicIds: List<String>) : AdjustReason

    /** The day's own topics are already strong, so part of its time goes to mixed practice. */
    data class StrongDay(val topicIds: List<String>) : AdjustReason

    /** The day was moved later because earlier days were missed. */
    data class Shifted(val days: Int) : AdjustReason

    /** Review/mixed days were dropped to keep the target date. */
    data class Compressed(val droppedDays: Int) : AdjustReason

    fun encode(): String = when (this) {
        is MoreWeak -> "MORE_WEAK|$sharePercent|${topicIds.joinToString(",")}"
        is StrongDay -> "STRONG_DAY|${topicIds.joinToString(",")}"
        is Shifted -> "SHIFTED|$days"
        is Compressed -> "COMPRESSED|$droppedDays"
    }

    companion object {
        fun decode(raw: String?): AdjustReason? {
            val parts = raw?.split('|') ?: return null
            return when (parts.firstOrNull()) {
                "MORE_WEAK" -> MoreWeak(parts.getOrNull(1)?.toIntOrNull() ?: return null, parts.getOrNull(2)?.split(',')?.filter { it.isNotEmpty() }.orEmpty())
                "STRONG_DAY" -> StrongDay(parts.getOrNull(1)?.split(',')?.filter { it.isNotEmpty() }.orEmpty())
                "SHIFTED" -> Shifted(parts.getOrNull(1)?.toIntOrNull() ?: return null)
                "COMPRESSED" -> Compressed(parts.getOrNull(1)?.toIntOrNull() ?: return null)
                else -> null
            }
        }
    }
}

/** Time split of a day's practice budget (after theory, warm-up and error review). */
data class Allocation(val weakShare: Double, val spacedShare: Double, val reason: AdjustReason?) {
    companion object {
        val DEFAULT = Allocation(weakShare = 0.20, spacedShare = 0.10, reason = null)
    }
}

/**
 * The "70 / 20 / 10" rule, adapted by evidence but never broken: the roadmap always keeps at least 60%.
 *  - each clearly weak topic (>= 3 attempts, score < 0.4) adds 5 points to the weak share, up to 30%;
 *  - on a day whose own topics are all strong (score > 0.85) the spaced/mixed share grows to 15%
 *    (so the day carries less new material) and the weak share is capped at 25%.
 */
object AdaptivePolicy {
    private const val SERIOUS_SCORE = 0.40
    private const val STRONG_SCORE = 0.85
    private const val STEP = 0.05
    private const val MAX_WEAK = 0.30
    private const val MAX_NON_ROADMAP = 0.40

    fun allocate(dayTopics: List<String>, skills: Map<String, TopicSkill>, introduced: Set<String>): Allocation {
        val weak = WeakTopics.pick(skills.values.filter { it.topicId in introduced })
        val serious = weak.filter { it.attempts >= WeakTopics.MIN_ATTEMPTS && it.score < SERIOUS_SCORE }
        val strongDay = dayTopics.isNotEmpty() && dayTopics.all { t ->
            skills[t]?.let { it.attempts >= WeakTopics.MIN_ATTEMPTS && it.score > STRONG_SCORE } == true
        }

        val spaced = if (strongDay) 0.15 else Allocation.DEFAULT.spacedShare
        val weakShare = (Allocation.DEFAULT.weakShare + STEP * serious.size)
            .coerceAtMost(MAX_WEAK)
            .coerceAtMost(MAX_NON_ROADMAP - spaced)
        val reason = when {
            serious.isNotEmpty() && weakShare > Allocation.DEFAULT.weakShare ->
                AdjustReason.MoreWeak((weakShare * 100).roundToInt(), serious.map { it.topicId })
            strongDay -> AdjustReason.StrongDay(dayTopics)
            else -> null
        }
        return Allocation(weakShare, spaced, reason)
    }
}
