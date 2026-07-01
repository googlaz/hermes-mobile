package com.hermes.app.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.app.data.local.entity.LogEntryEntity
import com.hermes.app.data.remote.dto.RunDto
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    viewModel: LogViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Логи и задачи",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = {
                viewModel.syncLogs()
                viewModel.loadActiveRuns()
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Обновить лог", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Секция 1: Активные задачи (Runs) --- ФТ-5.2 ---
        if (state.activeRuns.isNotEmpty()) {
            Text("Запущенные задачи агента (Runs):", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.activeRuns) { run ->
                    RunItemRow(run = run, onCancelClicked = { viewModel.cancelRun(run.id) })
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- Секция 2: Терминал Логов --- ФТ-5.1 ---
        Text("Консоль действий Hermes:", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0A0D10), RoundedCornerShape(8.dp)) // Темная гиковская консоль
                .padding(12.dp)
        ) {
            if (state.logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Консоль пуста. Запустите агент на ПК.", color = Color.Gray, fontFamily = FontFamily.Monospace)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.logs) { log ->
                        TerminalLogLine(log = log)
                    }
                }
            }
        }
    }
}

@Composable
fun RunItemRow(
    run: RunDto,
    onCancelClicked: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Задача: ${run.id.take(8)}...", style = MaterialTheme.typography.titleSmall)
                Text("Текущий инструмент: ${run.currentTool ?: "Рассуждение LLM"}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("Статус: ${run.status.uppercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            IconButton(onClick = onCancelClicked) {
                Icon(Icons.Default.Close, contentDescription = "Отменить задачу", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun TerminalLogLine(log: LogEntryEntity) {
    val levelColor = when (log.level.uppercase()) {
        "SUCCESS" -> Color(0xFF81C784)  // Зеленый
        "INFO" -> Color(0xFF64B5F6)     // Синий
        "WARN" -> Color(0xFFFFB74D)     // Желтый
        "ERROR" -> Color(0xFFEF5350)    // Красный
        else -> Color.White
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val timeStr = timeFormat.format(Date(log.timestamp))

    Text(
        text = "[$timeStr] [${log.tag.uppercase()}] ${log.message}",
        color = levelColor,
        fontFamily = FontFamily.Monospace, // Моноширинный шрифт терминала
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
