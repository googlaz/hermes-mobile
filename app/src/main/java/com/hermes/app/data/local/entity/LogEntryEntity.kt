package com.hermes.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "log_entries")
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val runId: String?,        // Опционально: ID фоновой задачи (run), к которой относится лог
    val level: String,        // "INFO", "WARN", "ERROR", "SUCCESS"
    val tag: String,          // "terminal", "tool_call", "api", etc.
    val message: String,
    val timestamp: Long
)
