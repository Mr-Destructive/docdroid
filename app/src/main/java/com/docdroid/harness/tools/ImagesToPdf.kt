package com.docdroid.harness.tools

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.docdroid.harness.Tool
import com.docdroid.harness.ToolResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.io.FileOutputStream

class ImagesToPdf(private val context: Context) : Tool {

    private val mapper = jacksonObjectMapper()

    override suspend fun execute(argsJson: String): ToolResult {
        return try {
            val node = mapper.readTree(argsJson)
            val imagePaths = node.get("image_paths")?.map { it.asText() }
                ?: return ToolResult.Error("Missing 'image_paths'")
            val outputPath = node.get("output_path")?.asText()
                ?: return ToolResult.Error("Missing 'output_path'")

            if (imagePaths.isEmpty()) {
                return ToolResult.Error("No images provided")
            }

            val doc = PdfDocument()
            val addedImages = mutableListOf<String>()

            for ((index, imagePath) in imagePaths.withIndex()) {
                val imageFile = resolveFile(imagePath)
                if (!imageFile.exists()) continue

                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(imageFile.absolutePath, options)

                val pageInfo = PdfDocument.PageInfo.Builder(
                    options.outWidth, options.outHeight, index + 1
                ).create()
                val page = doc.startPage(pageInfo)

                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                if (bitmap != null) {
                    val canvas = page.canvas
                    val scaleX = canvas.width.toFloat() / bitmap.width
                    val scaleY = canvas.height.toFloat() / bitmap.height
                    val scale = minOf(scaleX, scaleY)

                    val offsetX = (canvas.width - bitmap.width * scale) / 2
                    val offsetY = (canvas.height - bitmap.height * scale) / 2

                    canvas.save()
                    canvas.translate(offsetX, offsetY)
                    canvas.scale(scale, scale)
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    canvas.restore()

                    bitmap.recycle()
                    addedImages.add(imageFile.name)
                }

                doc.finishPage(page)
            }

            if (addedImages.isEmpty()) {
                doc.close()
                return ToolResult.Error("No valid images found to convert")
            }

            val outputFile = resolveOutput(outputPath)
            FileOutputStream(outputFile).use { fos ->
                doc.writeTo(fos)
            }
            doc.close()

            val sizeKb = outputFile.length() / 1024
            ToolResult.Success(
                "Converted ${addedImages.size} images to ${outputFile.name} (${sizeKb} KB)",
                outputFile.absolutePath
            )
        } catch (e: Exception) {
            ToolResult.Error("Image-to-PDF failed: ${e.message}", "images_to_pdf")
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
