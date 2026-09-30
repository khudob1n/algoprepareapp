package com.algoprep.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.algoprep.app.data.db.dao.CatalogDao
import com.algoprep.app.data.db.dao.PlanDao
import com.algoprep.app.data.db.dao.ProfileDao
import com.algoprep.app.data.db.dao.TaskDao
import com.algoprep.app.data.db.dao.TrainingDao
import com.algoprep.app.data.db.entity.DuplicateCandidateEntity
import com.algoprep.app.data.db.entity.ErrorEntryEntity
import com.algoprep.app.data.db.entity.ImportBatchEntity
import com.algoprep.app.data.db.entity.PatternEntity
import com.algoprep.app.data.db.entity.PlanDayEntity
import com.algoprep.app.data.db.entity.PlanDayTopicEntity
import com.algoprep.app.data.db.entity.PlannedItemEntity
import com.algoprep.app.data.db.entity.ReviewStateEntity
import com.algoprep.app.data.db.entity.RoadmapTemplateEntity
import com.algoprep.app.data.db.entity.SolveSessionEntity
import com.algoprep.app.data.db.entity.TaskEntity
import com.algoprep.app.data.db.entity.TaskMentionEntity
import com.algoprep.app.data.db.entity.TaskPatternEntity
import com.algoprep.app.data.db.entity.TaskTopicEntity
import com.algoprep.app.data.db.entity.TopicEntity
import com.algoprep.app.data.db.entity.TopicSkillEntity
import com.algoprep.app.data.db.entity.UserProfileEntity

@Database(
    entities = [
        TopicEntity::class,
        PatternEntity::class,
        TaskEntity::class,
        TaskMentionEntity::class,
        TaskTopicEntity::class,
        TaskPatternEntity::class,
        ImportBatchEntity::class,
        DuplicateCandidateEntity::class,
        UserProfileEntity::class,
        TopicSkillEntity::class,
        RoadmapTemplateEntity::class,
        PlanDayEntity::class,
        PlanDayTopicEntity::class,
        PlannedItemEntity::class,
        SolveSessionEntity::class,
        ErrorEntryEntity::class,
        ReviewStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun taskDao(): TaskDao
    abstract fun profileDao(): ProfileDao
    abstract fun planDao(): PlanDao
    abstract fun trainingDao(): TrainingDao

    companion object {
        const val NAME = "algoprep.db"
    }
}
