package com.example.retypedocx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Paths

class DocxRetypeViewModel(
    private val service: DocxRetypingService = DocxRetypingServiceImpl(),
) : ViewModel() {

    var uiState by mutableStateOf(DocxRetypeUiState())
        private set

    // ── File selection ─────────────────────────────────────────────────────────

    fun onInputFilePicked(path: String) {
        uiState = uiState.copy(
            inputPath  = path,
            // Pre-fill output path: same folder, "clean_" prefix
            outputPath = buildOutputPath(path),
            status     = ProcessingStatus.Ready,
        )
    }

    fun onOutputFilePicked(path: String) {
        uiState = uiState.copy(outputPath = path, status = ProcessingStatus.Ready)
    }

    // ── Metadata ───────────────────────────────────────────────────────────────

    fun updateMetadata(meta: DocumentMetadata) {
        uiState = uiState.copy(metadata = meta)
    }

    fun resetMetadata() {
        uiState = uiState.copy(metadata = DocumentMetadata())
    }

    fun toggleMetadataExpanded() {
        uiState = uiState.copy(metadataExpanded = !uiState.metadataExpanded)
    }

    // ── Main action ────────────────────────────────────────────────────────────

    fun process() {
        if (!uiState.canProcess) return
        val snapshot = uiState
        uiState = uiState.copy(status = ProcessingStatus.Processing)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    service.retype(
                        input    = Paths.get(snapshot.inputPath),
                        output   = Paths.get(snapshot.outputPath),
                        metadata = snapshot.metadata,
                    )
                    ProcessingStatus.Success(snapshot.outputPath)
                }.getOrElse { e ->
                    ProcessingStatus.Error(e.message ?: "Неизвестная ошибка")
                }
            }
            uiState = uiState.copy(status = result)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun buildOutputPath(inputPath: String): String {
        if (inputPath.isEmpty()) return ""
        val f = File(inputPath)
        return File(f.parentFile, "clean_${f.name}").absolutePath
    }
}
