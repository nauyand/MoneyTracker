package com.moneytracker.app.data

import com.moneytracker.core.CategoryKind
import com.moneytracker.core.Nature
import com.moneytracker.core.TxType
import com.moneytracker.core.WalletType

/** Penyedia rekening/wallet. Logo resmi adalah merek dagang; aplikasi memakai badge inisial + warna. */
data class Provider(val code: String, val name: String, val type: WalletType, val color: Long, val initials: String)

object Providers {
    val all = listOf(
        // Bank konvensional
        Provider("BCA", "BCA", WalletType.BANK, 0xFF0060AF, "BCA"),
        Provider("BRI", "BRI", WalletType.BANK, 0xFF00529C, "BRI"),
        Provider("BNI", "BNI", WalletType.BANK, 0xFFF15A23, "BNI"),
        Provider("MANDIRI", "Mandiri", WalletType.BANK, 0xFF003D79, "MDR"),
        Provider("BTN", "BTN", WalletType.BANK, 0xFF0F4C9A, "BTN"),
        Provider("CIMB", "CIMB Niaga", WalletType.BANK, 0xFF7A0019, "CIMB"),
        Provider("DANAMON", "Danamon", WalletType.BANK, 0xFFF7A800, "DNM"),
        Provider("PERMATA", "Permata", WalletType.BANK, 0xFF00843D, "PMT"),
        Provider("OCBC", "OCBC", WalletType.BANK, 0xFFE30613, "OCBC"),
        Provider("MAYBANK", "Maybank", WalletType.BANK, 0xFFFFC72C, "MAY"),
        Provider("MEGA", "Bank Mega", WalletType.BANK, 0xFFF39200, "MEGA"),
        Provider("BSI", "Bank Syariah Indonesia", WalletType.BANK, 0xFF00A39D, "BSI"),
        Provider("BANK_OTHER", "Bank lainnya", WalletType.BANK, 0xFF5B6B8C, "BNK"),
        // E-wallet
        Provider("GOPAY", "GoPay", WalletType.EWALLET, 0xFF00AED6, "GP"),
        Provider("OVO", "OVO", WalletType.EWALLET, 0xFF4C3494, "OVO"),
        Provider("DANA", "DANA", WalletType.EWALLET, 0xFF118EEA, "DANA"),
        Provider("SHOPEEPAY", "ShopeePay", WalletType.EWALLET, 0xFFEE4D2D, "SP"),
        Provider("LINKAJA", "LinkAja", WalletType.EWALLET, 0xFFE82529, "LA"),
        Provider("ISAKU", "i.Saku", WalletType.EWALLET, 0xFF0D4EA6, "iS"),
        Provider("ASTRAPAY", "AstraPay", WalletType.EWALLET, 0xFF1C4DA1, "AP"),
        Provider("EWALLET_OTHER", "E-wallet lainnya", WalletType.EWALLET, 0xFF5B6B8C, "EW"),
        // Bank digital
        Provider("JAGO", "Bank Jago", WalletType.DIGITAL_BANK, 0xFFFFB800, "JAGO"),
        Provider("BLU", "blu by BCA Digital", WalletType.DIGITAL_BANK, 0xFF00B5E2, "blu"),
        Provider("SEABANK", "SeaBank", WalletType.DIGITAL_BANK, 0xFFFF6A00, "SEA"),
        Provider("NEOBANK", "Neobank", WalletType.DIGITAL_BANK, 0xFFFFD500, "NEO"),
        Provider("ALLO", "Allo Bank", WalletType.DIGITAL_BANK, 0xFF7B2CBF, "ALLO"),
        Provider("SUPERBANK", "Superbank", WalletType.DIGITAL_BANK, 0xFF6C2BD9, "SUP"),
        Provider("DIGITAL_OTHER", "Bank digital lainnya", WalletType.DIGITAL_BANK, 0xFF5B6B8C, "DIG"),
        // Lainnya
        Provider("CASH", "Uang Tunai", WalletType.CASH, 0xFF2BB673, "Rp"),
        Provider("BUSINESS", "Rekening Bisnis", WalletType.BUSINESS, 0xFF3D8BFF, "BIZ"),
        Provider("SAVINGS", "Rekening Tabungan", WalletType.SAVINGS, 0xFF2DD4A4, "TAB"),
        Provider("INVESTMENT", "Rekening Investasi", WalletType.INVESTMENT, 0xFFB57CFF, "INV"),
        Provider("CREDIT_CARD", "Kartu Kredit", WalletType.CREDIT_CARD, 0xFFFF5C7A, "CC"),
        Provider("CUSTOM", "Lainnya (custom)", WalletType.BANK, 0xFF7C5CFF, "?"),
    )

