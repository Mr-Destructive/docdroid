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
                    append(",""enum"":[${param.enum.joinToString(",") { """"$it"""" }}]")
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
    pdfToolDefinitions + imageToolDefinitions + textToolDefinitions +
            spreadsheetToolDefinitions + presentationToolDefinitions +
            audioToolDefinitions + videoToolDefinitions +
            archiveToolDefinitions + genericToolDefinitions

// ===== PDF TOOLS =====
val pdfToolDefinitions = listOf(
    ToolDefinition("merge_pdfs", "Merge multiple PDFs into one.", mapOf(
        "input_paths" to ParameterDef("string", "Comma-separated PDF paths"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("split_pdf", "Split a PDF by page ranges (e.g. '1-3,5').", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "page_ranges" to ParameterDef("string", "Page ranges"), "output_dir" to ParameterDef("string", "Output dir"))),
    ToolDefinition("extract_pages", "Extract specific pages from a PDF.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "pages" to ParameterDef("string", "Page numbers e.g. '1,3,5'"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("delete_pages", "Delete pages from a PDF.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "pages" to ParameterDef("string", "Pages to delete"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("reorder_pages", "Reorder pages in a PDF.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "new_order" to ParameterDef("string", "New order e.g. '3,1,2'"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("rotate_pages", "Rotate pages by 90/180/270 degrees.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "pages" to ParameterDef("string", "Pages or 'all'"), "degrees" to ParameterDef("string", "90, 180, or 270"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("crop_pdf", "Crop page margins.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "left" to ParameterDef("string", "Left margin pt"), "top" to ParameterDef("string", "Top margin pt"), "right" to ParameterDef("string", "Right margin pt"), "bottom" to ParameterDef("string", "Bottom margin pt"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("resize_pdf", "Resize PDF to a4/letter/legal.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "size" to ParameterDef("string", "a4, letter, legal, a3, a5"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("add_watermark_text", "Add text watermark to every page.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "text" to ParameterDef("string", "Watermark text"), "font_size" to ParameterDef("string", "Font size (default 48)"), "opacity" to ParameterDef("string", "0.0-1.0 (default 0.3)"), "rotation" to ParameterDef("string", "Degrees (default -45)"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("add_watermark_image", "Add image watermark to every page.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "image_path" to ParameterDef("string", "Watermark image"), "opacity" to ParameterDef("string", "0.0-1.0"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("add_page_numbers", "Add page numbers to PDF.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "position" to ParameterDef("string", "bottom-center, bottom-right, top-center, top-right"), "font_size" to ParameterDef("string", "Size (default 10)"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("add_header_footer", "Add header/footer text.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "header" to ParameterDef("string", "Header text"), "footer" to ParameterDef("string", "Footer text"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("extract_text", "Extract text from PDF.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "pages" to ParameterDef("string", "Optional pages e.g. '1-5'"))),
    ToolDefinition("extract_text_with_positions", "Extract text with x,y coordinates.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"))),
    ToolDefinition("extract_images", "Extract embedded images from PDF.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "output_dir" to ParameterDef("string", "Output dir"))),
    ToolDefinition("extract_tables", "Extract tables from PDF as CSV.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "pages" to ParameterDef("string", "Optional pages"))),
    ToolDefinition("compress_pdf", "Reduce PDF file size.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "quality" to ParameterDef("string", "1-100 (default 60)"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("encrypt_pdf", "Password-protect a PDF.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "user_password" to ParameterDef("string", "Open password"), "owner_password" to ParameterDef("string", "Owner password"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("decrypt_pdf", "Remove PDF password.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "password" to ParameterDef("string", "Current password"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("fill_form", "Fill AcroForm fields.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "fields" to ParameterDef("string", "JSON {field:value}"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("create_form", "Create PDF with fillable fields.", mapOf(
        "fields" to ParameterDef("string", "JSON array of fields"), "title" to ParameterDef("string", "Title"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("add_bookmarks", "Add bookmarks/TOC.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "bookmarks" to ParameterDef("string", "JSON [{title,page}]"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("extract_metadata", "Get PDF metadata.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"))),
    ToolDefinition("set_metadata", "Set PDF metadata.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "title" to ParameterDef("string", "Title"), "author" to ParameterDef("string", "Author"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("pdf_to_images", "Convert PDF pages to images.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "format" to ParameterDef("string", "png or jpg"), "dpi" to ParameterDef("string", "DPI (default 200)"), "output_dir" to ParameterDef("string", "Output dir"))),
    ToolDefinition("images_to_pdf", "Combine images into a PDF.", mapOf(
        "input_paths" to ParameterDef("string", "Comma-separated image paths"), "output_path" to ParameterDef("string", "Output PDF"))),
    ToolDefinition("html_to_pdf", "Convert HTML to PDF.", mapOf(
        "input_path" to ParameterDef("string", "Input HTML"), "output_path" to ParameterDef("string", "Output PDF"))),
    ToolDefinition("text_to_pdf", "Convert text to formatted PDF.", mapOf(
        "input_path" to ParameterDef("string", "Input text"), "output_path" to ParameterDef("string", "Output PDF"), "font_size" to ParameterDef("string", "Default 12"))),
    ToolDefinition("overlay_pdfs", "Overlay one PDF on another.", mapOf(
        "base_path" to ParameterDef("string", "Base PDF"), "overlay_path" to ParameterDef("string", "Overlay PDF"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("flatten_pdf", "Flatten form fields into page content.", mapOf(
        "input_path" to ParameterDef("string", "Input PDF"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("get_pdf_info", "Get PDF summary (pages, size, permissions).", mapOf(
        "input_path" to ParameterDef("string", "Input PDF")))
)

// ===== IMAGE TOOLS =====
val imageToolDefinitions = listOf(
    ToolDefinition("resize_image", "Resize an image.", mapOf(
        "input_path" to ParameterDef("string", "Input image"), "width" to ParameterDef("string", "Width px (0=proportional)"), "height" to ParameterDef("string", "Height px"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("crop_image", "Crop an image to a region.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "x" to ParameterDef("string", "Left"), "y" to ParameterDef("string", "Top"), "width" to ParameterDef("string", "Width"), "height" to ParameterDef("string", "Height"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("rotate_image", "Rotate image by degrees.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "degrees" to ParameterDef("string", "Degrees"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("flip_image", "Flip horizontally or vertically.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "direction" to ParameterDef("string", "horizontal or vertical"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("convert_image_format", "Convert between PNG/JPG/WebP/BMP/TIFF/GIF.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "format" to ParameterDef("string", "Target format"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("compress_image", "Compress image by quality.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "quality" to ParameterDef("string", "1-100 (default 60)"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("get_image_metadata", "Get image dimensions, format, EXIF.", mapOf(
        "input_path" to ParameterDef("string", "Input"))),
    ToolDefinition("strip_image_metadata", "Remove all metadata from image.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("adjust_brightness", "Adjust brightness.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "factor" to ParameterDef("string", "1.0=no change"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("adjust_contrast", "Adjust contrast.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "factor" to ParameterDef("string", "1.0=no change"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("adjust_saturation", "Adjust color saturation.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "factor" to ParameterDef("string", "1.0=no change"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("apply_image_filter", "Apply filter: grayscale, sepia, invert, blur, sharpen, emboss, edge_detect, smooth.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "filter" to ParameterDef("string", "Filter name"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("add_text_overlay", "Draw text on image.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "text" to ParameterDef("string", "Text"), "x" to ParameterDef("string", "X"), "y" to ParameterDef("string", "Y"), "font_size" to ParameterDef("string", "Default 24"), "color" to ParameterDef("string", "Hex color"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("add_image_overlay", "Overlay one image on another.", mapOf(
        "base_path" to ParameterDef("string", "Base"), "overlay_path" to ParameterDef("string", "Overlay"), "x" to ParameterDef("string", "X pos"), "y" to ParameterDef("string", "Y pos"), "opacity" to ParameterDef("string", "0.0-1.0"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("add_watermark_image", "Add watermark to image.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "watermark_text" to ParameterDef("string", "Text"), "opacity" to ParameterDef("string", "0.0-1.0"), "position" to ParameterDef("string", "center, top-left, etc"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("generate_thumbnail", "Generate thumbnail.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "max_size" to ParameterDef("string", "Max px (default 200)"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("auto_enhance", "Auto-enhance image.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("create_border", "Add border/frame.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "border_size" to ParameterDef("string", "Width px"), "color" to ParameterDef("string", "Hex color"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("change_dpi", "Change DPI metadata.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "dpi" to ParameterDef("string", "Target DPI"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("create_image", "Create blank image.", mapOf(
        "width" to ParameterDef("string", "Width"), "height" to ParameterDef("string", "Height"), "color" to ParameterDef("string", "Hex color"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("create_collage", "Create grid collage.", mapOf(
        "input_paths" to ParameterDef("string", "Comma-separated paths"), "columns" to ParameterDef("string", "Columns"), "spacing" to ParameterDef("string", "Px"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("draw_shapes", "Draw shapes on image.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "shapes" to ParameterDef("string", "JSON shapes array"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("batch_resize", "Resize multiple images.", mapOf(
        "input_paths" to ParameterDef("string", "Comma-separated paths"), "width" to ParameterDef("string", "Width"), "height" to ParameterDef("string", "Height"), "output_dir" to ParameterDef("string", "Output dir"))),
    ToolDefinition("batch_convert_format", "Convert multiple images.", mapOf(
        "input_paths" to ParameterDef("string", "Comma-separated paths"), "format" to ParameterDef("string", "Target format"), "output_dir" to ParameterDef("string", "Output dir"))),
    ToolDefinition("generate_qr_code", "Create QR code from text.", mapOf(
        "content" to ParameterDef("string", "Text/URL"), "size" to ParameterDef("string", "Px (default 300)"), "output_path" to ParameterDef("string", "Output")))
)

// ===== TEXT TOOLS =====
val textToolDefinitions = listOf(
    ToolDefinition("read_text_file", "Read text file contents.", mapOf(
        "input_path" to ParameterDef("string", "File path"))),
    ToolDefinition("create_text_file", "Create a text file.", mapOf(
        "content" to ParameterDef("string", "Text content"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("find_replace_text", "Find and replace text.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "find" to ParameterDef("string", "Find text"), "replace" to ParameterDef("string", "Replace with"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("word_count", "Count words/chars/lines.", mapOf(
        "input_path" to ParameterDef("string", "Input"))),
    ToolDefinition("read_docx", "Extract text from Word document.", mapOf(
        "input_path" to ParameterDef("string", "DOCX path"))),
    ToolDefinition("create_docx", "Create Word document.", mapOf(
        "content" to ParameterDef("string", "JSON with title, paragraphs"), "output_path" to ParameterDef("string", "Output DOCX"))),
    ToolDefinition("edit_docx", "Edit Word document.", mapOf(
        "input_path" to ParameterDef("string", "Input DOCX"), "edits" to ParameterDef("string", "JSON edits array"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("docx_to_pdf", "Convert DOCX to PDF.", mapOf(
        "input_path" to ParameterDef("string", "Input DOCX"), "output_path" to ParameterDef("string", "Output PDF"))),
    ToolDefinition("markdown_to_pdf", "Convert Markdown to PDF.", mapOf(
        "input_path" to ParameterDef("string", "Input MD"), "output_path" to ParameterDef("string", "Output PDF"))),
    ToolDefinition("html_to_text", "Strip HTML tags.", mapOf(
        "input_path" to ParameterDef("string", "Input HTML"))),
    ToolDefinition("extract_docx_images", "Extract images from DOCX.", mapOf(
        "input_path" to ParameterDef("string", "Input DOCX"), "output_dir" to ParameterDef("string", "Output dir"))),
    ToolDefinition("merge_docx", "Merge Word documents.", mapOf(
        "input_paths" to ParameterDef("string", "Comma-separated DOCX paths"), "output_path" to ParameterDef("string", "Output")))
)

// ===== SPREADSHEET TOOLS =====
val spreadsheetToolDefinitions = listOf(
    ToolDefinition("read_spreadsheet", "Read XLSX/XLS/CSV data.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "sheet" to ParameterDef("string", "Sheet name"))),
    ToolDefinition("create_spreadsheet", "Create spreadsheet from data.", mapOf(
        "data" to ParameterDef("string", "JSON sheets data"), "output_path" to ParameterDef("string", "Output XLSX"))),
    ToolDefinition("edit_cell", "Edit a cell.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "sheet" to ParameterDef("string", "Sheet"), "cell" to ParameterDef("string", "Cell e.g. A1"), "value" to ParameterDef("string", "Value"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("csv_to_xlsx", "Convert CSV to Excel.", mapOf(
        "input_path" to ParameterDef("string", "CSV"), "output_path" to ParameterDef("string", "XLSX"))),
    ToolDefinition("spreadsheet_to_pdf", "Convert spreadsheet to PDF.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "output_path" to ParameterDef("string", "Output PDF"))),
    ToolDefinition("merge_spreadsheets", "Merge spreadsheets.", mapOf(
        "input_paths" to ParameterDef("string", "Comma-separated paths"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("sort_spreadsheet", "Sort by column.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "sheet" to ParameterDef("string", "Sheet"), "column" to ParameterDef("string", "Column letter"), "ascending" to ParameterDef("string", "true/false"), "output_path" to ParameterDef("string", "Output")))
)

// ===== PRESENTATION TOOLS =====
val presentationToolDefinitions = listOf(
    ToolDefinition("read_presentation", "Extract text from PowerPoint.", mapOf(
        "input_path" to ParameterDef("string", "Input PPTX"))),
    ToolDefinition("create_presentation", "Create PowerPoint.", mapOf(
        "slides" to ParameterDef("string", "JSON slides array"), "output_path" to ParameterDef("string", "Output PPTX"))),
    ToolDefinition("presentation_to_pdf", "Convert PPTX to PDF.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "output_path" to ParameterDef("string", "Output PDF"))),
    ToolDefinition("get_presentation_info", "Get slide count and metadata.", mapOf(
        "input_path" to ParameterDef("string", "Input PPTX")))
)

// ===== AUDIO TOOLS =====
val audioToolDefinitions = listOf(
    ToolDefinition("get_audio_info", "Get audio metadata.", mapOf(
        "input_path" to ParameterDef("string", "Input audio"))),
    ToolDefinition("trim_audio", "Trim audio by timestamps.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "start" to ParameterDef("string", "Start time"), "end" to ParameterDef("string", "End time"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("convert_audio_format", "Convert audio format.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "format" to ParameterDef("string", "mp3, aac, wav, flac, ogg"), "output_path" to ParameterDef("string", "Output")))
)

// ===== VIDEO TOOLS =====
val videoToolDefinitions = listOf(
    ToolDefinition("get_video_info", "Get video metadata.", mapOf(
        "input_path" to ParameterDef("string", "Input video"))),
    ToolDefinition("trim_video", "Trim video by timestamps.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "start" to ParameterDef("string", "Start"), "end" to ParameterDef("string", "End"), "output_path" to ParameterDef("string", "Output"))),
    ToolDefinition("extract_audio_from_video", "Extract audio track.", mapOf(
        "input_path" to ParameterDef("string", "Input video"), "format" to ParameterDef("string", "mp3, aac, wav"), "output_path" to ParameterDef("string", "Output audio"))),
    ToolDefinition("video_to_gif", "Convert video to GIF.", mapOf(
        "input_path" to ParameterDef("string", "Input"), "start" to ParameterDef("string", "Start sec"), "duration" to ParameterDef("string", "Duration sec"), "output_path" to ParameterDef("string", "Output GIF"))),
    ToolDefinition("generate_video_thumbnail", "Extract frame as image.", mapOf(
        "input_path" to ParameterDef("string", "Input video"), "time" to ParameterDef("string", "Timestamp"), "output_path" to ParameterDef("string", "Output image")))
)

// ===== ARCHIVE TOOLS =====
val archiveToolDefinitions = listOf(
    ToolDefinition("create_zip", "Create ZIP from files.", mapOf(
        "input_paths" to ParameterDef("string", "Comma-separated paths"), "output_path" to ParameterDef("string", "Output ZIP"))),
    ToolDefinition("extract_zip", "Extract ZIP archive.", mapOf(
        "input_path" to ParameterDef("string", "Input ZIP"), "output_dir" to ParameterDef("string", "Output dir"))),
    ToolDefinition("list_archive_contents", "List archive contents.", mapOf(
        "input_path" to ParameterDef("string", "Input archive")))
)

// ===== GENERIC TOOLS =====
val genericToolDefinitions = listOf(
    ToolDefinition("get_file_info", "Get file type, size, MIME.", mapOf(
        "input_path" to ParameterDef("string", "File path"))),
    ToolDefinition("compare_files", "Compare two files.", mapOf(
        "path1" to ParameterDef("string", "File 1"), "path2" to ParameterDef("string", "File 2"))),
    ToolDefinition("execute_python", "Run arbitrary Python code with access to Pillow, pypdf, reportlab, docx, openpyxl, pptx.", mapOf(
        "code" to ParameterDef("string", "Python code"), "input_files" to ParameterDef("string", "Available file paths"), "output_path" to ParameterDef("string", "Output path"))),
    ToolDefinition("batch_rename", "Rename files by pattern.", mapOf(
        "input_paths" to ParameterDef("string", "Comma-separated paths"), "pattern" to ParameterDef("string", "Pattern: {n}=number, {o}=original, {ext}=ext"), "output_dir" to ParameterDef("string", "Output dir")))
)
