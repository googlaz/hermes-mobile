package com.hermes.app.data.repository

import com.hermes.app.data.remote.SidecarApiService
import com.hermes.app.data.remote.dto.SetModelRequest
import com.hermes.app.data.remote.dto.SidecarCurrentDto
import com.hermes.app.data.remote.dto.SidecarModelDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    private val sidecarApiService: SidecarApiService
) {
    companion object {
        // Fallback на случай недоступности sidecar — экран не должен быть пустым.
        val FALLBACK_MODELS: List<SidecarModelDto> = listOf(
            SidecarModelDto(id = "qwen3.5:9b", provider = "ollama", label = "qwen3.5:9b"),
            SidecarModelDto(
                id = "anthropic/claude-sonnet-4.6",
                provider = "openrouter",
                label = "anthropic/claude-sonnet-4.6"
            )
        )
    }

    /**
     * Полный список моделей от sidecar GET /models (Ollama + OpenRouter).
     * При сетевой ошибке возвращаем fallback, чтобы экран не был пустым.
     */
    suspend fun getModels(): Result<List<SidecarModelDto>> = withContext(Dispatchers.IO) {
        try {
            val response = sidecarApiService.getModels()
            if (response.isSuccessful && response.body() != null) {
                val list = response.body()!!.data
                Result.success(if (list.isEmpty()) FALLBACK_MODELS else list)
            } else {
                Result.success(FALLBACK_MODELS)
            }
        } catch (e: Exception) {
            Result.success(FALLBACK_MODELS)
        }
    }

    /**
     * Текущая активная модель (sidecar GET /current).
     */
    suspend fun getCurrentModel(): Result<SidecarCurrentDto> = withContext(Dispatchers.IO) {
        try {
            val response = sidecarApiService.getCurrent()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Не удалось получить текущую модель: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Глобальное переключение модели через sidecar POST /model (правит config.yaml вживую).
     * Провайдер можно не передавать — sidecar выведет его сам (нет "/" => ollama).
     */
    suspend fun switchModel(modelId: String, provider: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = sidecarApiService.setModel(
                    SetModelRequest(model = modelId, provider = provider)
                )
                if (response.isSuccessful && response.body()?.ok == true) {
                    Result.success(Unit)
                } else {
                    val err = response.body()?.error ?: "код ${response.code()}"
                    Result.failure(Exception("Сервер отклонил переключение ($err)"))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Ошибка сети при переключении модели: ${e.message}"))
            }
        }
}
