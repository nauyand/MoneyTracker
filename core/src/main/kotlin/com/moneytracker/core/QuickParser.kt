package com.moneytracker.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Pengenal kalimat "Ketik cepat": mengubah teks bebas seperti "jajan 10rb" atau "gaji masuk 5jt bca"
 * menjadi draf transaksi pemasukan/pengeluaran.
 *
 * Bukan AI/LLM. Cara kerjanya:
 *  1. Nominal dikenali dari pola teks (10000, 10.000, 10rb, 10k, 1,5jt, 1jt500, sepuluh ribu, goceng, …).
 *  2. Jenis (pemasukan/pengeluaran) ditentukan dengan sistem poin: kata kunci di [KEYWORDS] bernilai 2,
 *     ditambah "suara" dari riwayat transaksi pengguna. Tanpa petunjuk sama sekali → pengeluaran.
 *  3. Kategori: riwayat pengguna dulu, lalu nama kategori, lalu petunjuk kamus.
 *  4. Wallet: nama wallet / kode penyedia ("bca", "gopay"), atau "rek 1" = wallet urutan pertama.
 *
 * Banyak baris sekaligus didukung. Baris yang hanya berisi wallet:
 *  - "semua ke bca" / "semuanya masuk ke rek 1" → berlaku untuk semua transaksi;
 *  - "rek 2" → berlaku untuk baris transaksi tepat di atasnya.
 *
 * Hasilnya selalu draf: aplikasi menampilkannya dulu agar pengguna bisa mengoreksi sebelum disimpan.
 */
object QuickParser {

    data class WalletRef(val id: Long, val name: String, val provider: String = "")
    data class CategoryRef(val id: Long, val name: String, val kind: CategoryKind, val parentId: Long? = null)
    data class HistoryItem(val description: String, val income: Boolean, val categoryId: Long?)
    data class KnownBill(val name: String, val amount: Long, val walletId: Long?, val categoryId: Long?, val income: Boolean)

    /**
     * Data pengguna yang dipakai untuk menebak. Buat sekali per perubahan data, lalu pakai berulang.
     * @param wallets urutan tampil — "rek 1" = elemen pertama.
     */
    class Context(
        val wallets: List<WalletRef>,
        val categories: List<CategoryRef> = emptyList(),
        history: List<HistoryItem> = emptyList(),
        val bills: List<KnownBill> = emptyList(),
        val defaultWalletId: Long? = wallets.firstOrNull()?.id,
    ) {
        internal val categoryKind: Map<Long, CategoryKind> = categories.associate { it.id to it.kind }
        internal val walletIds: Set<Long> = wallets.map { it.id }.toSet()
        internal val walletAliases: List<Pair<Long, List<String>>> = wallets.flatMap { w ->
            (listOf(tokenize(w.name)) + providerAliases(w.provider).map { listOf(it) })
                .filter { it.isNotEmpty() }.map { w.id to it }
        }

        /** kata → [jumlah sebagai pemasukan, jumlah sebagai pengeluaran] */
        internal val typeVotes: Map<String, IntArray>

        /** kata → (kategori → jumlah) */
        internal val categoryVotes: Map<String, Map<Long, Int>>

        init {
            val types = HashMap<String, IntArray>()
            val cats = HashMap<String, HashMap<Long, Int>>()
            for (h in history) {
                for (w in meaningfulWords(h.description)) {
                    types.getOrPut(w) { IntArray(2) }[if (h.income) 0 else 1]++
                    if (h.categoryId != null) cats.getOrPut(w) { HashMap() }.merge(h.categoryId, 1, Int::plus)
                }
            }
            typeVotes = types
            categoryVotes = cats
        }
    }

    enum class Note {
        /** Tidak ada nominal; pengguna perlu mengisinya. */
        NO_AMOUNT,

        /** Angka kecil tanpa satuan (mis. "jajan 10") dianggap ribuan. */
        AMOUNT_ASSUMED_THOUSANDS,

        /** Nominal diambil dari langganan/tagihan rutin yang namanya cocok. */
        AMOUNT_FROM_BILL,

        /** Tidak ada petunjuk jenis; dianggap pengeluaran. */
        TYPE_GUESSED,
    }

    data class Draft(
        /** Baris asli yang menghasilkan draf ini. */
        val source: String,
        val income: Boolean,
        val amount: Long?,
        val walletId: Long?,
        val categoryId: Long?,
        val description: String,
        val notes: Set<Note> = emptySet(),
    ) {
        val type: TxType get() = if (income) TxType.INCOME else TxType.EXPENSE
    }

