package com.docdroid.agent

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ToolDefinitionsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `getAllToolDefinitions returns non-empty list`() {
        val defs = getAllToolDefinitions()
        assertTrue("Expected >0 tool definitions, got ${defs.size}", defs.isNotEmpty())
    }

    @Test
    fun `all tools have non-empty names`() {
        val defs = getAllToolDefinitions()
        defs.forEach { tool ->
            assertTrue("Tool name should not be blank: '${tool.name}'", tool.name.isNotBlank())
        }
    }

    @Test
    fun `all tools have non-empty descriptions`() {
        val defs = getAllToolDefinitions()
        defs.forEach { tool ->
            assertTrue("Tool '${tool.name}' should have description", tool.description.isNotBlank())
        }
    }

    @Test
    fun `all tools have non-empty parameters`() {
        val defs = getAllToolDefinitions()
        defs.forEach { tool ->
            assertTrue("Tool '${tool.name}' should have parameters", tool.parameters.isNotEmpty())
        }
    }

    @Test
    fun `consolidated tools include expected names`() {
        val defs = getAllToolDefinitions()
        val names = defs.map { it.name }.toSet()
        val expected = setOf(
            "pdf_edit", "pdf_pages", "pdf_watermark", "pdf_extract", "pdf_security",
            "pdf_forms", "pdf_convert", "pdf_info",
            "image_edit", "image_overlay", "image_convert", "image_qr",
            "text_file", "docx", "markdown_to_pdf",
            "spreadsheet", "presentation",
            "audio", "video", "archive",
            "ocr", "file_info", "execute_python"
        )
        expected.forEach { name ->
            assertTrue("$name should exist", names.contains(name))
        }
    }

    @Test
    fun `tool count fits 26M model budget`() {
        val defs = getAllToolDefinitions()
        assertTrue("Expected 20-25 tools for 26M model, got ${defs.size}", defs.size in 20..25)
    }

    @Test
    fun `buildToolsJson produces valid JSON array`() {
        val jsonStr = buildToolsJson()
        val arr = json.parseToJsonElement(jsonStr) as JsonArray
        assertTrue("Should have at least 1 tool in JSON", arr.isNotEmpty())
    }

    @Test
    fun `buildToolsJson each entry has name, description, parameters`() {
        val jsonStr = buildToolsJson()
        val arr = json.parseToJsonElement(jsonStr) as JsonArray
        for (i in arr.indices) {
            val obj = arr[i] as JsonObject
            assertTrue("Tool $i should have 'name'", obj.containsKey("name"))
            assertTrue("Tool $i should have 'description'", obj.containsKey("description"))
            assertTrue("Tool $i should have 'parameters'", obj.containsKey("parameters"))
            assertTrue("Tool $i name should be non-empty",
                (obj["name"] as JsonPrimitive).content.isNotEmpty())
        }
    }

    @Test
    fun `buildToolsJson parameter entries have type and description`() {
        val jsonStr = buildToolsJson()
        val arr = json.parseToJsonElement(jsonStr) as JsonArray
        val firstTool = arr[0] as JsonObject
        val params = firstTool["parameters"] as JsonObject
        for ((key, value) in params) {
            val param = value as JsonObject
            assertTrue("Param '$key' should have 'type'", param.containsKey("type"))
            assertTrue("Param '$key' should have 'description'", param.containsKey("description"))
        }
    }

    @Test
    fun `ToolCall data class works correctly`() {
        val call = ToolCall(name = "test", arguments = mapOf("k" to "v"))
        assertEquals("test", call.name)
        assertEquals("v", call.arguments["k"])
    }

    @Test
    fun `ToolCall default arguments is empty map`() {
        val call = ToolCall(name = "test")
        assertTrue(call.arguments.isEmpty())
    }

    @Test
    fun `consolidated tools have operation parameter where applicable`() {
        val defs = getAllToolDefinitions()
        val toolsWithOp = defs.filter { it.parameters.containsKey("operation") }
        assertTrue("Expected >=15 tools with operation param, got ${toolsWithOp.size}",
            toolsWithOp.size >= 15)
    }

    @Test
    fun `every tool definition roundtrips through serialization`() {
        val defs = getAllToolDefinitions()
        defs.forEach { tool ->
            val serialized = Json.encodeToJsonElement(tool)
            val deserialized = Json.decodeFromJsonElement<ToolDefinition>(serialized)
            assertEquals(tool.name, deserialized.name)
            assertEquals(tool.description, deserialized.description)
            assertEquals(tool.parameters.size, deserialized.parameters.size)
        }
    }

    @Test
    fun `tool JSON is compact enough for 26M model`() {
        val jsonStr = buildToolsJson()
        val estTokens = jsonStr.length / 4
        assertTrue("Tool JSON ~$estTokens tokens, should be under 3000", estTokens < 3000)
    }
}
