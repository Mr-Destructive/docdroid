package com.docdroid.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-level wrapper around the Cactus Needle model.
 * Handles model lifecycle, initialization, and tool-call generation.
 *
 * Usage:
 *   val engine = NeedleEngine(context)
 *   engine.init()
 *   val result = engine.route("Merge report.pdf and appendix.pdf")
 *   engine.destroy()
 */
class NeedleEngine(private val context: Context) {

    private var handle: Long = 0L
    private var initialized = false

    companion object {
        private const val BUFFER_SIZE = 65536
        private const val MODEL_ASSET_DIR = "needle"
    }

    /**
     * Copy the CQ4 model bundle from assets to internal storage,
     * then initialize the Cactus engine.
     */
    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        if (initialized) return@withContext true

        try {
            val modelDir = copyAssetDir(MODEL_ASSET_DIR, "needle_model")
            handle = CactusJNI.nativeInit(modelDir.absolutePath, null, false)
            if (handle == 0L) {
                return@withContext false
            }
            initialized = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Route a natural language request to the appropriate tool.
     * Returns the raw JSON function call from Needle.
     *
     * @param query User's natural language request
     * @param toolsJson JSON array of tool definitions
     * @return Raw JSON string: {"name":"tool_name","arguments":{...}}
     */
    suspend fun route(query: String, toolsJson: String): String? = withContext(Dispatchers.Default) {
        if (!initialized || handle == 0L) return@withContext null

        try {
            val messages = """[{"role":"user","content":${escapeJson(query)}}]"""
            val buffer = ByteArray(BUFFER_SIZE)

            CactusJNI.nativeComplete(
                handle = handle,
                messagesJson = messages,
                responseBuffer = buffer,
                optionsJson = null,
                toolsJson = toolsJson,
                callback = null,
                pcmData = null,
                pcmDataSize = 0
            )

            val response = String(buffer, 0, buffer.indexOf(0).coerceAtLeast(0))
            if (response.isBlank()) null else response.trim()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Free model resources.
     */
    fun destroy() {
        if (handle != 0L) {
            CactusJNI.nativeDestroy(handle)
            handle = 0L
        }
        initialized = false
    }

    fun isInitialized(): Boolean = initialized

    /**
     * Copy an asset directory to internal storage.
     * Returns the destination directory.
     */
    private fun copyAssetDir(assetName: String, destName: String): java.io.File {
        val destDir = java.io.File(context.filesDir, destName)
        if (destDir.exists()) return destDir
        destDir.mkdirs()

        val assetManager = context.assets
        val files = assetManager.list(assetName) ?: emptyArray()

        if (files.isEmpty()) {
            // It's a file, copy it
            val destFile = java.io.File(destDir, assetName.substringAfterLast('/'))
            if (!destFile.exists()) {
                assetManager.open(assetName).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } else {
            // It's a directory, recurse
            for (file in files) {
                copyAssetDir("$assetName/$file", "$destName/$file")
            }
        }

        return destDir
    }

    private fun escapeJson(s: String): String {
        return "\"${s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
    }
}
