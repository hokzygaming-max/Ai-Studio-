package com.example.domain.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Xml
import com.example.domain.model.AttachmentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

class FileProcessor(private val context: Context) {

    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    private val textExtensions = setOf(
        "txt", "md", "markdown", "csv", "tsv", "json", "xml", "html", "htm",
        "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "css", "scss",
        "sql", "sh", "bash", "yaml", "yml", "toml", "ini", "properties", "gradle",
        "c", "h", "cpp", "hpp", "cs", "go", "rs", "rb", "php", "swift", "dart", "r"
    )

    suspend fun processUri(uri: Uri): AttachmentItem = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val (name, size) = queryNameAndSize(uri)
        val mime = context.contentResolver.getType(uri) ?: guessMimeFromName(name)
        val ext = name.substringAfterLast('.', "").lowercase()

        if (size > MAX_FILE_BYTES) {
            return@withContext AttachmentItem(
                id = id,
                fileName = name,
                mimeType = mime,
                sizeBytes = size,
                isImage = false,
                errorMessage = "File '$name' exceeds the 20 MB limit for local processing."
            )
        }

        val rawBytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()

        if (rawBytes == null) {
            return@withContext AttachmentItem(
                id = id,
                fileName = name,
                mimeType = mime,
                sizeBytes = size,
                isImage = false,
                errorMessage = "Could not open '$name'. Verify storage permission."
            )
        }

        val actualSize = if (size > 0) size else rawBytes.size.toLong()

