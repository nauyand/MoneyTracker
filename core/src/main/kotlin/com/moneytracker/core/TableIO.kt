package com.moneytracker.core

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/** CSV RFC-4180 (mendukung koma, titik koma, tab; kutip ganda). */
object Csv {
    fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        return listOf(',', ';', '\t', '|').maxByOrNull { d -> firstLine.count { it == d } } ?: ','
    }

    fun parse(text: String, delimiter: Char = detectDelimiter(text)): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val c = s[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                delimiter -> { row.add(field.toString()); field.clear() }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field.clear(); if (row.any { it.isNotBlank() }) rows.add(row); row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        row.add(field.toString())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }

    fun escape(v: String, delimiter: Char = ','): String =
        if (v.any { it == delimiter || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    fun write(rows: List<List<String>>, delimiter: Char = ','): String =
        "﻿" + rows.joinToString("\r\n") { r -> r.joinToString(delimiter.toString()) { escape(it, delimiter) } } + "\r\n"
}

/** Penulis/pembaca XLSX minimal (tanpa dependensi), cukup untuk ekspor/impor tabel transaksi. */
object Xlsx {
    sealed interface Cell
    data class Text(val v: String) : Cell
    data class Num(val v: Double) : Cell

    fun write(sheets: List<Pair<String, List<List<Cell>>>>, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            put("[Content_Types].xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>""")
                append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
                append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
                sheets.indices.forEach { append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""") }
                append("</Types>")
            })
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
                sheets.forEachIndexed { i, (name, _) -> append("""<sheet name="${xml(name.take(31))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
                append("</sheets></workbook>")
            })
            put("xl/_rels/workbook.xml.rels", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                sheets.indices.forEach { append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""") }
                append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
                append("</Relationships>")
            })
            put("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf/></cellStyleXfs><cellXfs count="3"><xf/><xf fontId="1" applyFont="1"/><xf numFmtId="164" applyNumberFormat="1"/></cellXfs></styleSheet>""")
            sheets.forEachIndexed { si, (_, rows) ->
                put("xl/worksheets/sheet${si + 1}.xml", buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                    rows.forEachIndexed { ri, row ->
                        append("<row r=\"${ri + 1}\">")
                        row.forEachIndexed { ci, cell ->
                            val ref = colName(ci) + (ri + 1)
                            when (cell) {
                                is Text -> append("<c r=\"$ref\" t=\"inlineStr\"${if (ri == 0) " s=\"1\"" else ""}><is><t xml:space=\"preserve\">${xml(cell.v)}</t></is></c>")
                                is Num -> append("<c r=\"$ref\" s=\"2\"><v>${if (cell.v % 1.0 == 0.0) cell.v.toLong().toString() else cell.v.toString()}</v></c>")
                            }
                        }
                        append("</row>")
                    }
                    append("</sheetData></worksheet>")
                })
            }
        }
    }

    /** Membaca sheet pertama sebagai tabel string. */
    fun readFirstSheet(input: InputStream): List<List<String>> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name == "xl/sharedStrings.xml" || e.name.startsWith("xl/worksheets/sheet") || e.name == "xl/workbook.xml") {
                    val bos = ByteArrayOutputStream(); zip.copyTo(bos); files[e.name] = bos.toByteArray()
                }
                e = zip.nextEntry
            }
        }
        val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val shared = files["xl/sharedStrings.xml"]?.let { bytes ->
            val doc = dbf.newDocumentBuilder().parse(bytes.inputStream())
            val sis = doc.getElementsByTagName("si")
            (0 until sis.length).map { i ->
                val ts = (sis.item(i) as org.w3c.dom.Element).getElementsByTagName("t")
                (0 until ts.length).joinToString("") { ts.item(it).textContent }
            }
        } ?: emptyList()
        val sheetName = files.keys.filter { it.startsWith("xl/worksheets/sheet") }.minByOrNull { it.filter(Char::isDigit).toIntOrNull() ?: 99 }
            ?: return emptyList()
        val doc = dbf.newDocumentBuilder().parse(files.getValue(sheetName).inputStream())
        val rowNodes = doc.getElementsByTagName("row")
        val result = mutableListOf<List<String>>()
        for (i in 0 until rowNodes.length) {
            val rowEl = rowNodes.item(i) as org.w3c.dom.Element
            val cells = rowEl.getElementsByTagName("c")
            val values = sortedMapOf<Int, String>()
            for (j in 0 until cells.length) {
                val c = cells.item(j) as org.w3c.dom.Element
                val ref = c.getAttribute("r")
                val col = if (ref.isNotEmpty()) colIndex(ref) else j
                val t = c.getAttribute("t")
                val v = c.getElementsByTagName("v").item(0)?.textContent
                val text = when (t) {
                    "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    "inlineStr" -> c.getElementsByTagName("t").let { ts -> (0 until ts.length).joinToString("") { ts.item(it).textContent } }
                    else -> v ?: ""
                }
                values[col] = text
            }
            if (values.isEmpty()) continue
            val maxCol = values.lastKey()
            val row = (0..maxCol).map { values[it] ?: "" }
            if (row.any { it.isNotBlank() }) result += row
        }
        return result
    }

    fun colName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.insert(0, ('A' + r)); n = (n - 1) / 26 }
        return sb.toString()
    }

    fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) { if (!ch.isLetter()) break; n = n * 26 + (ch.uppercaseChar() - 'A' + 1) }
        return n - 1
    }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it == '\t' || it == '\n' || it == '\r' || it >= ' ' }
}

