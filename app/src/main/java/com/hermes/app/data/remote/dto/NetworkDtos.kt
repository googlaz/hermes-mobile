package com.hermes.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// 1. Статус здоровья сервера
data class HealthStatusDto(
    @SerializedName("status") val status: String,       // "ok"
    @SerializedName("platform") val platform: String?,  // "hermes-agent"
    @SerializedName("version") val version: String?     // "0.17.0"
)

// Ответ /v1/models (OpenAI-совместимый формат)
data class ModelsResponse(
    @SerializedName("data") val data: List<ModelDto>
)

// 2. Сессия (реальный формат Hermes API Server v0.17.0)
data class ChatSessionDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String?,          // может быть null
    @SerializedName("model") val model: String,
    @SerializedName("started_at") val startedAt: Double,  // FLOAT-секунды
    @SerializedName("last_active") val lastActive: Double? = null, // FLOAT-секунды (в списке)
    @SerializedName("message_count") val messageCount: Int? = null
)

// Ответ на создание сессии — объект вложен под "session"
data class CreateSessionResponse(
    @SerializedName("object") val object_: String?,
    @SerializedName("session") val session: ChatSessionDto
)

// Список сессий: {"object":"list","data":[...]}
data class SessionListResponse(
    @SerializedName("data") val data: List<ChatSessionDto>
)

// Список сообщений: {"object":"list","session_id":"...","data":[...]}
data class MessageListResponse(
    @SerializedName("data") val data: List<ChatMessageDto>
)

// Запрос на создание сессии (provider сервер игнорирует, но не мешает)
data class CreateSessionRequest(
    @SerializedName("title") val title: String,
    @SerializedName("model") val model: String,
    @SerializedName("provider") val provider: String
)

// 3. Сообщение
data class ChatMessageDto(
    @SerializedName("id") val id: String,
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String,
    @SerializedName("timestamp") val timestamp: Long
)

// Запрос на отправку сообщения — сервер ожидает поле "message"
data class SendMessageRequest(
    @SerializedName("message") val content: String
)

// 4. Доступные LLM-модели
data class ModelDto(
    @SerializedName("id") val id: String,             // e.g. "qwen3.5:9b"
    @SerializedName("name") val name: String,         // Понятное имя модели
    @SerializedName("provider") val provider: String, // "ollama" или "openrouter"
    @SerializedName("size") val size: Long,           // Размер в байтах (для Ollama)
    @SerializedName("contextLength") val contextLength: Int
)

// Запрос на переключение модели
data class SwitchModelRequest(
    @SerializedName("model") val model: String,
    @SerializedName("provider") val provider: String
)

// Запрос на обновление параметров сессии через PATCH (модель, провайдер)
data class PatchSessionRequest(
    @SerializedName("model") val model: String? = null,
    @SerializedName("provider") val provider: String? = null
)

// 5. Запись файла
data class FileItemDto(
    @SerializedName("name") val name: String,
    @SerializedName("path") val path: String,         // Относительный путь от корня проекта
    @SerializedName("isDirectory") val isDirectory: Boolean,
    @SerializedName("size") val size: Long,
    @SerializedName("lastModified") val lastModified: Long
)

// Запрос смены рабочей директории
data class ChangeWorkdirRequest(
    @SerializedName("path") val path: String
)

// Ответ на аплоад файлов
data class FileUploadResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("files") val uploadedFiles: List<String>,
    @SerializedName("message") val message: String?
)

// 6. Активные фоновые задачи (runs)
data class RunDto(
    @SerializedName("id") val id: String,
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("status") val status: String,     // "queued", "running", "completed", "failed", "cancelled"
    @SerializedName("currentTool") val currentTool: String?,
    @SerializedName("error") val error: String?,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("completedAt") val completedAt: Long?
)
