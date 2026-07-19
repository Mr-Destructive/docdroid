package com.docdroid.harness

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * The tool definitions JSON sent to Cactus Needle.
 * These describe the 8 MVP document tools.
 */
object ToolDefinitions {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    val JSON: String = mapper.writeValueAsString(listOf(
        mapOf(
            "name" to "merge_pdfs",
            "description" to "Combine multiple PDF files into a single merged document.",
            "parameters" to mapOf(
                "input_paths" to mapOf(
                    "type" to "array",
                    "description" to "List of PDF file paths to merge together.",
                    "required" to true
                ),
                "output_path" to mapOf(
                    "type" to "string",
                    "description" to "Output file path for the merged PDF.",
                    "required" to true
                )
            )
        ),
        mapOf(
            "name" to "split_pdf",
            "description" to "Split a PDF into separate files by page ranges.",
            "parameters" to mapOf(
                "input_path" to mapOf(
                    "type" to "string",
                    "description" to "Input PDF file path to split.",
                    "required" to true
                ),
                "page_ranges" to mapOf(
                    "type" to "string",
                    "description" to "Page ranges to extract, e.g. '1-3,5,7-9'.",
                    "required" to true
                ),
                "output_dir" to mapOf(
                    "type" to "string",
                    "description" to "Directory to save the split PDF files.",
                    "required" to true
                )
            )
        ),
        mapOf(
            "name" to "rotate_pdf",
            "description" to "Rotate specific pages of a PDF by 90, 180, or 270 degrees.",
            "parameters" to mapOf(
                "input_path" to mapOf(
                    "type" to "string",
                    "description" to "Input PDF file path.",
                    "required" to true
                ),
                "pages" to mapOf(
                    "type" to "string",
                    "description" to "Pages to rotate, e.g. '1,3,5' or 'all'.",
                    "required" to true
                ),
                "angle" to mapOf(
                    "type" to "integer",
                    "description" to "Rotation angle: 90, 180, or 270.",
                    "required" to true
                ),
                "output_path" to mapOf(
                    "type" to "string",
                    "description" to "Output file path for the rotated PDF.",
                    "required" to true
                )
            )
        ),
        mapOf(
            "name" to "extract_text",
            "description" to "Extract text content from a PDF document.",
            "parameters" to mapOf(
                "input_path" to mapOf(
                    "type" to "string",
                    "description" to "Input PDF file path.",
                    "required" to true
                ),
                "pages" to mapOf(
                    "type" to "string",
                    "description" to "Page range to extract, e.g. '1-5' or 'all'.",
                    "required" to false
                )
            )
        ),
        mapOf(
            "name" to "extract_images",
            "description" to "Extract all embedded images from a PDF document.",
            "parameters" to mapOf(
                "input_path" to mapOf(
                    "type" to "string",
                    "description" to "Input PDF file path.",
                    "required" to true
                ),
                "output_dir" to mapOf(
                    "type" to "string",
                    "description" to "Directory to save extracted images.",
                    "required" to true
                )
            )
        ),
        mapOf(
            "name" to "compress_pdf",
            "description" to "Reduce the file size of a PDF by compressing images and removing metadata.",
            "parameters" to mapOf(
                "input_path" to mapOf(
                    "type" to "string",
                    "description" to "Input PDF file path.",
                    "required" to true
                ),
                "output_path" to mapOf(
                    "type" to "string",
                    "description" to "Output file path for the compressed PDF.",
                    "required" to true
                )
            )
        ),
        mapOf(
            "name" to "add_watermark",
            "description" to "Add a text watermark to every page of a PDF document.",
            "parameters" to mapOf(
                "input_path" to mapOf(
                    "type" to "string",
                    "description" to "Input PDF file path.",
                    "required" to true
                ),
                "text" to mapOf(
                    "type" to "string",
                    "description" to "Watermark text to stamp on each page.",
                    "required" to true
                ),
                "output_path" to mapOf(
                    "type" to "string",
                    "description" to "Output file path for the watermarked PDF.",
                    "required" to true
                )
            )
        ),
        mapOf(
            "name" to "images_to_pdf",
            "description" to "Convert one or more image files (PNG, JPG) into a single PDF document.",
            "parameters" to mapOf(
                "image_paths" to mapOf(
                    "type" to "array",
                    "description" to "List of image file paths to convert.",
                    "required" to true
                ),
                "output_path" to mapOf(
                    "type" to "string",
                    "description" to "Output PDF file path.",
                    "required" to true
                )
            )
        )
    ))
}
