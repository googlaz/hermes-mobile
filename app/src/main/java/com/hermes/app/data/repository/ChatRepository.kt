package com.hermes.app.data.repository

import com.hermes.app.data.local.dao.ChatMessageDao
import com.hermes.app.data.local.dao.ChatSessionDao
import com.hermes.app.data.local.entity.ChatMessageEntity
import com.hermes.app.data.local.entity.ChatSessionEntity
import com.hermes.app.data.remote.HermesApiService
import com.hermes.app.data.remote.dto.CreateSessionRequest
import com.hermes.app.data.remote.dto.SendMessageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao,
    private val apiService: HermesApiService
) {
    // Наблюдаемые из Room БД чат-сессии для вкладок UI (ФТ-2.1)
    val allSessionsFlow: Flow<List<ChatSessionEntity>> = sessionDao.observeAllSessions()

    fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>> {
        return messageDao.observeMessagesForSession(sessionId)
    }

    /**
     * Создание новой сессии. Сначала заводится на ПК бэкенде, затем дублируется в локальную Room DB.
     */
    suspend fun createNewSession(title: String, model: String, provider: String): Result<ChatSessionEntity> = withContext(Dispatchers.IO) {
        val request = CreateSessionRequest(title, model, provider)
        try {
            val response = apiService.createSession(request)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                val entity = ChatSessionEntity(
                    id = dto.id,
                    title = dto.title,
                    model = dto.model,
                    provider = dto.provider,
                    createdAt = dto.createdAt,
                    updatedAt = dto.updatedAt
                )
                sessionDao.upsertSession(entity)
                Result.success(entity)
            } else {
                Result.failure(Exception("Ошибка создания сессии на сервере: ${response.code()}"))
            }
        } catch (e: Exception) {
            // Офлайн режим (ФТ-3.8): создаем временную сессию локально в Room
            val localId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val entity = ChatSessionEntity(
                id = localId,
                title = "$title (Офлайн)",
                model = model,
                provider = provider,
                createdAt = now,
                updatedAt = now
            )
            sessionDao.upsertSession(entity)
            Result.success(entity)
        }
    }

    /**
     * Отправка нового сообщения от пользователя
     */
    suspend fun sendUserMessage(sessionId: String, content: String): Result<ChatMessageEntity> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val messageId = UUID.randomUUID().toString()
        val localUserMessage = ChatMessageEntity(
            id = messageId,
            sessionId = sessionId,
            role = "user",
            content = content,
            timestamp = now
        )

        // Сразу сохраняем локально, чтобы мгновенно отобразить в чате (ФТ-2.4)
        messageDao.insertMessage(localUserMessage)

        try {
            val response = apiService.sendMessage(sessionId, SendMessageRequest(content))
            if (response.isSuccessful && response.body() != null) {
                // Сервер обработал — обновляем или подтверждаем сообщение
                // Опционально: ТЗ не требует жесткой синхронизации UUIDs
                Result.success(localUserMessage)
            } else {
                Result.failure(Exception("Сервер отклонил сообщение: ${response.code()}"))
            }
        } catch (e: Exception) {
            // В офлайн-режиме сообщение остается локально, сигнализируем об успехе локального сохранения
            Result.success(localUserMessage)
        }
    }

    /**
     * Запись входящего ассистентского токена/сообщения в локальную базу данных
     */
    suspend fun saveAssistantMessage(sessionId: String, messageId: String, content: String) = withContext(Dispatchers.IO) {
        val assistantMessage = ChatMessageEntity(
            id = messageId,
            sessionId = sessionId,
            role = "assistant",
            content = content,
            timestamp = System.currentTimeMillis()
        )
        messageDao.insertMessage(assistantMessage)
    }

    suspend fun deleteSession(session: ChatSessionEntity) = withContext(Dispatchers.IO) {
        try {
            apiService.deleteSession(session.id)
        } catch (e: Exception) {
            // Игнорируем или логируем сетевой сбой при удалении
        }
        sessionDao.deleteSession(session)
    }
}