    fun byCode(code: String): Provider = all.firstOrNull { it.code == code } ?: all.last()

    val fuelTypes = listOf(
        "Pertalite", "Pertamax", "Pertamax Turbo", "Pertamax Green", "Solar", "Biosolar", "Dexlite", "Pertamina Dex",
        "Shell Super", "Shell V-Power", "BP 92", "Vivo Revvo 92", "Lainnya",
    )
}

object Seed {
    private class Node(
        val name: String, val icon: String, val color: Long, val nature: Nature = Nature.NONE,
        val key: String? = null, val preferred: TxType? = null, val children: List<Node> = emptyList(),
    )

    private fun leaf(name: String, icon: String, nature: Nature = Nature.NONE, key: String? = null, preferred: TxType? = null) =
        Node(name, icon, 0, nature, key, preferred)

    private val expense = listOf(
        Node("Kebutuhan Sehari-hari", "🍽️", 0xFFFF8A4C, Nature.NEED, "daily", children = listOf(
            leaf("Makan", "🍛", key = "food"), leaf("Minum", "🥤", key = "food"), leaf("Sarapan", "🥐", key = "food"),
            leaf("Makan siang", "🍱", key = "food"), leaf("Makan malam", "🍜", key = "food"), leaf("Kopi", "☕", Nature.WANT, key = "food"),
            leaf("Snack", "🍪", Nature.WANT, key = "food"), leaf("Air mineral", "💧", key = "food"), leaf("Belanja dapur", "🧺"),
            leaf("Sembako", "🍚"), leaf("Sayuran", "🥬"), leaf("Daging", "🥩"), leaf("Buah", "🍎"), leaf("Kebutuhan rumah tangga", "🧴"),
        )),
        Node("Transportasi", "🚗", 0xFF3D8BFF, Nature.NEED, "transport", children = listOf(
            leaf("Bensin", "⛽", key = "fuel"), leaf("Solar", "⛽", key = "fuel"), leaf("Pertamax", "⛽", key = "fuel"),
            leaf("Pertalite", "⛽", key = "fuel"), leaf("Dex", "⛽", key = "fuel"), leaf("Tol", "🛣️"), leaf("Parkir", "🅿️"),
            leaf("Cuci kendaraan", "🧽"), leaf("Servis kendaraan", "🔧"), leaf("Oli", "🛢️"), leaf("Sparepart", "⚙️"),
            leaf("Transportasi online", "🛵"), leaf("Transportasi umum", "🚌"),
        )),
        Node("Tagihan", "🧾", 0xFFFFB547, Nature.NEED, "bills", children = listOf(
            leaf("Listrik", "💡"), leaf("Air", "🚰"), leaf("Internet", "🌐"), leaf("WiFi", "📶"), leaf("Pulsa", "📱"),
            leaf("Paket data", "📲"), leaf("BPJS", "🏥"), leaf("Asuransi", "🛡️"), leaf("Cicilan", "📆"),
            leaf("Langganan aplikasi", "🎬", Nature.WANT),
        )),
        Node("Personal", "🧍", 0xFFE879F9, Nature.WANT, "personal", children = listOf(
            leaf("Pakaian", "👕"), leaf("Sepatu", "👟"), leaf("Skincare", "🧴"), leaf("Perawatan diri", "💇"),
            leaf("Kesehatan", "🩺", Nature.NEED), leaf("Obat", "💊", Nature.NEED), leaf("Olahraga", "🏃"), leaf("Hiburan", "🎉"),
            leaf("Gaming", "🎮"), leaf("Hobi", "🎨"),
        )),
        Node("Pekerjaan", "💼", 0xFF22C3E6, Nature.NEED, "work", children = listOf(
            leaf("Bensin perjalanan kerja", "⛽", key = "fuel"), leaf("Makan saat bekerja", "🍱", key = "food"), leaf("Peralatan kerja", "🧰"),
            leaf("Komponen elektronik", "🔌"), leaf("Software", "💿"), leaf("Hosting/VPS", "🖥️"), leaf("Domain", "🌍"),
            leaf("Transportasi dinas", "🚕"), leaf("Pengeluaran proyek", "📐"), leaf("Reimbursement", "🔁"),
        )),
        Node("Keuangan", "🏦", 0xFF2DD4A4, Nature.NONE, "finance", children = listOf(
            leaf("Tabungan", "🐷", key = "savings", preferred = TxType.SAVINGS_ALLOCATION),
            leaf("Investasi", "📈", preferred = TxType.TRANSFER),
            leaf("Dana darurat", "🆘", preferred = TxType.SAVINGS_ALLOCATION),
            leaf("Pembayaran utang", "🤝", key = "debt", preferred = TxType.DEBT_PAYMENT),
            leaf("Pembayaran kartu kredit", "💳", key = "cc", preferred = TxType.CC_SETTLEMENT),
            leaf("Transfer keluarga", "👨‍👩‍👧"),
            leaf("Biaya admin & bunga", "🏷️", key = "fee"),
        )),
        Node("Sosial", "🎁", 0xFFFF5C7A, Nature.WANT, "social", children = listOf(
            leaf("Hadiah", "🎁"), leaf("Donasi", "🙏", Nature.NEED), leaf("Keluarga", "👪", Nature.NEED), leaf("Nongkrong", "🍻"),
            leaf("Liburan", "🏖️"), leaf("Pernikahan", "💍"), leaf("Acara khusus", "🎊"),
        )),
    )

