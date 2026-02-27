package com.example.retypedocx

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ── Entry composable ───────────────────────────────────────────────────────────

@Composable
fun App() {
    val vm: DocxRetypeViewModel = viewModel { DocxRetypeViewModel() }
    MainScreen(vm)
}

// ── Main screen ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: DocxRetypeViewModel) {
    val state = vm.uiState

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ── Title ─────────────────────────────────────────────────
                Text(
                    text  = "Перенабор документа .docx",
                    style = MaterialTheme.typography.headlineSmall,
                )

                // ── Input file ────────────────────────────────────────────
                FilePickerRow(
                    label    = "Входной файл .docx",
                    path     = state.inputPath,
                    onChoose = {
                        showOpenDialog("Выберите входной файл .docx")?.let {
                            vm.onInputFilePicked(it)
                        }
                    }
                )

                // ── Metadata (collapsible card) ───────────────────────────
                MetadataCard(
                    metadata  = state.metadata,
                    expanded  = state.metadataExpanded,
                    onToggle  = { vm.toggleMetadataExpanded() },
                    onChange  = { vm.updateMetadata(it) },
                    onReset   = { vm.resetMetadata() },
                )

                // ── Output file ───────────────────────────────────────────
                FilePickerRow(
                    label    = "Сохранить результат как",
                    path     = state.outputPath,
                    onChoose = {
                        val defaultName = if (state.inputPath.isNotEmpty())
                            "clean_${File(state.inputPath).name}"
                        else
                            "output.docx"
                        showSaveDialog("Сохранить результат как .docx", defaultName)
                            ?.let { vm.onOutputFilePicked(it) }
                    }
                )

                Spacer(Modifier.height(4.dp))

                // ── Action row ────────────────────────────────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { vm.process() },
                        enabled = state.canProcess,
                    ) {
                        Text("Перенабрать текст → Создать DOCX")
                    }

                    if (state.metadataExpanded) {
                        OutlinedButton(onClick = { vm.resetMetadata() }) {
                            Text("Сбросить метаданные")
                        }
                    }
                }

                // ── Status feedback ───────────────────────────────────────
                when (val s = state.status) {
                    ProcessingStatus.Ready      -> { /* nothing */ }
                    ProcessingStatus.Processing ->
                        Text(
                            "Идёт обработка...",
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    is ProcessingStatus.Success ->
                        Text(
                            "Готово! Файл сохранён:\n${s.outputPath}",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    is ProcessingStatus.Error   ->
                        Text(
                            "Ошибка: ${s.message}",
                            color = MaterialTheme.colorScheme.error,
                        )
                }

                // Bottom spacer so the last element isn't flush with the edge
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Metadata collapsible card ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetadataCard(
    metadata : DocumentMetadata,
    expanded : Boolean,
    onToggle : () -> Unit,
    onChange : (DocumentMetadata) -> Unit,
    onReset  : () -> Unit,
) {
    var showCreatedPicker  by remember { mutableStateOf(false) }
    var showModifiedPicker by remember { mutableStateOf(false) }

    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ── Header row (always visible) ────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth().clickable { onToggle() },
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text       = "✏️  Метаданные нового файла",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text  = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            // ── Expandable fields ──────────────────────────────────────
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier              = Modifier.fillMaxWidth().padding(top = 14.dp),
                    verticalArrangement   = Arrangement.spacedBy(10.dp),
                ) {
                    MetaField("Автор",            metadata.author)   { onChange(metadata.copy(author   = it)) }
                    MetaField("Компания",          metadata.company)  { onChange(metadata.copy(company  = it)) }
                    MetaField("Заголовок",         metadata.title)    { onChange(metadata.copy(title    = it)) }
                    MetaField("Тема",              metadata.subject)  { onChange(metadata.copy(subject  = it)) }
                    MetaField("Ключевые слова",    metadata.keywords) { onChange(metadata.copy(keywords = it)) }
                    MetaField(
                        label    = "Комментарий",
                        value    = metadata.comments,
                        maxLines = 3,
                        onChange = { onChange(metadata.copy(comments = it)) }
                    )
                    MetaField("Категория", metadata.category) { onChange(metadata.copy(category = it)) }

                    // ── Date rows ──────────────────────────────────────
                    DateRow(
                        label     = "Дата создания",
                        date      = metadata.created,
                        formatter = dateFormatter,
                        onClick   = { showCreatedPicker = true },
                    )
                    DateRow(
                        label     = "Дата изменения",
                        date      = metadata.modified,
                        formatter = dateFormatter,
                        onClick   = { showModifiedPicker = true },
                    )

                    TextButton(onClick = onReset) {
                        Text("Сбросить к значениям по умолчанию")
                    }
                }
            }
        }
    }

    // ── Date picker dialogs ────────────────────────────────────────────────────

    if (showCreatedPicker) {
        DatePickerSheet(
            initial   = metadata.created,
            onConfirm = { date ->
                onChange(metadata.copy(created = date))
                showCreatedPicker = false
            },
            onDismiss = { showCreatedPicker = false },
        )
    }
    if (showModifiedPicker) {
        DatePickerSheet(
            initial   = metadata.modified,
            onConfirm = { date ->
                onChange(metadata.copy(modified = date))
                showModifiedPicker = false
            },
            onDismiss = { showModifiedPicker = false },
        )
    }
}

