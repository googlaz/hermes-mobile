package com.hermes.app.data.remote

import com.hermes.app.data.local.SecurePreferences
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Аналог DynamicUrlInterceptor, но принудительно направляет запросы на sidecar-сервис
 * (тот же хост Tailscale, порт 8643). Sidecar редактирует config.yaml вживую.
 */
@Singleton
class SidecarUrlInterceptor @Inject constructor(
    private val securePreferences: SecurePreferences
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val host = securePreferences.tailscaleHost
            ?: throw IOException("Hermes не настроен. Укажите IP и ключ в Настройках.")
        val port = SecurePreferences.SIDECAR_PORT

        val newHttpUrl = "http://$host:$port".toHttpUrlOrNull()
            ?: throw IOException("Некорректный адрес sidecar: $host:$port")

        val updatedUrl = originalRequest.url.newBuilder()
            .scheme(newHttpUrl.scheme)
            .host(newHttpUrl.host)
            .port(newHttpUrl.port)
            .build()

        val newRequest = originalRequest.newBuilder()
            .url(updatedUrl)
            .build()

        return chain.proceed(newRequest)
    }
}