    private val income = listOf(
        Node("Gaji bulanan", "💰", 0xFF2DD4A4, key = "salary"),
        Node("Bonus", "🎯", 0xFF7C5CFF),
        Node("Freelance", "🧑‍💻", 0xFF3D8BFF),
        Node("Project", "📐", 0xFF22C3E6),
        Node("Bisnis", "🏪", 0xFFFFB547),
        Node("Komisi", "🤝", 0xFFE879F9),
        Node("Reimbursement", "🔁", 0xFF8FA3BF),
        Node("Hadiah", "🎁", 0xFFFF5C7A),
        Node("Pendapatan lainnya", "➕", 0xFF5B6B8C),
    )

    /** Menyisipkan kategori bawaan (induk lalu anak). */
    suspend fun seedCategories(dao: AppDao) {
        if (dao.categoryCount() > 0) return
        var order = 0
        for (n in expense) {
            val pid = dao.upsertCategory(CategoryEntity(name = n.name, kind = CategoryKind.EXPENSE, icon = n.icon, color = n.color, nature = n.nature, systemKey = n.key, sortOrder = order++))
            dao.upsertCategories(n.children.map {
                CategoryEntity(name = it.name, parentId = pid, kind = CategoryKind.EXPENSE, icon = it.icon, color = n.color,
                    nature = it.nature, systemKey = it.key, preferredType = it.preferred, sortOrder = order++)
            })
        }
        for (n in income) {
            dao.upsertCategory(CategoryEntity(name = n.name, kind = CategoryKind.INCOME, icon = n.icon, color = n.color, systemKey = n.key, sortOrder = order++))
        }
    }
}
