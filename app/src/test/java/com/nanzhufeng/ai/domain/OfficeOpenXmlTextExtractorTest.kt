package com.nanzhufeng.ai.domain

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeOpenXmlTextExtractorTest {
    @Test fun `DOCX paragraphs are extracted as inert text`() {
        val bytes = zipBytes(
            "_rels/" to "",
            "word/" to "",
            "word/_rels/" to "",
            "word/document.xml" to """
                <w:document xmlns:w="urn:w"><w:body>
                  <w:p><w:r><w:t>第一段</w:t></w:r></w:p>
                  <w:p><w:r><w:t>第二段</w:t></w:r></w:p>
                </w:body></w:document>
            """.trimIndent(),
        )

        val result = requireNotNull(extractOfficeOpenXmlText(bytes, DOCX_MIME_TYPE))

        assertTrue(result.text.contains("第一段"))
        assertTrue(result.text.contains("第二段"))
    }

    @Test fun `XLSX shared strings and PPTX slide order are readable`() {
        val xlsx = zipBytes(
            "xl/sharedStrings.xml" to "<sst><si><t>名称</t></si><si><t>南枫 AI</t></si></sst>",
            "xl/worksheets/sheet1.xml" to "<worksheet><sheetData><row><c t=\"s\"><v>0</v></c><c t=\"s\"><v>1</v></c></row></sheetData></worksheet>",
        )
        val pptx = zipBytes(
            "ppt/slides/slide2.xml" to "<p:sld xmlns:p=\"urn:p\" xmlns:a=\"urn:a\"><a:p><a:r><a:t>第二页</a:t></a:r></a:p></p:sld>",
            "ppt/slides/slide1.xml" to "<p:sld xmlns:p=\"urn:p\" xmlns:a=\"urn:a\"><a:p><a:r><a:t>第一页</a:t></a:r></a:p></p:sld>",
        )

        assertTrue(requireNotNull(extractOfficeOpenXmlText(xlsx, XLSX_MIME_TYPE)).text.contains("名称\t南枫 AI"))
        val slides = requireNotNull(extractOfficeOpenXmlText(pptx, PPTX_MIME_TYPE)).text
        assertTrue(slides.indexOf("第一页") < slides.indexOf("第二页"))
    }

    @Test fun `unsafe OOXML paths and doctypes fail closed`() {
        assertNull(extractOfficeOpenXmlText(zipBytes("../word/document.xml" to "<w:document/>"), DOCX_MIME_TYPE))
        assertNull(extractOfficeOpenXmlText(zipBytes("word/document.xml" to "<!DOCTYPE x [<!ENTITY y SYSTEM \"file:///x\">]><x>&y;</x>"), DOCX_MIME_TYPE))
    }

    private fun zipBytes(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { output ->
            entries.forEach { (name, value) ->
                output.putNextEntry(ZipEntry(name))
                output.write(value.toByteArray())
                output.closeEntry()
            }
        }
        bytes.toByteArray()
    }
}
