package com.hermes.app.ui.models

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.hermes.app.data.remote.dto.ModelDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSwitcherScreen(
    viewModel: ModelViewModel,
    activeSessionId: String?,
    currentSessionModel: String?,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Смена LLM-модели",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (activeSessionId == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Откройте вкладку чата, чтобы переключить на ней модель", color = Color.Gray)
            }
            return
        }

        Text(
            text = "Текущая модель сессии: ${currentSessionModel ?: "Не установлена"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Разделяем локальные Ollama от облачных OpenRouter
        val ollamaModels = state.models.filter { it.provider == "ollama" }
        val openRouterModels = state.models.filter { it.provider == "openrouter" }

        Box(modifier = Modifier.weight(1f)) {
            if (state.isLoading && state.models.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // --- Секция Ollama (локальные модели) ---
                    if (ollamaModels.isNotEmpty()) {
                        item {
                            Text("Локальные модели (Ollama)", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(ollamaModels) { model ->
                            ModelItemRow(
                                model = model,
                                isSelected = model.id == currentSessionModel,
                                onClick = { viewModel.switchActiveModel(activeSessionId, model.id, "ollama") }
                            )
                        }
                    }

                    // --- Секция OpenRouter (облачные модели) ---
                    if (openRouterModels.isNotEmpty()) {
                        item {
                            Text("Облачные модели (OpenRouter)", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(openRouterModels) { model ->
                            ModelItemRow(
                                model = model,
                                isSelected = model.id == currentSessionModel,
                                onClick = { viewModel.switchActiveModel(activeSessionId, model.id, "openrouter") }
                            )
                        }
                    }
                }
            }
        }

        // Вывод ошибок/успехов
        state.successMessage?.let {
            Text(it, color = Color(0xFF81C784), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        }

        // Кнопка ручной синхронизации моделей с ПК
        Button(
            onClick = { viewModel.loadModels() },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Обновить список моделей с ПК")
        }
    }
}

@Composable
fun ModelItemRow(
    model: ModelDto,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        color = containerColor,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(model.name, style = MaterialTheme.typography.titleMedium, color = contentColor)
                Text(
                    text = "ID: ${model.id} | Контекст: ${model.contextLength} токенов",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}
