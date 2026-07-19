package com.docdroid.agent

import kotlinx.serialization.Serializable

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, ParameterDef>
)

@Serializable
data class ParameterDef(
    val type: String,
    val description: String,
    val required: Boolean = true,
    val enum: List<String>? = null
)

@Serializable
data class ToolCall(
    val name: String,
    val arguments: Map<String, String> = emptyMap()
)

fun buildToolsJson(): String {
    val tools = getAllToolDefinitions()
    return buildString {
        append("[")
        tools.forEachIndexed { i, tool ->
            if (i > 0) append(",")
            append("""{"name":"${tool.name}","description":${escapeJson(tool.description)},"parameters":{""")
            tool.parameters.entries.forEachIndexed { j, (key, param) ->
                if (j > 0) append(",")
                append(""""$key":{""")
                append(""""type":"${param.type}","description":${escapeJson(param.description)},"required":${param.required}""")
                if (param.enum != null) {
                    append(",\"enum\":[${param.enum.joinToString(",") { "\"$it\"" }}]")
                }
                append("}")
            }
            append("}")
        }
        append("]")
    }
}

private fun escapeJson(s: String): String =
    """"${s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")}""""

fun getAllToolDefinitions(): List<ToolDefinition> =
    consolidatedToolDefinitions

// ===== CONSOLIDATED TOOLS (23 total for 26M param model) =====
val consolidatedToolDefinitions = listOf(
    // --- PDF Operations ---
    ToolDefinition(
        name = "pdf_merge_split",
        description = "Merge multiple PDFs into one, or split a PDF by page ranges.",
        mapOf(
            "operation" to ParameterDef("string", "merge or split", enum = listOf("merge", "split")),
            "input_paths" to ParameterDef("string", "Comma-separated PDF paths (for merge)", required = false),
            "input_path" to ParameterDef("string", "Input PDF path (for split)", required = false),
            "page_ranges" to ParameterDef("string", "Page ranges e.g. '1-3,5' (for split)", required = false),
            "output_path" to ParameterDef("string", "Output file path")
        )
    ),
    ToolDefinition(
        name = "pdf_pages",
        description = "Extract, delete, reorder, or rotate pages in a PDF.",
        mapOf(
            "operation" to ParameterDef("string", "extract, delete, reorder, or rotate", enum = listOf("extract", "delete", "reorder", "rotate")),
            "input_path" to ParameterDef("string", "Input PDF path"),
            "pages" to ParameterDef("string", "Pages e.g. '1,3,5' or 'all' (required for extract/delete/rotate)", required = false),
            "new_order" to ParameterDef("string", "New page order e.g. '3,1,2' (for reorder)", required = false),
            "degrees" to ParameterDef("string", "Rotation degrees: 90, 180, or 270 (for rotate)", required = false),
            "output_path" to ParameterDef("string", "Output PDF path")
        )
    ),
    ToolDefinition(
        name = "pdf_crop_resize",
        description = "Crop margins or resize PDF to standard page sizes (a4, letter, legal).",
        mapOf(
            "operation" to ParameterDef("string", "crop or resize", enum = listOf("crop", "resize")),
            "input_path" to ParameterDef("string", "Input PDF path"),
            "left" to ParameterDef("string", "Left margin in pt (for crop)", required = false),
            "top" to ParameterDef("string", "Top margin in pt (for crop)", required = false),
            "right" to ParameterDef("string", "Right margin in pt (for crop)", required = false),
            "bottom" to ParameterDef("string", "Bottom margin in pt (for crop)", required = false),
            "size" to ParameterDef("string", "Target size: a4, letter, legal, a3, a5 (for resize)", required = false),
            "output_path" to ParameterDef("string", "Output PDF path")
        )
    ),
    ToolDefinition(
        name = "pdf_watermark",
        description = "Add text or image watermark, page numbers, or header/footer to a PDF.",
        mapOf(
            "operation" to ParameterDef("string", "text_watermark, image_watermark, page_numbers, header_footer", enum = listOf("text_watermark", "image_watermark", "page_numbers", "header_footer")),
            "input_path" to ParameterDef("string", "Input PDF path"),
            "text" to ParameterDef("string", "Watermark/header/footer text", required = false),
            "image_path" to ParameterDef("string", "Watermark image path (for image_watermark)", required = false),
            "font_size" to ParameterDef("string", "Font size (default 48)", required = false),
            "opacity" to ParameterDef("string", "0.0-1.0 (default 0.3)", required = false),
            "rotation" to ParameterDef("string", "Degrees (default -45)", required = false),
            "position" to ParameterDef("string", "bottom-center, bottom-right, top-center, top-right (for page_numbers)", required = false),
            "header" to ParameterDef("string", "Header text (for header_footer)", required = false),
            "footer" to ParameterDef("string", "Footer text (for header_footer)", required = false),
            "output_path" to ParameterDef("string", "Output PDF path")
        )
    ),
    ToolDefinition(
        name = "pdf_extract",
        description = "Extract text, images, tables, or metadata from a PDF.",
        mapOf(
            "operation" to ParameterDef("string", "text, images, tables, metadata", enum = listOf("text", "images", "tables", "metadata")),
            "input_path" to ParameterDef("string", "Input PDF path"),
            "pages" to ParameterDef("string", "Optional page range e.g. '1-5'", required = false),
            "output_dir" to ParameterDef("string", "Output directory (for images)", required = false)
        )
    ),
    ToolDefinition(
        name = "pdf_security",
        description = "Encrypt (password-protect), decrypt, or flatten a PDF.",
        mapOf(
            "operation" to ParameterDef("string", "encrypt, decrypt, flatten", enum = listOf("encrypt", "decrypt", "flatten")),
            "input_path" to ParameterDef("string", "Input PDF path"),
            "password" to ParameterDef("string", "Password (for encrypt/decrypt)", required = false),
            "user_password" to ParameterDef("string", "Open password (for encrypt)", required = false),
            "owner_password" to ParameterDef("string", "Owner password (for encrypt)", required = false),
            "output_path" to ParameterDef("string", "Output PDF path")
        )
    ),
    ToolDefinition(
        name = "pdf_forms",
        description = "Fill AcroForm fields or create fillable PDF forms.",
        mapOf(
            "operation" to ParameterDef("string", "fill, create", enum = listOf("fill", "create")),
            "input_path" to ParameterDef("string", "Input PDF path (for fill)", required = false),
            "fields" to ParameterDef("string", "JSON {field:value} for fill, or JSON array of fields for create"),
            "title" to ParameterDef("string", "Title (for create)", required = false),
            "output_path" to ParameterDef("string", "Output PDF path")
        )
    ),
    ToolDefinition(
        name = "pdf_convert",
        description = "Convert PDF to images, images to PDF, HTML to PDF, or text to PDF.",
        mapOf(
            "operation" to ParameterDef("string", "to_images, from_images, from_html, from_text, overlay", enum = listOf("to_images", "from_images", "from_html", "from_text", "overlay")),
            "input_path" to ParameterDef("string", "Input file path (for to_images/from_html/from_text)", required = false),
            "input_paths" to ParameterDef("string", "Comma-separated image paths (for from_images)", required = false),
            "base_path" to ParameterDef("string", "Base PDF (for overlay)", required = false),
            "overlay_path" to ParameterDef("string", "Overlay PDF (for overlay)", required = false),
            "format" to ParameterDef("string", "Output format: png or jpg (for to_images)", required = false),
            "dpi" to ParameterDef("string", "DPI (default 200, for to_images)", required = false),
            "font_size" to ParameterDef("string", "Font size (default 12, for from_text)", required = false),
            "output_path" to ParameterDef("string", "Output file path")
        )
    ),
    ToolDefinition(
        name = "pdf_info",
        description = "Get PDF summary: page count, file size, permissions, metadata.",
        mapOf(
            "input_path" to ParameterDef("string", "Input PDF path")
        )
    ),

    // --- Image Operations ---
    ToolDefinition(
        name = "image_transform",
        description = "Resize, crop, rotate, or flip an image.",
        mapOf(
            "operation" to ParameterDef("string", "resize, crop, rotate, flip", enum = listOf("resize", "crop", "rotate", "flip")),
            "input_path" to ParameterDef("string", "Input image path"),
            "width" to ParameterDef("string", "Width in px (for resize/crop)", required = false),
            "height" to ParameterDef("string", "Height in px (for resize/crop)", required = false),
            "x" to ParameterDef("string", "Left offset (for crop)", required = false),
            "y" to ParameterDef("string", "Top offset (for crop)", required = false),
            "degrees" to ParameterDef("string", "Degrees (for rotate)", required = false),
            "direction" to ParameterDef("string", "horizontal or vertical (for flip)", required = false),
            "output_path" to ParameterDef("string", "Output image path")
        )
    ),
    ToolDefinition(
        name = "image_adjust",
        description = "Adjust brightness, contrast, or saturation of an image.",
        mapOf(
            "operation" to ParameterDef("string", "brightness, contrast, saturation", enum = listOf("brightness", "contrast", "saturation")),
            "input_path" to ParameterDef("string", "Input image path"),
            "factor" to ParameterDef("string", "Adjustment factor (1.0 = no change)"),
            "output_path" to ParameterDef("string", "Output image path")
        )
    ),
    ToolDefinition(
        name = "image_filter",
        description = "Apply a filter: grayscale, sepia, invert, blur, sharpen, emboss, edge_detect.",
        mapOf(
            "filter" to ParameterDef("string", "Filter name", enum = listOf("grayscale", "sepia", "invert", "blur", "sharpen", "emboss", "edge_detect")),
            "input_path" to ParameterDef("string", "Input image path"),
            "output_path" to ParameterDef("string", "Output image path")
        )
    ),
    ToolDefinition(
        name = "image_overlay",
        description = "Add text overlay, image overlay, watermark, border, or thumbnail.",
        mapOf(
            "operation" to ParameterDef("string", "text, image, watermark, border, thumbnail", enum = listOf("text", "image", "watermark", "border", "thumbnail")),
            "input_path" to ParameterDef("string", "Input image path"),
            "overlay_path" to ParameterDef("string", "Overlay image path (for image)", required = false),
            "text" to ParameterDef("string", "Text to draw (for text/watermark)", required = false),
            "x" to ParameterDef("string", "X position (for text/image)", required = false),
            "y" to ParameterDef("string", "Y position (for text/image)", required = false),
            "font_size" to ParameterDef("string", "Font size (default 24)", required = false),
            "color" to ParameterDef("string", "Hex color (for text/border)", required = false),
            "opacity" to ParameterDef("string", "0.0-1.0 (for image/watermark)", required = false),
            "position" to ParameterDef("string", "center, top-left, etc (for watermark)", required = false),
            "border_size" to ParameterDef("string", "Border width in px (for border)", required = false),
            "max_size" to ParameterDef("string", "Max px (default 200, for thumbnail)", required = false),
            "output_path" to ParameterDef("string", "Output image path")
        )
    ),
    ToolDefinition(
        name = "image_convert",
        description = "Convert image format, compress, get metadata, strip metadata, change DPI, or create blank image.",
        mapOf(
            "operation" to ParameterDef("string", "convert_format, compress, metadata, strip_metadata, change_dpi, create", enum = listOf("convert_format", "compress", "metadata", "strip_metadata", "change_dpi", "create")),
            "input_path" to ParameterDef("string", "Input image path (not needed for create)", required = false),
            "format" to ParameterDef("string", "Target format (for convert_format)", required = false),
            "quality" to ParameterDef("string", "1-100 (default 60, for compress)", required = false),
            "dpi" to ParameterDef("string", "Target DPI (for change_dpi)", required = false),
            "width" to ParameterDef("string", "Width (for create)", required = false),
            "height" to ParameterDef("string", "Height (for create)", required = false),
            "color" to ParameterDef("string", "Hex color (for create)", required = false),
            "output_path" to ParameterDef("string", "Output image path (required except for metadata)")
        )
    ),
    ToolDefinition(
        name = "image_batch",
        description = "Batch resize or batch convert format for multiple images.",
        mapOf(
            "operation" to ParameterDef("string", "batch_resize, batch_convert", enum = listOf("batch_resize", "batch_convert")),
            "input_paths" to ParameterDef("string", "Comma-separated image paths"),
            "width" to ParameterDef("string", "Width (for batch_resize)", required = false),
            "height" to ParameterDef("string", "Height (for batch_resize)", required = false),
            "format" to ParameterDef("string", "Target format (for batch_convert)", required = false),
            "output_dir" to ParameterDef("string", "Output directory")
        )
    ),
    ToolDefinition(
        name = "image_create_qr",
        description = "Generate a QR code from text or URL.",
        mapOf(
            "content" to ParameterDef("string", "Text or URL to encode"),
            "size" to ParameterDef("string", "Size in px (default 300)"),
            "output_path" to ParameterDef("string", "Output image path")
        )
    ),

    // --- Text/Document Operations ---
    ToolDefinition(
        name = "text_read_write",
        description = "Read or create a plain text file.",
        mapOf(
            "operation" to ParameterDef("string", "read, create", enum = listOf("read", "create")),
            "input_path" to ParameterDef("string", "File path (for read)", required = false),
            "content" to ParameterDef("string", "Text content (for create)", required = false),
            "output_path" to ParameterDef("string", "Output path (for create)", required = false)
        )
    ),
    ToolDefinition(
        name = "text_edit",
        description = "Find and replace text in a file, or count words/characters/lines.",
        mapOf(
            "operation" to ParameterDef("string", "find_replace, word_count", enum = listOf("find_replace", "word_count")),
            "input_path" to ParameterDef("string", "Input file path"),
            "find" to ParameterDef("string", "Text to find (for find_replace)", required = false),
            "replace" to ParameterDef("string", "Replacement text (for find_replace)", required = false),
            "output_path" to ParameterDef("string", "Output path (for find_replace)", required = false)
        )
    ),
    ToolDefinition(
        name = "docx_operation",
        description = "Read, create, edit, merge Word documents, or convert DOCX to PDF.",
        mapOf(
            "operation" to ParameterDef("string", "read, create, edit, merge, to_pdf, extract_images", enum = listOf("read", "create", "edit", "merge", "to_pdf", "extract_images")),
            "input_path" to ParameterDef("string", "Input DOCX path (not needed for create)", required = false),
            "input_paths" to ParameterDef("string", "Comma-separated DOCX paths (for merge)", required = false),
            "content" to ParameterDef("string", "JSON content (for create/edit)", required = false),
            "edits" to ParameterDef("string", "JSON edits array (for edit)", required = false),
            "output_path" to ParameterDef("string", "Output file path"),
            "output_dir" to ParameterDef("string", "Output directory (for extract_images)", required = false)
        )
    ),
    ToolDefinition(
        name = "markdown_to_pdf",
        description = "Convert a Markdown file to PDF.",
        mapOf(
            "input_path" to ParameterDef("string", "Input Markdown file path"),
            "output_path" to ParameterDef("string", "Output PDF path")
        )
    ),

    // --- Spreadsheet Operations ---
    ToolDefinition(
        name = "spreadsheet_operation",
        description = "Read, create, edit cells, sort, merge, or convert spreadsheets.",
        mapOf(
            "operation" to ParameterDef("string", "read, create, edit_cell, sort, merge, csv_to_xlsx, to_pdf", enum = listOf("read", "create", "edit_cell", "sort", "merge", "csv_to_xlsx", "to_pdf")),
            "input_path" to ParameterDef("string", "Input spreadsheet path (not needed for create)", required = false),
            "input_paths" to ParameterDef("string", "Comma-separated paths (for merge)", required = false),
            "sheet" to ParameterDef("string", "Sheet name", required = false),
            "cell" to ParameterDef("string", "Cell e.g. A1 (for edit_cell)", required = false),
            "value" to ParameterDef("string", "Value (for edit_cell)", required = false),
            "column" to ParameterDef("string", "Column letter (for sort)", required = false),
            "ascending" to ParameterDef("string", "true/false (for sort)", required = false),
            "data" to ParameterDef("string", "JSON sheets data (for create)", required = false),
            "output_path" to ParameterDef("string", "Output file path")
        )
    ),

    // --- Presentation Operations ---
    ToolDefinition(
        name = "presentation_operation",
        description = "Read, create presentations, or convert PPTX to PDF.",
        mapOf(
            "operation" to ParameterDef("string", "read, create, to_pdf, info", enum = listOf("read", "create", "to_pdf", "info")),
            "input_path" to ParameterDef("string", "Input PPTX path (for read/to_pdf/info)", required = false),
            "slides" to ParameterDef("string", "JSON slides array (for create)", required = false),
            "output_path" to ParameterDef("string", "Output file path")
        )
    ),

    // --- Audio Operations ---
    ToolDefinition(
        name = "audio_operation",
        description = "Get audio info, trim audio, or convert audio format.",
        mapOf(
            "operation" to ParameterDef("string", "info, trim, convert", enum = listOf("info", "trim", "convert")),
            "input_path" to ParameterDef("string", "Input audio path"),
            "start" to ParameterDef("string", "Start time (for trim)", required = false),
            "end" to ParameterDef("string", "End time (for trim)", required = false),
            "format" to ParameterDef("string", "Target format: mp3, aac, wav, flac, ogg (for convert)", required = false),
            "output_path" to ParameterDef("string", "Output path (required for trim/convert)", required = false)
        )
    ),

    // --- Video Operations ---
    ToolDefinition(
        name = "video_operation",
        description = "Get video info, trim video, extract audio from video, convert to GIF, or extract thumbnail.",
        mapOf(
            "operation" to ParameterDef("string", "info, trim, extract_audio, to_gif, thumbnail", enum = listOf("info", "trim", "extract_audio", "to_gif", "thumbnail")),
            "input_path" to ParameterDef("string", "Input video path"),
            "start" to ParameterDef("string", "Start time (for trim)", required = false),
            "end" to ParameterDef("string", "End time (for trim)", required = false),
            "duration" to ParameterDef("string", "Duration in sec (for to_gif)", required = false),
            "time" to ParameterDef("string", "Timestamp (for thumbnail)", required = false),
            "format" to ParameterDef("string", "Audio format: mp3, aac, wav (for extract_audio)", required = false),
            "output_path" to ParameterDef("string", "Output file path")
        )
    ),

    // --- Archive Operations ---
    ToolDefinition(
        name = "archive_operation",
        description = "Create ZIP, extract ZIP, or list archive contents.",
        mapOf(
            "operation" to ParameterDef("string", "create, extract, list", enum = listOf("create", "extract", "list")),
            "input_path" to ParameterDef("string", "Archive path (for extract/list)", required = false),
            "input_paths" to ParameterDef("string", "Comma-separated file paths (for create)", required = false),
            "output_path" to ParameterDef("string", "Output ZIP path (for create)", required = false),
            "output_dir" to ParameterDef("string", "Output directory (for extract)", required = false)
        )
    ),

    // --- OCR Operations ---
    ToolDefinition(
        name = "ocr_extract_text",
        description = "Extract text from an image or PDF using optical character recognition (OCR).",
        mapOf(
            "input_path" to ParameterDef("string", "Input image or PDF path")
        )
    ),

    // --- Utility Operations ---
    ToolDefinition(
        name = "get_file_info",
        description = "Get file type, size, and MIME type for any file.",
        mapOf(
            "input_path" to ParameterDef("string", "File path")
        )
    ),
    ToolDefinition(
        name = "compare_files",
        description = "Compare two files and report differences.",
        mapOf(
            "path1" to ParameterDef("string", "First file path"),
            "path2" to ParameterDef("string", "Second file path")
        )
    ),
    ToolDefinition(
        name = "execute_python",
        description = "Run arbitrary Python code with access to Pillow, pypdf, reportlab, docx, openpyxl, pptx. Use as a last resort when other tools cannot accomplish the task.",
        mapOf(
            "code" to ParameterDef("string", "Python code to execute"),
            "input_files" to ParameterDef("string", "Available file paths (comma-separated)", required = false),
            "output_path" to ParameterDef("string", "Output path (if applicable)", required = false)
        )
    )
)
