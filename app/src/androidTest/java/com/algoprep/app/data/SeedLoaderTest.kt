package com.algoprep.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.algoprep.app.data.db.AppDatabase
import com.algoprep.app.data.repository.TaskRepositoryImpl
import com.algoprep.app.data.seed.SeedLoader
import com.algoprep.app.domain.model.TaskOrigin
import java.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SeedLoaderTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After fun tearDown() = db.close()

    @Test fun seedingIsIdempotentAndLinksAreValid() = runBlocking {
        val loader = SeedLoader(context, db, Clock.systemUTC())
        loader.seedIfNeeded()
        val repo = TaskRepositoryImpl(db, Clock.systemUTC())
        val first = repo.observeAll().first()
        loader.seedIfNeeded()
        val second = repo.observeAll().first()

        assertTrue(first.isNotEmpty())
        assertEquals(first.size, second.size)
        assertTrue(first.all { it.origin == TaskOrigin.SEED && it.topics.isNotEmpty() })
        assertEquals(30, db.catalogDao().getRoadmap().size)
    }

    @Test fun deletingTaskCascadesToLinks() = runBlocking {
        SeedLoader(context, db, Clock.systemUTC()).seedIfNeeded()
        val repo = TaskRepositoryImpl(db, Clock.systemUTC())
        val task = repo.observeAll().first().first()
        db.openHelper.writableDatabase.execSQL("DELETE FROM task WHERE id = ${task.id}")
        assertEquals(null, repo.get(task.id))
        val cursor = db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM task_topic WHERE taskId = ${task.id}")
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
        cursor.close()
    }
}
