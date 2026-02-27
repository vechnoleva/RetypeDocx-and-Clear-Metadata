package com.example.retypedocx

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Paths

// ── State ──────────────────────────────────────────────────────────────────────

sealed interface RetypeState {
    data object Idle       : RetypeState
    data object Processing : RetypeState
    data class  Success(val outputPath: String) : RetypeState
    data class  Error(val message: String)      : RetypeState
}

// ── Root composable ────────────────────────────────────────────────────────────

@Composable
fun App() {
    val service = remember { DocxRetypingServiceImpl() }
    val scope   = rememberCoroutineScope()

    var inputPath  by remember { mutableStateOf("") }
    var outputPath by remember { mutableStateOf("") }
    var state      by remember { mutableStateOf<RetypeState>(RetypeState.Idle) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Text(
                    text  = "Перенабор документа .docx",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(Modifier.height(4.dp))

                // ── Input file ────────────────────────────────────────────────
                FilePickerRow(
                    label      = "Входной файл .docx",
                    path       = inputPath,
                    buttonText = "Выбрать...",
                    onPick     = {
                        showOpenDialog("Выберите входной файл .docx")?.let { picked ->
                            inputPath = picked
                            // Reset status when a new file is chosen
                            if (state is RetypeState.Success || state is RetypeState.Error) {
                                state = RetypeState.Idle
                            }
                        }
                    }
                )

                // ── Output file ───────────────────────────────────────────────
                FilePickerRow(
                    label      = "Сохранить результат как",
                    path       = outputPath,
                    buttonText = "Выбрать...",
                    onPick     = {
                        showSaveDialog(
                            title       = "Сохранить результат как .docx",
                            defaultName = buildDefaultOutputName(inputPath)
                        )?.let { picked ->
                            outputPath = picked
                            if (state is RetypeState.Success || state is RetypeState.Error) {
                                state = RetypeState.Idle
                            }
                        }
                    }
                )

                Spacer(Modifier.height(4.dp))

                // ── Action button ─────────────────────────────────────────────
                Button(
                    onClick = {
                        state = RetypeState.Processing
                        scope.launch {
                            state = withContext(Dispatchers.IO) {
                                runCatching {
                                    service.retype(
                                        Paths.get(inputPath),
                                        Paths.get(outputPath)
                                    )
                                    RetypeState.Success(outputPath)
                                }.getOrElse { e ->
                                    RetypeState.Error(e.message ?: "Неизвестная ошибка")
                                }
                            }
                        }
                    },
                    enabled = inputPath.isNotEmpty()
                            && outputPath.isNotEmpty()
                            && state !is RetypeState.Processing
                ) {
                    Text("Перенабрать текст")
                }

                // ── Status feedback ───────────────────────────────────────────
                when (val s = state) {
                    RetypeState.Idle       -> { /* nothing shown */ }

                    RetypeState.Processing ->
                        Text(
                            "Идёт обработка...",
                            color = MaterialTheme.colorScheme.secondary
                        )

                    is RetypeState.Success ->
                        Text(
                            "Готово! Файл сохранён:\n${s.outputPath}",
                            color = MaterialTheme.colorScheme.primary
                        )

                    is RetypeState.Error ->
                        Text(
                            "Ошибка: ${s.message}",
                            color = MaterialTheme.colorScheme.error
                        )
                }
            }
        }
    }
}

// ── Reusable file-picker row ───────────────────────────────────────────────────

@Composable
private fun FilePickerRow(
    label      : String,
    path       : String,
    buttonText : String,
    onPick     : () -> Unit
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier              = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value         = path,
            onValueChange = {},
            label         = { Text(label) },
            readOnly      = true,
            placeholder   = { Text("Не выбрано") },
            modifier      = Modifier.weight(1f),
            singleLine    = true
        )
        Button(onClick = onPick) {
            Text(buttonText)
        }
    }
}

// ── Native file-dialog helpers (AWT — works on macOS & Windows) ────────────────

/**
 * Shows a native open-file dialog filtered to *.docx.
 * Runs on the calling thread (must be the EDT, which it is in Compose Desktop).
 */
private fun showOpenDialog(title: String): String? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
    dialog.filenameFilter = java.io.FilenameFilter { _, name ->
        name.endsWith(".docx", ignoreCase = true)
    }
    dialog.isVisible = true          // blocks until user dismisses

    val dir  = dialog.directory ?: return null
    val file = dialog.file       ?: return null
    return File(dir, file).absolutePath
}

/**
 * Shows a native save-file dialog with [defaultName] pre-filled.
 * Automatically appends .docx if the user omits the extension.
 */
private fun showSaveDialog(title: String, defaultName: String): String? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.SAVE)
    dialog.file = defaultName
    dialog.isVisible = true

    val dir  = dialog.directory ?: return null
    val file = dialog.file       ?: return null

    val result = File(dir, file)
    return if (result.extension.equals("docx", ignoreCase = true)) {
        result.absolutePath
    } else {
        "${result.absolutePath}.docx"
    }
}

/** Suggests "original_retyped.docx" as the output name. */
private fun buildDefaultOutputName(inputPath: String): String {
    if (inputPath.isEmpty()) return "output.docx"
    val base = File(inputPath).nameWithoutExtension
    return "${base}_retyped.docx"
}
