package com.docdroid.harness.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.docdroid.harness.Tool
import com.docdroid.harness.ToolResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File
import java.io.FileOutputStream
import javax.imageio.ImageIO

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

            val doc = PDDocument.load(inputFile)
            val renderer = PDFRenderer(doc)
            val outDir = resolveOutput(outputDir)
            outDir.mkdirs()

            val extractedFiles = mutableListOf<String>()
            for (pageIndex in 0 until doc.numberOfPages) {
                val image = renderer.renderImageWithDPI(pageIndex, 200f)
                val outFile = File(outDir, "page_${pageIndex + 1}.png")
                FileOutputStream(outFile).use { fos ->
                    javax.imageio.ImageIO.write(image, "png", fos)
                }
                extractedFiles.add(outFile.name)
            }
            doc.close()

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
