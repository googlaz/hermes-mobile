package com.hermes.app.data.remote

import com.hermes.app.data.local.SecurePreferences
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicUrlInterceptor @Inject constructor(
    private val securePreferences: SecurePreferences
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val host = securePreferences.tailscaleHost ?: "127.0.0.1"
        val port = securePreferences.serverPort
        
        // Динамически перестраиваем базовый URL по актуальным настройкам на лету!
        val newUrlString = "http://$host:$port"
        val newHttpUrl = newUrlString.toHttpUrlOrNull()

        val newRequest = if (newHttpUrl != null) {
            val updatedUrl = originalRequest.url.newBuilder()
                .scheme(newHttpUrl.scheme)
                .host(newHttpUrl.host)
                .port(newHttpUrl.port)
                .build()
            
            originalRequest.newBuilder()
                .url(updatedUrl)
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(newRequest)
    }
}