    // ===================================================================================
    // Parsing
    // ===================================================================================

    fun parse(text: String, ctx: Context): List<Draft> {
        class Line(val source: String, val segments: List<Segment>, val inlineWallet: Long?) {
            var attachedWallet: Long? = null
        }

        val lines = mutableListOf<Line>()
        var globalWallet: Long? = null

        for (raw in text.lines()) {
            val source = raw.trim()
            val toks = tokenize(source.replace(LIST_MARKER, ""))
            if (toks.isEmpty()) continue

            val wallet = findWallet(toks, ctx)
            val items = toItems(toks.filterIndexed { i, _ -> wallet == null || i !in wallet.consumed })
            val hasAmount = items.any { it is Item.Amt }
            val words = items.filterIsInstance<Item.Word>().map { it.text }

            if (!hasAmount && wallet != null && words.all { it in DIRECTIVE_WORDS }) {
                // Baris perintah wallet, bukan transaksi.
                val prev = lines.lastOrNull()
                if (words.any { it in GLOBAL_WORDS } || prev == null) globalWallet = wallet.id else prev.attachedWallet = wallet.id
                continue
            }
            if (!hasAmount && words.all { it in FILLER }) continue

            lines += Line(source, segments(items), wallet?.id)
        }

        return lines.flatMap { l ->
            l.segments.map { seg -> draft(l.source, seg, l.inlineWallet ?: l.attachedWallet, globalWallet, ctx) }
        }
    }

    private fun draft(source: String, seg: Segment, explicitWallet: Long?, globalWallet: Long?, ctx: Context): Draft {
        val canon = seg.words.map(::canonical)
        val notes = mutableSetOf<Note>()

        // ---------- Jenis: sistem poin ----------
        var incomePts = 0.0
        var expensePts = 0.0
        for (w in canon) {
            when (KEYWORDS[w]?.income) {
                true -> incomePts += 2
                false -> expensePts += 2
                null -> {}
            }
            ctx.typeVotes[w]?.let { v ->
                val total = (v[0] + v[1]).toDouble()
                incomePts += v[0] / total
                expensePts += v[1] / total
            }
        }
        val bill = if (seg.amount == null) findBill(canon, ctx) else null
        val income = if (incomePts == expensePts && bill != null) bill.income else incomePts > expensePts
        if (incomePts == 0.0 && expensePts == 0.0 && bill == null) notes += Note.TYPE_GUESSED
        val kind = if (income) CategoryKind.INCOME else CategoryKind.EXPENSE

        // ---------- Kategori ----------
        val categoryId = historyCategory(canon, kind, ctx)
            ?: bill?.categoryId?.takeIf { ctx.categoryKind[it] == kind }
            ?: nameCategory(canon, kind, ctx)
            ?: hintCategory(canon, kind, ctx)

        // ---------- Nominal ----------
        val amount = when {
            seg.amount != null -> {
                if (seg.amount.assumed) notes += Note.AMOUNT_ASSUMED_THOUSANDS
                seg.amount.value
            }
            bill != null -> { notes += Note.AMOUNT_FROM_BILL; bill.amount }
            else -> { notes += Note.NO_AMOUNT; null }
        }

        val walletId = explicitWallet ?: globalWallet ?: bill?.walletId?.takeIf { it in ctx.walletIds } ?: ctx.defaultWalletId
        val description = seg.words.filter { it !in FILLER }.joinToString(" ").replaceFirstChar { it.uppercase() }

        return Draft(source, income, amount, walletId, categoryId, description, notes)
    }

    // ===================================================================================
    // Token & nominal
    // ===================================================================================

    private val LIST_MARKER = Regex("^(?:[-*•]|\\d{1,2}[.)])\\s+")

    /** "bensin50rb" → "bensin 50rb", "rek1" → "rek 1"; tetapi "1jt500" tetap utuh. */
    private val LETTER_DIGIT = Regex("(?<!\\d(?:jt|juta|jeti))(?<=\\p{L})(?=\\d)")
    private const val TRIM = ",.;:!?()\"'=+*/-–•"

    internal fun tokenize(s: String): List<String> =
        s.lowercase().replace(LETTER_DIGIT, " ").split(Regex("\\s+"))
            .map { it.trim { c -> c in TRIM } }
            .filter { it.isNotEmpty() && it != "rp" }

