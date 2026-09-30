package com.algoprep.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.algoprep.app.R
import com.algoprep.app.domain.model.ReminderKind

/** Separate channels so the user can silence, say, the streak warning without losing the morning plan. */
object NotificationChannels {
    const val PLAN = "reminders_plan"
    const val REVIEW = "reminders_review"
    const val STREAK = "reminders_streak"

    fun idFor(kind: ReminderKind): String = when (kind) {
        ReminderKind.MORNING, ReminderKind.EVENING -> PLAN
        ReminderKind.REVIEW -> REVIEW
        ReminderKind.STREAK -> STREAK
    }

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(PLAN, context.getString(R.string.channel_plan), NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(REVIEW, context.getString(R.string.channel_review), NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(STREAK, context.getString(R.string.channel_streak), NotificationManager.IMPORTANCE_LOW),
        )
    }
}
