package com.algoprep.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.algoprep.app.data.db.AppDatabase
import com.algoprep.app.data.repository.ImportRepositoryImpl
import com.algoprep.app.data.repository.TaskRepositoryImpl
import com.algoprep.app.data.seed.SeedLoader
import com.algoprep.app.domain.importer.ImportFormat
import com.algoprep.app.domain.importer.ImportSavePlan
import com.algoprep.app.domain.importer.NewMention
import com.algoprep.app.domain.importer.NewTaskDraft
import com.algoprep.app.domain.importer.SaveEntry
import com.algoprep.app.domain.importer.SeparatePair
import com.algoprep.app.domain.model.BatchStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock

class ImportRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val clock = Clock.systemUTC()

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        runBlocking { SeedLoader(context, db, clock).seedIfNeeded() }
    }

    @After fun tearDown() = db.close()

    private fun mention(source: String) = NewMention("t", "x", source, null, "Acme", null, null, null)

    private fun plan(seedTaskId: Long) = ImportSavePlan(
        sourceName = "f.md", format = ImportFormat.MARKDOWN, rawSize = 10, candidatesFound = 4,
        entries = listOf(
            SaveEntry.NewTask("a", NewTaskDraft("Brand new task", "statement", null, setOf("arrays"), setOf("hash_map"), null, "my notes"), mention("A"), listOf(SeparatePair(seedTaskId, 0.85))),
            SaveEntry.MergeIntoBatch("a", mention("B")),
            SaveEntry.MergeIntoTask(seedTaskId, mention("C")),
        ),
    )

    @Test fun saveCreatesTasksAndMentionsAndRollbackUndoesThem() = runBlocking {
        val tasks = TaskRepositoryImpl(db, clock)
        val repo = ImportRepositoryImpl(db, clock)
        val seeded = tasks.observeAll().first()
        val seedTask = seeded.first()

        val batch = repo.save(plan(seedTask.id))
        assertEquals(1, batch.saved)
        assertEquals(2, batch.merged)
        assertEquals(1, batch.skipped)

        val after = tasks.observeAll().first()
        assertEquals(seeded.size + 1, after.size)
        val created = after.single { it.title == "Brand new task" }
        assertEquals(2, created.mentions.size)
        assertEquals(setOf("A", "B"), created.mentions.map { it.source }.toSet())
        assertEquals("my notes", created.personalNotes)
        assertEquals(setOf("arrays"), created.topics)
        assertEquals(setOf("hash_map"), created.patterns)
        assertEquals(1, after.single { it.id == seedTask.id }.mentions.size)

        repo.rollback(batch.id)

        val rolledBack = tasks.observeAll().first()
        assertEquals(seeded.size, rolledBack.size)
        assertTrue(rolledBack.none { it.title == "Brand new task" })
        assertEquals(0, rolledBack.single { it.id == seedTask.id }.mentions.size)
        assertEquals(BatchStatus.ROLLED_BACK, repo.observeBatches().first().single().status)
    }

    @Test fun rollbackTwiceIsHarmless() = runBlocking {
        val tasks = TaskRepositoryImpl(db, clock)
        val repo = ImportRepositoryImpl(db, clock)
        val batch = repo.save(plan(tasks.observeAll().first().first().id))
        repo.rollback(batch.id)
        repo.rollback(batch.id)
        assertEquals(BatchStatus.ROLLED_BACK, repo.observeBatches().first().single().status)
    }

    @Test fun taskBriefsCoverTheWholeBank() = runBlocking {
        val repo = ImportRepositoryImpl(db, clock)
        val briefs = repo.taskBriefs()
        assertEquals(TaskRepositoryImpl(db, clock).observeAll().first().size, briefs.size)
        assertTrue(briefs.all { it.title.isNotBlank() && it.text.isNotBlank() })
        assertNull(briefs.firstOrNull { it.id <= 0 })
    }
}
