package com.docdroid.agent

import com.docdroid.model.ToolResult
import com.docdroid.model.ToolStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ToolDispatcher(private val registry: ToolRegistry) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun dispatch(toolCall: ToolCall): ToolResult {
        return registry.execute(toolCall.name, toolCall.arguments)
    }

    fun parseToolCalls(responseJson: String): List<ToolCall> {
        return try {
            val trimmed = responseJson.trim()
            val arr = json.parseToJsonElement(trimmed).jsonArray
            arr.map { element ->
                val obj = element.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: ""
                val args = obj["arguments"]?.jsonObject?.map { (k, v) ->
                    k to v.jsonPrimitive.content
                }?.toMap() ?: emptyMap()
                ToolCall(name = name, arguments = args)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
