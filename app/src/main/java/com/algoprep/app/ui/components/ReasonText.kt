package com.algoprep.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.algoprep.app.R
import com.algoprep.app.domain.model.PlanReason
import com.algoprep.app.domain.model.ReasonCode
import com.algoprep.app.domain.planning.AdjustReason

/** Localised, human-readable text for a structured plan reason ("Why this task?"). */
@Composable
fun reasonText(reason: PlanReason, topicTitles: Map<String, String>): String {
    val res = LocalContext.current.resources
    val topic = reason.arg?.let { topicTitles[it] ?: it }.orEmpty()
    return when (reason.code) {
        ReasonCode.WEAK_TOPIC -> res.getString(R.string.reason_weak_topic, topic)
        ReasonCode.NOT_SOLVED_YET -> res.getString(R.string.reason_not_solved)
        ReasonCode.REVIEW_DUE -> {
            val days = reason.arg?.toIntOrNull() ?: 0
            if (days > 0) res.getQuantityString(R.plurals.reason_review_overdue, days, days)
            else res.getString(R.string.reason_review_due)
        }
        ReasonCode.RECENT_FAILURE -> res.getString(R.string.reason_recent_failure)
        ReasonCode.DIFFICULTY_FIT -> res.getString(R.string.reason_difficulty_fit)
        ReasonCode.FREQUENT_IN_DATASET -> {
            val n = reason.arg?.toIntOrNull() ?: 2
            res.getQuantityString(R.plurals.reason_frequent, n, n)
        }
        ReasonCode.ROADMAP_TOPIC -> res.getString(R.string.reason_roadmap_topic, topic)
        ReasonCode.MIXED -> res.getString(R.string.reason_mixed)
        ReasonCode.CARRIED_OVER -> res.getString(R.string.reason_carried_over)
        ReasonCode.MOCK_DAY -> res.getString(R.string.reason_mock_day)
    }
}

/** Why a plan day differs from the plain 70 / 20 / 10 split, as shown on the Plan screen. */
@Composable
fun adjustReasonText(code: String?, topicTitles: Map<String, String>): String? {
    val res = LocalContext.current.resources
    fun names(ids: List<String>) = ids.joinToString(", ") { topicTitles[it] ?: it }
    return when (val reason = AdjustReason.decode(code)) {
        is AdjustReason.MoreWeak -> res.getString(R.string.adjust_more_weak, reason.sharePercent, names(reason.topicIds))
        is AdjustReason.StrongDay -> res.getString(R.string.adjust_strong_day, names(reason.topicIds))
        is AdjustReason.Shifted -> res.getQuantityString(R.plurals.adjust_shifted, reason.days, reason.days)
        is AdjustReason.Compressed -> res.getQuantityString(R.plurals.adjust_compressed, reason.droppedDays, reason.droppedDays)
        null -> null
    }
}
