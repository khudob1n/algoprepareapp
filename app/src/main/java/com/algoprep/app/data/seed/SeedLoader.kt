package com.algoprep.app.data.seed

import android.content.Context
import androidx.room.withTransaction
import com.algoprep.app.core.AppJson
import com.algoprep.app.data.db.AppDatabase
import com.algoprep.app.data.db.entity.PatternEntity
import com.algoprep.app.data.db.entity.RoadmapTemplateEntity
import com.algoprep.app.data.db.entity.TaskEntity
import com.algoprep.app.data.db.entity.TaskPatternEntity
import com.algoprep.app.data.db.entity.TaskTopicEntity
import com.algoprep.app.data.db.entity.TopicEntity
import com.algoprep.app.domain.model.TaskOrigin
import com.algoprep.app.domain.util.TitleNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads built-in reference data and starter tasks from assets/seed.
 * Idempotent: reference rows are upserted, tasks are inserted only when their seedKey is new,
 * so user edits to seeded tasks are never overwritten.
 */
@Singleton
class SeedLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val clock: Clock,
) {
    suspend fun seedIfNeeded() {
        val data = withContext(Dispatchers.IO) {
            SeedParser.parse(
                topics = read("topics.json"),
                patterns = read("patterns.json"),
                roadmap = read("roadmap.json"),
                tasks = read("tasks_seed.json"),
            )
        }
        val now = clock.millis()
        val catalog = db.catalogDao()
        val tasks = db.taskDao()
        db.withTransaction {
            catalog.upsertTopics(data.topics.mapIndexed { i, t -> TopicEntity(t.id, t.title, t.parentId, i) })
            catalog.upsertPatterns(data.patterns.map { PatternEntity(it.id, it.title, it.topicHint) })
            catalog.upsertRoadmap(
                data.roadmap.map {
                    RoadmapTemplateEntity(it.day, it.title, AppJson.encodeToString(it.topics), it.theory, it.notes)
                },
            )
            for (s in data.tasks) {
                val id = tasks.insertIgnore(
                    TaskEntity(
                        title = s.title,
                        originalText = s.text,
                        canonicalKey = TitleNormalizer.canonicalKey(s.title),
                        difficulty = s.difficulty,
                        estimatedSolveMin = s.minutes,
                        hintsJson = AppJson.encodeToString(s.hints),
                        solutionIdea = s.idea,
                        timeComplexity = s.time,
                        spaceComplexity = s.space,
                        origin = TaskOrigin.SEED,
                        seedKey = s.key,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                if (id == -1L) continue // already seeded earlier
                tasks.insertTopicLinks(s.topics.map { TaskTopicEntity(id, it) })
                tasks.insertPatternLinks(s.patterns.map { TaskPatternEntity(id, it) })
            }
        }
    }

    private fun read(name: String): String =
        context.assets.open("seed/$name").bufferedReader().use { it.readText() }
}
