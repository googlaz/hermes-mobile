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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hermes.app.data.remote.dto.SidecarModelDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSwitcherScreen(
    viewModel: ModelViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var query by remember { mutableStateOf("") }

    // Загружаем модели и текущую при первом открытии
    LaunchedEffect(Unit) {
        viewModel.loadModels()
    }

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
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Текущая модель: ${state.currentModel ?: "неизвестна"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Поиск модели") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )

        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) state.models else state.models.filter { m ->
            m.id.lowercase().contains(q) || (m.label?.lowercase()?.contains(q) == true)
        }
        val ollama = filtered.filter { it.provider.equals("ollama", ignoreCase = true) }
        val openrouter = filtered.filter { !it.provider.equals("ollama", ignoreCase = true) }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (ollama.isNotEmpty()) {
                    item {
                        SectionHeader("Локальные (Ollama) · ${ollama.size}")
                    }
                    items(ollama, key = { "ollama-" + it.id }) { model ->
                        ModelItemRow(
                            model = model,
                            isSelected = model.id == state.currentModel,
                            onClick = { viewModel.switchActiveModel(model.id, model.provider) }
                        )
                    }
                }
                if (openrouter.isNotEmpty()) {
                    item {
                        SectionHeader("OpenRouter · ${openrouter.size}")
                    }
                    items(openrouter, key = { "or-" + it.id }) { model ->
                        ModelItemRow(
                            model = model,
                            isSelected = model.id == state.currentModel,
                            onClick = { viewModel.switchActiveModel(model.id, model.provider) }
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text("Ничего не найдено", color = Color.Gray, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }

        state.successMessage?.let {
            Text(it, color = Color(0xFF81C784), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        }

        Button(
            onClick = { viewModel.loadModels() },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(if (state.isLoading) "Загрузка..." else "Обновить список моделей с ПК")
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.Gray,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun ModelItemRow(
    model: SidecarModelDto,
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
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.label ?: model.id,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
                Text(
                    text = model.id,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.6f)
                )
            }
            // Бейдж провайдера
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = model.provider,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
