package com.docdroid.agent

import com.cactus.cactusInit
import com.cactus.cactusComplete
import com.cactus.cactusDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class NeedleAgent {

    private var modelHandle: Long = 0L
    private var initialized = false

    fun init(modelPath: String) {
        try {
            modelHandle = cactusInit(modelPath, null, false)
            initialized = true
        } catch (e: Exception) {
            initialized = false
            android.util.Log.e(TAG, "Failed to init Cactus: ${e.message}")
        }
    }

    suspend fun query(query: String, toolsJson: String): Result<List<ToolCall>> =
        withContext(Dispatchers.IO) {
            if (!initialized) {
                return@withContext Result.failure(Exception("Needle model not initialized"))
            }
            try {
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", query)
                    })
                }

                val resultJson = cactusComplete(
                    modelHandle,
                    messages.toString(),
                    null,
                    toolsJson,
                    null
                )

                val toolCalls = parseResponse(resultJson)
                Result.success(toolCalls)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun parseResponse(responseJson: String): List<ToolCall> {
        return try {
            val trimmed = responseJson.trim()
            val json = if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                val obj = JSONObject(trimmed)
                val text = obj.optString("response", trimmed)
                if (text.startsWith("[")) {
                    JSONArray(text)
                } else {
                    return extractToolCallsFromText(text)
                }
            }

            val calls = mutableListOf<ToolCall>()
            for (i in 0 until json.length()) {
                val item = json.getJSONObject(i)
                val name = item.optString("name", "")
                if (name.isEmpty()) continue

                val args = mutableMapOf<String, String>()
                val argsObj = item.optJSONObject("arguments") ?: item.optJSONObject("parameters")
                argsObj?.keys()?.forEach { key ->
                    args[key] = argsObj.optString(key, "")
                }
                calls.add(ToolCall(name = name, arguments = args))
            }
            calls
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Parse error: ${e.message}")
            emptyList()
        }
    }

    private fun extractToolCallsFromText(text: String): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        val pattern = Regex("""(\w+)\(([^)]*)\)""")
        pattern.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            val argsStr = match.groupValues[2]
            val args = mutableMapOf<String, String>()
            argsStr.split(",").forEach { arg ->
                val parts = arg.trim().split("=", limit = 2)
                if (parts.size == 2) {
                    args[parts[0].trim()] = parts[1].trim().removeSurrounding("\"").removeSurrounding("'")
                }
            }
            calls.add(ToolCall(name = name, arguments = args))
        }
        return calls
    }

    fun destroy() {
        if (initialized) {
            cactusDestroy(modelHandle)
            initialized = false
        }
    }

    fun isInitialized(): Boolean = initialized

    companion object {
        private const val TAG = "NeedleAgent"
    }
}
