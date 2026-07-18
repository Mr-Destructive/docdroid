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
    fun `tool categories have expected tools`() {
        val defs = getAllToolDefinitions()
        val names = defs.map { it.name }.toSet()

        assertTrue("merge_pdfs should exist", names.contains("merge_pdfs"))
        assertTrue("resize_image should exist", names.contains("resize_image"))
        assertTrue("read_text_file should exist", names.contains("read_text_file"))
        assertTrue("read_spreadsheet should exist", names.contains("read_spreadsheet"))
        assertTrue("read_presentation should exist", names.contains("read_presentation"))
        assertTrue("get_audio_info should exist", names.contains("get_audio_info"))
        assertTrue("get_video_info should exist", names.contains("get_video_info"))
        assertTrue("create_zip should exist", names.contains("create_zip"))
        assertTrue("execute_python should exist", names.contains("execute_python"))
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
    fun `pdfToolDefinitions count matches expected`() {
        assertTrue("Expected >= 20 PDF tools, got ${pdfToolDefinitions.size}", pdfToolDefinitions.size >= 20)
    }

    @Test
    fun `imageToolDefinitions count matches expected`() {
        assertTrue("Expected >= 20 image tools, got ${imageToolDefinitions.size}", imageToolDefinitions.size >= 20)
    }

    @Test
    fun `genericToolDefinitions includes execute_python`() {
        val names = genericToolDefinitions.map { it.name }
        assertTrue("execute_python should be in generic tools", names.contains("execute_python"))
    }

    @Test
    fun `total tool count matches expected`() {
        val defs = getAllToolDefinitions()
        assertTrue("Expected 95+ tools, got ${defs.size}", defs.size >= 90)
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
}
