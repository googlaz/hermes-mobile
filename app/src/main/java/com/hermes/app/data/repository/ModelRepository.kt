package com.hermes.app.data.repository

import com.hermes.app.data.remote.HermesApiService
import com.hermes.app.data.remote.dto.ModelDto
import com.hermes.app.data.remote.dto.PatchSessionRequest
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
     * Список доступных моделей через GET /v1/models (OpenAI-совместимый формат) (ФТ-3.1)
     */
    suspend fun getAvailableModels(): Result<List<ModelDto>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getAvailableModels()
            if (response.isSuccessful && response.body() != null) {
                // /v1/models возвращает {data: [...]} — извлекаем список
                Result.success(response.body()!!.data)
            } else {
                Result.failure(Exception("Не удалось получить модели: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Переключение модели в сессии через PATCH /api/sessions/{id} (ФТ-3.2)
     */
    suspend fun switchModelForSession(
        sessionId: String,
        modelId: String,
        provider: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.patchSession(
                sessionId,
                PatchSessionRequest(model = modelId, provider = provider)
            )
            if (response.isSuccessful) {
                // Обновляем локальный кэш Room
                val local = sessionDao.getSessionById(sessionId)
                if (local != null) {
                    sessionDao.upsertSession(
                        local.copy(model = modelId, provider = provider, updatedAt = System.currentTimeMillis())
                    )
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Сервер отклонил переключение: ${response.code()}"))
            }
        } catch (e: Exception) {
            // Офлайн-режим: переключаем только локально
            val local = sessionDao.getSessionById(sessionId)
            if (local != null) {
                sessionDao.upsertSession(
                    local.copy(model = modelId, provider = provider, updatedAt = System.currentTimeMillis())
                )
                Result.success(Unit)
            } else {
                Result.failure(e)
            }
        }
    }
}