    internal sealed class Item {
        data class Word(val text: String) : Item()
        data class Amt(val value: Long, val strong: Boolean, val assumed: Boolean, val text: String) : Item()
    }

    private class Segment(val words: List<String>, val amount: Item.Amt?)

    private val NUMERIC = Regex("^\\d+(?:[.,]\\d+)*$")
    private val AMOUNT = Regex("^(\\d+(?:[.,]\\d+)*)(rb|ribu|rbu|rebu|k|jt|juta|jeti)?(\\d{1,3})?$")
    private val UNITS = setOf("rb", "ribu", "rbu", "rebu", "k", "jt", "juta", "jeti")
    private val MILLION = setOf("jt", "juta", "jeti")

    /** Nominal dari satu token, mis. "10rb", "1,5jt", "10.000". Null bila bukan nominal. */
    internal fun parseAmount(t: String): Item.Amt? {
        val m = AMOUNT.matchEntire(t) ?: return null
        val (num, unit, tail) = m.destructured
        if (num.count { it.isDigit() } > 13) return null
        val million = unit in MILLION
        if (tail.isNotEmpty() && !million) return null
        val mult = when {
            unit.isEmpty() -> 1L
            million -> 1_000_000L
            else -> 1_000L
        }
        val groups = num.split('.', ',')
        // "10.000" / "1.500.000" = pemisah ribuan; "1,5jt" / "1.5k" = desimal.
        val thousandsSep = groups.size > 1 && groups.drop(1).all { it.length == 3 } && !(million && groups.size == 2)
        val base = when {
            groups.size == 1 -> BigDecimal(num)
            thousandsSep -> BigDecimal(groups.joinToString(""))
            groups.size == 2 -> BigDecimal(groups[0] + "." + groups[1])
            else -> return null
        }
        var value = base.multiply(BigDecimal(mult)).setScale(0, RoundingMode.HALF_UP).toLong()
        // "1jt500" = 1.500.000, "1jt5" = 1.500.000
        if (tail.isNotEmpty()) value += tail.toLong() * when (tail.length) { 1 -> 100_000L; 2 -> 10_000L; else -> 1_000L }
        if (value <= 0) return null
        return Item.Amt(value, strong = unit.isNotEmpty() || thousandsSep || value >= 1000, assumed = false, text = t)
    }

    private val SLANG = mapOf(
        "gopek" to 500L, "seceng" to 1_000L, "noceng" to 2_000L, "goceng" to 5_000L,
        "ceban" to 10_000L, "noban" to 20_000L, "goban" to 50_000L,
    )

    private val DIGIT_WORDS = mapOf(
        "satu" to 1L, "dua" to 2L, "tiga" to 3L, "empat" to 4L, "lima" to 5L,
        "enam" to 6L, "tujuh" to 7L, "delapan" to 8L, "sembilan" to 9L,
    )
    private val MULTIPLIER_WORDS = setOf("ratus", "seratus", "ribu", "seribu", "juta", "sejuta")
    private val NUMBER_WORDS = DIGIT_WORDS.keys + MULTIPLIER_WORDS + setOf("sepuluh", "sebelas", "belas", "puluh", "setengah")

    /** "seratus lima puluh ribu" → 150000, "dua setengah juta" → 2500000. */
    internal fun wordsToNumber(run: List<String>): Long? {
        var total = 0L
        var group = 0L
        var pending = 0L
        var half = false
        for (w in run) {
            when (w) {
                in DIGIT_WORDS -> {
                    if (pending != 0L) return null
                    pending = DIGIT_WORDS.getValue(w)
                }
                "sepuluh" -> group += 10
                "sebelas" -> group += 11
                "belas" -> { group += 10 + pending; pending = 0 }
                "puluh" -> { group += pending * 10; pending = 0 }
                "seratus" -> group += 100
                "ratus" -> { group += pending * 100; pending = 0 }
                "setengah" -> half = true
                "seribu" -> total += 1_000
                "sejuta" -> total += 1_000_000
                "ribu", "juta" -> {
                    val m = if (w == "ribu") 1_000L else 1_000_000L
                    val g = group + pending
                    total += g * m + (if (half) m / 2 else 0) + (if (g == 0L && !half) m else 0)
                    group = 0; pending = 0; half = false
                }
                else -> return null
            }
        }
        total += group + pending
        return total.takeIf { it > 0 }
    }

