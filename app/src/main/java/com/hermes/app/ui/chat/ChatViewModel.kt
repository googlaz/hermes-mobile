package com.hermes.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.app.data.local.entity.ChatMessageEntity
import com.hermes.app.data.local.entity.ChatSessionEntity
import com.hermes.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ChatUiState(
    val sessions: List<ChatSessionEntity> = emptyList(),
    val activeSessionId: String? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val streamingTextBySession: Map<String, String> = emptyMap(), // Оставлено для совместимости UI; SSE больше не используется
    val isSending: Boolean = false,
    val isAgentRunningTasks: Boolean = false, // Задача агента выполняется? (ФТ-2.5)
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var messageObserveJob: Job? = null

    init {
        // Подписываемся на вкладки сессий из Room DB (ФТ-2.1)
        chatRepository.allSessionsFlow
            .distinctUntilChanged()
            .onEach { sessions ->
                _uiState.update { it.copy(sessions = sessions) }
                if (sessions.isNotEmpty() && _uiState.value.activeSessionId == null) {
                    // Выделяем первую вкладку по умолчанию
                    selectSession(sessions.first().id)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Смена активной сессии-вкладки (ФТ-2.2)
     */
    fun selectSession(sessionId: String) {
        _uiState.update { it.copy(activeSessionId = sessionId, error = null) }

        // Переподписываемся на сообщения конкретной сессии
        messageObserveJob?.cancel()
        messageObserveJob = chatRepository.observeMessages(sessionId)
            .distinctUntilChanged()
            .onEach { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Создать вкладку / сессию (ФТ-2.2). Заголовок делаем уникальным по умолчанию,
     * т.к. сервер отклоняет дубликаты заголовков (invalid_title, HTTP 400).
     */
    fun createSession(title: String, defaultModel: String, provider: String) {
        val uniqueTitle = if (title.isBlank()) uniqueDefaultTitle() else title
        viewModelScope.launch {
            chatRepository.createNewSession(uniqueTitle, defaultModel, provider)
                .onSuccess { newSession ->
                    selectSession(newSession.id)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = err.localizedMessage) }
                }
        }
    }

    private fun uniqueDefaultTitle(): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        return "Чат $time"
    }

    /**
     * Удалить сессию/вкладку (ФТ-2.2)
     */
    fun deleteActiveSession() {
        val currentSessionId = _uiState.value.activeSessionId ?: return
        val currentSession = _uiState.value.sessions.find { it.id == currentSessionId } ?: return

        viewModelScope.launch {
            chatRepository.deleteSession(currentSession)

            _uiState.update {
                it.copy(
                    activeSessionId = null,
                    messages = emptyList()
                )
            }
        }
    }

    /**
     * Отправка сообщения. /chat синхронный: ChatRepository сохраняет ответ ассистента
     * в Room, а observeMessages его отобразит. SSE больше не используется.
     */
    fun sendMessage(content: String) {
        val sessionId = _uiState.value.activeSessionId ?: return
        if (content.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, isAgentRunningTasks = true, error = null) }
            try {
                chatRepository.sendUserMessage(sessionId, content)
                    .onFailure { err ->
                        _uiState.update { it.copy(error = err.localizedMessage) }
                    }
            } finally {
                _uiState.update { it.copy(isSending = false, isAgentRunningTasks = false) }
            }
        }
    }
}
