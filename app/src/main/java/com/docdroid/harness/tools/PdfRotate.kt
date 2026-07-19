package com.docdroid.harness.tools

import android.content.Context
import com.docdroid.harness.Tool
import com.docdroid.harness.ToolResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import java.io.File

class PdfRotate(private val context: Context) : Tool {

    private val mapper = jacksonObjectMapper()

    override suspend fun execute(argsJson: String): ToolResult {
        return try {
            val node = mapper.readTree(argsJson)
            val inputPath = node.get("input_path")?.asText()
                ?: return ToolResult.Error("Missing 'input_path'")
            val pagesStr = node.get("pages")?.asText() ?: "all"
            val angle = node.get("angle")?.asInt()
                ?: return ToolResult.Error("Missing 'angle'")
            val outputPath = node.get("output_path")?.asText()
                ?: return ToolResult.Error("Missing 'output_path'")

            if (angle !in listOf(90, 180, 270)) {
                return ToolResult.Error("Angle must be 90, 180, or 270")
            }

            val inputFile = resolveFile(inputPath)
            if (!inputFile.exists()) {
                return ToolResult.Error("File not found: $inputPath")
            }

            val doc = PDDocument.load(inputFile)
            val pages = if (pagesStr.equals("all", ignoreCase = true)) {
                (0 until doc.numberOfPages).toList()
            } else {
                parsePages(pagesStr, doc.numberOfPages)
            }

            for (pageIndex in pages) {
                val page = doc.getPage(pageIndex)
                val currentRotation = page.rotation
                page.rotation = (currentRotation + angle) % 360
            }

            val outputFile = resolveOutput(outputPath)
            doc.save(outputFile.absolutePath)
            doc.close()

            ToolResult.Success(
                "Rotated ${pages.size} page(s) by ${angle}° in ${outputFile.name}",
                outputFile.absolutePath
            )
        } catch (e: Exception) {
            ToolResult.Error("Rotate failed: ${e.message}", "rotate_pdf")
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

    private fun resolveOutput(path: String): File {
        val f = File(path)
        if (f.isAbsolute) { f.parentFile?.mkdirs(); return f }
        val out = File(context.filesDir, path)
        out.parentFile?.mkdirs()
        return out
    }
}
