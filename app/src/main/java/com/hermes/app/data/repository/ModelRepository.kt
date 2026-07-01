package com.hermes.app.data.repository

import com.hermes.app.data.remote.HermesApiService
import com.hermes.app.data.remote.dto.ModelDto
import com.hermes.app.data.remote.dto.SwitchModelRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    private val apiService: HermesApiService,
    private val sessionDao: com.hermes.app.data.local.dao.ChatSessionDao
) {
    /**
     * Дает список доступных моделей: локальные Ollama + облачные OpenRouter (ФТ-3.1)
     */
    suspend fun getAvailableModels(): Result<List<ModelDto>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getAvailableModels()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Не удалось получить список моделей: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Переключает активную LLM-модель в открытой сессии чата на лету (ФТ-3.2, КП-4)
     */
    suspend fun switchModelForSession(sessionId: String, modelId: String, provider: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.switchModel(sessionId, SwitchModelRequest(modelId, provider))
            if (response.isSuccessful && response.body() != null) {
                // Обновляем модель в кэше локальной Room DB этой сессии
                val localSession = sessionDao.getSessionById(sessionId)
                if (localSession != null) {
                    sessionDao.upsertSession(
                        localSession.copy(
                            model = modelId,
                            provider = provider,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Сервер отклонил переключение модели: ${response.code()}"))
            }
        } catch (e: Exception) {
            // Офлайн режим: просто переключаем локально в Room
            val localSession = sessionDao.getSessionById(sessionId)
            if (localSession != null) {
                sessionDao.upsertSession(
                    localSession.copy(
                        model = modelId,
                        provider = provider,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                Result.success(Unit)
            } else {
                Result.failure(e)
            }
        }
    }
}
