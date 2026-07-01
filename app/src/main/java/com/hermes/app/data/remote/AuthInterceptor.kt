package com.hermes.app.data.remote

import com.hermes.app.data.local.SecurePreferences
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val securePreferences: SecurePreferences
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val apiKey = securePreferences.apiServerKey

        val newRequest = if (!apiKey.isNullOrBlank()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $apiKey") // Безопасная подстановка API_SERVER_KEY
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(newRequest)
    }
}
