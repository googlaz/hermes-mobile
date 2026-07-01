package com.hermes.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.app.data.local.entity.ChatMessageEntity
import com.hermes.app.data.local.entity.ChatSessionEntity
import com.hermes.app.data.remote.SseClient
import com.hermes.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val sessions: List<ChatSessionEntity> = emptyList(),
    val activeSessionId: String? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val streamingTextBySession: Map<String, String> = emptyMap(), // Стримящийся текст в разрезе сессии (ФТ-2.3)
    val isSending: Boolean = false,
    val isAgentRunningTasks: Boolean = false, // Задача агента выполняется? (ФТ-2.5)
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val sseClient: SseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var messageObserveJob: Job? = null
    private var sseJobs = mutableMapOf<String, Job>()

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
     * Создать вкладку / сессию (ФТ-2.2)
     */
    fun createSession(title: String, defaultModel: String, provider: String) {
        viewModelScope.launch {
            chatRepository.createNewSession(title, defaultModel, provider)
                .onSuccess { newSession ->
                    selectSession(entityId(newSession))
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = err.localizedMessage) }
                }
        }
    }

    /**
     * Удалить сессию/вкладку (ФТ-2.2)
     */
    fun deleteActiveSession() {
        val currentSessionId = _uiState.value.activeSessionId ?: return
        val currentSession = _uiState.value.sessions.find { it.id == currentSessionId } ?: return
        
        viewModelScope.launch {
            // Закрываем висящие стримы по удаляемой сессии
            sseJobs[currentSessionId]?.cancel()
            sseJobs.remove(currentSessionId)

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
     * Отправка сообщения и запуск реалтайм SSE прослушки токенов (ФТ-2.3)
     */
    fun sendMessage(content: String) {
        val sessionId = _uiState.value.activeSessionId ?: return
        if (content.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, isAgentRunningTasks = true) }

            // 1. Отправляем REST-запрос на регистрацию текста пользователя в БД
            chatRepository.sendUserMessage(sessionId, content)

            // 2. Инициируем SSE Stream для стриминга токенов ассистента в реальном времени
            sseJobs[sessionId]?.cancel() // Отменяем старый стрим если был
            
            val assistantResponseText = StringBuilder()
            val assMessageId = UUID.randomUUID().toString()

            sseJobs[sessionId] = viewModelScope.launch {
                sseClient.connectSessionStream(sessionId)
                    .onEach { chunk ->
                        // Наращиваем текст чанка на экране (ФТ-2.3)
                        assistantResponseText.append(chunk)
                        _uiState.update { state ->
                            val updatedMap = state.streamingTextBySession.toMutableMap()
                            updatedMap[sessionId] = assistantResponseText.toString()
                            state.copy(streamingTextBySession = updatedMap)
                        }
                    }
                    .onCompletion { error ->
                        // Поток завершен успешно или оборвался
                        _uiState.update { it.copy(isSending = false, isAgentRunningTasks = false) }
                        
                        val finalText = assistantResponseText.toString()
                        if (finalText.isNotBlank()) {
                            // Вбиваем итоговое собранное сообщение в локальный кэш Room
                            chatRepository.saveAssistantMessage(sessionId, assMessageId, finalText)
                            
                            // Очищаем стриминговый буфер отображения
                            _uiState.update { state ->
                                val updatedMap = state.streamingTextBySession.toMutableMap()
                                updatedMap.remove(sessionId)
                                state.copy(streamingTextBySession = updatedMap)
                            }
                        }
                    }
                    .collect()
            }
        }
    }

    private fun entityId(session: ChatSessionEntity): String = session.id
}
