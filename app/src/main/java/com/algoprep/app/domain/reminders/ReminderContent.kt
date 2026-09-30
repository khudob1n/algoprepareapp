package com.algoprep.app.domain.reminders

/** What a reminder says, as data; the Android layer turns it into localised text. */
sealed interface ReminderContent {
    data class Morning(val dayIndex: Int, val totalDays: Int, val title: String, val plannedMinutes: Int) : ReminderContent
    data class Evening(val percent: Int, val tasksLeft: Int) : ReminderContent
    data class Review(val count: Int) : ReminderContent
    /** [streakDays] is the streak that a practice today would extend (0 = none to protect). */
    data class Streak(val streakDays: Int) : ReminderContent
}
