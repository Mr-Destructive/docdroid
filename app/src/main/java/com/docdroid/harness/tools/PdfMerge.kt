package com.docdroid.harness.tools

import android.content.Context
import com.docdroid.harness.Tool
import com.docdroid.harness.ToolResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.pdfbox.multipdf.PDFMergerUtility
import java.io.File

class PdfMerge(private val context: Context) : Tool {

    private val mapper = jacksonObjectMapper()

    override suspend fun execute(argsJson: String): ToolResult {
        return try {
            val node = mapper.readTree(argsJson)
            val inputPaths = node.get("input_paths")?.map { it.asText() }
                ?: return ToolResult.Error("Missing 'input_paths'")
            val outputPath = node.get("output_path")?.asText()
                ?: return ToolResult.Error("Missing 'output_path'")

            if (inputPaths.size < 2) {
                return ToolResult.Error("Need at least 2 PDFs to merge")
            }

            val merger = PDFMergerUtility()
            for (path in inputPaths) {
                val file = resolveFile(path)
                if (!file.exists()) {
                    return ToolResult.Error("File not found: $path")
                }
                merger.addSource(file)
            }

            val outputFile = resolveOutput(outputPath)
            merger.destinationFileName = outputFile.absolutePath
            merger.mergeDocuments(null)

            val sizeKb = outputFile.length() / 1024
            ToolResult.Success(
                "Merged ${inputPaths.size} PDFs into ${outputFile.name} (${sizeKb} KB)",
                outputFile.absolutePath
            )
        } catch (e: Exception) {
            ToolResult.Error("Merge failed: ${e.message}", "merge_pdfs")
        }
    }

    fun resolveFile(path: String): File {
        val f = File(path)
        if (f.isAbsolute) return f
        return File(context.filesDir, path)
    }

    fun resolveOutput(path: String): File {
        val f = File(path)
        if (f.isAbsolute) {
            f.parentFile?.mkdirs()
            return f
        }
        val out = File(context.filesDir, path)
        out.parentFile?.mkdirs()
        return out
    }
}