// ── Small helpers ──────────────────────────────────────────────────────────────

@Composable
private fun MetaField(
    label    : String,
    value    : String,
    maxLines : Int = 1,
    onChange : (String) -> Unit,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        label         = { Text(label) },
        modifier      = Modifier.fillMaxWidth(),
        maxLines      = maxLines,
        singleLine    = maxLines == 1,
    )
}

@Composable
private fun DateRow(
    label     : String,
    date      : LocalDate,
    formatter : DateTimeFormatter,
    onClick   : () -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text     = label,
            modifier = Modifier.width(140.dp),
            style    = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onClick) {
            Text(date.format(formatter))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initial   : LocalDate,
    onConfirm : (LocalDate) -> Unit,
    onDismiss : () -> Unit,
) {
    // DatePicker works with UTC epoch-millis (start of day in UTC)
    val initialMillis = initial
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val selected = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        onConfirm(selected)
                    } else {
                        onDismiss()
                    }
                }
            ) { Text("ОК") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun FilePickerRow(
    label    : String,
    path     : String,
    onChoose : () -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value         = path,
            onValueChange = {},
            label         = { Text(label) },
            readOnly      = true,
            placeholder   = { Text("Не выбрано") },
            modifier      = Modifier.weight(1f),
            singleLine    = true,
        )
        Button(onClick = onChoose) { Text("Выбрать...") }
    }
}

// ── Native file-dialog helpers (AWT — macOS & Windows native look) ─────────────

/**
 * Opens a native file-open dialog filtered to *.docx.
 * Called directly on the Compose / Swing EDT — safe to block.
 */
private fun showOpenDialog(title: String): String? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
    dialog.filenameFilter = java.io.FilenameFilter { _, name ->
        name.endsWith(".docx", ignoreCase = true)
    }
    dialog.isVisible = true
    val dir  = dialog.directory ?: return null
    val file = dialog.file       ?: return null
    return File(dir, file).absolutePath
}

/**
 * Opens a native file-save dialog with [defaultName] pre-filled.
 * Appends ".docx" automatically if the user omits the extension.
 */
private fun showSaveDialog(title: String, defaultName: String): String? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.SAVE)
    dialog.file = defaultName
    dialog.isVisible = true
    val dir  = dialog.directory ?: return null
    val file = dialog.file       ?: return null
    val result = File(dir, file)
    return if (result.extension.equals("docx", ignoreCase = true))
        result.absolutePath
    else
        "${result.absolutePath}.docx"
}