/** Pemetaan kolom file mutasi bank → transaksi. */
data class ImportMapping(
    val dateCol: Int,
    val descriptionCol: Int?,
    /** Satu kolom nominal bertanda (negatif = keluar). */
    val amountCol: Int? = null,
    /** Atau dua kolom terpisah: debit (keluar) & kredit (masuk). */
    val debitCol: Int? = null,
    val creditCol: Int? = null,
    val hasHeader: Boolean = true,
    /** Jika true, nominal positif dianggap pengeluaran (mis. ekspor e-wallet). */
    val positiveIsExpense: Boolean = false,
)

data class ImportedRow(val rowIndex: Int, val dateTime: LocalDateTime, val description: String, val amount: Long)

data class ImportResult(val rows: List<ImportedRow>, val errors: List<String>)

object Importer {
    private val ID_MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "peb" to 2, "mar" to 3, "apr" to 4, "mei" to 5, "may" to 5, "jun" to 6, "jul" to 7,
        "agu" to 8, "agt" to 8, "aug" to 8, "sep" to 9, "okt" to 10, "oct" to 10, "nov" to 11, "nop" to 11, "des" to 12, "dec" to 12,
    )

    private val patterns: List<DateTimeFormatter> = listOf(
        "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm", "dd/MM/yyyy", "d/M/yyyy",
        "dd-MM-yyyy HH:mm:ss", "dd-MM-yyyy HH:mm", "dd-MM-yyyy", "d-M-yyyy",
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
        "dd/MM/yy", "dd.MM.yyyy", "yyyy/MM/dd",
    ).map { DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ROOT) }

    fun parseDate(raw: String): LocalDateTime? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        for (f in patterns) {
            try {
                val parsed = f.parseBest(s, LocalDateTime::from, LocalDate::from)
                return when (parsed) {
                    is LocalDateTime -> parsed
                    is LocalDate -> parsed.atTime(LocalTime.NOON)
                    else -> null
                }
            } catch (_: Exception) { }
        }
        // "01 Nov 2026", "1 November 2026", "01-Nov-26"
        val m = Regex("""^(\d{1,2})[\s\-/]+([A-Za-z]+)[\s\-/]+(\d{2,4})""").find(s)
        if (m != null) {
            val day = m.groupValues[1].toInt()
            val month = ID_MONTHS[m.groupValues[2].take(3).lowercase(Locale.ROOT)] ?: return null
            var year = m.groupValues[3].toInt(); if (year < 100) year += 2000
            return runCatching { LocalDate.of(year, month, day).atTime(LocalTime.NOON) }.getOrNull()
        }
        // Nomor seri tanggal Excel
        s.toDoubleOrNull()?.let { serial ->
            if (serial in 20000.0..80000.0) {
                val base = LocalDate.of(1899, 12, 30)
                val days = serial.toLong()
                val secs = ((serial - days) * 86400).toLong()
                return base.plusDays(days).atStartOfDay().plusSeconds(secs).let { if (secs == 0L) it.withHour(12) else it }
            }
        }
        return null
    }

    fun apply(table: List<List<String>>, map: ImportMapping): ImportResult {
        val rows = mutableListOf<ImportedRow>()
        val errors = mutableListOf<String>()
        val start = if (map.hasHeader) 1 else 0
        for (i in start until table.size) {
            val r = table[i]
            fun cell(c: Int?) = c?.let { r.getOrNull(it) }?.trim().orEmpty()
            val date = parseDate(cell(map.dateCol))
            if (date == null) { errors += "Baris ${i + 1}: tanggal \"${cell(map.dateCol)}\" tidak dikenali"; continue }
            val amount: Long? = if (map.amountCol != null) {
                Fmt.parseAmount(cell(map.amountCol))?.let { if (map.positiveIsExpense) -it else it }
            } else {
                val d = Fmt.parseAmount(cell(map.debitCol))?.let { kotlin.math.abs(it) } ?: 0
                val c = Fmt.parseAmount(cell(map.creditCol))?.let { kotlin.math.abs(it) } ?: 0
                if (d == 0L && c == 0L) null else c - d
            }
            if (amount == null || amount == 0L) { errors += "Baris ${i + 1}: nominal kosong/tidak valid"; continue }
            rows += ImportedRow(i, date, cell(map.descriptionCol), amount)
        }
        return ImportResult(rows, errors)
    }

    /** Kunci duplikat: tanggal (hari) + nominal + deskripsi dinormalisasi. */
    fun duplicateKey(date: LocalDate, amount: Long, description: String): String =
        "$date|$amount|" + description.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }.take(40)
}
