package com.docdroid.harness

import android.content.Context
import com.docdroid.harness.tools.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Central dispatcher that routes Needle's JSON output to the correct tool.
 * The harness: parses JSON → looks up tool → executes → returns result.
 */
class ToolRegistry(private val context: Context) {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    private val tools: Map<String, Tool> = mapOf(
        "merge_pdfs" to PdfMerge(context),
        "split_pdf" to PdfSplit(context),
        "rotate_pdf" to PdfRotate(context),
        "extract_text" to PdfTextExtract(context),
        "extract_images" to PdfImageExtract(context),
        "compress_pdf" to PdfCompress(context),
        "add_watermark" to PdfWatermark(context),
        "images_to_pdf" to ImagesToPdf(context),
    )

    /**
     * Execute a tool call from Needle's JSON output.
     *
     * @param toolCallJson Raw JSON from Needle, e.g. {"name":"merge_pdfs","arguments":{...}}
     * @return ToolResult with success message or error
     */
    suspend fun execute(toolCallJson: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            val node = mapper.readTree(toolCallJson)

            // Handle both single object and array responses
            val toolNode = when {
                node.isArray && node.size() > 0 -> node[0]
                node.isObject -> node
                else -> return@withContext ToolResult.Error(
                    "Could not parse tool call: $toolCallJson"
                )
            }

            val toolName = toolNode.get("name")?.asText()
                ?: return@withContext ToolResult.Error("Tool call missing 'name' field")

            val arguments = toolNode.get("arguments")?.toString() ?: "{}"

            val tool = tools[toolName]
                ?: return@withContext ToolResult.Error(
                    "Unknown tool: $toolName",
                    toolName
                )

            tool.execute(arguments)
        } catch (e: Exception) {
            ToolResult.Error("Failed to execute tool: ${e.message}")
        }
    }

    /**
     * Get all registered tool names.
     */
    fun listTools(): List<String> = tools.keys.toList()
}

/**
 * Interface that all tool implementations must satisfy.
 */
interface Tool {
    /**
     * Execute the tool with the given JSON arguments string.
     * @param argsJson JSON string of arguments from Needle
     * @return ToolResult
     */
    suspend fun execute(argsJson: String): ToolResult
}
