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
        // Пробуем создать; при коллизии заголовка (invalid_title, HTTP 400) — один повтор с суффиксом.
        val firstResult = attemptCreateSession(title, model, provider)
        if (firstResult.isSuccess) return@withContext firstResult

        val err = firstResult.exceptionOrNull()
        if (err is InvalidTitleException) {
            val retryTitle = title + " " + (System.currentTimeMillis() % 10000).toString()
            return@withContext attemptCreateSession(retryTitle, model, provider)
        }
        firstResult
    }

    private class InvalidTitleException : Exception("invalid_title")

    private suspend fun attemptCreateSession(title: String, model: String, provider: String): Result<ChatSessionEntity> {
        val request = CreateSessionRequest(title, model, provider)
        return try {
            val response = apiService.createSession(request)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!.session
                val createdMs = (dto.startedAt * 1000).toLong()
                val entity = ChatSessionEntity(
                    id = dto.id,
                    title = dto.title ?: title,
                    model = dto.model,
                    provider = provider,
                    createdAt = createdMs,
                    updatedAt = createdMs
                )
                sessionDao.upsertSession(entity)
                Result.success(entity)
            } else if (response.code() == 400) {
                val body = response.errorBody()?.string().orEmpty()
                if (body.contains("invalid_title")) {
                    Result.failure(InvalidTitleException())
                } else {
                    Result.failure(Exception("Ошибка создания сессии: HTTP 400. $body"))
                }
            } else {
                Result.failure(Exception("Ошибка создания сессии: HTTP ${response.code()}. Проверьте подключение в Настройках."))
            }
        } catch (e: Exception) {
            // Не создаём офлайн-сессию — локальный UUID бесполезен для отправки сообщений
            Result.failure(Exception("Нет соединения с Hermes. Настройте IP и ключ в разделе Настройки."))
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
                // /chat синхронный — полный ответ агента лежит в message.content.
                // Сохраняем его как ассистентское сообщение, чтобы observeMessages его отобразил.
                val answer = response.body()!!.message.content
                saveAssistantMessage(sessionId, UUID.randomUUID().toString(), answer)
                Result.success(localUserMessage)
            } else {
                Result.failure(Exception("Сервер отклонил сообщение: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            // Таймаут/сеть: локальная модель может думать долго
            Result.failure(Exception("Нет ответа от агента (таймаут). Локальная модель может отвечать долго."))
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
