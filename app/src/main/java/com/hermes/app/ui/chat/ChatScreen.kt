package com.hermes.app.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.hermes.app.ui.chat.components.ChatTabs
import com.hermes.app.ui.chat.components.MessageBubble

// Доступные модели для выбора при создании сессии
private val AVAILABLE_MODELS = listOf(
    "qwen3.5:9b" to "ollama",
    "qwen3.5-tuned" to "ollama",
    "google/gemini-2.5-flash" to "openrouter",
    "anthropic/claude-sonnet-4.5" to "openrouter",
    "openai/gpt-4o" to "openrouter",
    "meta-llama/llama-3.3-70b-instruct" to "openrouter"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    // Сбор стейта без Recomposition Storms (Pitfall 5)
    val state by viewModel.uiState.collectAsState()
    
    val listState = rememberLazyListState()
    var inputText by rememberSaveable { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Пикер файлов (SAF, без разрешений хранилища): Excel/Word/PDF/картинки/любые
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addAttachments(uris)
    }

    // Показываем ошибки (нет соединения, сессия не создалась и т.д.)
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long) }
    }

    // Контролируем автоскрол при прилете новых токенов
    LaunchedEffect(state.messages.size, state.streamingTextBySession[state.activeSessionId]) {
        if (state.messages.isNotEmpty() || !state.streamingTextBySession[state.activeSessionId].isNullOrBlank()) {
            val lastIndex = state.messages.size + (if (state.streamingTextBySession[state.activeSessionId] != null) 1 else 0) - 1
            if (lastIndex >= 0) {
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Прокручиваемый список вкладок-сессий (ФТ-2.1)
        ChatTabs(
            sessions = state.sessions,
            activeSessionId = state.activeSessionId,
            onSessionSelected = { viewModel.selectSession(it) },
            onAddSessionClicked = { showCreateDialog = true }
        )

        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

        // Индикатор выполнения активной фоновой задачи агента (ФТ-2.5)
        if (state.isAgentRunningTasks) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = "Агент выполняет задачу на ПК...",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }

        // 2. Тело чата (Лента истории)
        Box(modifier = Modifier.weight(1f)) {
            if (state.activeSessionId == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Создайте новую сессию чата, чтобы начать диалог", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    // Историческая лента из Room DB (ФТ-2.4)
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(
                            role = message.role,
                            content = message.content,
                            timestamp = message.timestamp
                        )
                    }

                    // Летучий стриминг токенов (ФТ-2.3)
                    state.streamingTextBySession[state.activeSessionId]?.let { activeTokens ->
                        if (activeTokens.isNotBlank()) {
                            item(key = "streaming_token") {
                                MessageBubble(
                                    role = "assistant",
                                    content = activeTokens,
                                    timestamp = System.currentTimeMillis()
                                )
                            }
                        }
                    }
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

        // 3. Нижняя панель ввода и управления
        if (state.activeSessionId != null) {
            // 3a. Чипы прикреплённых файлов (📎 имя  ×)
            if (state.pendingAttachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.pendingAttachments.forEach { att ->
                        AssistChip(
                            onClick = { viewModel.removeAttachment(att.uri) },
                            label = { Text("📎 ${att.name}") },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Убрать ${att.name}",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }

            // 3b. Индикатор загрузки файлов на ПК
            if (state.isUploadingAttachments) {
                Text(
                    text = "Загрузка файлов…",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 16.dp, bottom = 2.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка удаления вкладки
                IconButton(onClick = { viewModel.deleteActiveSession() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить вкладку", tint = MaterialTheme.colorScheme.error)
                }

                // Кнопка прикрепления файлов (📎) — открывает системный пикер (SAF)
                IconButton(
                    onClick = { filePicker.launch("*/*") },
                    enabled = !state.isSending
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Прикрепить файлы", tint = MaterialTheme.colorScheme.primary)
                }

                // Текстовый инпут (ФТ-2.3)
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Задайте вопрос агенту...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Кнопка Отправить (ФТ-2.3) — активна, если есть текст ИЛИ прикреплённые файлы
                IconButton(
                    onClick = {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    },
                    enabled = (inputText.isNotBlank() || state.pendingAttachments.isNotEmpty()) && !state.isSending,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Отправить", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }

    // --- Диалог добавления вкладки (ФТ-2.2) с выбором модели ---
    if (showCreateDialog) {
        var newTitle by remember { mutableStateOf("") }
        var selectedModelIdx by remember { mutableStateOf(0) }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Новая сессия") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Название диалога") },
                        placeholder = { Text("Например: My Project") },
                        singleLine = true
                    )
                    Text("Модель:", style = MaterialTheme.typography.labelMedium)
                    Column {
                        AVAILABLE_MODELS.forEachIndexed { index, (model, provider) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedModelIdx == index,
                                    onClick = { selectedModelIdx = index }
                                )
                                Column {
                                    Text(model, style = MaterialTheme.typography.bodySmall)
                                    Text(provider, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (model, provider) = AVAILABLE_MODELS[selectedModelIdx]
                        viewModel.createSession(newTitle, model, provider)
                        showCreateDialog = false
                    }
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Snackbar для ошибок (нет соединения и т.д.)
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    } // Box
}
