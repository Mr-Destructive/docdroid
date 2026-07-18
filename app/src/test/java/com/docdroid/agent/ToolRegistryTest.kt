package com.docdroid.agent

import com.docdroid.model.ToolResult
import com.docdroid.model.ToolStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ToolRegistryTest {

    private lateinit var registry: ToolRegistry

    @Before
    fun setup() {
        registry = ToolRegistry()
    }

    @Test
    fun `register and execute tool`() = runTest {
        registry.register("test_tool") { args ->
            ToolResult(
                toolName = "test_tool",
                status = ToolStatus.SUCCESS,
                result = "done: ${args["input"]}"
            )
        }

        assertTrue(registry.hasTool("test_tool"))
        val result = registry.execute("test_tool", mapOf("input" to "hello"))
        assertEquals(ToolStatus.SUCCESS, result.status)
        assertEquals("done: hello", result.result)
    }

    @Test
    fun `execute unknown tool returns FAILED`() = runTest {
        val result = registry.execute("nonexistent", emptyMap())
        assertEquals(ToolStatus.FAILED, result.status)
        assertTrue(result.error!!.contains("Unknown tool"))
    }

    @Test
    fun `handler exception returns FAILED`() = runTest {
        registry.register("crash_tool") { _ ->
            throw IllegalStateException("boom")
        }

        val result = registry.execute("crash_tool", emptyMap())
        assertEquals(ToolStatus.FAILED, result.status)
        assertTrue(result.error!!.contains("IllegalStateException"))
        assertTrue(result.error!!.contains("boom"))
    }

    @Test
    fun `hasTool returns false for unregistered`() {
        assertFalse(registry.hasTool("nope"))
    }

    @Test
    fun `registeredTools returns all registered names`() {
        registry.register("a") { ToolResult("a", ToolStatus.SUCCESS) }
        registry.register("b") { ToolResult("b", ToolStatus.SUCCESS) }
        registry.register("c") { ToolResult("c", ToolStatus.SUCCESS) }

        val tools = registry.registeredTools()
        assertEquals(setOf("a", "b", "c"), tools)
    }

    @Test
    fun `register overwrites existing handler`() = runTest {
        registry.register("dup") { ToolResult("dup", ToolStatus.SUCCESS, result = "first") }
        registry.register("dup") { ToolResult("dup", ToolStatus.SUCCESS, result = "second") }

        val result = registry.execute("dup", emptyMap())
        assertEquals("second", result.result)
    }
}