    private fun toItems(toks: List<String>): List<Item> {
        val out = mutableListOf<Item>()
        var i = 0
        while (i < toks.size) {
            val t = toks[i]
            // "10 ribu", "1,5 jt"
            if (NUMERIC.matches(t) && i + 1 < toks.size && toks[i + 1] in UNITS) {
                val merged = parseAmount(t + toks[i + 1])
                if (merged != null) { out += merged; i += 2; continue }
            }
            val amt = parseAmount(t)
            if (amt != null) { out += amt; i++; continue }
            val slang = SLANG[t]
            if (slang != null) { out += Item.Amt(slang, strong = true, assumed = false, text = t); i++; continue }
            if (t in NUMBER_WORDS) {
                var j = i
                while (j < toks.size && toks[j] in NUMBER_WORDS) j++
                val run = toks.subList(i, j)
                val v = if (run.any { it in MULTIPLIER_WORDS }) wordsToNumber(run) else null
                if (v != null) { out += Item.Amt(v, strong = v >= 1000, assumed = false, text = run.joinToString(" ")); i = j; continue }
            }
            out += Item.Word(t)
            i++
        }
        // "beli 2 kopi 30rb": angka kecil tanpa satuan diabaikan bila ada nominal yang jelas.
        val hasStrong = out.any { it is Item.Amt && it.strong }
        return out.map { item ->
            if (item is Item.Amt && !item.strong) {
                if (hasStrong) Item.Word(item.text) else item.copy(value = item.value * 1000, assumed = true)
            } else {
                item
            }
        }
    }

    /** Satu baris bisa berisi beberapa transaksi: "bensin 50rb parkir 5rb". */
    private fun segments(items: List<Item>): List<Segment> {
        val amtIdx = items.indices.filter { items[it] is Item.Amt }
        fun words(part: List<Item>) = part.filterIsInstance<Item.Word>().map { it.text }
        if (amtIdx.size <= 1) return listOf(Segment(words(items), amtIdx.firstOrNull()?.let { items[it] as Item.Amt }))

        val segs = mutableListOf<Segment>()
        if (amtIdx.first() == 0) {
            // Nominal di depan: "10rb jajan 5rb parkir"
            amtIdx.forEachIndexed { k, a ->
                val end = amtIdx.getOrNull(k + 1) ?: items.size
                segs += Segment(words(items.subList(a + 1, end)), items[a] as Item.Amt)
            }
        } else {
            // Nominal di belakang: "jajan 10rb parkir 5rb" (kata sisa di akhir ikut transaksi terakhir)
            var start = 0
            amtIdx.forEachIndexed { k, a ->
                val end = if (k == amtIdx.lastIndex) items.size else a + 1
                segs += Segment(words(items.subList(start, end)), items[a] as Item.Amt)
                start = a + 1
            }
        }
        return segs
    }

    // ===================================================================================
    // Wallet
    // ===================================================================================

    private class WalletHit(val id: Long, val consumed: Set<Int>)

    private val POSITIONAL = setOf("rek", "rekening", "wallet", "dompet", "akun")

    private fun providerAliases(code: String): List<String> = when (code) {
        "", "CUSTOM", "BANK_OTHER", "EWALLET_OTHER", "DIGITAL_OTHER", "BUSINESS", "SAVINGS", "INVESTMENT", "CREDIT_CARD" -> emptyList()
        "CASH" -> listOf("cash", "tunai", "cas", "kes")
        "SHOPEEPAY" -> listOf("shopeepay", "spay")
        else -> listOf(code.lowercase())
    }

    private fun findWallet(toks: List<String>, ctx: Context): WalletHit? {
        var bestId: Long? = null
        var bestRange: IntRange? = null
        for ((id, alias) in ctx.walletAliases) {
            val at = indexOfSequence(toks, alias)
            if (at >= 0 && (bestRange == null || alias.size > bestRange.count())) {
                bestId = id
                bestRange = at until at + alias.size
            }
        }
        if (bestRange == null) {
            for (i in 0 until toks.size - 1) {
                val n = toks[i + 1].toIntOrNull() ?: continue
                if (toks[i] in POSITIONAL && n in 1..ctx.wallets.size) {
                    bestId = ctx.wallets[n - 1].id
                    bestRange = i..i + 1
                    break
                }
            }
        }
        if (bestId == null || bestRange == null) return null
        val consumed = bestRange.toMutableSet()
        val before = bestRange.first - 1
        if (before >= 0 && toks[before] in PREPOSITIONS) consumed += before
        return WalletHit(bestId, consumed)
    }

