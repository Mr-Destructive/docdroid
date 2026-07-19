package com.docdroid.harness.tools

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.docdroid.harness.Tool
import com.docdroid.harness.ToolResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class PdfWatermark(private val context: Context) : Tool {

    private val mapper = jacksonObjectMapper()

    override suspend fun execute(argsJson: String): ToolResult {
        return try {
            val node = mapper.readTree(argsJson)
            val inputPath = node.get("input_path")?.asText()
                ?: return ToolResult.Error("Missing 'input_path'")
            val text = node.get("text")?.asText()
                ?: return ToolResult.Error("Missing 'text'")
            val outputPath = node.get("output_path")?.asText()
                ?: return ToolResult.Error("Missing 'output_path'")

            val inputFile = resolveFile(inputPath)
            if (!inputFile.exists()) {
                return ToolResult.Error("File not found: $inputPath")
            }

            // Use Android's built-in PDF APIs
            val renderer = PdfRenderer(
                ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            )
            val pageCount = renderer.pageCount

            val doc = PdfDocument()

            for (pageIndex in 0 until pageCount) {
                val page = renderer.openPage(pageIndex)
                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, pageIndex + 1).create()
                val pdfPage = doc.startPage(pageInfo)

                // Render original page content
                val canvas = pdfPage.canvas
                page.render(canvas, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                // Draw watermark overlay
                val paint = Paint().apply {
                    color = Color.argb(40, 200, 0, 0) // Semi-transparent red
                    textSize = 48f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }

                val centerX = canvas.width / 2f
                val centerY = canvas.height / 2f

                canvas.save()
                canvas.rotate(-45f, centerX, centerY)
                canvas.drawText(text, centerX, centerY, paint)
                canvas.restore()

                doc.finishPage(pdfPage)
                page.close()
            }

            renderer.close()

            val outputFile = resolveOutput(outputPath)
            FileOutputStream(outputFile).use { fos ->
                doc.writeTo(fos)
            }
            doc.close()

            ToolResult.Success(
                "Added watermark \"$text\" to all $pageCount pages in ${outputFile.name}",
                outputFile.absolutePath
            )
        } catch (e: Exception) {
            ToolResult.Error("Watermark failed: ${e.message}", "add_watermark")
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
