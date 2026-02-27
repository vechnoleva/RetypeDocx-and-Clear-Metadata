package com.example.retypedocx

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path
import java.time.ZoneId
import java.util.Date
import java.util.Optional

class DocxRetypingServiceImpl : DocxRetypingService {

    override fun retype(input: Path, output: Path, metadata: DocumentMetadata) {
        val inputFile = input.toFile()

        require(inputFile.exists()) {
            "Входной файл не найден: ${inputFile.absolutePath}"
        }
        require(inputFile.extension.equals("docx", ignoreCase = true)) {
            "Файл должен иметь расширение .docx"
        }

        val sourceDoc = try {
            FileInputStream(inputFile).use { XWPFDocument(it) }
        } catch (e: Exception) {
            throw IllegalStateException(
                "Не удалось прочитать файл. Проверьте, что это корректный .docx документ.", e
            )
        }

        // New document from scratch — no styles, no metadata from source
        val outputDoc = XWPFDocument()

        try {
            // bodyElements preserves the original order of paragraphs and tables
            for (element in sourceDoc.bodyElements) {
                when (element) {
                    is XWPFParagraph -> copyParagraph(element, outputDoc)
                    is XWPFTable     -> copyTable(element, outputDoc)
                    // Other element types (SDT, etc.) are silently skipped
                }
            }

            applyMetadata(outputDoc, metadata)

            try {
                FileOutputStream(output.toFile()).use { outputDoc.write(it) }
            } catch (e: Exception) {
                throw IllegalStateException("Не удалось сохранить файл: ${e.message}", e)
            }
        } finally {
            sourceDoc.close()
            outputDoc.close()
        }
    }

    // ── Metadata ───────────────────────────────────────────────────────────────

    /**
     * Writes [meta] into the freshly-created document's properties.
     *
     * Core fields (author, title, subject, etc.) are written via the stable
     * [org.apache.poi.openxml4j.opc.PackageProperties] interface, obtained
     * through explicit Java method calls to avoid Kotlin reserved-word / K2
     * acronym issues (e.g. `doc.getPackage()` instead of `doc.package`).
     *
     * [meta.company] lives in the OOXML extended-properties part (app.xml) and
     * is written via CTProperties.  The property name "company" is a normal
     * lower-camelCase word, so the Kotlin K2 accessor works correctly here.
     */
    private fun applyMetadata(doc: XWPFDocument, meta: DocumentMetadata) {
        // ── Core properties ────────────────────────────────────────────────
        val corePart = try {
            // Explicit .getPackage() call: avoids the Kotlin reserved keyword 'package'.
            // getPackageProperties() returns the PackageProperties interface (openxml4j).
            doc.getPackage().getPackageProperties()
        } catch (_: Exception) {
            null
        }

        if (corePart != null) {
            val zone = ZoneId.systemDefault()
            fun toDate(ld: java.time.LocalDate): Date =
                Date.from(ld.atStartOfDay(zone).toInstant())

            // Explicit setXxxProperty() calls — always safe regardless of K2 naming
            if (meta.author.isNotEmpty())   corePart.setCreatorProperty(meta.author)
            if (meta.title.isNotEmpty())    corePart.setTitleProperty(meta.title)
            if (meta.subject.isNotEmpty())  corePart.setSubjectProperty(meta.subject)
            if (meta.keywords.isNotEmpty()) corePart.setKeywordsProperty(meta.keywords)
            if (meta.comments.isNotEmpty()) corePart.setDescriptionProperty(meta.comments)
            if (meta.category.isNotEmpty()) corePart.setCategoryProperty(meta.category)

            corePart.setCreatedProperty(Optional.of(toDate(meta.created)))
            corePart.setModifiedProperty(Optional.of(toDate(meta.modified)))
        }

        // ── Extended properties: Company ───────────────────────────────────
        // underlyingProperties → CTProperties (officeDocument ooxml-lite schema).
        // "company" is a plain lower-camelCase name — K2 property accessor is fine.
        if (meta.company.isNotEmpty()) {
            try {
                doc.properties.extendedProperties.underlyingProperties.company =
                    meta.company
            } catch (_: Exception) {
                // Non-critical: skip if the schema class is unavailable at runtime
            }
        }
    }

    // ── Paragraph ─────────────────────────────────────────────────────────────

    private fun copyParagraph(source: XWPFParagraph, doc: XWPFDocument) {
        val text    = source.text  // XWPFParagraph.getText() — concatenates all runs
        val newPara = doc.createParagraph()
        if (text.isNotEmpty()) {
            newPara.createRun().setText(text)
        }
        // Empty paragraphs are kept above to preserve blank lines between sections
    }

    // ── Table ──────────────────────────────────────────────────────────────────

    private fun copyTable(source: XWPFTable, doc: XWPFDocument) {
        val rows = source.rows
        if (rows.isEmpty()) return

        val maxCols  = rows.maxOf { it.tableCells.size }.coerceAtLeast(1)
        val newTable = doc.createTable(rows.size, maxCols)

        rows.forEachIndexed { rowIdx, row ->
            val newRow = newTable.getRow(rowIdx)
            row.tableCells.forEachIndexed { cellIdx, sourceCell ->
                val newCell = if (cellIdx < newRow.tableCells.size) {
                    newRow.getCell(cellIdx)
                } else {
                    newRow.createCell()
                }
                val cellText = sourceCell.text
                if (cellText.isNotEmpty()) {
                    val para = newCell.paragraphs.firstOrNull() ?: newCell.addParagraph()
                    val run  = if (para.runs.isNotEmpty()) para.runs[0] else para.createRun()
                    run.setText(cellText)
                }
            }
        }
    }
}
