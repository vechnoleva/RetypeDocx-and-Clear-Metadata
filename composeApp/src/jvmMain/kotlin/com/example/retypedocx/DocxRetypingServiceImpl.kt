package com.example.retypedocx

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path

class DocxRetypingServiceImpl : DocxRetypingService {

    override fun retype(input: Path, output: Path) {
        val inputFile = input.toFile()

        require(inputFile.exists()) {
            "Входной файл не найден: ${inputFile.absolutePath}"
        }
        require(inputFile.extension.equals("docx", ignoreCase = true)) {
            "Файл должен иметь расширение .docx"
        }

        // Open source — wrapped in try so we give a readable error on corrupt files
        val sourceDoc = try {
            FileInputStream(inputFile).use { XWPFDocument(it) }
        } catch (e: Exception) {
            throw IllegalStateException(
                "Не удалось прочитать файл. Проверьте, что это корректный .docx документ.", e
            )
        }

        // New document created from scratch — no styles, no metadata copied from source
        val outputDoc = XWPFDocument()

        try {
            // bodyElements preserves the original order of paragraphs and tables
            for (element in sourceDoc.bodyElements) {
                when (element) {
                    is XWPFParagraph -> copyParagraph(element, outputDoc)
                    is XWPFTable    -> copyTable(element, outputDoc)
                    // Other element types (SDT, etc.) are silently skipped
                }
            }

            try {
                FileOutputStream(output.toFile()).use { outputDoc.write(it) }
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Не удалось сохранить файл: ${e.message}", e
                )
            }
        } finally {
            sourceDoc.close()
            outputDoc.close()
        }
    }

    // ── Paragraph ─────────────────────────────────────────────────────────────

    private fun copyParagraph(source: XWPFParagraph, doc: XWPFDocument) {
        val text = source.text   // XWPFParagraph.getText() — concatenates all runs

        val newPara = doc.createParagraph()

        if (text.isEmpty()) {
            // Preserve empty paragraphs (blank lines between sections)
            return
        }

        newPara.createRun().setText(text)
    }

    // ── Table ──────────────────────────────────────────────────────────────────

    private fun copyTable(source: XWPFTable, doc: XWPFDocument) {
        val rows = source.rows
        if (rows.isEmpty()) return

        val maxCols = rows.maxOf { it.tableCells.size }.coerceAtLeast(1)

        // createTable pre-fills all cells with one empty paragraph each
        val newTable = doc.createTable(rows.size, maxCols)

        rows.forEachIndexed { rowIdx, row ->
            val newRow = newTable.getRow(rowIdx)

            row.tableCells.forEachIndexed { cellIdx, sourceCell ->
                // Get or create the target cell
                val newCell = if (cellIdx < newRow.tableCells.size) {
                    newRow.getCell(cellIdx)
                } else {
                    newRow.createCell()
                }

                val cellText = sourceCell.text
                if (cellText.isNotEmpty()) {
                    // The cell already has one paragraph from createTable — reuse it
                    val para = newCell.paragraphs.firstOrNull() ?: newCell.addParagraph()
                    val run = if (para.runs.isNotEmpty()) para.runs[0] else para.createRun()
                    run.setText(cellText)
                }
            }
        }
    }
}
