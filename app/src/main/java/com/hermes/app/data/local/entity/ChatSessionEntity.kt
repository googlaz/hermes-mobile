package com.hermes.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val model: String,        // Активная модель в рамках данной сессии
    val provider: String,     // ollama или openrouter
    val createdAt: Long,
    val updatedAt: Long
)
