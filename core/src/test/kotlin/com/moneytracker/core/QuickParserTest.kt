package com.moneytracker.core

import com.moneytracker.core.QuickParser.CategoryRef
import com.moneytracker.core.QuickParser.Context
import com.moneytracker.core.QuickParser.HistoryItem
import com.moneytracker.core.QuickParser.KnownBill
import com.moneytracker.core.QuickParser.Note
import com.moneytracker.core.QuickParser.WalletRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickParserTest {
    // Kategori mengikuti kategori bawaan aplikasi (Seed).
    private val snack = CategoryRef(1, "Snack", CategoryKind.EXPENSE, parentId = 100)
    private val bensin = CategoryRef(2, "Bensin", CategoryKind.EXPENSE, parentId = 101)
    private val parkir = CategoryRef(3, "Parkir", CategoryKind.EXPENSE, parentId = 101)
    private val wifi = CategoryRef(4, "WiFi", CategoryKind.EXPENSE, parentId = 102)
    private val makan = CategoryRef(5, "Makan", CategoryKind.EXPENSE, parentId = 100)
    private val gaji = CategoryRef(10, "Gaji bulanan", CategoryKind.INCOME)
    private val lain = CategoryRef(11, "Pendapatan lainnya", CategoryKind.INCOME)
    private val categories = listOf(snack, bensin, parkir, wifi, makan, gaji, lain)

    private val bca = WalletRef(1, "BCA", "BCA")
    private val gopay = WalletRef(2, "GoPay", "GOPAY")
    private val tunai = WalletRef(3, "Dompet", "CASH")
    private val ctx = Context(listOf(bca, gopay, tunai), categories)

    private fun one(text: String, c: Context = ctx) = QuickParser.parse(text, c).single()

    @Test fun jajanInAllSpellings() {
        for (text in listOf("jajan 10k", "10k jajan", "10000 jajan", "jajan 10ribu", "jajan 10rb", "jajan 10.000", "Jajan Rp10.000", "jajan 10 ribu", "JAJAN 10K")) {
            val d = one(text)
            assertFalse(d.income, text)
            assertEquals(10_000L, d.amount, text)
            assertEquals(snack.id, d.categoryId, text)
            assertEquals(bca.id, d.walletId, text) // wallet default = pertama
            assertTrue(d.notes.isEmpty(), "$text: ${d.notes}")
        }
    }

    @Test fun amountFormats() {
        val cases = mapOf(
            "gaji 1,5jt" to 1_500_000L, "gaji 1.5 juta" to 1_500_000L, "gaji 1jt500" to 1_500_000L, "gaji 1.500.000" to 1_500_000L,
            "gaji 100000" to 100_000L, "jajan sepuluh ribu" to 10_000L, "jajan seratus lima puluh ribu" to 150_000L,
            "gaji dua setengah juta" to 2_500_000L, "gaji satu juta lima ratus ribu" to 1_500_000L, "jajan goceng" to 5_000L,
            "jajan ceban" to 10_000L, "jajan seribu" to 1_000L, "jajan dua belas ribu" to 12_000L, "gaji sejuta" to 1_000_000L,
        )
        for ((text, expected) in cases) assertEquals(expected, one(text).amount, text)
    }

    @Test fun incomeVersusExpense() {
        assertTrue(one("gaji 100000").income)
        assertEquals(gaji.id, one("gaji 100000").categoryId)
        assertTrue(one("gaji masuk 5jt").income)
        assertTrue(one("terima transferan 1,5jt").income)
        assertTrue(one("bonus 500rb").income)
        assertFalse(one("beli bensin 50rb").income)
        assertFalse(one("bayar wifi 300rb").income)
    }

    @Test fun typosAreUnderstood() {
        val g = one("gji masuk 5jt")
        assertTrue(g.income)
        assertEquals(gaji.id, g.categoryId)
        assertEquals(snack.id, one("jjan 10rb").categoryId)
        assertEquals(bensin.id, one("bensn 20rb").categoryId)
    }

    @Test fun gluedWordsAndNumbers() {
        val d = one("bensin50rb")
        assertEquals(50_000L, d.amount)
        assertEquals(bensin.id, d.categoryId)
        assertEquals("Bensin", d.description)
    }

    @Test fun noKeywordDefaultsToExpenseWithNote() {
        val d = one("xyz 10rb")
        assertFalse(d.income)
        assertTrue(Note.TYPE_GUESSED in d.notes)
    }

    @Test fun smallBareNumberIsThousandsWithNote() {
        val d = one("jajan 10")
        assertEquals(10_000L, d.amount)
        assertTrue(Note.AMOUNT_ASSUMED_THOUSANDS in d.notes)
    }

    @Test fun quantityIsIgnoredWhenRealAmountExists() {
        val d = one("beli 2 kopi 30rb")
        assertEquals(30_000L, d.amount)
        assertTrue(d.notes.isEmpty())
    }

    @Test fun twoTransactionsInOneLine() {
        val r = QuickParser.parse("bensin 50rb parkir 5rb", ctx)
        assertEquals(2, r.size)
        assertEquals(listOf(50_000L, 5_000L), r.map { it.amount })
        assertEquals(listOf(bensin.id, parkir.id), r.map { it.categoryId })
    }

    @Test fun walletByNameAndProvider() {
        assertEquals(gopay.id, one("jajan 10rb gopay").walletId)
        assertEquals(gopay.id, one("jajan 10rb pakai gopay").walletId)
        assertEquals("Jajan", one("jajan 10rb pakai gopay").description)
        assertEquals(tunai.id, one("parkir 5rb cash").walletId)
        assertEquals(bca.id, one("gaji masuk 5jt ke bca").walletId)
    }

    @Test fun walletByPosition() {
        assertEquals(gopay.id, one("jajan 10rb rek 2").walletId)
        assertEquals(tunai.id, one("jajan 10rb rek3").walletId)
    }

    @Test fun walletNamedRekMatchesByName() {
        val c = Context(listOf(WalletRef(7, "Rek 2"), WalletRef(8, "Rek 1")), categories)
        assertEquals(8L, one("jajan 10rb rek 1", c).walletId)
    }

    @Test fun batchExampleFromUser() {
        val text = """
            jajan 10rb
            bensin50rb
            gaji 100000
            semuanya masuk ke rek 1
            uang bayar wifi
            rek 2
        """.trimIndent()
        val c = Context(listOf(gopay, bca), categories) // rek 1 = GoPay, rek 2 = BCA
        val r = QuickParser.parse(text, c)
        assertEquals(4, r.size)

        assertEquals(listOf(false, false, true, false), r.map { it.income })
        assertEquals(listOf(10_000L, 50_000L, 100_000L, null), r.map { it.amount })
        assertEquals(listOf(snack.id, bensin.id, gaji.id, wifi.id), r.map { it.categoryId })
        assertEquals(listOf(gopay.id, gopay.id, gopay.id, bca.id), r.map { it.walletId })
        assertTrue(Note.NO_AMOUNT in r[3].notes)
        assertEquals("Uang bayar wifi", r[3].description)
    }

    @Test fun missingAmountTakenFromSubscription() {
        val c = Context(listOf(bca, gopay), categories, bills = listOf(KnownBill("WiFi IndiHome", 350_000, walletId = gopay.id, categoryId = wifi.id, income = false)))
        val d = one("bayar wifi", c)
        assertEquals(350_000L, d.amount)
        assertEquals(gopay.id, d.walletId)
        assertEquals(wifi.id, d.categoryId)
        assertTrue(Note.AMOUNT_FROM_BILL in d.notes)
    }

    @Test fun learnsFromHistory() {
        val history = List(5) { HistoryItem("Seblak level 3", income = false, categoryId = makan.id) } +
            listOf(HistoryItem("Proyek web", income = true, categoryId = lain.id))
        val c = Context(listOf(bca), categories, history)
        assertEquals(makan.id, one("seblak 15rb", c).categoryId) // kamus bilang Snack, riwayat bilang Makan
        val p = one("proyek web 2jt", c)
        assertTrue(p.income) // "proyek" tidak ada di kamus, tapi riwayat mencatatnya sebagai pemasukan
        assertEquals(lain.id, p.categoryId)
    }

    @Test fun globalWalletLineAndListMarkers() {
        val r = QuickParser.parse("- jajan 10rb\n- parkir 5rb\nsemua pakai gopay", ctx)
        assertEquals(2, r.size)
        assertTrue(r.all { it.walletId == gopay.id })
    }

    @Test fun lineWithoutAmountKeepsType() {
        val d = one("gaji masuk")
        assertTrue(d.income)
        assertNull(d.amount)
        assertTrue(Note.NO_AMOUNT in d.notes)
    }

    @Test fun blankAndFillerLinesAreSkipped() {
        assertTrue(QuickParser.parse("\n   \n", ctx).isEmpty())
        assertEquals(1, QuickParser.parse("jajan 10rb\n\nterus\n", ctx).size)
    }
}
