package com.example.utils

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

object EpubParser {

    fun parseEpubToLines(context: Context, uri: Uri): List<String> {
        val tempFile = File(context.cacheDir, "temp_epub_reader.epub")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Cannot open input stream")

            return parseEpubFile(tempFile)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun parseEpubFile(file: File): List<String> {
        val zipFile = ZipFile(file)
        try {
            // 1. Parse container.xml to find content.opf path
            val containerEntry = zipFile.getEntry("META-INF/container.xml")
                ?: throw Exception("Invalid EPUB: META-INF/container.xml not found")
            val opfPath = zipFile.getInputStream(containerEntry).use { parseContainerXml(it) }
                ?: throw Exception("Invalid EPUB: OPF file path not found in container.xml")

            // 2. Resolve OPF relative directory
            val opfDir = if (opfPath.contains("/")) {
                opfPath.substringBeforeLast("/") + "/"
            } else {
                ""
            }

            // 3. Parse content.opf to build manifest map and spine list
            val opfEntry = zipFile.getEntry(opfPath) ?: throw Exception("OPF file not found: $opfPath")
            val (manifest, spine) = zipFile.getInputStream(opfEntry).use { parseOpfXml(it) }

            // 4. Read spine files in order and extract text
            val lines = mutableListOf<String>()
            for (idref in spine) {
                val href = manifest[idref] ?: continue
                val fullHref = opfDir + href
                val cleanHref = normalizePath(fullHref)
                val entry = zipFile.getEntry(cleanHref) ?: continue

                val chapterHtml = zipFile.getInputStream(entry).use { it.bufferedReader(Charsets.UTF_8).readText() }
                val chapterLines = cleanHtmlToLines(chapterHtml)
                lines.addAll(chapterLines)
                lines.add("") // Add space between chapters
            }
            return lines
        } finally {
            zipFile.close()
        }
    }

    private fun parseContainerXml(inputStream: InputStream): String? {
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                for (i in 0 until parser.attributeCount) {
                    if (parser.getAttributeName(i) == "full-path") {
                        return parser.getAttributeValue(i)
                    }
                }
            }
            eventType = parser.next()
        }
        return null
    }

    private fun parseOpfXml(inputStream: InputStream): Pair<Map<String, String>, List<String>> {
        val manifest = mutableMapOf<String, String>()
        val spine = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "item" -> {
                        var id: String? = null
                        var href: String? = null
                        for (i in 0 until parser.attributeCount) {
                            when (parser.getAttributeName(i)) {
                                "id" -> id = parser.getAttributeValue(i)
                                "href" -> href = parser.getAttributeValue(i)
                            }
                        }
                        if (id != null && href != null) {
                            manifest[id] = Uri.decode(href)
                        }
                    }
                    "itemref" -> {
                        var idref: String? = null
                        for (i in 0 until parser.attributeCount) {
                            if (parser.getAttributeName(i) == "idref") {
                                idref = parser.getAttributeValue(i)
                            }
                        }
                        if (idref != null) {
                            spine.add(idref)
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return Pair(manifest, spine)
    }

    private fun normalizePath(path: String): String {
        val parts = path.split("/")
        val result = mutableListOf<String>()
        for (part in parts) {
            if (part == "..") {
                if (result.isNotEmpty()) result.removeAt(result.size - 1)
            } else if (part != "." && part.isNotEmpty()) {
                result.add(part)
            }
        }
        return result.joinToString("/")
    }

    private fun cleanHtmlToLines(html: String): List<String> {
        var text = html
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<head[\\s\\S]*?</head>", RegexOption.IGNORE_CASE), "")

        text = text
            .replace(Regex("<p[^\b]*?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<div[^\b]*?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</div>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<h[1-6][^\b]*?>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("</h[1-6]>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<li[^\b]*?>", RegexOption.IGNORE_CASE), "\n• ")

        text = text.replace(Regex("<[^>]+>"), "")
        text = decodeHtmlEntities(text)

        return text.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&ldquo;", "“")
            .replace("&rdquo;", "”")
            .replace("&lsquo;", "‘")
            .replace("&rsquo;", "’")
            .replace("&middot;", "·")
            .replace("&mdash;", "—")
    }
}
