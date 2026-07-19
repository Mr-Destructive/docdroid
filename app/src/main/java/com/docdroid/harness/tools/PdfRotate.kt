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

            val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount
            val pagesToRotate = if (pagesStr.equals("all", ignoreCase = true)) {
                (0 until totalPages).toList()
            } else {
                parsePages(pagesStr, totalPages)
            }

            val doc = PdfDocument()
            for (pageIndex in 0 until totalPages) {
                val page = renderer.openPage(pageIndex)
                val rotating = pageIndex in pagesToRotate
                val swapDims = rotating && (angle == 90 || angle == 270)

                val outW = if (swapDims) page.height else page.width
                val outH = if (swapDims) page.width else page.height

                val pageInfo = PdfDocument.PageInfo.Builder(outW, outH, pageIndex + 1).create()
                val pdfPage = doc.startPage(pageInfo)
                val canvas = pdfPage.canvas

                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                canvas.save()
                if (rotating) {
                    when (angle) {
                        90 -> {
                            canvas.rotate(90f, outW / 2f, outH / 2f)
                            canvas.translate(outW - page.width.toFloat(), 0f)
                        }
                        180 -> canvas.rotate(180f, outW / 2f, outH / 2f)
                        270 -> {
                            canvas.rotate(270f, outW / 2f, outH / 2f)
                            canvas.translate(0f, outH - page.height.toFloat())
                        }
                    }
                }
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                canvas.restore()
                bitmap.recycle()

                doc.finishPage(pdfPage)
                page.close()
            }

            renderer.close()
            pfd.close()

            val outputFile = resolveOutput(outputPath)
            FileOutputStream(outputFile).use { fos -> doc.writeTo(fos) }
            doc.close()

            ToolResult.Success(
                "Rotated ${pagesToRotate.size} page(s) by ${angle}° in ${outputFile.name}",
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
