package com.algoprep.app.di

import com.algoprep.app.data.repository.CatalogRepositoryImpl
import com.algoprep.app.data.repository.PlanRepositoryImpl
import com.algoprep.app.data.repository.ProfileRepositoryImpl
import com.algoprep.app.data.repository.RoomTransactionRunner
import com.algoprep.app.data.repository.TaskRepositoryImpl
import com.algoprep.app.data.repository.TrainingRepositoryImpl
import com.algoprep.app.domain.repository.CatalogRepository
import com.algoprep.app.domain.repository.PlanRepository
import com.algoprep.app.domain.repository.ProfileRepository
import com.algoprep.app.domain.repository.TaskRepository
import com.algoprep.app.domain.repository.TrainingRepository
import com.algoprep.app.domain.repository.TransactionRunner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun catalog(impl: CatalogRepositoryImpl): CatalogRepository
    @Binds abstract fun tasks(impl: TaskRepositoryImpl): TaskRepository
    @Binds abstract fun profile(impl: ProfileRepositoryImpl): ProfileRepository
    @Binds abstract fun plan(impl: PlanRepositoryImpl): PlanRepository
    @Binds abstract fun training(impl: TrainingRepositoryImpl): TrainingRepository
    @Binds abstract fun transactions(impl: RoomTransactionRunner): TransactionRunner
}
