package com.hermes.app.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.app.data.local.entity.ChatMessageEntity
import com.hermes.app.data.local.entity.ChatSessionEntity
import com.hermes.app.data.repository.ChatRepository
import com.hermes.app.data.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Файл, выбранный пользователем и ожидающий загрузки на ПК при отправке. */
data class PendingAttachment(
    val uri: Uri,
    val name: String
)

data class ChatUiState(
    val sessions: List<ChatSessionEntity> = emptyList(),
    val activeSessionId: String? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val streamingTextBySession: Map<String, String> = emptyMap(), // Оставлено для совместимости UI; SSE больше не используется
    val isSending: Boolean = false,
    val isAgentRunningTasks: Boolean = false, // Задача агента выполняется? (ФТ-2.5)
    val pendingAttachments: List<PendingAttachment> = emptyList(), // Прикреплённые файлы, ожидающие загрузки
    val isUploadingAttachments: Boolean = false, // Идёт загрузка файлов на ПК
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val fileRepository: FileRepository
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
     * Добавить выбранные пользователем файлы в список ожидающих отправки.
     */
    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val existing = _uiState.value.pendingAttachments
        val existingUris = existing.map { it.uri }.toSet()
        val newOnes = uris.filter { it !in existingUris }
            .map { PendingAttachment(uri = it, name = fileRepository.resolveFileName(it)) }
        if (newOnes.isEmpty()) return
        _uiState.update { it.copy(pendingAttachments = existing + newOnes) }
    }

    /**
     * Убрать один прикреплённый файл из списка ожидающих (крестик на чипе).
     */
    fun removeAttachment(uri: Uri) {
        _uiState.update { st ->
            st.copy(pendingAttachments = st.pendingAttachments.filterNot { it.uri == uri })
        }
    }

    /**
     * Отправка сообщения. Если есть прикреплённые файлы — сначала загружаем каждый на ПК
     * через sidecar /upload, затем добавляем машиночитаемую преамбулу с абсолютными путями,
     * чтобы агент открыл файлы своими инструментами. В чат-пузыре показываем чистый текст
     * с чипами "📎 имена", а не гигантскую преамбулу. /chat синхронный: ChatRepository
     * сохраняет ответ ассистента в Room, а observeMessages его отобразит.
     */
    fun sendMessage(content: String) {
        val sessionId = _uiState.value.activeSessionId ?: return
        val attachments = _uiState.value.pendingAttachments
        val typed = content.trim()
        // Нечего отправлять, если нет ни текста, ни файлов
        if (typed.isBlank() && attachments.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, isAgentRunningTasks = true, error = null) }
            try {
                if (attachments.isEmpty()) {
                    // Обычное текстовое сообщение
                    chatRepository.sendUserMessage(sessionId, typed)
                        .onFailure { err -> _uiState.update { it.copy(error = err.localizedMessage) } }
                } else {
                    // 1. Загружаем каждый файл на ПК
                    _uiState.update { it.copy(isUploadingAttachments = true) }
                    val uploaded = mutableListOf<Pair<String, String>>() // filename -> absolutePath
                    var uploadError: String? = null
                    for (att in attachments) {
                        val res = fileRepository.uploadToSidecar(att.uri)
                        res.onSuccess { resp ->
                            val name = resp.filename ?: att.name
                            val path = resp.path
                            if (path != null) uploaded.add(name to path)
                            else uploadError = "Сервер не вернул путь для «$name»"
                        }.onFailure { err ->
                            uploadError = err.message ?: "Не удалось загрузить «${att.name}»"
                        }
                        if (uploadError != null) break
                    }
                    _uiState.update { it.copy(isUploadingAttachments = false) }

                    if (uploadError != null) {
                        // Не отправляем сообщение, если загрузка провалилась — файлы остаются прикреплёнными
                        _uiState.update { it.copy(error = "Ошибка загрузки файлов: $uploadError") }
                    } else {
                        // 2. Строим преамбулу с путями для агента
                        val preamble = buildString {
                            append("[Прикреплённые файлы на ПК — открой их своими инструментами (terminal/read_file), это Excel/Word/PDF/данные]:\n")
                            uploaded.forEach { (name, path) -> append("- $name -> $path\n") }
                            append("\n")
                        }
                        val userText = if (typed.isBlank()) "Разбери прикреплённые файлы." else typed
                        val messageToSend = preamble + userText

                        // 3. Чистый текст для чат-пузыря: "📎 file1, file2\n<текст>"
                        val chipLine = "📎 " + uploaded.joinToString(", ") { it.first }
                        val displayText = if (typed.isBlank()) chipLine else "$chipLine\n$typed"

                        chatRepository.sendUserMessage(sessionId, messageToSend, displayText)
                            .onSuccess {
                                // Очищаем прикрепления только после успешной отправки
                                _uiState.update { it.copy(pendingAttachments = emptyList()) }
                            }
                            .onFailure { err -> _uiState.update { it.copy(error = err.localizedMessage) } }
                    }
                }
            } finally {
                _uiState.update {
                    it.copy(isSending = false, isAgentRunningTasks = false, isUploadingAttachments = false)
                }
            }
        }
    }
}
