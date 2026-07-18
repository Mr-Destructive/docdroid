package com.docdroid.python

import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.docdroid.model.ToolResult
import com.docdroid.model.ToolStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PythonBridge {

    private var python: Python? = null

    fun init(context: android.content.Context) {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        python = Python.getInstance()
    }

    fun isInitialized(): Boolean = python != null

    suspend fun executePdfTool(toolName: String, args: Map<String, String>): ToolResult =
        withContext(Dispatchers.IO) {
            callHandler("pdf_handler", toolName, args)
        }

    suspend fun executeImageTool(toolName: String, args: Map<String, String>): ToolResult =
        withContext(Dispatchers.IO) {
            callHandler("image_handler", toolName, args)
        }

    suspend fun executeTextTool(toolName: String, args: Map<String, String>): ToolResult =
        withContext(Dispatchers.IO) {
            callHandler("text_handler", toolName, args)
        }

    suspend fun executeSpreadsheetTool(toolName: String, args: Map<String, String>): ToolResult =
        withContext(Dispatchers.IO) {
            callHandler("spreadsheet_handler", toolName, args)
        }

    suspend fun executePresentationTool(toolName: String, args: Map<String, String>): ToolResult =
        withContext(Dispatchers.IO) {
            callHandler("presentation_handler", toolName, args)
        }

    suspend fun executeAudioTool(toolName: String, args: Map<String, String>): ToolResult =
        withContext(Dispatchers.IO) {
            callHandler("audio_handler", toolName, args)
        }

    suspend fun executeVideoTool(toolName: String, args: Map<String, String>): ToolResult =
        withContext(Dispatchers.IO) {
            callHandler("video_handler", toolName, args)
        }

    suspend fun executeArchiveTool(toolName: String, args: Map<String, String>): ToolResult =
        withContext(Dispatchers.IO) {
            callHandler("archive_handler", toolName, args)
        }

    suspend fun executeArbitraryPython(code: String): ToolResult =
        withContext(Dispatchers.IO) {
            try {
                val py = python ?: return@withContext ToolResult(
                    toolName = "execute_python",
                    status = ToolStatus.FAILED,
                    error = "Python not initialized"
                )
                val module = py.getModule("code_executor")
                val result = module.callAttr("run_code", code)
                ToolResult(
                    toolName = "execute_python",
                    status = ToolStatus.SUCCESS,
                    result = result.toString()
                )
            } catch (e: Exception) {
                Log.e("PythonBridge", "Python execution failed", e)
                ToolResult(
                    toolName = "execute_python",
                    status = ToolStatus.FAILED,
                    error = e.message ?: "Unknown Python error"
                )
            }
        }

    private fun callHandler(
        moduleName: String,
        toolName: String,
        args: Map<String, String>
    ): ToolResult {
        return try {
            val py = python ?: return ToolResult(
                toolName = toolName,
                status = ToolStatus.FAILED,
                error = "Python not initialized"
            )
            val module = py.getModule(moduleName)

            val argsJson = org.json.JSONObject(args as Map<*, *>).toString()
            val result = module.callAttr(toolName, argsJson)

            val resultStr = result.toString()
            if (resultStr.startsWith("ERROR:")) {
                ToolResult(
                    toolName = toolName,
                    status = ToolStatus.FAILED,
                    error = resultStr.removePrefix("ERROR: ")
                )
            } else {
                ToolResult(
                    toolName = toolName,
                    status = ToolStatus.SUCCESS,
                    result = resultStr
                )
            }
        } catch (e: Exception) {
            Log.e("PythonBridge", "Handler $moduleName.$toolName failed", e)
            ToolResult(
                toolName = toolName,
                status = ToolStatus.FAILED,
                error = "${e::class.simpleName}: ${e.message}"
            )
        }
    }
}
