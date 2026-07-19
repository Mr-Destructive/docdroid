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

        assertTrue("pdf_merge_split should exist", names.contains("pdf_merge_split"))
        assertTrue("pdf_pages should exist", names.contains("pdf_pages"))
        assertTrue("pdf_watermark should exist", names.contains("pdf_watermark"))
        assertTrue("pdf_extract should exist", names.contains("pdf_extract"))
        assertTrue("pdf_security should exist", names.contains("pdf_security"))
        assertTrue("pdf_forms should exist", names.contains("pdf_forms"))
        assertTrue("pdf_convert should exist", names.contains("pdf_convert"))
        assertTrue("pdf_info should exist", names.contains("pdf_info"))
        assertTrue("image_transform should exist", names.contains("image_transform"))
        assertTrue("image_adjust should exist", names.contains("image_adjust"))
        assertTrue("image_filter should exist", names.contains("image_filter"))
        assertTrue("image_overlay should exist", names.contains("image_overlay"))
        assertTrue("image_convert should exist", names.contains("image_convert"))
        assertTrue("image_batch should exist", names.contains("image_batch"))
        assertTrue("image_create_qr should exist", names.contains("image_create_qr"))
        assertTrue("text_read_write should exist", names.contains("text_read_write"))
        assertTrue("text_edit should exist", names.contains("text_edit"))
        assertTrue("docx_operation should exist", names.contains("docx_operation"))
        assertTrue("markdown_to_pdf should exist", names.contains("markdown_to_pdf"))
        assertTrue("spreadsheet_operation should exist", names.contains("spreadsheet_operation"))
        assertTrue("presentation_operation should exist", names.contains("presentation_operation"))
        assertTrue("audio_operation should exist", names.contains("audio_operation"))
        assertTrue("video_operation should exist", names.contains("video_operation"))
        assertTrue("archive_operation should exist", names.contains("archive_operation"))
        assertTrue("ocr_extract_text should exist", names.contains("ocr_extract_text"))
        assertTrue("get_file_info should exist", names.contains("get_file_info"))
        assertTrue("compare_files should exist", names.contains("compare_files"))
        assertTrue("execute_python should exist", names.contains("execute_python"))
    }

    @Test
    fun `consolidated tool count is manageable for small model`() {
        val defs = getAllToolDefinitions()
        assertTrue("Expected ~23-30 consolidated tools, got ${defs.size}", defs.size in 20..35)
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
        val toolsWithOperation = defs.filter { it.parameters.containsKey("operation") }
        assertTrue("Expected multiple tools with 'operation' param, got ${toolsWithOperation.size}",
            toolsWithOperation.size >= 15)
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
