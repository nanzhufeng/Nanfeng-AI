package com.nanzhufeng.ai.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

const val DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
const val PPTX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
val OFFICE_OPEN_XML_MIME_TYPES = setOf(DOCX_MIME_TYPE, XLSX_MIME_TYPE, PPTX_MIME_TYPE)

data class OfficeOpenXmlText(val text: String, val truncated: Boolean)

/**
 * Reads only the bounded XML parts required for an inert local preview. Macros, links, embedded
 * objects and external relationships are never interpreted. Old binary Office files are not
 * mislabeled as OOXML.
 */
fun extractOfficeOpenXmlText(bytes: ByteArray, mimeType: String): OfficeOpenXmlText? = runCatching {
    if (mimeType !in OFFICE_OPEN_XML_MIME_TYPES || bytes.size < 4 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) return null
    val parts = readBoundedOfficeParts(bytes, mimeType)
    val raw = when (mimeType) {
        DOCX_MIME_TYPE -> extractDocx(parts)
        XLSX_MIME_TYPE -> extractXlsx(parts)
        PPTX_MIME_TYPE -> extractPptx(parts)
        else -> null
    } ?: return null
    val normalized = raw.replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n").trim()
    if (normalized.isBlank()) return null
    OfficeOpenXmlText(normalized.take(MAX_OFFICE_PREVIEW_CHARS), normalized.length > MAX_OFFICE_PREVIEW_CHARS)
}.getOrNull()

private fun readBoundedOfficeParts(bytes: ByteArray, mimeType: String): Map<String, ByteArray> {
    val selected = linkedMapOf<String, ByteArray>()
    var entryCount = 0
    var selectedBytes = 0L
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            entryCount += 1
            require(entryCount <= MAX_OFFICE_ZIP_ENTRIES)
            val path = safeOfficePath(entry.name) ?: error("unsafe OOXML path")
            if (!entry.isDirectory && isRequiredOfficePart(path, mimeType)) {
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var partBytes = 0L
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    partBytes += count
                    selectedBytes += count
                    require(partBytes <= MAX_OFFICE_XML_PART_BYTES && selectedBytes <= MAX_OFFICE_SELECTED_XML_BYTES)
                    output.write(buffer, 0, count)
                }
                selected[path] = output.toByteArray()
            }
            zip.closeEntry()
        }
    }
    return selected
}

private fun isRequiredOfficePart(path: String, mimeType: String): Boolean = when (mimeType) {
    DOCX_MIME_TYPE -> path == "word/document.xml" || path.matches(Regex("word/(header|footer)\\d+\\.xml"))
    XLSX_MIME_TYPE -> path == "xl/sharedStrings.xml" || path.matches(Regex("xl/worksheets/sheet\\d+\\.xml"))
    PPTX_MIME_TYPE -> path.matches(Regex("ppt/slides/slide\\d+\\.xml"))
    else -> false
}

private fun extractDocx(parts: Map<String, ByteArray>): String? {
    val ordered = parts.entries.sortedWith(compareBy({ it.key != "word/document.xml" }, { it.key }))
    return ordered.joinToString("\n\n") { (_, bytes) ->
        val document = secureXml(bytes)
        buildString {
            document.documentElement.walkElements { element ->
                when (element.localTag()) {
                    "t" -> append(element.textContent)
                    "tab" -> append('\t')
                    "br", "cr" -> append('\n')
                    "p" -> if (isNotEmpty() && last() != '\n') append('\n')
                }
            }
        }.trim()
    }.takeIf(String::isNotBlank)
}

private fun extractXlsx(parts: Map<String, ByteArray>): String? {
    val sharedStrings = parts["xl/sharedStrings.xml"]?.let(::secureXml)?.elements("si")?.map { item ->
        item.elements("t").joinToString("") { it.textContent }
    }.orEmpty()
    val sheets = parts.entries.filter { it.key.startsWith("xl/worksheets/sheet") }.sortedBy { numericSuffix(it.key) }
    return sheets.mapIndexedNotNull { index, (_, bytes) ->
        val rows = secureXml(bytes).elements("row").mapNotNull { row ->
            row.childElements("c").map { cell ->
                val type = cell.getAttribute("t")
                val raw = cell.elements("v").firstOrNull()?.textContent
                    ?: cell.elements("t").firstOrNull()?.textContent.orEmpty()
                if (type == "s") raw.toIntOrNull()?.let(sharedStrings::getOrNull).orEmpty() else raw
            }.joinToString("\t").trimEnd()
        }.filter(String::isNotBlank)
        if (rows.isEmpty()) null else "工作表 ${index + 1}\n${rows.joinToString("\n")}"
    }.joinToString("\n\n").takeIf(String::isNotBlank)
}

private fun extractPptx(parts: Map<String, ByteArray>): String? = parts.entries
    .sortedBy { numericSuffix(it.key) }
    .mapIndexedNotNull { index, (_, bytes) ->
        val lines = secureXml(bytes).elements("p").map { paragraph ->
            paragraph.elements("t").joinToString("") { it.textContent }
        }.filter(String::isNotBlank)
        if (lines.isEmpty()) null else "第 ${index + 1} 页\n${lines.joinToString("\n")}"
    }.joinToString("\n\n").takeIf(String::isNotBlank)

private fun secureXml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = true
    isExpandEntityReferences = false
    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
    runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
    runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
}.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

private fun org.w3c.dom.Document.elements(localName: String): List<Element> = documentElement.elements(localName)

private fun Element.elements(localName: String): List<Element> = buildList {
    walkElements { if (it.localTag() == localName) add(it) }
}

private fun Element.childElements(localName: String): List<Element> = buildList {
    for (index in 0 until childNodes.length) {
        val child = childNodes.item(index)
        if (child is Element && child.localTag() == localName) add(child)
    }
}

private fun Element.walkElements(visitor: (Element) -> Unit) {
    visitor(this)
    for (index in 0 until childNodes.length) {
        val child: Node = childNodes.item(index)
        if (child is Element) child.walkElements(visitor)
    }
}

private fun Element.localTag(): String = localName ?: tagName.substringAfter(':')

private fun safeOfficePath(raw: String): String? {
    val normalized = raw.replace('\\', '/')
    if (normalized.startsWith('/') || normalized.contains('\u0000')) return null
    // Normal DOCX/XLSX/PPTX archives commonly retain explicit directory entries such as
    // `word/` and `_rels/`. They never carry content, but still pass through this path check;
    // ignore only one terminal directory separator before validating all real segments.
    val path = normalized.removeSuffix("/")
    if (path.isBlank()) return null
    val parts = path.split('/')
    if (parts.any { it.isBlank() || it == "." || it == ".." || it.contains(':') }) return null
    return parts.joinToString("/")
}

private fun numericSuffix(path: String): Int = Regex("(\\d+)(?=\\.xml$)").find(path)?.value?.toIntOrNull() ?: Int.MAX_VALUE

private const val MAX_OFFICE_ZIP_ENTRIES = 2_000
private const val MAX_OFFICE_XML_PART_BYTES = 4L * 1024L * 1024L
private const val MAX_OFFICE_SELECTED_XML_BYTES = 12L * 1024L * 1024L
private const val MAX_OFFICE_PREVIEW_CHARS = 128 * 1024