        when {
            mime.startsWith("image/") || ext in imageExtensions -> {
                processImage(id, name, mime, actualSize, rawBytes)
            }
            mime == "application/pdf" || ext == "pdf" -> {
                processPdf(id, name, actualSize, rawBytes)
            }
            ext == "docx" || mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                processDocx(id, name, mime, actualSize, rawBytes)
            }
            ext == "html" || ext == "htm" || mime == "text/html" -> {
                val rawText = String(rawBytes, StandardCharsets.UTF_8)
                val clean = stripHtmlPreserveStructure(rawText)
                AttachmentItem(
                    id = id,
                    fileName = name,
                    mimeType = mime,
                    sizeBytes = actualSize,
                    isImage = false,
                    extractedText = clean.take(MAX_EXTRACTED_CHARS)
                )
            }
            mime.startsWith("text/") || ext in textExtensions || looksLikeUtf8Text(rawBytes) -> {
                val text = String(rawBytes, StandardCharsets.UTF_8)
                AttachmentItem(
                    id = id,
                    fileName = name,
                    mimeType = mime,
                    sizeBytes = actualSize,
                    isImage = false,
                    extractedText = text.take(MAX_EXTRACTED_CHARS)
                )
            }
            else -> {
                AttachmentItem(
                    id = id,
                    fileName = name,
                    mimeType = mime,
                    sizeBytes = actualSize,
                    isImage = false,
                    errorMessage = "Unsupported file type '$ext' ($mime). Supported: PDF, DOCX, TXT, MD, CSV, JSON, XML, HTML, source code, and images."
                )
            }
        }
    }

    private fun processImage(
        id: String,
        name: String,
        mime: String,
        size: Long,
        bytes: ByteArray
    ): AttachmentItem {
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return AttachmentItem(
                id = id,
                fileName = name,
                mimeType = mime,
                sizeBytes = size,
                isImage = true,
                errorMessage = "Unable to decode image '$name'."
            )

        val maxDim = 1536
        val scale = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt().coerceAtLeast(1),
                (original.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else original

        val outStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, outStream)
        val jpegBytes = outStream.toByteArray()

        val cacheDir = File(context.cacheDir, "attachments").apply { mkdirs() }
        val cacheFile = File(cacheDir, "$id.jpg")
        FileOutputStream(cacheFile).use { it.write(jpegBytes) }

        val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        return AttachmentItem(
            id = id,
            fileName = name,
            mimeType = "image/jpeg",
            sizeBytes = size,
            isImage = true,
            base64Data = b64,
            localCachePath = cacheFile.absolutePath
        )
    }

    private fun processPdf(
        id: String,
        name: String,
        size: Long,
        bytes: ByteArray
    ): AttachmentItem {
        val cacheDir = File(context.cacheDir, "attachments").apply { mkdirs() }
        val tempPdf = File(cacheDir, "$id.pdf")
        FileOutputStream(tempPdf).use { it.write(bytes) }

        var pageCount = 0
        runCatching {
            ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount
                }
            }
        }

        val extracted = extractPdfTextBestEffort(bytes).trim()
        val finalText = if (extracted.isNotBlank()) {
            extracted.take(MAX_EXTRACTED_CHARS)
        } else {
            "[PDF has $pageCount page(s). No plain text layer was found—may be scanned. If using a vision/PDF-capable model such as Gemini or Claude, the raw PDF is attached.]"
        }

        val b64 = if (bytes.size <= 8 * 1024 * 1024) {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } else ""

        return AttachmentItem(
            id = id,
            fileName = name,
            mimeType = "application/pdf",
            sizeBytes = size,
            isImage = false,
            isPdf = true,
            extractedText = finalText,
            base64Data = b64,
            localCachePath = tempPdf.absolutePath,
            pageCount = pageCount
        )
    }

    private fun processDocx(
        id: String,
        name: String,
        mime: String,
        size: Long,
        bytes: ByteArray
    ): AttachmentItem {
        val sb = StringBuilder()
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val parser = Xml.newPullParser()
                        parser.setInput(zis, "UTF-8")
                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            when (event) {
                                XmlPullParser.START_TAG -> {
                                    if (parser.name == "t") {
                                        sb.append(parser.nextText())
                                    } else if (parser.name == "tab") {
                                        sb.append("\t")
                                    }
                                }
                                XmlPullParser.END_TAG -> {
                                    if (parser.name == "p") sb.append("\n")
                                }
                            }
                            event = parser.next()
                        }
                        break
                    }
                    entry = zis.nextEntry
                }
            }
        }.onFailure {
            return AttachmentItem(
                id = id,
                fileName = name,
                mimeType = mime,
                sizeBytes = size,
                isImage = false,
                errorMessage = "Could not parse DOCX '$name': ${it.localizedMessage}"
            )
        }

        val text = sb.toString().trim()
        return AttachmentItem(
            id = id,
            fileName = name,
            mimeType = mime,
            sizeBytes = size,
            isImage = false,
            extractedText = text.ifBlank { "[Empty DOCX document]" }.take(MAX_EXTRACTED_CHARS)
        )
    }

    private fun extractPdfTextBestEffort(bytes: ByteArray): String {
        val latin = String(bytes, StandardCharsets.ISO_8859_1)
        val collected = StringBuilder()
        val streamRegex = Regex("stream\\r?\\n([\\s\\S]*?)\\r?\\nendstream")
        for (match in streamRegex.findAll(latin)) {
            val rawStream = match.groupValues[1].toByteArray(StandardCharsets.ISO_8859_1)
            val decoded = tryInflate(rawStream) ?: String(rawStream, StandardCharsets.ISO_8859_1)
            if (decoded.contains("BT") && decoded.contains("Tj")) {
                extractTextOperators(decoded, collected)
            } else if (decoded.contains("TJ")) {
                extractTextOperators(decoded, collected)
            }
        }
        return collected.toString().replace(Regex("\\s{3,}"), "\n").trim()
    }

    private fun tryInflate(input: ByteArray): String? {
        return runCatching {
            val inflater = Inflater()
            inflater.setInput(input)
            val buffer = ByteArray(4096)
            val out = ByteArrayOutputStream()
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    out.write(buffer, 0, count)
                }
            }
            inflater.end()
            String(out.toByteArray(), StandardCharsets.ISO_8859_1)
        }.getOrNull()
    }

    private fun extractTextOperators(streamText: String, out: StringBuilder) {
        val literalRegex = Regex("\\(([^()\\\\]*(?:\\\\.[^()\\\\]*)*)\\)\\s*(?:Tj|'|\"|\\])")
        for (m in literalRegex.findAll(streamText)) {
            val raw = m.groupValues[1]
                .replace("\\n", "\n")
                .replace("\\r", "")
                .replace("\\t", " ")
                .replace("\\(", "(")
                .replace("\\)", ")")
                .replace("\\\\", "\\")
            if (raw.any { it.isLetterOrDigit() }) {
                out.append(raw).append(' ')
            }
        }
        if (out.isNotEmpty()) out.append('\n')
    }

    private fun stripHtmlPreserveStructure(html: String): String {
        return html
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<br\\s*/?>|</p>|</div>|</h[1-6]>|</li>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun looksLikeUtf8Text(bytes: ByteArray): Boolean {
        val sample = bytes.take(2048)
        if (sample.isEmpty()) return true
        val controlBytes = sample.count { b ->
            val v = b.toInt() and 0xFF
            v < 9 || (v in 14..31 && v != 27)
        }
        return controlBytes == 0
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long> {
        var name = "attachment_${System.currentTimeMillis()}"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        return name to size
    }

    private fun guessMimeFromName(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "md", "markdown" -> "text/markdown"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }

    fun clearCachedFiles() {
        File(context.cacheDir, "attachments").deleteRecursively()
        File(context.cacheDir, "exports").deleteRecursively()
    }

    companion object {
        private const val MAX_FILE_BYTES = 20L * 1024 * 1024
        private const val MAX_EXTRACTED_CHARS = 45_000

        fun formatBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
