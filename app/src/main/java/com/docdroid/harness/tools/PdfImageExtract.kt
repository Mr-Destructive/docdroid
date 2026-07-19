package com.docdroid.harness.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.docdroid.harness.Tool
import com.docdroid.harness.ToolResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.io.FileOutputStream

class PdfImageExtract(private val context: Context) : Tool {

    private val mapper = jacksonObjectMapper()

    override suspend fun execute(argsJson: String): ToolResult {
        return try {
            val node = mapper.readTree(argsJson)
            val inputPath = node.get("input_path")?.asText()
                ?: return ToolResult.Error("Missing 'input_path'")
            val outputDir = node.get("output_dir")?.asText()
                ?: return ToolResult.Error("Missing 'output_dir'")

            val inputFile = resolveFile(inputPath)
            if (!inputFile.exists()) {
                return ToolResult.Error("File not found: $inputPath")
            }

            val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val outDir = resolveOutput(outputDir)
            outDir.mkdirs()

            val extractedFiles = mutableListOf<String>()
            for (pageIndex in 0 until renderer.pageCount) {
                val page = renderer.openPage(pageIndex)
                val scale = 2f
                val bitmap = Bitmap.createBitmap(
                    (page.width * scale).toInt(),
                    (page.height * scale).toInt(),
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val outFile = File(outDir, "page_${pageIndex + 1}.png")
                FileOutputStream(outFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                bitmap.recycle()
                page.close()
                extractedFiles.add(outFile.name)
            }
            renderer.close()
            pfd.close()

            ToolResult.Success(
                "Extracted ${extractedFiles.size} page images from ${inputFile.name}: ${extractedFiles.joinToString(", ")}",
                outDir.absolutePath
            )
        } catch (e: Exception) {
            ToolResult.Error("Image extraction failed: ${e.message}", "extract_images")
        }
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
