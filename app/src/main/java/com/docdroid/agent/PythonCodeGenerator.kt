package com.docdroid.agent

import com.docdroid.model.DocumentFile

class PythonCodeGenerator {

    fun generate(
        query: String,
        files: List<DocumentFile>,
        failedTool: ToolCall? = null
    ): String {
        val fileList = files.joinToString("\n") { "FILE: ${it.path} (${it.mimeType})" }

        return buildString {
            appendLine("# Auto-generated Python code for: $query")
            appendLine("# Available libraries: Pillow, pypdf, reportlab, docx, openpyxl, pptx, img2pdf, pdfplumber")
            appendLine()
            appendLine("import os")
            appendLine("import sys")
            appendLine()

            if (files.any { it.mimeType.contains("pdf") }) {
                appendLine("from pypdf import PdfReader, PdfWriter")
                appendLine("from reportlab.pdfgen import canvas")
                appendLine("from reportlab.lib.pagesizes import A4, letter")
                appendLine()
            }
            if (files.any { it.mimeType.startsWith("image") }) {
                appendLine("from PIL import Image, ImageDraw, ImageFilter, ImageEnhance")
                appendLine()
            }
            if (files.any { it.mimeType.contains("word") || it.mimeType.contains("docx") }) {
                appendLine("from docx import Document")
                appendLine("from docx.shared import Inches, Pt")
                appendLine()
            }
            if (files.any { it.mimeType.contains("sheet") || it.mimeType.contains("excel") || it.mimeType.contains("csv")) {
                appendLine("from openpyxl import Workbook, load_workbook")
                appendLine()
            }
            if (files.any { it.mimeType.contains("presentation") || it.mimeType.contains("pptx") }) {
                appendLine("from pptx import Presentation")
                appendLine()
            }
            if (files.any { it.mimeType.contains("image") }) {
                appendLine("import img2pdf")
                appendLine()
            }

            appendLine("# File paths:")
            files.forEachIndexed { i, f ->
                appendLine("file_${i} = '${f.path}'")
            }
            appendLine()

            if (failedTool != null) {
                appendLine("# Previously failed tool call: ${failedTool.name}")
                appendLine("# Arguments: ${failedTool.arguments}")
                appendLine()
            }

            appendLine("# TODO: Implement the requested operation")
            appendLine("# $query")
            appendLine()
            appendLine("print('Operation completed')")
        }
    }

    fun generateForUnknownOperation(query: String, files: List<DocumentFile>): String {
        val fileContext = files.joinToString("\n") { f ->
            "  - ${f.name} (${f.mimeType}) at ${f.path}"
        }

        return """
# Python code generation for: $query
# Available files:
$fileContext
#
# Libraries available: Pillow, pypdf, reportlab, docx, openpyxl, pptx, img2pdf, pdfplumber
# Use the input files and generate the requested output.

import os

def main():
    # TODO: Implement '$query'
    # Access files via the paths above
    # Write output to the output directory
    print("Operation: $query")
    print("This is a placeholder - customize this code for your specific needs.")

if __name__ == "__main__":
    main()
""".trimIndent()
    }
}
