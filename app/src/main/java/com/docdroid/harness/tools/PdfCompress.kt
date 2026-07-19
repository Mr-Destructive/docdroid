package com.docdroid.harness.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.docdroid.harness.Tool
import com.docdroid.harness.ToolResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.io.FileOutputStream

class PdfCompress(private val context: Context) : Tool {

    private val mapper = jacksonObjectMapper()

    override suspend fun execute(argsJson: String): ToolResult {
        return try {
            val node = mapper.readTree(argsJson)
            val inputPath = node.get("input_path")?.asText()
                ?: return ToolResult.Error("Missing 'input_path'")
            val outputPath = node.get("output_path")?.asText()
                ?: return ToolResult.Error("Missing 'output_path'")
            val quality = node.get("quality")?.asText()?.lowercase() ?: "medium"

            val inputFile = resolveFile(inputPath)
            if (!inputFile.exists()) {
                return ToolResult.Error("File not found: $inputPath")
            }

            val originalSize = inputFile.length()
            val scale = when (quality) {
                "high" -> 1.5f
                "low" -> 0.75f
                else -> 1.0f
            }

            val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount

            val doc = PdfDocument()
            for (pageIndex in 0 until totalPages) {
                val page = renderer.openPage(pageIndex)
                val newWidth = (page.width * scale).toInt()
                val newHeight = (page.height * scale).toInt()
                val pageInfo = PdfDocument.PageInfo.Builder(newWidth, newHeight, pageIndex + 1).create()
                val pdfPage = doc.startPage(pageInfo)
                val bitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                bitmap.recycle()
                doc.finishPage(pdfPage)
                page.close()
            }
            renderer.close()
            pfd.close()

            val outputFile = resolveOutput(outputPath)
            FileOutputStream(outputFile).use { fos -> doc.writeTo(fos) }
            doc.close()

            val newSize = outputFile.length()
            val reduction = if (originalSize > 0) ((1.0 - newSize.toDouble() / originalSize) * 100).toInt() else 0

            ToolResult.Success(
                "Compressed ${inputFile.name}: ${originalSize / 1024} KB -> ${newSize / 1024} KB (${reduction}% change)",
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
