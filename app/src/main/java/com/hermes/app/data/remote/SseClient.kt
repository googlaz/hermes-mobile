package com.hermes.app.data.remote

import com.hermes.app.data.local.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class SseClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val securePreferences: SecurePreferences
) {
    /**
     * Подключается к SSE-эндпоинту конкретной сессии и стримит входящие чанк-токены в реальном времени.
     */
    fun connectSessionStream(sessionId: String): Flow<String> {
        val host = securePreferences.tailscaleHost ?: "127.0.0.1"
        val port = securePreferences.serverPort
        // Реальный стриминговый эндпоинт Hermes: POST /api/sessions/{id}/chat/stream
        val url = "http://$host:$port/api/sessions/$sessionId/chat/stream"

        return callbackFlow {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${securePreferences.apiServerKey ?: ""}")
                .header("Accept", "text/event-stream")
                .build()

            val listener = object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    trySend(data) // Отправляем текстовый токен в корутиновый Flow поток
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    // Передаем ошибку во Flow для инициации бэкоффа ниже
                    close(t ?: IOException("SSE connection dropped with code ${response?.code ?: -1}"))
                }

                override fun onClosed(eventSource: EventSource) {
                    channel.close()
                }
            }

            val eventSource = EventSources.createFactory(okHttpClient)
                .newEventSource(request, listener)

            // При отмене подписки из Compose UI (или отмене корутины) корректно закрываем SSE-сокет
            awaitClose {
                eventSource.cancel()
            }
        }
        .flowOn(Dispatchers.IO) // Выполняем чисто в фоновом пуле (Pitfall 2)
        .retryWhen { cause, attempt ->
            // --- СТРОГОЕ СОБЛЮДЕНИЕ Pitfall 1 (SSE Reconnection Storm Protection) ---
            if (cause is IOException) {
                // Экспоненциальный бэкофф: 2с, 4с, 8с... максимум 30 секунд
                val backoffDelay = min(2000L * (1 shl attempt.toInt()), 30000L)
                delay(backoffDelay)
                true // Повторить подключение
            } else {
                false // Ошибки типов NullPointer или парсинга не повторяем
            }
        }
    }
}
