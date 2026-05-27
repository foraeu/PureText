package com.example.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset

object FileUtils {

    data class FileMetadata(
        val name: String,
        val size: Long,
        val mimeType: String?
    )

    fun getMetadata(context: Context, uri: Uri): FileMetadata {
        val contentResolver = context.contentResolver
        var name = "unknown"
        var size = 0L
        var mimeType: String? = null
        try {
            mimeType = contentResolver.getType(uri)
        } catch (e: Exception) {
            // Ignore
        }

        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex) ?: "unknown"
                        }
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback to last path segment
                name = uri.lastPathSegment ?: "unknown"
            }
        } else if (uri.scheme == "file") {
            name = uri.lastPathSegment ?: "unknown"
            try {
                val file = java.io.File(uri.path ?: "")
                if (file.exists()) {
                    size = file.length()
                }
            } catch (e: Exception) {
                // Ignore
            }
        } else {
            name = uri.lastPathSegment ?: "unknown"
        }

        return FileMetadata(name, size, mimeType)
    }

    fun determineCharset(context: Context, uri: Uri, encoding: String): Charset {
        var bytes = ByteArray(0)
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(65536)
                val read = inputStream.read(buffer)
                if (read > 0) {
                    bytes = buffer.copyOf(read)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        return when (encoding.uppercase()) {
            "AUTO" -> {
                if (bytes.isNotEmpty() && isUtf8(bytes)) {
                    Charsets.UTF_8
                } else {
                    try {
                        Charset.forName("GBK")
                    } catch (e: Exception) {
                        Charset.defaultCharset()
                    }
                }
            }
            else -> {
                try {
                    Charset.forName(encoding)
                } catch (e: Exception) {
                    Charsets.UTF_8
                }
            }
        }
    }

    fun readTextFromUri(context: Context, uri: Uri, encoding: String): String {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bytes = inputStream.readBytes()
            return when (encoding.uppercase()) {
                "AUTO" -> {
                    // Quick UTF-8 vs GBK vs ISO-8859-1 check
                    if (isUtf8(bytes)) {
                        String(bytes, Charsets.UTF_8)
                    } else {
                        // GBK is common for non-UTF-8 local Chinese files, windows-1252/ISO-8859-1 for others.
                        // Let's check if the system can load GBK. If not, fallback to Default Charset.
                        try {
                            String(bytes, Charset.forName("GBK"))
                        } catch (e: Exception) {
                            String(bytes, Charset.defaultCharset())
                        }
                    }
                }
                else -> {
                    try {
                        String(bytes, Charset.forName(encoding))
                    } catch (e: Exception) {
                        String(bytes, Charsets.UTF_8)
                    }
                }
            }
        } ?: throw Exception("Failed to open file stream")
    }

    fun writeTextToUri(context: Context, uri: Uri, text: String, encoding: String): Boolean {
        return try {
            val charset = try {
                if (encoding.uppercase() == "AUTO") Charsets.UTF_8 else Charset.forName(encoding)
            } catch (e: Exception) {
                Charsets.UTF_8
            }
            context.contentResolver.openOutputStream(uri, "rwt")?.use { outputStream ->
                outputStream.write(text.toByteArray(charset))
                outputStream.flush()
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun writeLinesToUri(context: Context, uri: Uri, lines: List<String>, encoding: String): Boolean {
        return try {
            val charset = try {
                if (encoding.uppercase() == "AUTO") Charsets.UTF_8 else Charset.forName(encoding)
            } catch (e: Exception) {
                Charsets.UTF_8
            }
            context.contentResolver.openOutputStream(uri, "rwt")?.use { outputStream ->
                outputStream.bufferedWriter(charset).use { writer ->
                    lines.forEachIndexed { index, line ->
                        writer.write(line)
                        if (index < lines.size - 1) {
                            writer.write("\n")
                        }
                    }
                    writer.flush()
                }
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun isUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val raw = bytes[i].toInt() and 0xFF
            if (raw < 0x80) {
                i++
                continue
            }
            val bytesNeeded: Int
            if (raw in 0xC0..0xDF) {
                bytesNeeded = 1
            } else if (raw in 0xE0..0xEF) {
                bytesNeeded = 2
            } else if (raw in 0xF0..0xF4) {
                bytesNeeded = 3
            } else {
                return false
            }
            if (i + bytesNeeded >= bytes.size) return false
            for (j in 1..bytesNeeded) {
                val nextRaw = bytes[i + j].toInt() and 0xFF
                if (nextRaw !in 0x80..0xBF) return false
            }
            i += bytesNeeded + 1
        }
        return true
    }
}