    private fun indexOfSequence(toks: List<String>, seq: List<String>): Int {
        if (seq.isEmpty() || seq.size > toks.size) return -1
        for (i in 0..toks.size - seq.size) if (seq.indices.all { toks[i + it] == seq[it] }) return i
        return -1
    }

    // ===================================================================================
    // Kategori & tagihan
    // ===================================================================================

    private fun historyCategory(canon: List<String>, kind: CategoryKind, ctx: Context): Long? {
        val score = HashMap<Long, Int>()
        for (w in canon) {
            if (w in GENERIC) continue
            ctx.categoryVotes[w]?.forEach { (id, n) -> if (ctx.categoryKind[id] == kind) score.merge(id, n, Int::plus) }
        }
        return score.maxByOrNull { it.value }?.key
    }

    private fun nameCategory(canon: List<String>, kind: CategoryKind, ctx: Context): Long? {
        val cats = ctx.categories.filter { it.kind == kind }
        for (w in canon) {
            if (w.length < 3 || w in FILLER || w in GENERIC) continue
            cats.firstOrNull { it.name.lowercase() == w }?.let { return it.id }
            cats.filter { tokenize(it.name).firstOrNull() == w }
                .minByOrNull { if (it.parentId != null) 0 else 1 }?.let { return it.id }
        }
        return null
    }

    private fun hintCategory(canon: List<String>, kind: CategoryKind, ctx: Context): Long? {
        for (w in canon) {
            for (name in KEYWORDS[w]?.hints.orEmpty()) {
                ctx.categories.firstOrNull { it.kind == kind && it.name.equals(name, ignoreCase = true) }?.let { return it.id }
            }
        }
        return null
    }

    private fun findBill(canon: List<String>, ctx: Context): KnownBill? =
        ctx.bills.mapNotNull { b ->
            val names = meaningfulWords(b.name).toSet()
            val hits = canon.count { it in names }
            if (hits > 0) b to hits else null
        }.maxByOrNull { it.second }?.first

    // ===================================================================================
    // Kamus
    // ===================================================================================

    private class Kw(val income: Boolean?, val hints: List<String>)

    /** Kata kunci → jenis (true = pemasukan, false = pengeluaran, null = netral) + nama kategori yang dicoba. */
    private val KEYWORDS: Map<String, Kw> = buildMap {
        fun income(vararg ws: String, hints: List<String> = emptyList()) = ws.forEach { put(it, Kw(true, hints)) }
        fun expense(vararg ws: String, hints: List<String> = emptyList()) = ws.forEach { put(it, Kw(false, hints)) }
        fun neutral(vararg ws: String, hints: List<String>) = ws.forEach { put(it, Kw(null, hints)) }

        // ---------- Pemasukan ----------
        income("gaji", "gajian", "salary", hints = listOf("Gaji bulanan", "Gaji"))
        income("bonus", "thr", hints = listOf("Bonus"))
        income("komisi", hints = listOf("Komisi"))
        income("freelance", "honor", "upah", "bayaran", hints = listOf("Freelance"))
        income("jual", "jualan", "penjualan", "omzet", "omset", "laba", "untung", hints = listOf("Bisnis"))
        income("reimburse", "reimbursement", hints = listOf("Reimbursement"))
        income("angpao", "angpau", hints = listOf("Hadiah"))
        income(
            "masuk", "pemasukan", "terima", "nerima", "diterima", "cair", "pencairan", "dikasih", "dikasi", "dikirim",
            "kiriman", "transferan", "dapat", "dapet", "refund", "cashback", "dividen", "income",
            hints = listOf("Pendapatan lainnya"),
        )

        // ---------- Pengeluaran ----------
        expense("jajan", "jajanan", "cemilan", "camilan", "snack", "gorengan", "seblak", "cilok", "batagor", hints = listOf("Snack"))
        expense("makan", "bakso", "nasi", "mie", "soto", "warteg", "padang", hints = listOf("Makan"))
        expense("sarapan", hints = listOf("Sarapan"))
        expense("minum", "es", "teh", "jus", hints = listOf("Minum"))
        expense("kopi", "ngopi", hints = listOf("Kopi"))
        expense("galon", hints = listOf("Air mineral", "Kebutuhan rumah tangga"))
        expense("belanja", "belanjaan", "pasar", hints = listOf("Belanja dapur"))
        expense("sembako", "beras", "minyak", "telur", hints = listOf("Sembako"))
        expense("sayur", "sayuran", hints = listOf("Sayuran"))
        expense("sabun", "sampo", "shampo", "gas", "elpiji", "laundry", "deterjen", hints = listOf("Kebutuhan rumah tangga"))
        expense("bensin", hints = listOf("Bensin"))
        expense("pertalite", hints = listOf("Pertalite", "Bensin"))
        expense("pertamax", hints = listOf("Pertamax", "Bensin"))
        expense("solar", hints = listOf("Solar"))
        expense("parkir", hints = listOf("Parkir"))
        expense("tol", hints = listOf("Tol"))
        expense("grab", "gojek", "ojol", "ojek", "maxim", "taksi", "taxi", hints = listOf("Transportasi online"))
        expense("bus", "busway", "kereta", "krl", "mrt", "lrt", "angkot", hints = listOf("Transportasi umum"))
        expense("servis", "service", "bengkel", hints = listOf("Servis kendaraan"))
        expense("oli", hints = listOf("Oli"))
        expense("pulsa", hints = listOf("Pulsa"))
        expense("kuota", hints = listOf("Paket data"))
        expense("listrik", "token", "pln", hints = listOf("Listrik"))
        expense("pdam", hints = listOf("Air"))
        expense("wifi", "indihome", "biznet", "firstmedia", hints = listOf("WiFi", "Internet"))
        expense("internet", hints = listOf("Internet", "WiFi"))
        expense("netflix", "spotify", "youtube", "langganan", hints = listOf("Langganan aplikasi"))
        expense("cicilan", "angsuran", "kredit", hints = listOf("Cicilan"))
        expense("bpjs", hints = listOf("BPJS"))
        expense("obat", "apotek", "apotik", hints = listOf("Obat"))
        expense("dokter", "klinik", hints = listOf("Kesehatan"))
        expense("nonton", "bioskop", hints = listOf("Hiburan"))
        expense("baju", "kaos", "celana", hints = listOf("Pakaian"))
        expense("sepatu", hints = listOf("Sepatu"))
        expense("donasi", "sedekah", "infak", "infaq", "zakat", hints = listOf("Donasi"))
        expense("kado", hints = listOf("Hadiah"))
        expense("nongkrong", hints = listOf("Nongkrong"))
        expense(
            "beli", "bayar", "keluar", "pengeluaran", "ongkir", "ongkos", "sewa", "kos", "kost", "kontrakan",
            "tagihan", "rokok", "potong", "biaya",
        )

        neutral("hadiah", hints = listOf("Hadiah"))
    }

