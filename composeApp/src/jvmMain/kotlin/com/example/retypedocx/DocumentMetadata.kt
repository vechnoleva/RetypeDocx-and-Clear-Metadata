package com.example.retypedocx

import java.time.LocalDate

/**
 * Metadata to embed in the output DOCX.
 * Core properties (title, subject, etc.) go to the OPC core-properties part.
 * [company] goes to the extended-properties part (separate XML in the OOXML package).
 */
data class DocumentMetadata(
    val author   : String    = System.getProperty("user.name") ?: "",
    val company  : String    = "",
    val title    : String    = "",
    val subject  : String    = "",
    val keywords : String    = "",
    val comments : String    = "",
    val category : String    = "",
    val created  : LocalDate = LocalDate.now(),
    val modified : LocalDate = LocalDate.now(),
)
