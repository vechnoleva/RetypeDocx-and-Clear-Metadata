package com.example.retypedocx

// ── Processing status ──────────────────────────────────────────────────────────

sealed interface ProcessingStatus {
    data object Ready      : ProcessingStatus
    data object Processing : ProcessingStatus
    data class  Success(val outputPath: String) : ProcessingStatus
    data class  Error(val message: String)      : ProcessingStatus
}

// ── Full UI state (immutable snapshot) ────────────────────────────────────────

data class DocxRetypeUiState(
    val inputPath        : String           = "",
    val outputPath       : String           = "",
    val metadata         : DocumentMetadata = DocumentMetadata(),
    val status           : ProcessingStatus = ProcessingStatus.Ready,
    val metadataExpanded : Boolean          = false,
) {
    /** True when both paths are provided and we're not currently processing. */
    val canProcess: Boolean
        get() = inputPath.isNotEmpty()
                && outputPath.isNotEmpty()
                && status !is ProcessingStatus.Processing
}
