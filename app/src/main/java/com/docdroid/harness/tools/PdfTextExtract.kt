package com.docdroid.harness.tools

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.docdroid.harness.Tool
import com.docdroid.harness.ToolResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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

            val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount
            renderer.close()
            pfd.close()

            val sb = StringBuilder()
            sb.appendLine("PDF: ${inputFile.name}")
            sb.appendLine("Total pages: $totalPages")
            sb.appendLine()
            sb.appendLine("Note: Pure text extraction requires a PDF parsing library (PDFBox).")
            sb.appendLine("Each page can be rendered as an image using the 'extract_images' tool.")
            sb.appendLine("Page details:")

            val pfd2 = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer2 = PdfRenderer(pfd2)
            val pagesToRead = if (pagesStr.equals("all", ignoreCase = true)) {
                (0 until totalPages).toList()
            } else {
                parsePages(pagesStr, totalPages)
            }

            for (pageIndex in pagesToRead) {
                val page = renderer2.openPage(pageIndex)
                sb.appendLine("  Page ${pageIndex + 1}: ${page.width}x${page.height} pts")
                page.close()
            }

            renderer2.close()
            pfd2.close()

            ToolResult.Success(sb.toString())
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
        return pages.distinct().sorted().map { it - 1 }
    }

    private fun resolveFile(path: String): File {
        val f = File(path)
        if (f.isAbsolute) return f
        return File(context.filesDir, path)
    }
}
