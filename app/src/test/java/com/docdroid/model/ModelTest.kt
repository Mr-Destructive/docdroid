package com.docdroid.model

import org.junit.Assert.*
import org.junit.Test

class ModelTest {

    @Test
    fun `ToolResult default values`() {
        val result = ToolResult(toolName = "test", status = ToolStatus.SUCCESS)
        assertEquals("test", result.toolName)
        assertEquals(ToolStatus.SUCCESS, result.status)
        assertEquals("", result.result)
        assertNull(result.outputPath)
        assertNull(result.error)
        assertEquals(0L, result.executionTimeMs)
    }

    @Test
    fun `ToolResult with all fields`() {
        val result = ToolResult(
            toolName = "merge_pdfs",
            status = ToolStatus.SUCCESS,
            result = "Merged 3 files",
            outputPath = "/output/merged.pdf",
            error = null,
            executionTimeMs = 1500
        )
        assertEquals("merge_pdfs", result.toolName)
        assertEquals(ToolStatus.SUCCESS, result.status)
        assertEquals("Merged 3 files", result.result)
        assertEquals("/output/merged.pdf", result.outputPath)
        assertEquals(1500L, result.executionTimeMs)
    }

    @Test
    fun `DocumentFile default values`() {
        val doc = DocumentFile(name = "test.pdf", path = "/tmp/test.pdf", mimeType = "application/pdf", size = 1024)
        assertEquals("test.pdf", doc.name)
        assertEquals("/tmp/test.pdf", doc.path)
        assertEquals("application/pdf", doc.mimeType)
        assertEquals(1024L, doc.size)
        assertEquals("", doc.uri)
        assertNotNull(doc.id)
    }

    @Test
    fun `DocumentFile unique ids`() {
        val doc1 = DocumentFile(name = "a.pdf", path = "/a", mimeType = "application/pdf", size = 1)
        val doc2 = DocumentFile(name = "b.pdf", path = "/b", mimeType = "application/pdf", size = 1)
        assertNotEquals(doc1.id, doc2.id)
    }

    @Test
    fun `ChatMessage default values`() {
        val msg = ChatMessage(role = Role.USER, content = "hello")
        assertNotNull(msg.id)
        assertEquals(Role.USER, msg.role)
        assertEquals("hello", msg.content)
        assertTrue(msg.attachments.isEmpty())
        assertTrue(msg.toolCalls.isEmpty())
        assertTrue(msg.timestamp > 0)
    }

    @Test
    fun `ToolCallResult fields`() {
        val result = ToolCallResult(
            toolName = "compress",
            status = ToolStatus.FAILED,
            error = "file not found"
        )
        assertEquals("compress", result.toolName)
        assertEquals(ToolStatus.FAILED, result.status)
        assertEquals("file not found", result.error)
    }

    @Test
    fun `ToolStatus enum values`() {
        val values = ToolStatus.entries
        assertEquals(5, values.size)
        assertTrue(values.contains(ToolStatus.PENDING))
        assertTrue(values.contains(ToolStatus.RUNNING))
        assertTrue(values.contains(ToolStatus.SUCCESS))
        assertTrue(values.contains(ToolStatus.FAILED))
        assertTrue(values.contains(ToolStatus.CODE_GENERATED))
    }

    @Test
    fun `Role enum values`() {
        val values = Role.entries
        assertEquals(3, values.size)
        assertTrue(values.contains(Role.USER))
        assertTrue(values.contains(Role.ASSISTANT))
        assertTrue(values.contains(Role.SYSTEM))
    }
}
