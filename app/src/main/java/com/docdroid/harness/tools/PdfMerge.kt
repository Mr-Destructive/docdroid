package com.docdroid.harness.tools

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import com.docdroid.harness.Tool
import com.docdroid.harness.ToolResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.io.FileOutputStream

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

            val doc = PdfDocument()
            var totalPages = 0

            for (path in inputPaths) {
                val file = resolveFile(path)
                if (!file.exists()) {
                    return ToolResult.Error("File not found: $path")
                }

                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)

                for (pageIndex in 0 until renderer.pageCount) {
                    val page = renderer.openPage(pageIndex)
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        page.width, page.height, totalPages + 1
                    ).create()
                    val pdfPage = doc.startPage(pageInfo)
                    page.render(pdfPage.canvas, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    doc.finishPage(pdfPage)
                    page.close()
                    totalPages++
                }

                renderer.close()
                pfd.close()
            }

            val outputFile = resolveOutput(outputPath)
            FileOutputStream(outputFile).use { fos ->
                doc.writeTo(fos)
            }
            doc.close()

            val sizeKb = outputFile.length() / 1024
            ToolResult.Success(
                "Merged ${inputPaths.size} PDFs ($totalPages pages) into ${outputFile.name} (${sizeKb} KB)",
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
        if (f.isAbsolute) { f.parentFile?.mkdirs(); return f }
        val out = File(context.filesDir, path)
        out.parentFile?.mkdirs()
        return out
    }
}
