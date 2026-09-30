package com.algoprep.app.notifications

import android.content.Context
import com.algoprep.app.R
import com.algoprep.app.domain.reminders.ReminderContent
import com.algoprep.app.ui.components.durationText
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ReminderTextFormatter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun title(content: ReminderContent): String = context.getString(
        when (content) {
            is ReminderContent.Morning -> R.string.reminder_title_morning
            is ReminderContent.Evening -> R.string.reminder_title_evening
            is ReminderContent.Review -> R.string.reminder_title_review
            is ReminderContent.Streak -> R.string.reminder_title_streak
        },
    )

    fun text(content: ReminderContent): String {
        val res = context.resources
        return when (content) {
            is ReminderContent.Morning -> context.getString(
                R.string.reminder_text_morning,
                content.dayIndex,
                content.title,
                context.durationText(content.plannedMinutes),
            )
            is ReminderContent.Evening -> res.getQuantityString(
                R.plurals.reminder_text_evening, content.tasksLeft, content.percent, content.tasksLeft,
            )
            is ReminderContent.Review -> res.getQuantityString(R.plurals.reminder_text_review, content.count, content.count)
            is ReminderContent.Streak ->
                if (content.streakDays > 0) res.getQuantityString(R.plurals.reminder_text_streak_days, content.streakDays, content.streakDays)
                else context.getString(R.string.reminder_text_streak)
        }
    }
}
