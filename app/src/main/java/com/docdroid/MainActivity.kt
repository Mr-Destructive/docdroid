package com.docdroid

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.docdroid.agent.*
import com.docdroid.data.ChatRepository
import com.docdroid.data.FileStore
import com.docdroid.model.DocumentFile
import com.docdroid.python.PythonBridge
import com.docdroid.ui.screens.ChatScreen
import com.docdroid.ui.theme.DocDroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var agentLoop: AgentLoop
    private lateinit var repository: ChatRepository
    private lateinit var fileStore: FileStore
    private lateinit var needleAgent: NeedleAgent
    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DocDroidApp
        fileStore = app.fileStore
        repository = ChatRepository()

        PythonBridge.init(this)

        needleAgent = NeedleAgent()
        val dispatcher = ToolDispatcher(app.toolRegistry)
        val pythonCodeGenerator = PythonCodeGenerator()

        agentLoop = AgentLoop(needleAgent, dispatcher, pythonCodeGenerator)

        registerToolHandlers(app.toolRegistry)

        activityScope.launch {
            val success = needleAgent.initFromAssets(this@MainActivity)
            if (success) {
                Log.i(TAG, "Needle model initialized successfully")
            } else {
                Log.w(TAG, "Needle model init failed: ${needleAgent.getInitError()}")
                repository.addSystemMessage(
                    "Note: AI model could not be loaded. " +
                    "Check that the model file is included in the APK. " +
                    "Error: ${needleAgent.getInitError()}"
                )
            }
        }

        handleIncomingIntent(intent)

        setContent {
            DocDroidTheme {
                ChatScreen(
                    agentLoop = agentLoop,
                    repository = repository,
                    onFilePicked = ::handlePickedFiles
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                val file = uriToFile(uri)
                if (file != null) {
                    repository.addSystemMessage("Received file: ${file.name}")
                }
            }
            Intent.ACTION_SEND -> {
                val uri = getUriExtraCompat(intent, Intent.EXTRA_STREAM) ?: return
                val file = uriToFile(uri)
                if (file != null) {
                    repository.addSystemMessage("Shared file: ${file.name}")
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = getUriArrayListExtraCompat(intent, Intent.EXTRA_STREAM) ?: return
                uris.forEach { uri ->
                    uriToFile(uri)
                }
                repository.addSystemMessage("Shared ${uris.size} files")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getUriExtraCompat(intent: Intent, key: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Uri::class.java)
        } else {
            intent.getParcelableExtra(key)
        }
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    private fun getUriArrayListExtraCompat(intent: Intent, key: String): ArrayList<Uri>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableArrayListExtra(key, Uri::class.java)
        }
        return (intent.getParcelableArrayListExtra<android.os.Parcelable>(key) as? ArrayList<Uri>)
    }

    private fun uriToFile(uri: Uri): DocumentFile? {
        return try {
            var name = uri.lastPathSegment ?: "unknown"
            var size = 0L
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        name = cursor.getString(nameIdx) ?: name
                    }
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIdx >= 0) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }

            val copiedFile = fileStore.copyUriToFile(uri, name)
            DocumentFile(
                name = name,
                path = copiedFile.absolutePath,
                mimeType = mimeType,
                size = size,
                uri = uri.toString()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve file from URI: ${e.message}")
            null
        }
    }

    private fun handlePickedFiles(files: List<DocumentFile>) {
        files.forEach { file ->
            Log.d(TAG, "Picked file: ${file.name} -> ${file.path}")
        }
    }

    private fun registerToolHandlers(registry: com.docdroid.agent.ToolRegistry) {
        val pdfTools = listOf(
            "merge_pdfs", "split_pdf", "extract_pages", "delete_pages",
            "reorder_pages", "rotate_pages", "add_watermark_text",
            "add_page_numbers", "extract_text", "extract_text_with_positions",
            "extract_images", "extract_tables", "compress_pdf",
            "encrypt_pdf", "decrypt_pdf", "fill_form", "add_bookmarks",
            "extract_metadata", "set_metadata", "get_pdf_info",
            "crop_pdf", "resize_pdf", "add_header_footer",
            "overlay_pdfs", "flatten_pdf", "images_to_pdf",
            "text_to_pdf", "create_form", "add_watermark_image"
        )
        pdfTools.forEach { tool ->
            registry.register(tool) { args -> PythonBridge.executePdfTool(tool, args) }
        }

        val imageTools = listOf(
            "resize_image", "crop_image", "rotate_image", "flip_image",
            "convert_image_format", "compress_image", "get_image_metadata",
            "strip_image_metadata", "adjust_brightness", "adjust_contrast",
            "adjust_saturation", "apply_image_filter", "add_text_overlay",
            "add_image_overlay", "add_watermark_image", "generate_thumbnail",
            "auto_enhance", "create_border", "change_dpi", "create_image",
            "create_collage", "batch_resize", "batch_convert_format",
            "draw_shapes", "generate_qr_code", "color_space_convert",
            "label_image", "detect_faces", "remove_background", "batch_watermark"
        )
        imageTools.forEach { tool ->
            registry.register(tool) { args -> PythonBridge.executeImageTool(tool, args) }
        }

        val textTools = listOf(
            "read_text_file", "create_text_file", "find_replace_text",
            "word_count", "read_docx", "create_docx", "edit_docx",
            "docx_to_pdf", "extract_docx_images", "merge_docx",
            "markdown_to_pdf", "html_to_text"
        )
        textTools.forEach { tool ->
            registry.register(tool) { args -> PythonBridge.executeTextTool(tool, args) }
        }

        val spreadsheetTools = listOf(
            "read_spreadsheet", "create_spreadsheet", "edit_cell",
            "csv_to_xlsx", "spreadsheet_to_pdf", "merge_spreadsheets",
            "sort_spreadsheet"
        )
        spreadsheetTools.forEach { tool ->
            registry.register(tool) { args -> PythonBridge.executeSpreadsheetTool(tool, args) }
        }

        val presentationTools = listOf(
            "read_presentation", "create_presentation",
            "presentation_to_pdf", "get_presentation_info"
        )
        presentationTools.forEach { tool ->
            registry.register(tool) { args -> PythonBridge.executePresentationTool(tool, args) }
        }

        val archiveTools = listOf(
            "create_zip", "extract_zip", "list_archive_contents"
        )
        archiveTools.forEach { tool ->
            registry.register(tool) { args -> PythonBridge.executeArchiveTool(tool, args) }
        }

        registry.register("execute_python") { args ->
            val code = args["code"] ?: return@register com.docdroid.model.ToolResult(
                toolName = "execute_python",
                status = com.docdroid.model.ToolStatus.FAILED,
                error = "No code provided"
            )
            PythonBridge.executeArbitraryPython(code)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
