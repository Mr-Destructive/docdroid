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
        // --- PDF ---
        registry.register("pdf_edit") { args ->
            val op = args["operation"] ?: return@register err("pdf_edit", "No operation")
            when (op) {
                "merge" -> PythonBridge.executePdfTool("merge_pdfs", mapOf(
                    "input_path" to (args["input_paths"] ?: ""),
                    "output_path" to (args["output_path"] ?: "")
                ))
                "split" -> PythonBridge.executePdfTool("split_pdf", args)
                "crop" -> PythonBridge.executePdfTool("crop_pdf", args)
                "resize" -> PythonBridge.executePdfTool("resize_pdf", args)
                else -> err("pdf_edit", "Unknown: $op")
            }
        }
        registry.register("pdf_pages") { args ->
            val op = args["operation"] ?: return@register err("pdf_pages", "No operation")
            when (op) {
                "extract" -> PythonBridge.executePdfTool("extract_pages", args)
                "delete" -> PythonBridge.executePdfTool("delete_pages", args)
                "reorder" -> PythonBridge.executePdfTool("reorder_pages", args)
                "rotate" -> PythonBridge.executePdfTool("rotate_pages", args)
                else -> err("pdf_pages", "Unknown: $op")
            }
        }
        registry.register("pdf_watermark") { args ->
            val op = args["operation"] ?: return@register err("pdf_watermark", "No operation")
            when (op) {
                "text" -> PythonBridge.executePdfTool("add_watermark_text", args)
                "image" -> PythonBridge.executePdfTool("add_watermark_image", args)
                "page_numbers" -> PythonBridge.executePdfTool("add_page_numbers", args)
                "header_footer" -> PythonBridge.executePdfTool("add_header_footer", args)
                else -> err("pdf_watermark", "Unknown: $op")
            }
        }
        registry.register("pdf_extract") { args ->
            val op = args["operation"] ?: return@register err("pdf_extract", "No operation")
            when (op) {
                "text" -> PythonBridge.executePdfTool("extract_text", args)
                "images" -> PythonBridge.executePdfTool("extract_images", args)
                "tables" -> PythonBridge.executePdfTool("extract_tables", args)
                "metadata" -> PythonBridge.executePdfTool("extract_metadata", args)
                else -> err("pdf_extract", "Unknown: $op")
            }
        }
        registry.register("pdf_security") { args ->
            val op = args["operation"] ?: return@register err("pdf_security", "No operation")
            when (op) {
                "encrypt" -> PythonBridge.executePdfTool("encrypt_pdf", args)
                "decrypt" -> PythonBridge.executePdfTool("decrypt_pdf", args)
                "flatten" -> PythonBridge.executePdfTool("flatten_pdf", args)
                else -> err("pdf_security", "Unknown: $op")
            }
        }
        registry.register("pdf_forms") { args ->
            val op = args["operation"] ?: return@register err("pdf_forms", "No operation")
            when (op) {
                "fill" -> PythonBridge.executePdfTool("fill_form", args)
                "create" -> PythonBridge.executePdfTool("create_form", args)
                else -> err("pdf_forms", "Unknown: $op")
            }
        }
        registry.register("pdf_convert") { args ->
            val op = args["operation"] ?: return@register err("pdf_convert", "No operation")
            when (op) {
                "to_images" -> PythonBridge.executePdfTool("pdf_to_images", args)
                "from_images" -> PythonBridge.executePdfTool("images_to_pdf", mapOf(
                    "input_path" to (args["input_paths"] ?: ""),
                    "output_path" to (args["output_path"] ?: "")
                ))
                "from_html" -> PythonBridge.executePdfTool("html_to_pdf", args)
                "from_text" -> PythonBridge.executePdfTool("text_to_pdf", args)
                "overlay" -> PythonBridge.executePdfTool("overlay_pdfs", args)
                else -> err("pdf_convert", "Unknown: $op")
            }
        }
        registry.register("pdf_info") { args ->
            PythonBridge.executePdfTool("get_pdf_info", args)
        }

        // --- Image ---
        registry.register("image_edit") { args ->
            val op = args["operation"] ?: return@register err("image_edit", "No operation")
            when (op) {
                "resize" -> PythonBridge.executeImageTool("resize_image", args)
                "crop" -> PythonBridge.executeImageTool("crop_image", args)
                "rotate" -> PythonBridge.executeImageTool("rotate_image", args)
                "flip" -> PythonBridge.executeImageTool("flip_image", args)
                "brightness" -> PythonBridge.executeImageTool("adjust_brightness", args)
                "contrast" -> PythonBridge.executeImageTool("adjust_contrast", args)
                "saturation" -> PythonBridge.executeImageTool("adjust_saturation", args)
                "grayscale" -> PythonBridge.executeImageTool("apply_image_filter", args + ("filter" to "grayscale"))
                "sepia" -> PythonBridge.executeImageTool("apply_image_filter", args + ("filter" to "sepia"))
                "blur" -> PythonBridge.executeImageTool("apply_image_filter", args + ("filter" to "blur"))
                "sharpen" -> PythonBridge.executeImageTool("apply_image_filter", args + ("filter" to "sharpen"))
                "invert" -> PythonBridge.executeImageTool("apply_image_filter", args + ("filter" to "invert"))
                else -> err("image_edit", "Unknown: $op")
            }
        }
        registry.register("image_overlay") { args ->
            val op = args["operation"] ?: return@register err("image_overlay", "No operation")
            when (op) {
                "text" -> PythonBridge.executeImageTool("add_text_overlay", args)
                "image" -> PythonBridge.executeImageTool("add_image_overlay", args)
                "watermark" -> PythonBridge.executeImageTool("add_watermark_image", args)
                "border" -> PythonBridge.executeImageTool("create_border", args)
                "thumbnail" -> PythonBridge.executeImageTool("generate_thumbnail", args)
                else -> err("image_overlay", "Unknown: $op")
            }
        }
        registry.register("image_convert") { args ->
            val op = args["operation"] ?: return@register err("image_convert", "No operation")
            when (op) {
                "convert" -> PythonBridge.executeImageTool("convert_image_format", args)
                "compress" -> PythonBridge.executeImageTool("compress_image", args)
                "metadata" -> PythonBridge.executeImageTool("get_image_metadata", args)
                "strip" -> PythonBridge.executeImageTool("strip_image_metadata", args)
                "dpi" -> PythonBridge.executeImageTool("change_dpi", args)
                "create" -> PythonBridge.executeImageTool("create_image", args)
                "batch_resize" -> PythonBridge.executeImageTool("batch_resize", args)
                "batch_convert" -> PythonBridge.executeImageTool("batch_convert_format", args)
                else -> err("image_convert", "Unknown: $op")
            }
        }
        registry.register("image_qr") { args ->
            PythonBridge.executeImageTool("generate_qr_code", args)
        }

        // --- Text ---
        registry.register("text_file") { args ->
            val op = args["operation"] ?: return@register err("text_file", "No operation")
            when (op) {
                "read" -> PythonBridge.executeTextTool("read_text_file", args)
                "create" -> PythonBridge.executeTextTool("create_text_file", args)
                "find_replace" -> PythonBridge.executeTextTool("find_replace_text", args)
                "word_count" -> PythonBridge.executeTextTool("word_count", args)
                else -> err("text_file", "Unknown: $op")
            }
        }
        registry.register("docx") { args ->
            val op = args["operation"] ?: return@register err("docx", "No operation")
            when (op) {
                "read" -> PythonBridge.executeTextTool("read_docx", args)
                "create" -> PythonBridge.executeTextTool("create_docx", args)
                "edit" -> PythonBridge.executeTextTool("edit_docx", args)
                "merge" -> PythonBridge.executeTextTool("merge_docx", mapOf(
                    "input_path" to (args["input_paths"] ?: ""),
                    "output_path" to (args["output_path"] ?: "")
                ))
                "to_pdf" -> PythonBridge.executeTextTool("docx_to_pdf", args)
                "images" -> PythonBridge.executeTextTool("extract_docx_images", args)
                else -> err("docx", "Unknown: $op")
            }
        }
        registry.register("markdown_to_pdf") { args ->
            PythonBridge.executeTextTool("markdown_to_pdf", args)
        }

        // --- Spreadsheet ---
        registry.register("spreadsheet") { args ->
            val op = args["operation"] ?: return@register err("spreadsheet", "No operation")
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
                else -> err("spreadsheet", "Unknown: $op")
            }
        }

        // --- Presentation ---
        registry.register("presentation") { args ->
            val op = args["operation"] ?: return@register err("presentation", "No operation")
            when (op) {
                "read" -> PythonBridge.executePresentationTool("read_presentation", args)
                "create" -> PythonBridge.executePresentationTool("create_presentation", args)
                "to_pdf" -> PythonBridge.executePresentationTool("presentation_to_pdf", args)
                "info" -> PythonBridge.executePresentationTool("get_presentation_info", args)
                else -> err("presentation", "Unknown: $op")
            }
        }

        // --- Audio ---
        registry.register("audio") { args ->
            val op = args["operation"] ?: return@register err("audio", "No operation")
            when (op) {
                "info" -> PythonBridge.executeAudioTool("get_audio_info", args)
                "trim" -> PythonBridge.executeAudioTool("trim_audio", args)
                "convert" -> PythonBridge.executeAudioTool("convert_audio_format", args)
                else -> err("audio", "Unknown: $op")
            }
        }

        // --- Video ---
        registry.register("video") { args ->
            val op = args["operation"] ?: return@register err("video", "No operation")
            when (op) {
                "info" -> PythonBridge.executeVideoTool("get_video_info", args)
                "trim" -> PythonBridge.executeVideoTool("trim_video", args)
                "extract_audio" -> PythonBridge.executeVideoTool("extract_audio_from_video", args)
                "to_gif" -> PythonBridge.executeVideoTool("video_to_gif", args)
                "thumbnail" -> PythonBridge.executeVideoTool("generate_video_thumbnail", args)
                else -> err("video", "Unknown: $op")
            }
        }

        // --- Archive ---
        registry.register("archive") { args ->
            val op = args["operation"] ?: return@register err("archive", "No operation")
            when (op) {
                "create" -> PythonBridge.executeArchiveTool("create_zip", mapOf(
                    "input_path" to (args["input_paths"] ?: ""),
                    "output_path" to (args["output_path"] ?: "")
                ))
                "extract" -> PythonBridge.executeArchiveTool("extract_zip", args)
                "list" -> PythonBridge.executeArchiveTool("list_archive_contents", args)
                else -> err("archive", "Unknown: $op")
            }
        }

        // --- OCR ---
        val ocrHelper = OcrHelper(this)
        registry.register("ocr") { args ->
            val path = args["input_path"] ?: return@register err("ocr", "No input_path")
            ocrHelper.extractTextFromFile(path)
        }

        // --- Utility ---
        registry.register("file_info") { args ->
            PythonBridge.executeImageTool("get_image_metadata", args)
        }
        registry.register("execute_python") { args ->
            val code = args["code"] ?: return@register err("execute_python", "No code")
            PythonBridge.executeArbitraryPython(code)
        }
    }

    private fun err(toolName: String, message: String): ToolResult {
        return ToolResult(toolName = toolName, status = ToolStatus.FAILED, error = message)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
