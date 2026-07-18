package com.docdroid.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class FileStore(private val context: Context) {

    val workingDir: File
        get() = File(context.filesDir, "working").also { it.mkdirs() }

    val outputDir: File
        get() = File(context.filesDir, "output").also { it.mkdirs() }

    val tempDir: File
        get() = File(context.cacheDir, "temp").also { it.mkdirs() }

    fun createTempFile(extension: String): File {
        val name = "tmp_${UUID.randomUUID()}.$extension"
        return File(tempDir, name)
    }

    fun createOutputFile(name: String): File {
        return File(outputDir, name)
    }

    fun copyUriToFile(uri: Uri, fileName: String): File {
        val destFile = File(workingDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.').lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "tiff", "tif" -> "image/tiff"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "html", "htm" -> "text/html"
            "md" -> "text/markdown"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            else -> "application/octet-stream"
        }
    }

    fun categorizeFile(mimeType: String): FileCategory = when {
        mimeType.contains("pdf") -> FileCategory.PDF
        mimeType.startsWith("image/") -> FileCategory.IMAGE
        mimeType.contains("word") || mimeType.contains("docx") || mimeType.contains("document") -> FileCategory.TEXT
        mimeType.contains("sheet") || mimeType.contains("excel") || mimeType.contains("csv") -> FileCategory.SPREADSHEET
        mimeType.contains("presentation") || mimeType.contains("pptx") -> FileCategory.PRESENTATION
        mimeType.startsWith("audio/") -> FileCategory.AUDIO
        mimeType.startsWith("video/") -> FileCategory.VIDEO
        mimeType.contains("zip") || mimeType.contains("rar") || mimeType.contains("7z") || mimeType.contains("tar") || mimeType.contains("gzip") -> FileCategory.ARCHIVE
        mimeType.startsWith("text/") -> FileCategory.TEXT
        else -> FileCategory.OTHER
    }

    fun cleanupTemp() {
        tempDir.listFiles()?.forEach { it.delete() }
    }

    fun cleanupWorking() {
        workingDir.listFiles()?.forEach { it.delete() }
    }

    enum class FileCategory {
        PDF, IMAGE, TEXT, SPREADSHEET, PRESENTATION, AUDIO, VIDEO, ARCHIVE, OTHER
    }
}
