package com.hermes.app.di

import android.content.Context
import androidx.room.Room
import com.hermes.app.data.local.AppDatabase
import com.hermes.app.data.local.dao.ChatMessageDao
import com.hermes.app.data.local.dao.ChatSessionDao
import com.hermes.app.data.local.dao.LogEntryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "hermes_database.db"
        )
        .fallbackToDestructiveMigration() // Полезно при разработке без явных миграций при смене классов
        .build()
    }

    @Provides
    @Singleton
    fun provideChatSessionDao(db: AppDatabase): ChatSessionDao {
        return db.chatSessionDao()
    }

    @Provides
    @Singleton
    fun provideChatMessageDao(db: AppDatabase): ChatMessageDao {
        return db.chatMessageDao()
    }

    @Provides
    @Singleton
    fun provideLogEntryDao(db: AppDatabase): LogEntryDao {
        return db.logEntryDao()
    }
}
