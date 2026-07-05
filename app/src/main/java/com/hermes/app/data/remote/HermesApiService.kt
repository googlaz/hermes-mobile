package com.hermes.app.data.remote

import com.hermes.app.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface HermesApiService {

    // === 1. Статус сервера ===
    @GET("health")
    suspend fun checkHealth(): Response<HealthStatusDto>

    // === 2. Модели (OpenAI-совместимый формат) ===
    @GET("v1/models")
    suspend fun getAvailableModels(): Response<ModelsResponse>

    // === 3. Сессии ===
    @GET("api/sessions")
    suspend fun getSessions(): Response<SessionListResponse>

    @POST("api/sessions")
    suspend fun createSession(
        @Body request: CreateSessionRequest
    ): Response<CreateSessionResponse>

    @DELETE("api/sessions/{id}")
    suspend fun deleteSession(
        @Path("id") sessionId: String
    ): Response<Unit>

    @GET("api/sessions/{id}/messages")
    suspend fun getMessages(
        @Path("id") sessionId: String
    ): Response<MessageListResponse>

    // Отправка сообщения (не-стриминговый)
    @POST("api/sessions/{id}/chat")
    suspend fun sendMessage(
        @Path("id") sessionId: String,
        @Body request: SendMessageRequest
    ): Response<ChatMessageDto>

    // Переключение модели в сессии через PATCH (возвращает тот же envelope {"session":{...}})
    @PATCH("api/sessions/{id}")
    suspend fun patchSession(
        @Path("id") sessionId: String,
        @Body request: PatchSessionRequest
    ): Response<CreateSessionResponse>

    // === 4. Jobs (активные задачи) ===
    @GET("api/jobs")
    suspend fun getActiveJobs(): Response<List<RunDto>>

    @DELETE("api/jobs/{id}")
    suspend fun cancelJob(
        @Path("id") jobId: String
    ): Response<Unit>

    // === 5. Файлы (через multipart в чат) ===
    @Multipart
    @POST("api/sessions/{id}/chat")
    suspend fun uploadFilesToSession(
        @Path("id") sessionId: String,
        @Part files: List<MultipartBody.Part>
    ): Response<ChatMessageDto>

    @Streaming
    @GET("api/sessions/{sessionId}/messages/{messageId}/attachment")
    suspend fun downloadAttachment(
        @Path("sessionId") sessionId: String,
        @Path("messageId") messageId: String
    ): Response<ResponseBody>
}
