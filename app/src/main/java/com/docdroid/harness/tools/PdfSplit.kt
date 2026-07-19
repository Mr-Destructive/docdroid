package com.docdroid.harness.tools

import android.content.Context
import com.docdroid.harness.Tool
import com.docdroid.harness.ToolResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.pdfbox.multipdf.Splitter
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.File

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

            val doc = PDDocument.load(inputFile)
            val totalPages = doc.numberOfPages

            val splitter = Splitter()
            val ranges = parsePageRanges(pageRanges, totalPages)
            splitter.pageRange = ranges

            val outDir = resolveOutput(outputDir)
            outDir.mkdirs()

            val pages = splitter.split(doc)
            val outputFiles = mutableListOf<String>()
            for ((index, page) in pages.withIndex()) {
                val outFile = File(outDir, "split_${index + 1}.pdf")
                page.save(outFile.absolutePath)
                page.close()
                outputFiles.add(outFile.name)
            }
            doc.close()

            ToolResult.Success(
                "Split $totalPages-page PDF into ${pages.size} files: ${outputFiles.joinToString(", ")}",
                outDir.absolutePath
            )
        } catch (e: Exception) {
            ToolResult.Error("Split failed: ${e.message}", "split_pdf")
        }
    }

    private fun parsePageRanges(ranges: String, totalPages: Int): List<Int> {
        val pages = mutableListOf<Int>()
        for (part in ranges.split(",")) {
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

    private fun resolveOutput(path: String): File {
        val f = File(path)
        if (f.isAbsolute) return f
        return File(context.filesDir, path)
    }
}
