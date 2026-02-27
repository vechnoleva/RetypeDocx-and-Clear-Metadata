package com.example.retypedocx

import java.nio.file.Path

interface DocxRetypingService {
    /**
     * Reads [input] .docx, extracts text (paragraphs + tables), creates a
     * brand-new document with no metadata from the source, embeds [metadata]
     * into it, and saves the result to [output].
     *
     * @throws IllegalArgumentException if [input] is not a .docx file
     * @throws IllegalStateException    if the file cannot be read or saved
     */
    fun retype(input: Path, output: Path, metadata: DocumentMetadata)
}
