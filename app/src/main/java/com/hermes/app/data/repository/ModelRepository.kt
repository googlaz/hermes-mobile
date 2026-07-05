package com.hermes.app.data.repository

import com.hermes.app.data.remote.HermesApiService
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
    companion object {
        // На сервере НЕТ каталога моделей: GET /v1/models отдаёт только идентичность агента
        // ("hermes-agent"), а реальные LLM живут в конфиге ПК и не отдаются по API.
        // Поэтому используем захардкоженный список доступных пользователю моделей.
        val FALLBACK_MODELS: List<String> = listOf("qwen3.5:9b", "qwen3.5-tuned")
        private const val AGENT_IDENTITY_ID = "hermes-agent"
    }

    /**
     * Список доступных моделей. Возвращаем захардкоженный fallback, дополненный тем,
     * что вернёт GET /v1/models (за вычетом идентичности агента "hermes-agent").
     * Экран никогда не остаётся пустым.
     */
    suspend fun getAvailableModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        val merged = LinkedHashSet<String>()
        merged.addAll(FALLBACK_MODELS)
        try {
            val response = apiService.getAvailableModels()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.data
                    .map { it.id }
                    .filter { it != AGENT_IDENTITY_ID }
                    .forEach { merged.add(it) }
            }
        } catch (e: Exception) {
            // Игнорируем сетевую ошибку — fallback-списка достаточно
        }
        Result.success(merged.toList())
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
