package com.hermes.app.ui.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.app.data.remote.dto.FileItemDto
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    viewModel: FileViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showWorkdirDialog by remember { mutableStateOf(false) }

    // Контракт на множественный выбор файлов до 50 штук (ФТ-4.3)
    val filesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadFiles(uris)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Рабочие файлы Hermes",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // --- Блок отображения и редактирования рабочей папки (ФТ-4.1) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Рабочая директория ПК:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        state.currentWorkdir.ifBlank { "Загрузка..." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showWorkdirDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Сменить папку", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка возврата на уровень выше (..)
        if (state.currentSubPath != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        val parent = state.currentSubPath!!.substringBeforeLast("/", "")
                        viewModel.loadFiles(if (parent.isEmpty()) null else parent)
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Назад", tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Вернуться назад (..)", color = MaterialTheme.colorScheme.primary)
            }
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        }

        // --- Дерево/Список файлов ---
        Box(modifier = Modifier.weight(1f)) {
            if (state.files.isEmpty() && !state.isUploading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Папка пуста или ПК недоступен", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.files) { file ->
                        FileRow(
                            item = file,
                            onFolderClicked = { viewModel.loadFiles(file.path) },
                            onFileClicked = {
                                // Предлагаем закачать файл во внешнюю папку Downloads (ФТ-4.5)
                                val sharedDownloadsDir = File(context.getExternalFilesDir(null), "Downloads").apply { mkdirs() }
                                viewModel.downloadFile(file.path, sharedDownloadsDir, file.name)
                            }
                        )
                    }
                }
            }
        }

        // Сигнальные стейты загрузки/ошибки
        state.uploadStatus?.let {
            Text(it, color = Color(0xFF81C784), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Кнопка пакетного аплоада до 50 файлов одновременно (ФТ-4.3) ---
        Button(
            onClick = { filesPickerLauncher.launch("*/*") },
            enabled = !state.isUploading,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (state.isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Загрузить пакет файлов на ПК (до 50 шт.)")
            }
        }
    }

    // --- Диалог смены рабочей директории ---
    if (showWorkdirDialog) {
        var newPathText by remember { mutableStateOf(state.currentWorkdir) }
        AlertDialog(
            onDismissRequest = { showWorkdirDialog = false },
            title = { Text("Сменить рабочую директорию") },
            text = {
                OutlinedTextField(
                    value = newPathText,
                    onValueChange = { newPathText = it },
                    label = { Text("Путь на ПК") },
                    placeholder = { Text("например, C:\\Projects\\HermesApp") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.changeWorkdir(newPathText.trim())
                        showWorkdirDialog = false
                    }
                ) {
                    Text("Изменить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWorkdirDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun FileRow(
    item: FileItemDto,
    onFolderClicked: () -> Unit,
    onFileClicked: () -> Unit
) {
    val icon = if (item.isDirectory) "📁" else "📄"
    val subtitle = if (item.isDirectory) "Папка" else "${item.size / 1024} КБ"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (item.isDirectory) onFolderClicked() else onFileClicked() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        if (!item.isDirectory) {
            Text("⬇️", modifier = Modifier.padding(end = 8.dp)) // Нажмите для скачивания (ФТ-4.5)
        }
    }
    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
}
