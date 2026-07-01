package com.hermes.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.app.ui.chat.components.ChatTabs
import com.hermes.app.ui.chat.components.MessageBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    // Сбор стейта без Recomposition Storms (Pitfall 5)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    
    val listState = rememberLazyListState()
    var inputText by rememberSaveable { mutableStateOf("") } // Pitfall 7
    var showCreateDialog by remember { mutableStateOf(false) }

    // Контролируем автоскрол при прилете новых токенов
    LaunchedEffect(state.messages.size, state.streamingTextBySession[state.activeSessionId]) {
        if (state.messages.isNotEmpty() || !state.streamingTextBySession[state.activeSessionId].isNullOrBlank()) {
            val lastIndex = state.messages.size + (if (state.streamingTextBySession[state.activeSessionId] != null) 1 else 0) - 1
            if (lastIndex >= 0) {
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    Column(
        modifier = modifier
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

                // Кнопка Отправить (ФТ-2.3)
                IconButton(
                    onClick = {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    },
                    enabled = inputText.isNotBlank() && !state.isSending,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Отправить", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }

    // --- Диалог добавления вкладки (ФТ-2.2) ---
    if (showCreateDialog) {
        var newTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Новая сессия") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Название диалога") },
                    placeholder = { Text("Например: My Project") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val t = if (newTitle.isBlank()) "Новый чат" else newTitle
                        // В ТЗ указано: переключение Ollama/OpenRouter на лету. 
                        // По дефолту вешаем qwen3.5:9b на локальный оллама (пользователь предпочитает локалки, см. профиль)
                        viewModel.createSession(t, "qwen3.5:9b", "ollama")
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
}
