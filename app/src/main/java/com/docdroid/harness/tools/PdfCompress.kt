package com.docdroid.harness.tools

import android.content.Context
import com.docdroid.harness.Tool
import com.docdroid.harness.ToolResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.pdfbox.multipdf.PDFCloneUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File

class PdfCompress(private val context: Context) : Tool {

    private val mapper = jacksonObjectMapper()

    override suspend fun execute(argsJson: String): ToolResult {
        return try {
            val node = mapper.readTree(argsJson)
            val inputPath = node.get("input_path")?.asText()
                ?: return ToolResult.Error("Missing 'input_path'")
            val outputPath = node.get("output_path")?.asText()
                ?: return ToolResult.Error("Missing 'output_path'")

            val inputFile = resolveFile(inputPath)
            if (!inputFile.exists()) {
                return ToolResult.Error("File not found: $inputPath")
            }

            val originalSize = inputFile.length()
            val doc = PDDocument.load(inputFile)

            // Remove metadata
            doc.documentInformation = null

            // Save with compression
            val outputFile = resolveOutput(outputPath)
            doc.save(outputFile.absolutePath)
            doc.close()

            val newSize = outputFile.length()
            val reduction = ((1.0 - newSize.toDouble() / originalSize) * 100).toInt()

            ToolResult.Success(
                "Compressed ${inputFile.name}: ${originalSize / 1024} KB → ${newSize / 1024} KB (${reduction}% reduction)",
                outputFile.absolutePath
            )
        } catch (e: Exception) {
            ToolResult.Error("Compression failed: ${e.message}", "compress_pdf")
        }
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
