package com.docdroid.agent

import com.docdroid.model.DocumentFile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PythonCodeGeneratorTest {

    private lateinit var generator: PythonCodeGenerator

    @Before
    fun setup() {
        generator = PythonCodeGenerator()
    }

    @Test
    fun `generate includes query in comment`() {
        val code = generator.generate("merge these PDFs", emptyList())
        assertTrue(code.contains("merge these PDFs"))
    }

    @Test
    fun `generate with PDF files includes pypdf imports`() {
        val pdf = DocumentFile(name = "doc.pdf", path = "/tmp/doc.pdf", mimeType = "application/pdf", size = 100)
        val code = generator.generate("extract text", listOf(pdf))
        assertTrue(code.contains("from pypdf import"))
        assertTrue(code.contains("from reportlab"))
    }

    @Test
    fun `generate with image files includes PIL imports`() {
        val img = DocumentFile(name = "pic.png", path = "/tmp/pic.png", mimeType = "image/png", size = 100)
        val code = generator.generate("resize this", listOf(img))
        assertTrue(code.contains("from PIL import"))
    }

    @Test
    fun `generate with DOCX files includes docx imports`() {
        val doc = DocumentFile(name = "report.docx", path = "/tmp/report.docx", mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document", size = 100)
        val code = generator.generate("read this", listOf(doc))
        assertTrue(code.contains("from docx import Document"))
    }

    @Test
    fun `generate with XLSX files includes openpyxl imports`() {
        val xls = DocumentFile(name = "data.xlsx", path = "/tmp/data.xlsx", mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", size = 100)
        val code = generator.generate("read spreadsheet", listOf(xls))
        assertTrue(code.contains("from openpyxl import"))
    }

    @Test
    fun `generate with PPTX files includes pptx imports`() {
        val ppt = DocumentFile(name = "slides.pptx", path = "/tmp/slides.pptx", mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation", size = 100)
        val code = generator.generate("read presentation", listOf(ppt))
        assertTrue(code.contains("from pptx import"))
    }

    @Test
    fun `generate includes file paths`() {
        val files = listOf(
            DocumentFile(name = "a.pdf", path = "/tmp/a.pdf", mimeType = "application/pdf", size = 100),
            DocumentFile(name = "b.pdf", path = "/tmp/b.pdf", mimeType = "application/pdf", size = 200)
        )
        val code = generator.generate("merge", files)
        assertTrue(code.contains("/tmp/a.pdf"))
        assertTrue(code.contains("/tmp/b.pdf"))
    }

    @Test
    fun `generate with failed tool includes failed tool info`() {
        val failedTool = ToolCall(name = "broken_tool", arguments = mapOf("x" to "y"))
        val code = generator.generate("fix this", emptyList(), failedTool)
        assertTrue(code.contains("broken_tool"))
        assertTrue(code.contains("Previously failed tool call"))
    }

    @Test
    fun `generate with no files still produces valid code`() {
        val code = generator.generate("create a PDF from scratch", emptyList())
        assertTrue(code.contains("import os"))
        assertTrue(code.contains("TODO"))
    }

    @Test
    fun `generateForUnknownOperation includes query`() {
        val code = generator.generateForUnknownOperation("do something weird", emptyList())
        assertTrue(code.contains("do something weird"))
        assertTrue(code.contains("Available files"))
        assertTrue(code.contains("Libraries available"))
    }

    @Test
    fun `generateForUnknownOperation includes file info`() {
        val files = listOf(
            DocumentFile(name = "test.csv", path = "/tmp/test.csv", mimeType = "text/csv", size = 500)
        )
        val code = generator.generateForUnknownOperation("analyze", files)
        assertTrue(code.contains("test.csv"))
        assertTrue(code.contains("/tmp/test.csv"))
    }
}
