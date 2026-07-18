package com.docdroid.agent

import com.docdroid.model.ToolResult
import com.docdroid.model.ToolStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ToolDispatcherTest {

    private lateinit var registry: ToolRegistry
    private lateinit var dispatcher: ToolDispatcher

    @Before
    fun setup() {
        registry = ToolRegistry()
        dispatcher = ToolDispatcher(registry)
    }

    @Test
    fun `dispatch calls correct handler`() = runTest {
        registry.register("resize_image") { args ->
            ToolResult("resize_image", ToolStatus.SUCCESS, result = "resized ${args["width"]}")
        }

        val toolCall = ToolCall(
            name = "resize_image",
            arguments = mapOf("input_path" to "/tmp/img.png", "width" to "800", "height" to "600", "output_path" to "/tmp/out.png")
        )

        val result = dispatcher.dispatch(toolCall)
        assertEquals(ToolStatus.SUCCESS, result.status)
        assertEquals("resized 800", result.result)
    }

    @Test
    fun `dispatch returns FAILED for unknown tool`() = runTest {
        val toolCall = ToolCall(name = "unknown_tool", arguments = emptyMap())
        val result = dispatcher.dispatch(toolCall)
        assertEquals(ToolStatus.FAILED, result.status)
    }

    @Test
    fun `parseToolCalls with valid JSON array`() {
        val json = """[{"name":"merge_pdfs","arguments":{"input_paths":"/a.pdf,/b.pdf","output_path":"/merged.pdf"}}]"""
        val calls = dispatcher.parseToolCalls(json)
        assertEquals(1, calls.size)
        assertEquals("merge_pdfs", calls[0].name)
        assertEquals("/a.pdf,/b.pdf", calls[0].arguments["input_paths"])
        assertEquals("/merged.pdf", calls[0].arguments["output_path"])
    }

    @Test
    fun `parseToolCalls with multiple tools`() {
        val json = """[{"name":"tool_a","arguments":{"k":"v"}},{"name":"tool_b","arguments":{}}]"""
        val calls = dispatcher.parseToolCalls(json)
        assertEquals(2, calls.size)
        assertEquals("tool_a", calls[0].name)
        assertEquals("tool_b", calls[1].name)
    }

    @Test
    fun `parseToolCalls with invalid JSON returns empty`() {
        val calls = dispatcher.parseToolCalls("not json at all")
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `parseToolCalls with empty array`() {
        val calls = dispatcher.parseToolCalls("[]")
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `parseToolCalls with missing arguments defaults to empty map`() {
        val json = """[{"name":"tool_x"}]"""
        val calls = dispatcher.parseToolCalls(json)
        assertEquals(1, calls.size)
        assertTrue(calls[0].arguments.isEmpty())
    }
}
