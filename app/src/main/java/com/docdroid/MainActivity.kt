package com.docdroid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.docdroid.agent.*
import com.docdroid.data.ChatRepository
import com.docdroid.data.FileStore
import com.docdroid.model.DocumentFile
import com.docdroid.python.PythonBridge
import com.docdroid.ui.screens.ChatScreen
import com.docdroid.ui.theme.DocDroidTheme

class MainActivity : ComponentActivity() {

    private lateinit var agentLoop: AgentLoop
    private lateinit var repository: ChatRepository
    private lateinit var fileStore: FileStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DocDroidApp
        fileStore = app.fileStore
        repository = ChatRepository()

        PythonBridge.init(this)

        val needleAgent = NeedleAgent()
        val dispatcher = ToolDispatcher(app.toolRegistry)
        val pythonCodeGenerator = PythonCodeGenerator()

        agentLoop = AgentLoop(needleAgent, dispatcher, pythonCodeGenerator)

        registerToolHandlers(app.toolRegistry)

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
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
                val file = uriToFile(uri)
                if (file != null) {
                    repository.addSystemMessage("Shared file: ${file.name}")
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: return
                uris.forEach { uri ->
                    uriToFile(uri)
                }
                repository.addSystemMessage("Shared ${uris.size} files")
            }
        }
    }

    private fun uriToFile(uri: Uri): DocumentFile? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            val name = cursor?.use {
                it.moveToFirst()
                it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME).let { idx ->
                    it.getString(idx)
                }
            } ?: uri.lastPathSegment ?: "unknown"
            val size = cursor?.use {
                it.moveToFirst()
                it.getColumnIndexOrThrow(android.provider.OpenableColumns.SIZE).let { idx ->
                    it.getLong(idx)
                }
            } ?: 0L
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

            val copiedFile = fileStore.copyUriToFile(uri, name)
            DocumentFile(
                name = name,
                path = copiedFile.absolutePath,
                mimeType = mimeType,
                size = size,
                uri = uri.toString()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun handlePickedFiles(files: List<DocumentFile>) {
        files.forEach { file ->
            if (file.uri.isNotEmpty()) {
                try {
                    val uri = Uri.parse(file.uri)
                    val copiedFile = fileStore.copyUriToFile(uri, file.name)
                } catch (_: Exception) {}
            }
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
}
