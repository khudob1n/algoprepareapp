package com.algoprep.app.domain.reminders

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

object ReminderTiming {
    /** Next occurrence of [time] strictly after [now] (today if still ahead, otherwise tomorrow). */
    fun nextTrigger(time: LocalTime, now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(time).atZone(now.zone)
        return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
    }

    fun delayUntilNext(time: LocalTime, now: ZonedDateTime): Duration =
        Duration.between(now, nextTrigger(time, now))
}