    /** Kata pengantar wallet: "ke bca", "pakai gopay". */
    private val PREPOSITIONS = setOf("ke", "dari", "dr", "pakai", "pake", "pakek", "via", "lewat", "di", "dgn", "dengan")

    /** Kata yang tidak bermakna untuk menebak dan dibuang dari deskripsi. */
    private val FILLER = PREPOSITIONS + setOf("dan", "terus", "trus", "lalu", "sama", "sm", "&", "tadi", "aja", "saja", "nya")

    /** Kata umum yang dipakai untuk banyak kategori, sehingga tidak dipakai untuk menebak kategori. */
    private val GENERIC = setOf(
        "beli", "bayar", "keluar", "pengeluaran", "masuk", "pemasukan", "terima", "uang", "duit", "buat", "untuk", "utk", "biaya", "tagihan",
    )

    private val GLOBAL_WORDS = setOf("semua", "semuanya", "smua", "smuanya", "sisanya", "sisa", "seluruhnya", "all")

    /** Kata yang boleh ada di baris perintah wallet ("semuanya masuk ke rek 1"). */
    private val DIRECTIVE_WORDS = GLOBAL_WORDS + FILLER + setOf("masuk", "keluar", "yang", "yg", "lain", "lainnya", "itu", "ini", "transaksi", "catat")

    /** Ejaan baku dari sebuah kata: kata kunci yang cocok (termasuk awalan & salah ketik 1 huruf), atau kata itu sendiri. */
    internal fun canonical(w: String): String {
        if (w in KEYWORDS || w.length < 3 || w.none { it.isLetter() }) return w
        KEYWORDS.keys.firstOrNull { it.length >= 4 && w.length > it.length && w.startsWith(it) }?.let { return it }
        val near = KEYWORDS.keys.filter { it.length >= 4 && abs(it.length - w.length) <= 1 && levenshtein(it, w) == 1 }
        return near.singleOrNull() ?: w
    }

    private fun meaningfulWords(text: String): List<String> =
        tokenize(text).filter { it.length >= 3 && it.any(Char::isLetter) && it !in FILLER }.map(::canonical).distinct()

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
