package com.algoprep.app.data.seed

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the bundled seed: a bad reference here would crash seeding with a foreign-key error. */
class SeedIntegrityTest {
    private fun read(name: String) = File("src/main/assets/seed/$name").readText()

    private val data = SeedParser.parse(
        topics = read("topics.json"),
        patterns = read("patterns.json"),
        roadmap = read("roadmap.json"),
        tasks = read("tasks_seed.json"),
    )

    @Test fun roadmapCoversThirtyConsecutiveDays() {
        assertEquals((1..30).toList(), data.roadmap.map { it.day })
    }

    @Test fun idsAreUnique() {
        assertEquals(data.topics.size, data.topics.map { it.id }.toSet().size)
        assertEquals(data.patterns.size, data.patterns.map { it.id }.toSet().size)
        assertEquals(data.tasks.size, data.tasks.map { it.key }.toSet().size)
    }

    @Test fun allReferencesResolve() {
        val topicIds = data.topics.map { it.id }.toSet()
        val patternIds = data.patterns.map { it.id }.toSet()
        data.roadmap.forEach { assertTrue("day ${it.day}", topicIds.containsAll(it.topics)) }
        data.patterns.forEach { assertTrue(it.id, it.topicHint == null || it.topicHint in topicIds) }
        data.tasks.forEach {
            assertTrue(it.key, it.topics.isNotEmpty() && topicIds.containsAll(it.topics))
            assertTrue(it.key, patternIds.containsAll(it.patterns))
            assertEquals(it.key, 3, it.hints.size)
        }
    }
}
