package com.algoprep.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.algoprep.app.R
import com.algoprep.app.domain.model.PlanReason
import com.algoprep.app.domain.model.ReasonCode

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
    }
}
