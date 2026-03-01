package com.example.retypedocx

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFStyle
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTString
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
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
        applyStyles(outputDoc)

        try {
            // bodyElements preserves the original order of paragraphs and tables
            for (element in sourceDoc.bodyElements) {
                when (element) {
                    is XWPFParagraph -> copyParagraph(element, outputDoc, sourceDoc)
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

    // ── Styles ─────────────────────────────────────────────────────────────────

    /**
     * Defines two paragraph styles in [doc]:
     *  • "Normal"   — Times New Roman 14pt, justified, 1.25cm first-line indent, 1.5× spacing
     *  • "Heading1" — Times New Roman 14pt bold, centered, 0 indent, 1.5× spacing
     *
     * All spacing values are in twips (1/20 pt). Font sizes are in half-points.
     * Explicit Java setter calls are used throughout to avoid Kotlin K2 issues.
     */
    private fun applyStyles(doc: XWPFDocument) {
        val styles = doc.createStyles()

        // ── Normal (default paragraph style) ───────────────────────────────
        val normalCT = CTStyle.Factory.newInstance().apply {
            setType(STStyleType.PARAGRAPH)
            setStyleId("Normal")
            setDefault(true)
            val nm = CTString.Factory.newInstance(); nm.setVal("Normal"); setName(nm)

            val pPr = addNewPPr()
            pPr.addNewJc().setVal(STJc.BOTH)               // justified
            pPr.addNewSpacing().also { sp ->
                sp.setLineRule(STLineSpacingRule.AUTO)
                sp.setLine(BigInteger.valueOf(360))         // 1.5× (240 = single)
                sp.setBefore(BigInteger.ZERO)
                sp.setAfter(BigInteger.ZERO)
            }
            pPr.addNewInd().also { ind ->
                ind.setFirstLine(BigInteger.valueOf(709))   // 1.25 cm ≈ 709 twips
                ind.setLeft(BigInteger.ZERO)
                ind.setRight(BigInteger.ZERO)
            }

            val rPr = addNewRPr()
            rPr.addNewRFonts().also { f ->
                f.setAscii("Times New Roman")
                f.setHAnsi("Times New Roman")
                f.setCs("Times New Roman")
            }
            rPr.addNewSz().setVal(BigInteger.valueOf(28))   // 14pt = 28 half-pts
            rPr.addNewSzCs().setVal(BigInteger.valueOf(28))
            // No <w:b> → regular weight
        }
        styles.addStyle(XWPFStyle(normalCT, styles))

        // ── Heading 1 ──────────────────────────────────────────────────────
        val heading1CT = CTStyle.Factory.newInstance().apply {
            setType(STStyleType.PARAGRAPH)
            setStyleId("Heading1")
            val nm = CTString.Factory.newInstance(); nm.setVal("heading 1"); setName(nm)

            val pPr = addNewPPr()
            pPr.addNewJc().setVal(STJc.CENTER)             // centered
            pPr.addNewSpacing().also { sp ->
                sp.setLineRule(STLineSpacingRule.AUTO)
                sp.setLine(BigInteger.valueOf(360))         // 1.5×
                sp.setBefore(BigInteger.ZERO)
                sp.setAfter(BigInteger.ZERO)
            }
            pPr.addNewInd().also { ind ->                  // explicit zero indent
                ind.setFirstLine(BigInteger.ZERO)
                ind.setLeft(BigInteger.ZERO)
                ind.setRight(BigInteger.ZERO)
            }

            val rPr = addNewRPr()
            rPr.addNewRFonts().also { f ->
                f.setAscii("Times New Roman")
                f.setHAnsi("Times New Roman")
                f.setCs("Times New Roman")
            }
            rPr.addNewSz().setVal(BigInteger.valueOf(28))   // 14pt
            rPr.addNewSzCs().setVal(BigInteger.valueOf(28))
            rPr.addNewB()                                   // bold
            rPr.addNewBCs()                                 // bold for complex scripts
        }
        styles.addStyle(XWPFStyle(heading1CT, styles))
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

    private fun copyParagraph(source: XWPFParagraph, doc: XWPFDocument, sourceDoc: XWPFDocument) {
        val text    = source.text
        val newPara = doc.createParagraph()

        val mappedId = mapStyleId(source.styleID, sourceDoc)
        if (mappedId != null) newPara.setStyle(mappedId)

        if (text.isNotEmpty()) {
            newPara.createRun().setText(text)
        }
    }

    /**
     * Resolves a source style ID to an output style ID.
     *
     * Strategy:
     *  1. If empty/null → null (paragraph inherits default = Normal).
     *  2. If ID directly matches one of our defined styles → use it as-is.
     *  3. Otherwise look up the style **name** in the source document (handles
     *     localised IDs, e.g. Russian Word uses "1" as the ID for "Заголовок 1").
     *     Map normalised names to our output style IDs.
     *  4. Fallback → keep the original ID (Word will use its own built-in or skip).
     */
    private fun mapStyleId(sourceId: String?, sourceDoc: XWPFDocument): String? {
        if (sourceId.isNullOrEmpty()) return null

        // Fast path: ID already matches one of our defined styles
        if (sourceId == "Normal" || sourceId == "Heading1") return sourceId

        // Look up the style name in the source document
        val name = try {
            sourceDoc.styles?.getStyle(sourceId)?.name?.trim()?.lowercase()
        } catch (_: Exception) { null }

        return when (name) {
            "normal", "обычный", "default paragraph font" -> "Normal"
            "heading 1", "заголовок 1"                   -> "Heading1"
            "heading 2", "заголовок 2"                   -> "Heading2"
            "heading 3", "заголовок 3"                   -> "Heading3"
            else                                          -> sourceId
        }
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
