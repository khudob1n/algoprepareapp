package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.ReviewState
import com.algoprep.app.domain.model.SolveOutcome
import com.algoprep.app.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReviewSchedulerTest {
    private val today = LocalDate.of(2026, 10, 12)
    private val zone = ZoneOffset.UTC

    private fun step(prev: ReviewState?, outcome: SolveOutcome, conf: Int = 5) =
        ReviewScheduler.next(1, prev, outcome, conf, today, zone)

    private fun dueInDays(s: ReviewState) = today.plusDays(s.intervalDays.toLong()).atStartOfDay(zone).toInstant()

    @Test fun confidentSolvesGrowTheInterval() {
        val r1 = step(null, SolveOutcome.INDEPENDENT)
        assertEquals(1, r1.intervalDays)
        assertEquals(1, r1.repetitions)
        val r2 = step(r1, SolveOutcome.INDEPENDENT)
        assertEquals(3, r2.intervalDays)
        val r3 = step(r2, SolveOutcome.INDEPENDENT)
        assertEquals(8, r3.intervalDays) // round(3 * 2.5)
        assertTrue(r3.intervalDays > r2.intervalDays)
        assertTrue(r3.ease > r2.ease)
        assertEquals(dueInDays(r3), r3.dueAt)
    }

    @Test fun easeAndIntervalAreCapped() {
        var s = step(null, SolveOutcome.INDEPENDENT)
        repeat(30) { s = step(s, SolveOutcome.INDEPENDENT) }
        assertEquals(ReviewScheduler.MAX_INTERVAL_DAYS, s.intervalDays)
        assertEquals(ReviewScheduler.MAX_EASE, s.ease, 1e-9)
    }

    @Test fun unsureSolveGrowsSlowlyWithoutChangingEase() {
        val base = ReviewState(1, 5, 2.3, 2, 0, Instant.EPOCH, null)
        val r = step(base, SolveOutcome.INDEPENDENT, conf = 3)
        assertEquals(6, r.intervalDays)
        assertEquals(2.3, r.ease, 1e-9)
        assertEquals(3, r.repetitions)
        val hint = step(null, SolveOutcome.SMALL_HINT)
        assertEquals(2, hint.intervalDays)
    }

    @Test fun bigHintResetsRepetitionsAndLowersEase() {
        val base = ReviewState(1, 10, 2.3, 4, 0, Instant.EPOCH, null)
        val r = step(base, SolveOutcome.BIG_HINT)
        assertEquals(2, r.intervalDays)
        assertEquals(0, r.repetitions)
        assertEquals(2.15, r.ease, 1e-9)
        assertEquals(0, r.lapses)
    }

    @Test fun failureIsALapseAndComesBackTomorrow() {
        val base = ReviewState(1, 10, 2.3, 4, 0, Instant.EPOCH, null)
        for (o in listOf(SolveOutcome.SAW_SOLUTION, SolveOutcome.NOT_SOLVED)) {
            val r = step(base, o, conf = 2)
            assertEquals(1, r.intervalDays)
            assertEquals(1, r.lapses)
            assertEquals(0, r.repetitions)
            assertEquals(2.1, r.ease, 1e-9)
            assertEquals(today.plusDays(1).atStartOfDay(zone).toInstant(), r.dueAt)
        }
    }

    @Test fun easeNeverDropsBelowTheFloor() {
        var s: ReviewState? = null
        repeat(20) { s = step(s, SolveOutcome.NOT_SOLVED, 1) }
        assertEquals(ReviewScheduler.MIN_EASE, s!!.ease, 1e-9)
    }

    @Test fun statusRules() {
        val state = ReviewState(1, 5, 2.5, 4, 0, Instant.EPOCH, null)
        val confident = Attempt(SolveOutcome.INDEPENDENT, 5)
        assertEquals(TaskStatus.MASTERED, TaskProgress.statusAfter(state, listOf(confident, confident)))
        assertEquals(TaskStatus.LEARNING, TaskProgress.statusAfter(state, listOf(confident)))
        assertEquals(TaskStatus.LEARNING, TaskProgress.statusAfter(state.copy(repetitions = 3), listOf(confident, confident)))
        assertEquals(TaskStatus.LEARNING, TaskProgress.statusAfter(state, listOf(confident, Attempt(SolveOutcome.SMALL_HINT, 5))))
        assertEquals(TaskStatus.LEARNING, TaskProgress.statusAfter(state, listOf(confident, Attempt(SolveOutcome.INDEPENDENT, 3))))
        assertEquals(TaskStatus.FAILED_RECENTLY, TaskProgress.statusAfter(state, listOf(Attempt(SolveOutcome.NOT_SOLVED, 2), confident)))
        assertEquals(TaskStatus.LEARNING, TaskProgress.statusAfter(state, listOf(Attempt(SolveOutcome.BIG_HINT, 3))))
    }
}
