package com.docdroid.agent

import android.content.Context
import com.cactus.cactusInit
import com.cactus.cactusComplete
import com.cactus.cactusDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

interface NeedleEngine {
    fun isInitialized(): Boolean
    suspend fun query(query: String, toolsJson: String): Result<List<ToolCall>>
    fun getInitError(): String? = null
}

class NeedleAgent : NeedleEngine {

    private var modelHandle: Long = 0L
    private var initialized = false
    private var initError: String? = null

    fun init(modelPath: String) {
        try {
            modelHandle = cactusInit(modelPath, null, false)
            initialized = true
            initError = null
            android.util.Log.i(TAG, "Model loaded from $modelPath")
        } catch (e: Exception) {
            initialized = false
            initError = e.message
            android.util.Log.e(TAG, "Failed to init Cactus: ${e.message}", e)
        }
    }

    suspend fun initFromAssets(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelDir = File(context.filesDir, "models/needle")
            modelDir.mkdirs()

            val rootFiles = context.assets.list("") ?: emptyArray()
            val needleDir = rootFiles.firstOrNull { it == "needle" }

            if (needleDir != null) {
                android.util.Log.i(TAG, "Found 'needle' directory in assets, extracting...")
                extractAssetDir(context, "needle", "needle", modelDir)
            } else {
                android.util.Log.i(TAG, "No 'needle' dir, looking for individual model files")
                val allAssets = mutableListOf<String>()
                listAssetFiles(context, "", allAssets)
                android.util.Log.i(TAG, "Found ${allAssets.size} asset files")

                if (allAssets.isEmpty()) {
                    initError = "No files found in APK assets"
                    return@withContext false
                }

                allAssets.forEach { assetPath ->
                    val destFile = File(modelDir, assetPath)
                    destFile.parentFile?.mkdirs()
                    if (!destFile.exists() || destFile.length() == 0L) {
                        context.assets.open(assetPath).use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }

            val modelFiles = modelDir.listFiles() ?: emptyArray()
            android.util.Log.i(TAG, "Model directory has ${modelFiles.size} files: ${modelFiles.map { it.name }.take(10)}")

            if (modelFiles.isEmpty()) {
                initError = "Model extraction produced empty directory"
                return@withContext false
            }

            android.util.Log.i(TAG, "Model directory ready: ${modelDir.absolutePath}")
            init(modelDir.absolutePath)
            initialized
        } catch (e: Exception) {
            initialized = false
            initError = "Asset extraction failed: ${e.message}"
            android.util.Log.e(TAG, "Failed to extract model from assets", e)
            false
        }
    }

    private fun extractAssetDir(context: Context, assetPath: String, rootAssetPath: String, destRoot: File) {
        val children = context.assets.list(assetPath) ?: emptyArray()

        if (children.isEmpty()) {
            val relPath = assetPath.removePrefix("$rootAssetPath/")
            val destFile = File(destRoot, relPath)
            destFile.parentFile?.mkdirs()
            if (!destFile.exists() || destFile.length() == 0L) {
                context.assets.open(assetPath).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } else {
            children.forEach { child ->
                extractAssetDir(context, "$assetPath/$child", rootAssetPath, destRoot)
            }
        }
    }

    private fun listAssetFiles(context: Context, path: String, result: MutableList<String>) {
        val children = context.assets.list(path) ?: emptyArray()
        if (children.isEmpty()) {
            result.add(path)
        } else {
            children.forEach { child ->
                listAssetFiles(context, if (path.isEmpty()) child else "$path/$child", result)
            }
        }
    }

    override fun isInitialized(): Boolean = initialized

    override fun getInitError(): String? = initError

    override suspend fun query(query: String, toolsJson: String): Result<List<ToolCall>> =
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

    companion object {
        private const val TAG = "NeedleAgent"
    }
}
