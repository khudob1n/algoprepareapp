package com.algoprep.app.ui.screens.today

import com.algoprep.app.domain.model.Bucket
import com.algoprep.app.domain.model.PlannedItem
import com.algoprep.app.domain.model.PlannedKind
import com.algoprep.app.domain.model.PlannedStatus
import com.algoprep.app.domain.planning.planDay
import com.algoprep.app.domain.planning.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayStateTest {
    private fun item(id: Long, kind: PlannedKind, taskId: Long?, min: Int, done: Boolean = false) = PlannedItem(
        id = id, dayIndex = 12, kind = kind, taskId = taskId, orderIndex = id.toInt(), estimatedMin = min,
        status = if (done) PlannedStatus.DONE else PlannedStatus.TODO, bucket = Bucket.ROADMAP,
        reasons = emptyList(), completedSessionId = null,
    )

    private val tasks = mapOf(1L to task(1, "A"), 2L to task(2, "B"), 3L to task(3, "C"), 4L to task(4, "D"))

    @Test fun groupsItemsIntoSectionsAndComputesProgressByMinutes() {
        val day = planDay().copy(
            items = listOf(
                item(0, PlannedKind.THEORY, null, 10, done = true),
                item(1, PlannedKind.WARMUP, 1, 10, done = true),
                item(2, PlannedKind.MAIN, 2, 30),
                item(3, PlannedKind.MAIN, 3, 30),
                item(4, PlannedKind.REVIEW, 4, 20),
            ),
        )
        val c = buildTodayContent(day, 30, tasks, mapOf("trees" to "Trees"), "Theory text")
        assertEquals(2, c.intro.size)
        assertEquals(2, c.main.size)
        assertEquals(1, c.review.size)
        assertEquals(100, c.plannedMinutes)
        assertEquals(80, c.remainingMinutes)
        assertEquals(20, c.progressPercent)
        assertEquals(listOf("Trees"), c.topicTitles)
        assertEquals("Theory text", c.intro.first().theoryText)
        assertEquals(2L, c.nextTaskItem?.taskId)
    }

    @Test fun nextTaskSkipsDoneItemsAndTheory() {
        val day = planDay().copy(
            items = listOf(
                item(0, PlannedKind.THEORY, null, 10),
                item(1, PlannedKind.MAIN, 1, 20, done = true),
                item(2, PlannedKind.MAIN, 2, 20),
            ),
        )
        assertEquals(2L, buildTodayContent(day, 30, tasks, emptyMap(), null).nextTaskItem?.taskId)
    }

    @Test fun allDoneHasNoNextTask() {
        val day = planDay().copy(items = listOf(item(1, PlannedKind.MAIN, 1, 20, done = true)))
        val c = buildTodayContent(day, 30, tasks, emptyMap(), null)
        assertNull(c.nextTaskItem)
        assertEquals(100, c.progressPercent)
    }

    @Test fun emptyDayHasZeroProgress() {
        val c = buildTodayContent(planDay(), 30, tasks, emptyMap(), null)
        assertEquals(0, c.progressPercent)
        assertTrue(!c.hasItems)
    }
}
