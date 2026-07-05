package com.hermes.app.data.remote

import com.hermes.app.data.remote.dto.SetModelRequest
import com.hermes.app.data.remote.dto.SetModelResponse
import com.hermes.app.data.remote.dto.SidecarCurrentDto
import com.hermes.app.data.remote.dto.SidecarModelsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Sidecar HTTP-сервис (порт 8643). Live-редактор config.yaml на ПК.
 * Auth = тот же Bearer-ключ, что и у основного API (через AuthInterceptor).
 */
interface SidecarApiService {

    @GET("models")
    suspend fun getModels(): Response<SidecarModelsResponse>

    @GET("current")
    suspend fun getCurrent(): Response<SidecarCurrentDto>

    @POST("model")
    suspend fun setModel(@Body body: SetModelRequest): Response<SetModelResponse>
}
