package com.docdroid.harness.tools

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.docdroid.harness.Tool
import com.docdroid.harness.ToolResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.io.FileOutputStream

class PdfSplit(private val context: Context) : Tool {

    private val mapper = jacksonObjectMapper()

    override suspend fun execute(argsJson: String): ToolResult {
        return try {
            val node = mapper.readTree(argsJson)
            val inputPath = node.get("input_path")?.asText()
                ?: return ToolResult.Error("Missing 'input_path'")
            val pageRanges = node.get("page_ranges")?.asText()
                ?: return ToolResult.Error("Missing 'page_ranges'")
            val outputDir = node.get("output_dir")?.asText()
                ?: return ToolResult.Error("Missing 'output_dir'")

            val inputFile = resolveFile(inputPath)
            if (!inputFile.exists()) {
                return ToolResult.Error("File not found: $inputPath")
            }

            val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount

            val ranges = parsePageRanges(pageRanges, totalPages)
            val outDir = resolveOutput(outputDir)
            outDir.mkdirs()

            val outputFiles = mutableListOf<String>()
            var fileIndex = 1

            for (range in ranges) {
                val doc = PdfDocument()
                for (pageIndex in range) {
                    val page = renderer.openPage(pageIndex)
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        page.width, page.height, doc.pages.size + 1
                    ).create()
                    val pdfPage = doc.startPage(pageInfo)
                    page.render(pdfPage.canvas, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    doc.finishPage(pdfPage)
                    page.close()
                }
                val outFile = File(outDir, "split_${fileIndex}.pdf")
                FileOutputStream(outFile).use { fos -> doc.writeTo(fos) }
                doc.close()
                outputFiles.add(outFile.name)
                fileIndex++
            }

            renderer.close()
            pfd.close()

            ToolResult.Success(
                "Split $totalPages-page PDF into ${outputFiles.size} files: ${outputFiles.joinToString(", ")}",
                outDir.absolutePath
            )
        } catch (e: Exception) {
            ToolResult.Error("Split failed: ${e.message}", "split_pdf")
        }
    }

    private fun parsePageRanges(ranges: String, totalPages: Int): List<List<Int>> {
        val result = mutableListOf<List<Int>>()
        for (part in ranges.split(",")) {
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val (start, end) = trimmed.split("-").map { it.trim().toInt() }
                result.add((start.coerceAtLeast(1)..end.coerceAtMost(totalPages)).toList())
            } else {
                val page = trimmed.toInt()
                if (page in 1..totalPages) result.add(listOf(page))
            }
        }
        return result.map { it.map { p -> p - 1 } }
    }

    private fun resolveFile(path: String): File {
        val f = File(path)
        if (f.isAbsolute) return f
        return File(context.filesDir, path)
    }

    private fun resolveOutput(path: String): File {
        val f = File(path)
        if (f.isAbsolute) return f
        return File(context.filesDir, path)
    }
}
