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

// 22 compact tool definitions (~2100 tokens) for 26M param Needle model
val consolidatedToolDefinitions = listOf(
    ToolDefinition("pdf_edit", "Merge/split/crop/resize PDF.", mapOf(
        "operation" to ParameterDef("string", "merge,split,crop,resize", enum = listOf("merge", "split", "crop", "resize")),
        "input_paths" to ParameterDef("string", "PDF paths comma-sep", required = false),
        "input_path" to ParameterDef("string", "PDF path", required = false),
        "output_path" to ParameterDef("string", "Output path"),
        "page_ranges" to ParameterDef("string", "e.g. 1-3,5", required = false),
        "left" to ParameterDef("string", "pt", required = false),
        "top" to ParameterDef("string", "pt", required = false),
        "right" to ParameterDef("string", "pt", required = false),
        "bottom" to ParameterDef("string", "pt", required = false),
        "size" to ParameterDef("string", "a4/letter/legal", required = false)
    )),
    ToolDefinition("pdf_pages", "Extract/delete/reorder/rotate pages.", mapOf(
        "operation" to ParameterDef("string", "extract,delete,reorder,rotate", enum = listOf("extract", "delete", "reorder", "rotate")),
        "input_path" to ParameterDef("string", "PDF path"),
        "pages" to ParameterDef("string", "e.g. 1,3,5 or all", required = false),
        "new_order" to ParameterDef("string", "e.g. 3,1,2", required = false),
        "degrees" to ParameterDef("string", "90/180/270", required = false),
        "output_path" to ParameterDef("string", "Output path")
    )),
    ToolDefinition("pdf_watermark", "Add watermark/numbers/header/footer.", mapOf(
        "operation" to ParameterDef("string", "text,image,page_numbers,header_footer", enum = listOf("text", "image", "page_numbers", "header_footer")),
        "input_path" to ParameterDef("string", "PDF path"),
        "text" to ParameterDef("string", "Text", required = false),
        "image_path" to ParameterDef("string", "Image path", required = false),
        "font_size" to ParameterDef("string", "Default 48", required = false),
        "opacity" to ParameterDef("string", "0-1", required = false),
        "position" to ParameterDef("string", "bottom-center etc", required = false),
        "header" to ParameterDef("string", "Header text", required = false),
        "footer" to ParameterDef("string", "Footer text", required = false),
        "output_path" to ParameterDef("string", "Output path")
    )),
    ToolDefinition("pdf_extract", "Extract text/images/tables/metadata.", mapOf(
        "operation" to ParameterDef("string", "text,images,tables,metadata", enum = listOf("text", "images", "tables", "metadata")),
        "input_path" to ParameterDef("string", "PDF path"),
        "pages" to ParameterDef("string", "e.g. 1-5", required = false),
        "output_dir" to ParameterDef("string", "Output dir", required = false)
    )),
    ToolDefinition("pdf_security", "Encrypt/decrypt/flatten PDF.", mapOf(
        "operation" to ParameterDef("string", "encrypt,decrypt,flatten", enum = listOf("encrypt", "decrypt", "flatten")),
        "input_path" to ParameterDef("string", "PDF path"),
        "password" to ParameterDef("string", "Password", required = false),
        "user_password" to ParameterDef("string", "Open password", required = false),
        "owner_password" to ParameterDef("string", "Owner password", required = false),
        "output_path" to ParameterDef("string", "Output path")
    )),
    ToolDefinition("pdf_forms", "Fill or create PDF forms.", mapOf(
        "operation" to ParameterDef("string", "fill,create", enum = listOf("fill", "create")),
        "input_path" to ParameterDef("string", "PDF path", required = false),
        "fields" to ParameterDef("string", "JSON fields"),
        "title" to ParameterDef("string", "Title", required = false),
        "output_path" to ParameterDef("string", "Output path")
    )),
    ToolDefinition("pdf_convert", "Convert PDF to/from images/HTML/text.", mapOf(
        "operation" to ParameterDef("string", "to_images,from_images,from_html,from_text,overlay", enum = listOf("to_images", "from_images", "from_html", "from_text", "overlay")),
        "input_path" to ParameterDef("string", "Input path", required = false),
        "input_paths" to ParameterDef("string", "Paths comma-sep", required = false),
        "base_path" to ParameterDef("string", "Base PDF", required = false),
        "overlay_path" to ParameterDef("string", "Overlay PDF", required = false),
        "format" to ParameterDef("string", "png/jpg", required = false),
        "dpi" to ParameterDef("string", "Default 200", required = false),
        "font_size" to ParameterDef("string", "Default 12", required = false),
        "output_path" to ParameterDef("string", "Output path")
    )),
    ToolDefinition("pdf_info", "Get PDF page count/size/metadata.", mapOf(
        "input_path" to ParameterDef("string", "PDF path")
    )),
    ToolDefinition("image_edit", "Resize/crop/rotate/flip/adjust/filter.", mapOf(
        "operation" to ParameterDef("string", "resize,crop,rotate,flip,brightness,contrast,saturation,grayscale,sepia,blur,sharpen,invert", enum = listOf("resize", "crop", "rotate", "flip", "brightness", "contrast", "saturation", "grayscale", "sepia", "blur", "sharpen", "invert")),
        "input_path" to ParameterDef("string", "Image path"),
        "width" to ParameterDef("string", "px", required = false),
        "height" to ParameterDef("string", "px", required = false),
        "degrees" to ParameterDef("string", "Degrees", required = false),
        "direction" to ParameterDef("string", "horizontal/vertical", required = false),
        "factor" to ParameterDef("string", "1.0=no change", required = false),
        "output_path" to ParameterDef("string", "Output path")
    )),
    ToolDefinition("image_overlay", "Add text/image/watermark/border/thumb.", mapOf(
        "operation" to ParameterDef("string", "text,image,watermark,border,thumbnail", enum = listOf("text", "image", "watermark", "border", "thumbnail")),
        "input_path" to ParameterDef("string", "Image path"),
        "overlay_path" to ParameterDef("string", "Overlay image", required = false),
        "text" to ParameterDef("string", "Text", required = false),
        "x" to ParameterDef("string", "X pos", required = false),
        "y" to ParameterDef("string", "Y pos", required = false),
        "font_size" to ParameterDef("string", "Default 24", required = false),
        "color" to ParameterDef("string", "Hex color", required = false),
        "opacity" to ParameterDef("string", "0-1", required = false),
        "position" to ParameterDef("string", "center/top-left", required = false),
        "border_size" to ParameterDef("string", "px", required = false),
        "max_size" to ParameterDef("string", "Max px", required = false),
        "output_path" to ParameterDef("string", "Output path")
    )),
    ToolDefinition("image_convert", "Convert/compress/metadata/create/batch.", mapOf(
        "operation" to ParameterDef("string", "convert,compress,metadata,strip,dpi,create,batch_resize,batch_convert", enum = listOf("convert", "compress", "metadata", "strip", "dpi", "create", "batch_resize", "batch_convert")),
        "input_path" to ParameterDef("string", "Image path", required = false),
        "input_paths" to ParameterDef("string", "Paths comma-sep", required = false),
        "format" to ParameterDef("string", "png/jpg/webp", required = false),
        "quality" to ParameterDef("string", "1-100", required = false),
        "dpi" to ParameterDef("string", "DPI", required = false),
        "width" to ParameterDef("string", "px", required = false),
        "height" to ParameterDef("string", "px", required = false),
        "color" to ParameterDef("string", "Hex", required = false),
        "output_path" to ParameterDef("string", "Output path", required = false),
        "output_dir" to ParameterDef("string", "Output dir", required = false)
    )),
    ToolDefinition("image_qr", "Generate QR code from text/URL.", mapOf(
        "content" to ParameterDef("string", "Text/URL"),
        "size" to ParameterDef("string", "px, default 300"),
        "output_path" to ParameterDef("string", "Output path")
    )),
    ToolDefinition("text_file", "Read/create/find-replace/word-count.", mapOf(
        "operation" to ParameterDef("string", "read,create,find_replace,word_count", enum = listOf("read", "create", "find_replace", "word_count")),
        "input_path" to ParameterDef("string", "Path", required = false),
        "content" to ParameterDef("string", "Text", required = false),
        "find" to ParameterDef("string", "Find text", required = false),
        "replace" to ParameterDef("string", "Replace with", required = false),
        "output_path" to ParameterDef("string", "Path", required = false)
    )),
    ToolDefinition("docx", "Read/create/edit/merge DOCX or to PDF.", mapOf(
        "operation" to ParameterDef("string", "read,create,edit,merge,to_pdf,images", enum = listOf("read", "create", "edit", "merge", "to_pdf", "images")),
        "input_path" to ParameterDef("string", "DOCX path", required = false),
        "input_paths" to ParameterDef("string", "Paths comma-sep", required = false),
        "content" to ParameterDef("string", "JSON", required = false),
        "edits" to ParameterDef("string", "JSON edits", required = false),
        "output_path" to ParameterDef("string", "Output path"),
        "output_dir" to ParameterDef("string", "Output dir", required = false)
    )),
    ToolDefinition("markdown_to_pdf", "Convert Markdown to PDF.", mapOf(
        "input_path" to ParameterDef("string", "MD path"),
        "output_path" to ParameterDef("string", "PDF path")
    )),
    ToolDefinition("spreadsheet", "Read/create/edit/sort/merge/convert.", mapOf(
        "operation" to ParameterDef("string", "read,create,edit_cell,sort,merge,csv_to_xlsx,to_pdf", enum = listOf("read", "create", "edit_cell", "sort", "merge", "csv_to_xlsx", "to_pdf")),
        "input_path" to ParameterDef("string", "Path", required = false),
        "input_paths" to ParameterDef("string", "Paths comma-sep", required = false),
        "sheet" to ParameterDef("string", "Sheet name", required = false),
        "cell" to ParameterDef("string", "e.g. A1", required = false),
        "value" to ParameterDef("string", "Value", required = false),
        "column" to ParameterDef("string", "Column letter", required = false),
        "ascending" to ParameterDef("string", "true/false", required = false),
        "data" to ParameterDef("string", "JSON data", required = false),
        "output_path" to ParameterDef("string", "Output path")
    )),
    ToolDefinition("presentation", "Read/create PPTX or to PDF.", mapOf(
        "operation" to ParameterDef("string", "read,create,to_pdf,info", enum = listOf("read", "create", "to_pdf", "info")),
        "input_path" to ParameterDef("string", "PPTX path", required = false),
        "slides" to ParameterDef("string", "JSON slides", required = false),
        "output_path" to ParameterDef("string", "Output path")
    )),
    ToolDefinition("audio", "Get info/trim/convert audio.", mapOf(
        "operation" to ParameterDef("string", "info,trim,convert", enum = listOf("info", "trim", "convert")),
        "input_path" to ParameterDef("string", "Audio path"),
        "start" to ParameterDef("string", "Start", required = false),
        "end" to ParameterDef("string", "End", required = false),
        "format" to ParameterDef("string", "mp3/aac/wav/flac", required = false),
        "output_path" to ParameterDef("string", "Output path", required = false)
    )),
    ToolDefinition("video", "Get info/trim/extract audio/GIF/thumb.", mapOf(
        "operation" to ParameterDef("string", "info,trim,extract_audio,to_gif,thumbnail", enum = listOf("info", "trim", "extract_audio", "to_gif", "thumbnail")),
        "input_path" to ParameterDef("string", "Video path"),
        "start" to ParameterDef("string", "Start", required = false),
        "end" to ParameterDef("string", "End", required = false),
        "duration" to ParameterDef("string", "Sec", required = false),
        "time" to ParameterDef("string", "Timestamp", required = false),
        "format" to ParameterDef("string", "mp3/aac/wav", required = false),
        "output_path" to ParameterDef("string", "Output path")
    )),
    ToolDefinition("archive", "Create/extract/list ZIP.", mapOf(
        "operation" to ParameterDef("string", "create,extract,list", enum = listOf("create", "extract", "list")),
        "input_path" to ParameterDef("string", "ZIP path", required = false),
        "input_paths" to ParameterDef("string", "Paths comma-sep", required = false),
        "output_path" to ParameterDef("string", "Output path", required = false),
        "output_dir" to ParameterDef("string", "Output dir", required = false)
    )),
    ToolDefinition("ocr", "OCR: extract text from image/PDF.", mapOf(
        "input_path" to ParameterDef("string", "Image or PDF path")
    )),
    ToolDefinition("file_info", "Get file type/size/MIME.", mapOf(
        "input_path" to ParameterDef("string", "File path")
    )),
    ToolDefinition("execute_python", "Run Python code (last resort).", mapOf(
        "code" to ParameterDef("string", "Python code"),
        "input_files" to ParameterDef("string", "Paths comma-sep", required = false),
        "output_path" to ParameterDef("string", "Output path", required = false)
    ))
)
