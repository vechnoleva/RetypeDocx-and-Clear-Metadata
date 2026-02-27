package com.example.retypedocx

import java.nio.file.Path

interface DocxRetypingService {
    /**
     * Reads [input] .docx, extracts text preserving paragraph/table structure,
     * and writes a clean new document (no metadata from source) to [output].
     *
     * @throws IllegalArgumentException if the input path is not a .docx file
     * @throws IllegalStateException    if the file cannot be read or saved
     */
    fun retype(input: Path, output: Path)
}
