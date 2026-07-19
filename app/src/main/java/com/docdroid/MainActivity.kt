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
import com.docdroid.model.ToolResult
import com.docdroid.model.ToolStatus
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
        repository = ChatRepository(this)

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

    private fun registerToolHandlers(registry: ToolRegistry) {
        // --- PDF consolidated tools ---
        registry.register("pdf_merge_split") { args ->
            val op = args["operation"] ?: return@register toolError("pdf_merge_split", "No operation")
            when (op) {
                "merge" -> PythonBridge.executePdfTool("merge_pdfs", mapOf(
                    "input_path" to (args["input_paths"] ?: ""),
                    "output_path" to (args["output_path"] ?: "")
                ))
                "split" -> PythonBridge.executePdfTool("split_pdf", args)
                else -> toolError("pdf_merge_split", "Unknown operation: $op")
            }
        }

        registry.register("pdf_pages") { args ->
            val op = args["operation"] ?: return@register toolError("pdf_pages", "No operation")
            when (op) {
                "extract" -> PythonBridge.executePdfTool("extract_pages", args)
                "delete" -> PythonBridge.executePdfTool("delete_pages", args)
                "reorder" -> PythonBridge.executePdfTool("reorder_pages", args)
                "rotate" -> PythonBridge.executePdfTool("rotate_pages", args)
                else -> toolError("pdf_pages", "Unknown operation: $op")
            }
        }

        registry.register("pdf_crop_resize") { args ->
            val op = args["operation"] ?: return@register toolError("pdf_crop_resize", "No operation")
            when (op) {
                "crop" -> PythonBridge.executePdfTool("crop_pdf", args)
                "resize" -> PythonBridge.executePdfTool("resize_pdf", args)
                else -> toolError("pdf_crop_resize", "Unknown operation: $op")
            }
        }

        registry.register("pdf_watermark") { args ->
            val op = args["operation"] ?: return@register toolError("pdf_watermark", "No operation")
            when (op) {
                "text_watermark" -> PythonBridge.executePdfTool("add_watermark_text", args)
                "image_watermark" -> PythonBridge.executePdfTool("add_watermark_image", args)
                "page_numbers" -> PythonBridge.executePdfTool("add_page_numbers", args)
                "header_footer" -> PythonBridge.executePdfTool("add_header_footer", args)
                else -> toolError("pdf_watermark", "Unknown operation: $op")
            }
        }

        registry.register("pdf_extract") { args ->
            val op = args["operation"] ?: return@register toolError("pdf_extract", "No operation")
            when (op) {
                "text" -> PythonBridge.executePdfTool("extract_text", args)
                "images" -> PythonBridge.executePdfTool("extract_images", args)
                "tables" -> PythonBridge.executePdfTool("extract_tables", args)
                "metadata" -> PythonBridge.executePdfTool("extract_metadata", args)
                else -> toolError("pdf_extract", "Unknown operation: $op")
            }
        }

        registry.register("pdf_security") { args ->
            val op = args["operation"] ?: return@register toolError("pdf_security", "No operation")
            when (op) {
                "encrypt" -> PythonBridge.executePdfTool("encrypt_pdf", args)
                "decrypt" -> PythonBridge.executePdfTool("decrypt_pdf", args)
                "flatten" -> PythonBridge.executePdfTool("flatten_pdf", args)
                else -> toolError("pdf_security", "Unknown operation: $op")
            }
        }

        registry.register("pdf_forms") { args ->
            val op = args["operation"] ?: return@register toolError("pdf_forms", "No operation")
            when (op) {
                "fill" -> PythonBridge.executePdfTool("fill_form", args)
                "create" -> PythonBridge.executePdfTool("create_form", args)
                else -> toolError("pdf_forms", "Unknown operation: $op")
            }
        }

        registry.register("pdf_convert") { args ->
            val op = args["operation"] ?: return@register toolError("pdf_convert", "No operation")
            when (op) {
                "to_images" -> PythonBridge.executePdfTool("pdf_to_images", args)
                "from_images" -> PythonBridge.executePdfTool("images_to_pdf", mapOf(
                    "input_path" to (args["input_paths"] ?: ""),
                    "output_path" to (args["output_path"] ?: "")
                ))
                "from_html" -> PythonBridge.executePdfTool("html_to_pdf", args)
                "from_text" -> PythonBridge.executePdfTool("text_to_pdf", args)
                "overlay" -> PythonBridge.executePdfTool("overlay_pdfs", args)
                else -> toolError("pdf_convert", "Unknown operation: $op")
            }
        }

        registry.register("pdf_info") { args ->
            PythonBridge.executePdfTool("get_pdf_info", args)
        }

        // --- Image consolidated tools ---
        registry.register("image_transform") { args ->
            val op = args["operation"] ?: return@register toolError("image_transform", "No operation")
            when (op) {
                "resize" -> PythonBridge.executeImageTool("resize_image", args)
                "crop" -> PythonBridge.executeImageTool("crop_image", args)
                "rotate" -> PythonBridge.executeImageTool("rotate_image", args)
                "flip" -> PythonBridge.executeImageTool("flip_image", args)
                else -> toolError("image_transform", "Unknown operation: $op")
            }
        }

        registry.register("image_adjust") { args ->
            val op = args["operation"] ?: return@register toolError("image_adjust", "No operation")
            when (op) {
                "brightness" -> PythonBridge.executeImageTool("adjust_brightness", args)
                "contrast" -> PythonBridge.executeImageTool("adjust_contrast", args)
                "saturation" -> PythonBridge.executeImageTool("adjust_saturation", args)
                else -> toolError("image_adjust", "Unknown operation: $op")
            }
        }

        registry.register("image_filter") { args ->
            PythonBridge.executeImageTool("apply_image_filter", args)
        }

        registry.register("image_overlay") { args ->
            val op = args["operation"] ?: return@register toolError("image_overlay", "No operation")
            when (op) {
                "text" -> PythonBridge.executeImageTool("add_text_overlay", args)
                "image" -> PythonBridge.executeImageTool("add_image_overlay", args)
                "watermark" -> PythonBridge.executeImageTool("add_watermark_image", args)
                "border" -> PythonBridge.executeImageTool("create_border", args)
                "thumbnail" -> PythonBridge.executeImageTool("generate_thumbnail", args)
                else -> toolError("image_overlay", "Unknown operation: $op")
            }
        }

        registry.register("image_convert") { args ->
            val op = args["operation"] ?: return@register toolError("image_convert", "No operation")
            when (op) {
                "convert_format" -> PythonBridge.executeImageTool("convert_image_format", args)
                "compress" -> PythonBridge.executeImageTool("compress_image", args)
                "metadata" -> PythonBridge.executeImageTool("get_image_metadata", args)
                "strip_metadata" -> PythonBridge.executeImageTool("strip_image_metadata", args)
                "change_dpi" -> PythonBridge.executeImageTool("change_dpi", args)
                "create" -> PythonBridge.executeImageTool("create_image", args)
                else -> toolError("image_convert", "Unknown operation: $op")
            }
        }

        registry.register("image_batch") { args ->
            val op = args["operation"] ?: return@register toolError("image_batch", "No operation")
            when (op) {
                "batch_resize" -> PythonBridge.executeImageTool("batch_resize", args)
                "batch_convert" -> PythonBridge.executeImageTool("batch_convert_format", args)
                else -> toolError("image_batch", "Unknown operation: $op")
            }
        }

        registry.register("image_create_qr") { args ->
            PythonBridge.executeImageTool("generate_qr_code", args)
        }

        // --- Text/Document consolidated tools ---
        registry.register("text_read_write") { args ->
            val op = args["operation"] ?: return@register toolError("text_read_write", "No operation")
            when (op) {
                "read" -> PythonBridge.executeTextTool("read_text_file", args)
                "create" -> PythonBridge.executeTextTool("create_text_file", args)
                else -> toolError("text_read_write", "Unknown operation: $op")
            }
        }

        registry.register("text_edit") { args ->
            val op = args["operation"] ?: return@register toolError("text_edit", "No operation")
            when (op) {
                "find_replace" -> PythonBridge.executeTextTool("find_replace_text", args)
                "word_count" -> PythonBridge.executeTextTool("word_count", args)
                else -> toolError("text_edit", "Unknown operation: $op")
            }
        }

        registry.register("docx_operation") { args ->
            val op = args["operation"] ?: return@register toolError("docx_operation", "No operation")
            when (op) {
                "read" -> PythonBridge.executeTextTool("read_docx", args)
                "create" -> PythonBridge.executeTextTool("create_docx", args)
                "edit" -> PythonBridge.executeTextTool("edit_docx", args)
                "merge" -> PythonBridge.executeTextTool("merge_docx", mapOf(
                    "input_path" to (args["input_paths"] ?: ""),
                    "output_path" to (args["output_path"] ?: "")
                ))
                "to_pdf" -> PythonBridge.executeTextTool("docx_to_pdf", args)
                "extract_images" -> PythonBridge.executeTextTool("extract_docx_images", args)
                else -> toolError("docx_operation", "Unknown operation: $op")
            }
        }

        registry.register("markdown_to_pdf") { args ->
            PythonBridge.executeTextTool("markdown_to_pdf", args)
        }

        // --- Spreadsheet consolidated tool ---
        registry.register("spreadsheet_operation") { args ->
            val op = args["operation"] ?: return@register toolError("spreadsheet_operation", "No operation")
            when (op) {
                "read" -> PythonBridge.executeSpreadsheetTool("read_spreadsheet", args)
                "create" -> PythonBridge.executeSpreadsheetTool("create_spreadsheet", args)
                "edit_cell" -> PythonBridge.executeSpreadsheetTool("edit_cell", args)
                "sort" -> PythonBridge.executeSpreadsheetTool("sort_spreadsheet", args)
                "merge" -> PythonBridge.executeSpreadsheetTool("merge_spreadsheets", mapOf(
                    "input_path" to (args["input_paths"] ?: ""),
                    "output_path" to (args["output_path"] ?: "")
                ))
                "csv_to_xlsx" -> PythonBridge.executeSpreadsheetTool("csv_to_xlsx", args)
                "to_pdf" -> PythonBridge.executeSpreadsheetTool("spreadsheet_to_pdf", args)
                else -> toolError("spreadsheet_operation", "Unknown operation: $op")
            }
        }

        // --- Presentation consolidated tool ---
        registry.register("presentation_operation") { args ->
            val op = args["operation"] ?: return@register toolError("presentation_operation", "No operation")
            when (op) {
                "read" -> PythonBridge.executePresentationTool("read_presentation", args)
                "create" -> PythonBridge.executePresentationTool("create_presentation", args)
                "to_pdf" -> PythonBridge.executePresentationTool("presentation_to_pdf", args)
                "info" -> PythonBridge.executePresentationTool("get_presentation_info", args)
                else -> toolError("presentation_operation", "Unknown operation: $op")
            }
        }

        // --- Audio consolidated tool ---
        registry.register("audio_operation") { args ->
            val op = args["operation"] ?: return@register toolError("audio_operation", "No operation")
            when (op) {
                "info" -> PythonBridge.executeAudioTool("get_audio_info", args)
                "trim" -> PythonBridge.executeAudioTool("trim_audio", args)
                "convert" -> PythonBridge.executeAudioTool("convert_audio_format", args)
                else -> toolError("audio_operation", "Unknown operation: $op")
            }
        }

        // --- Video consolidated tool ---
        registry.register("video_operation") { args ->
            val op = args["operation"] ?: return@register toolError("video_operation", "No operation")
            when (op) {
                "info" -> PythonBridge.executeVideoTool("get_video_info", args)
                "trim" -> PythonBridge.executeVideoTool("trim_video", args)
                "extract_audio" -> PythonBridge.executeVideoTool("extract_audio_from_video", args)
                "to_gif" -> PythonBridge.executeVideoTool("video_to_gif", args)
                "thumbnail" -> PythonBridge.executeVideoTool("generate_video_thumbnail", args)
                else -> toolError("video_operation", "Unknown operation: $op")
            }
        }

        // --- Archive consolidated tool ---
        registry.register("archive_operation") { args ->
            val op = args["operation"] ?: return@register toolError("archive_operation", "No operation")
            when (op) {
                "create" -> PythonBridge.executeArchiveTool("create_zip", mapOf(
                    "input_path" to (args["input_paths"] ?: ""),
                    "output_path" to (args["output_path"] ?: "")
                ))
                "extract" -> PythonBridge.executeArchiveTool("extract_zip", args)
                "list" -> PythonBridge.executeArchiveTool("list_archive_contents", args)
                else -> toolError("archive_operation", "Unknown operation: $op")
            }
        }

        // --- OCR tool (uses ML Kit) ---
        val ocrHelper = OcrHelper(this)
        registry.register("ocr_extract_text") { args ->
            val path = args["input_path"] ?: return@register toolError("ocr_extract_text", "No input_path")
            ocrHelper.extractTextFromFile(path)
        }

        // --- Utility tools ---
        registry.register("get_file_info") { args ->
            PythonBridge.executeImageTool("get_file_info", args)
        }

        registry.register("compare_files") { args ->
            PythonBridge.executeImageTool("compare_files", args)
        }

        registry.register("execute_python") { args ->
            val code = args["code"] ?: return@register toolError("execute_python", "No code provided")
            PythonBridge.executeArbitraryPython(code)
        }
    }

    private fun toolError(toolName: String, message: String): ToolResult {
        return ToolResult(
            toolName = toolName,
            status = ToolStatus.FAILED,
            error = message
        )
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
