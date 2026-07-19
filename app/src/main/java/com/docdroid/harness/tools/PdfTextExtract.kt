package com.docdroid.harness.tools

import android.content.Context
import com.docdroid.harness.Tool
import com.docdroid.harness.ToolResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

class PdfTextExtract(private val context: Context) : Tool {

    private val mapper = jacksonObjectMapper()

    override suspend fun execute(argsJson: String): ToolResult {
        return try {
            val node = mapper.readTree(argsJson)
            val inputPath = node.get("input_path")?.asText()
                ?: return ToolResult.Error("Missing 'input_path'")
            val pagesStr = node.get("pages")?.asText() ?: "all"

            val inputFile = resolveFile(inputPath)
            if (!inputFile.exists()) {
                return ToolResult.Error("File not found: $inputPath")
            }

            val doc = PDDocument.load(inputFile)
            val stripper = PDFTextStripper()

            if (!pagesStr.equals("all", ignoreCase = true)) {
                val pages = parsePages(pagesStr, doc.numberOfPages)
                if (pages.isNotEmpty()) {
                    stripper.startPage = pages.first()
                    stripper.endPage = pages.last()
                }
            }

            val text = stripper.getText(doc)
            doc.close()

            val preview = if (text.length > 500) {
                text.take(500) + "\n... (${text.length} chars total)"
            } else {
                text
            }

            ToolResult.Success(
                "Extracted ${text.length} characters from ${inputFile.name}:\n$preview"
            )
        } catch (e: Exception) {
            ToolResult.Error("Text extraction failed: ${e.message}", "extract_text")
        }
    }

    private fun parsePages(pagesStr: String, totalPages: Int): List<Int> {
        val pages = mutableListOf<Int>()
        for (part in pagesStr.split(",")) {
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val (start, end) = trimmed.split("-").map { it.trim().toInt() }
                pages.addAll(start.coerceAtLeast(1)..end.coerceAtMost(totalPages))
            } else {
                val page = trimmed.toInt()
                if (page in 1..totalPages) pages.add(page)
            }
        }
        return pages.distinct().sorted()
    }

    private fun resolveFile(path: String): File {
        val f = File(path)
        if (f.isAbsolute) return f
        return File(context.filesDir, path)
    }
}
