package com.algoprep.app.domain.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTimingTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone)

    @Test fun laterTodayStaysToday() {
        assertEquals(at("2026-10-12", "20:00"), ReminderTiming.nextTrigger(LocalTime.of(20, 0), at("2026-10-12", "08:30")))
        assertEquals(Duration.ofHours(11).plusMinutes(30), ReminderTiming.delayUntilNext(LocalTime.of(20, 0), at("2026-10-12", "08:30")))
    }

    @Test fun alreadyPassedMeansTomorrow() {
        assertEquals(at("2026-10-13", "08:00"), ReminderTiming.nextTrigger(LocalTime.of(8, 0), at("2026-10-12", "08:30")))
    }

    @Test fun exactlyNowMeansTomorrow() {
        assertEquals(at("2026-10-13", "08:00"), ReminderTiming.nextTrigger(LocalTime.of(8, 0), at("2026-10-12", "08:00")))
    }

    @Test fun monthEndRollsOver() {
        assertEquals(at("2026-11-01", "07:15"), ReminderTiming.nextTrigger(LocalTime.of(7, 15), at("2026-10-31", "23:00")))
    }

    @Test fun keepsWallClockTimeAcrossDaylightSavingChange() {
        // Clocks go back on 2026-10-25 in Berlin, so that night is 25 hours long but 08:00 stays 08:00 local.
        assertEquals(at("2026-10-25", "08:00"), ReminderTiming.nextTrigger(LocalTime.of(8, 0), at("2026-10-24", "09:00")))
        assertEquals(Duration.ofHours(25), ReminderTiming.delayUntilNext(LocalTime.of(8, 0), at("2026-10-24", "08:00")))
        assertEquals(Duration.ofHours(24), ReminderTiming.delayUntilNext(LocalTime.of(8, 0), at("2026-10-25", "08:00")))
    }
}
