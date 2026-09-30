package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.Bucket
import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.PlannedKind
import com.algoprep.app.domain.model.ReasonCode
import com.algoprep.app.domain.model.RoadmapDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePolicyTest {
    private val introduced = setOf("arrays", "graphs", "trees")

    @Test fun defaultSplitWhenNothingIsWeakOrStrong() {
        val a = AdaptivePolicy.allocate(listOf("trees"), mapOf("trees" to skill("trees", 0.6)), introduced)
        assertEquals(Allocation.DEFAULT, a)
        assertNull(a.reason)
    }

    @Test fun eachSeriouslyWeakTopicRaisesTheWeakShareUpToThirtyPercent() {
        fun share(vararg weak: String) = AdaptivePolicy.allocate(
            listOf("arrays"),
            weak.associateWith { skill(it, 0.2, attempts = 5) } + ("arrays" to skill("arrays", 0.6)),
            introduced + weak,
        ).weakShare
        assertEquals(0.25, share("graphs"), 1e-9)
        assertEquals(0.30, share("graphs", "trees"), 1e-9)
        assertEquals(0.30, share("graphs", "trees", "x"), 1e-9)
    }

    @Test fun reasonNamesTheWeakTopics() {
        val a = AdaptivePolicy.allocate(listOf("arrays"), mapOf("graphs" to skill("graphs", 0.1, attempts = 4)), introduced)
        assertEquals(AdjustReason.MoreWeak(25, listOf("graphs")), a.reason)
    }

    @Test fun weakTopicsWithoutEvidenceDoNotChangeTheSplit() {
        // explicit self-rating 1 and no attempts: weak, but not "seriously" weak
        val a = AdaptivePolicy.allocate(listOf("arrays"), mapOf("graphs" to skill("graphs", 0.0, attempts = 0, selfRating = 1)), introduced)
        assertEquals(0.20, a.weakShare, 1e-9)
        assertNull(a.reason)
    }

    @Test fun strongDayMovesTimeToMixedPracticeButKeepsTheRoadmapAtSixtyPercent() {
        val skills = mapOf("trees" to skill("trees", 0.9, attempts = 6), "graphs" to skill("graphs", 0.1, attempts = 5), "arrays" to skill("arrays", 0.1, attempts = 5))
        val a = AdaptivePolicy.allocate(listOf("trees"), skills, introduced)
        assertEquals(0.15, a.spacedShare, 1e-9)
        assertTrue(a.weakShare + a.spacedShare <= 0.40 + 1e-9)
        assertTrue(a.reason is AdjustReason.MoreWeak)
        val onlyStrong = AdaptivePolicy.allocate(listOf("trees"), mapOf("trees" to skill("trees", 0.9, attempts = 6)), introduced)
        assertEquals(AdjustReason.StrongDay(listOf("trees")), onlyStrong.reason)
    }

    @Test fun reasonsSurviveEncodingAndBadInputIsIgnored() {
        listOf(
            AdjustReason.MoreWeak(30, listOf("graphs", "trees")),
            AdjustReason.StrongDay(listOf("arrays")),
            AdjustReason.Shifted(3),
            AdjustReason.Compressed(2),
        ).forEach { assertEquals(it, AdjustReason.decode(it.encode())) }
        assertNull(AdjustReason.decode(null))
        assertNull(AdjustReason.decode("garbage"))
        assertNull(AdjustReason.decode("SHIFTED|x"))
    }

    // ---- carry-over in the day planner -----------------------------------------------------

    @Test fun carriedOverTasksComeFirstWithinThirtyPercentAndAreMarked() {
        val bank = listOf(
            task(1, "A", setOf("trees"), Difficulty.MEDIUM, 25),
            task(2, "B", setOf("trees"), Difficulty.MEDIUM, 25),
            task(10, "Carried 1", setOf("arrays"), Difficulty.MEDIUM, 30, solved = 0),
            task(11, "Carried 2", setOf("arrays"), Difficulty.MEDIUM, 30, solved = 0),
        )
        val input = PlannerInput(
            day = planDay(12, listOf("trees"), 120), roadmapDay = RoadmapDay(12, "T", listOf("trees"), "theory", null),
            tasks = bank, skills = emptyMap(), introducedTopicIds = setOf("arrays", "trees"), unresolvedErrors = emptyList(),
            recentTaskIds = emptySet(), now = NOW, carryOver = bank.filter { it.id >= 10 },
        )
        val items = DayPlanner.plan(input)
        val carried = items.filter { i -> i.reasons.any { it.code == ReasonCode.CARRIED_OVER } }
        assertTrue(carried.isNotEmpty())
        assertTrue(carried.all { it.kind == PlannedKind.MAIN && it.bucket == Bucket.ROADMAP })
        val practice = 120 - DayPlanner.THEORY_MIN - (items.firstOrNull { it.kind == PlannedKind.WARMUP }?.estimatedMin ?: 0)
        assertTrue("carried ${carried.sumOf { it.estimatedMin }} of $practice", carried.size == 1 || carried.sumOf { it.estimatedMin } <= practice * 0.3 + 30)
        assertEquals(items.mapNotNull { it.taskId }.toSet().size, items.mapNotNull { it.taskId }.size)
    }

    @Test fun allocationShiftsTheMinutesBetweenBuckets() {
        val bank = (1L..8L).map { task(it, "T$it", setOf("trees"), Difficulty.MEDIUM, 20) } +
            (20L..24L).map { task(it, "G$it", setOf("graphs"), Difficulty.MEDIUM, 20) }
        val skills = mapOf("graphs" to skill("graphs", 0.1, attempts = 6))
        fun weakMinutes(allocation: Allocation) = DayPlanner.plan(
            PlannerInput(
                day = planDay(12, listOf("trees"), 180), roadmapDay = null, tasks = bank, skills = skills,
                introducedTopicIds = setOf("trees", "graphs"), unresolvedErrors = emptyList(), recentTaskIds = emptySet(),
                now = NOW, allocation = allocation,
            ),
        ).filter { it.bucket == Bucket.WEAK }.sumOf { it.estimatedMin }
        assertTrue(weakMinutes(Allocation(0.30, 0.10, null)) >= weakMinutes(Allocation(0.10, 0.10, null)))
        assertTrue(weakMinutes(Allocation(0.30, 0.10, null)) > 0)
    }
}
