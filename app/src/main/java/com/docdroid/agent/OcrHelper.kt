package com.docdroid.agent

import android.content.Context
import android.net.Uri
import com.docdroid.model.ToolResult
import com.docdroid.model.ToolStatus
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

class OcrHelper(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractTextFromFile(filePath: String): ToolResult {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return ToolResult(
                    toolName = "ocr_extract_text",
                    status = ToolStatus.FAILED,
                    error = "File not found: $filePath"
                )
            }

            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val text = recognizeText(image)

            ToolResult(
                toolName = "ocr_extract_text",
                status = ToolStatus.SUCCESS,
                result = text.ifEmpty { "No text detected in the image." }
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = "ocr_extract_text",
                status = ToolStatus.FAILED,
                error = "OCR failed: ${e.message}"
            )
        }
    }

    private suspend fun recognizeText(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    cont.resume(result.text)
                }
                .addOnFailureListener { e ->
                    cont.resume("OCR recognition failed: ${e.message}")
                }
        }
}
