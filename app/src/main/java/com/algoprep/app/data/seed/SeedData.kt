package com.algoprep.app.data.seed

import com.algoprep.app.core.AppJson
import com.algoprep.app.domain.model.Difficulty
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

@Serializable
data class SeedTopic(val id: String, val title: String, val parentId: String? = null)

@Serializable
data class SeedPattern(val id: String, val title: String, val topicHint: String? = null)

@Serializable
data class SeedRoadmapDay(
    val day: Int,
    val title: String,
    val topics: List<String>,
    val theory: String? = null,
    val notes: String? = null,
)

@Serializable
data class SeedTask(
    val key: String,
    val title: String,
    val text: String,
    val difficulty: Difficulty,
    val topics: List<String>,
    val patterns: List<String> = emptyList(),
    val minutes: Int,
    val hints: List<String>,
    val idea: String,
    val time: String,
    val space: String,
)

data class SeedData(
    val topics: List<SeedTopic>,
    val patterns: List<SeedPattern>,
    val roadmap: List<SeedRoadmapDay>,
    val tasks: List<SeedTask>,
)

/** Pure Kotlin (no Android) so it can be unit-tested on the JVM. */
object SeedParser {
    fun parse(topics: String, patterns: String, roadmap: String, tasks: String) = SeedData(
        topics = AppJson.decodeFromString(topics),
        patterns = AppJson.decodeFromString(patterns),
        roadmap = AppJson.decodeFromString(roadmap),
        tasks = AppJson.decodeFromString(tasks),
    )
}
