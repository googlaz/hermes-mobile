package com.hermes.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hermes.app.data.local.dao.ChatMessageDao
import com.hermes.app.data.local.dao.ChatSessionDao
import com.hermes.app.data.local.dao.LogEntryDao
import com.hermes.app.data.local.entity.ChatMessageEntity
import com.hermes.app.data.local.entity.ChatSessionEntity
import com.hermes.app.data.local.entity.LogEntryEntity

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        LogEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun logEntryDao(): LogEntryDao
}
