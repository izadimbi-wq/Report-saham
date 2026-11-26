package com.izadimbi.reportsaham

import android.content.Context
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.zip.ZipInputStream

object XlsxReader {
    fun read(context: Context, uri: Uri): List<List<String>> {
        val entries = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "فایل Excel قابل خواندن نیست." }
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    entries[e.name] = zip.readBytes()
                }
            }
        }
        val shared = parseSharedStrings(entries["xl/sharedStrings.xml"])
        val sheetName = findFirstSheet(entries)
        val xml = entries[sheetName] ?: error("برگه اول Excel پیدا نشد.")
        return parseSheet(xml, shared)
    }

    private fun findFirstSheet(entries: Map<String, ByteArray>): String {
        val workbook = entries["xl/workbook.xml"] ?: return "xl/worksheets/sheet1.xml"
        val parser = newParser(workbook)
        var relId = "rId1"
        var inFirstSheet = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                relId = parser.getAttributeValue(null, "r:id") ?: "rId1"
                inFirstSheet = true
                break
            }
        }
        val rels = entries["xl/_rels/workbook.xml.rels"] ?: return "xl/worksheets/sheet1.xml"
        val rp = newParser(rels)
        while (rp.next() != XmlPullParser.END_DOCUMENT) {
            if (rp.eventType == XmlPullParser.START_TAG && rp.name == "Relationship" && rp.getAttributeValue(null, "Id") == relId) {
                val target = rp.getAttributeValue(null, "Target") ?: "worksheets/sheet1.xml"
                return if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
            }
        }
        return "xl/worksheets/sheet1.xml"
    }

    private fun parseSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val out = mutableListOf<String>()
        val p = newParser(bytes)
        var collecting = false
        val sb = StringBuilder()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "si") { sb.clear(); collecting = true }
            else if (collecting && p.eventType == XmlPullParser.TEXT) sb.append(p.text)
            else if (p.eventType == XmlPullParser.END_TAG && p.name == "si") { out.add(sb.toString()); collecting = false }
        }
        return out
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val rows = mutableListOf<MutableMap<Int,String>>()
        val p = newParser(bytes)
        var row: MutableMap<Int,String>? = null
        var currentCol = 0
        var type = ""
        var value = ""
        var inV = false
        var inT = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> row = mutableMapOf()
                    "c" -> { currentCol = colIndex(p.getAttributeValue(null,"r") ?: "A1"); type = p.getAttributeValue(null,"t") ?: ""; value = "" }
                    "v" -> { inV = true; value = "" }
                    "t" -> { inT = true; value = "" }
                }
                XmlPullParser.TEXT -> if (inV || inT) value += p.text
                XmlPullParser.END_TAG -> when (p.name) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "c" -> {
                        var v = value
                        if (type == "s") v = shared.getOrNull(v.toIntOrNull() ?: -1) ?: ""
                        row?.set(currentCol, v)
                    }
                    "row" -> row?.let { rows.add(it) }
                }
            }
        }
        val max = rows.maxOfOrNull { it.keys.maxOrNull() ?: -1 } ?: -1
        return rows.map { r -> (0..max).map { i -> r[i] ?: "" } }
    }

    private fun colIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }
        var n = 0
        for (ch in letters) n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        return n - 1
    }

    private fun newParser(bytes: ByteArray): XmlPullParser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(bytes.inputStream(), "UTF-8") }
}
