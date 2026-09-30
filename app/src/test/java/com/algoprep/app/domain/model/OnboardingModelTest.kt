package com.algoprep.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class OnboardingModelTest {
    @Test fun startOptionsResolveRelativeToToday() {
        val wednesday = LocalDate.of(2026, 9, 30)
        assertEquals(wednesday, StartOption.TODAY.resolve(wednesday))
        assertEquals(LocalDate.of(2026, 10, 1), StartOption.TOMORROW.resolve(wednesday))
        assertEquals(LocalDate.of(2026, 10, 5), StartOption.NEXT_MONDAY.resolve(wednesday))
        // On a Monday, "next Monday" is a week later, never today.
        val monday = LocalDate.of(2026, 10, 5)
        assertEquals(LocalDate.of(2026, 10, 12), StartOption.NEXT_MONDAY.resolve(monday))
    }

    @Test fun reminderSettingsUpdateOneSlot() {
        val updated = ReminderSettings.DEFAULT.withSlot(ReminderKind.EVENING, ReminderSlot(false, LocalTime.of(19, 30)))
        assertEquals(LocalTime.of(19, 30), updated[ReminderKind.EVENING].time)
        assertEquals(ReminderSettings.DEFAULT.morning, updated.morning)
        assertTrue(updated.anyEnabled)
        val allOff = ReminderKind.entries.fold(updated) { acc, k -> acc.withSlot(k, acc[k].copy(enabled = false)) }
        assertFalse(allOff.anyEnabled)
    }

    @Test fun defaultRatingRisesWithLevel() {
        assertTrue(defaultSelfRating(UserLevel.BEGINNER) < defaultSelfRating(UserLevel.INTERMEDIATE))
        assertTrue(defaultSelfRating(UserLevel.INTERMEDIATE) < defaultSelfRating(UserLevel.ADVANCED))
    }
}
