# MoneyTracker

Aplikasi manajemen keuangan pribadi Android untuk pengguna Indonesia. Aplikasi ini mengelola multi-rekening (bank, e-wallet, bank digital, tunai, kartu kredit), pemasukan dan pengeluaran, anggaran, target tabungan, utang/piutang, langganan, aset, serta **analisis konsumsi BBM kendaraan**.

Semua nominal memakai Rupiah (IDR) dan format tanggal Indonesia. Data tersimpan **terenkripsi di perangkat** (offline-first).

---

## Teknologi

| Bagian | Pilihan |
|---|---|
| Bahasa & UI | Kotlin, Jetpack Compose, Material 3 (dark mode utama) |
| Database | Room + SQLCipher (database terenkripsi penuh) |
| Grafik | Digambar sendiri dengan Compose Canvas: batang, garis, donut, gauge, semuanya interaktif |
| Tugas latar | WorkManager: transaksi rutin otomatis, pengingat jatuh tempo, peringatan anggaran |
| Keamanan | PIN (hash PBKDF2), biometrik (BiometricPrompt), kunci DB di Android Keystore |
| Ekspor/impor | CSV, Excel (.xlsx), PDF, backup JSON terenkripsi AES-GCM |
| Min. Android | 8.0 (API 26) |

## Struktur proyek

```
core/   Logika keuangan murni Kotlin (tanpa Android), lengkap dengan unit test
        ├─ Ledger.kt        aturan saldo & klasifikasi transaksi (satu sumber kebenaran)
        ├─ FuelAnalytics.kt analisis BBM full-to-full + tingkat akurasi
        ├─ Planning.kt      anggaran + rollover, target tabungan, jadwal berulang, utang
        ├─ Insights.kt      asisten keuangan berbasis data
        ├─ Analytics.kt     agregasi harian/mingguan/bulanan/tahunan
        └─ TableIO.kt       parser CSV, baca/tulis XLSX, pemetaan impor mutasi bank
app/    Aplikasi Android
        ├─ data/            Room entities, DAO, Repository, Snapshot, backup, ekspor
        ├─ security/        PIN, Keystore, enkripsi backup
        ├─ work/            DailyWorker (rutin + notifikasi)
        └─ ui/              dashboard, transactions, wallets, analytics, vehicles,
                            budget, savings, more (income, debt, subscriptions, reports,
                            assets, categories, settings, backup, assistant, audit)
```

## Aturan perhitungan (anti double counting)

Saldo wallet **tidak pernah disimpan**. Saldo selalu dihitung dari `saldo awal + Σ mutasi`, sehingga bisa direkonsiliasi kapan saja.

| Jenis | Efek saldo | Dihitung pemasukan | Dihitung pengeluaran |
|---|---|---|---|
| Pemasukan | + wallet | ✅ | – |
| Pengeluaran | − wallet | – | ✅ |
| Transfer | − asal, + tujuan | – | hanya biaya admin |
| Alokasi tabungan | − asal, + wallet tabungan | – | hanya biaya admin |
| Pelunasan kartu kredit | − bank, + kartu kredit | – | hanya biaya admin |
| Bayar utang | − wallet | – | hanya bunga/denda |
| Terima piutang / terima pinjaman | + wallet | – | – |
| Beri pinjaman | − wallet | – | – |
| Penyesuaian (rekonsiliasi) | ± wallet | – | – |

- **Kartu kredit** adalah wallet bersaldo negatif. Belanja dicatat sebagai pengeluaran saat terjadi. Pelunasannya hanya memindahkan uang, jadi tidak dihitung dua kali.
- **Net worth** = (saldo wallet positif + aset lain + piutang + tabungan di luar wallet) − (kartu kredit + sisa utang).
- **Rekonsiliasi**: pengguna memasukkan saldo aktual, lalu selisihnya dicatat sebagai transaksi *Penyesuaian* (bukan menimpa saldo).
- Kategori seperti “Tabungan”, “Pembayaran kartu kredit”, dan “Pembayaran utang” ditandai agar pengguna diarahkan memakai jenis transaksi yang benar.

## Analisis BBM

- **Liter dibeli ≠ liter dipakai.** Efisiensi (km/L) dihitung dengan metode **full-to-full**: jarak antara dua pengisian penuh dibagi total liter yang diisikan di antaranya (termasuk pengisian parsial).
- **Sisa BBM** disimulasikan dari pengisian penuh terakhir (= kapasitas tangki): dikurangi pemakaian (jarak ÷ km/L), ditambah setiap pengisian berikutnya, dibatasi kapasitas tangki.
- **Jarak harian** diambil dari odometer (pengisian + “Update odometer” tanpa isi BBM).
- **Tingkat akurasi** ditampilkan di setiap angka:
  - 🟢 Tinggi: ≥3 siklus full-to-full
  - 🟡 Sedang: 1–2 siklus
  - 🔴 Rendah: belum ada siklus; memakai perkiraan dari pembelian atau konsumsi pabrikan
- Panel **Last Refuel Analysis** menampilkan: tanggal & volume pengisian terakhir, biaya, hari sejak isi, km tempuh, L/hari, Rp/hari, km/L, Rp/km, estimasi sisa liter, tanggal disarankan isi ulang (cadangan 15%), dan perkiraan habis.
- Setiap pengisian BBM otomatis membuat transaksi pengeluaran (Transportasi › jenis BBM) di wallet yang dipilih.

## Keamanan & data

- PIN 6 digit disimpan sebagai **hash PBKDF2-HMAC-SHA256 (120.000 iterasi) + salt**. Setelah 5 kali salah, aplikasi terkunci sementara.
- Database dienkripsi **SQLCipher**. Kuncinya 32 byte acak yang dibungkus kunci AES di **Android Keystore**.
- Tersedia kunci biometrik, kunci otomatis saat aplikasi ke background, dan FLAG_SECURE (blokir screenshot).
- Aplikasi **tidak pernah meminta atau menyimpan** user ID/password/PIN internet banking. Mutasi bank masuk lewat **impor CSV/Excel** yang diunduh pengguna sendiri, dengan pemetaan kolom dan deteksi duplikat. Integrasi Open Banking belum ada karena belum tersedia API resmi yang umum.
- Backup memakai file `.mtbak` (AES-256-GCM, kunci dari password). Restore mengganti seluruh data dalam satu transaksi database.
- Riwayat perubahan (audit log) mencatat setiap transaksi yang dibuat, diubah, atau dihapus.
- Tersedia hapus akun: database, lampiran, preferensi, dan kunci Keystore dihapus.

## Build

Butuh JDK 17 dan Android SDK (API 35).

```bash
./gradlew :core:test            # unit test logika keuangan
./gradlew :app:assembleDebug    # APK debug → app/build/outputs/apk/debug/
```

Bisa juga dibuka langsung di **Android Studio** (Ladybug atau lebih baru) lalu Run.

Repo ini menyertakan workflow **GitHub Actions** (`.github/workflows/build.yml`). Setiap kali kode di-push, workflow menjalankan test, mem-build APK debug & release, lalu mengunggahnya sebagai artifact `MoneyTracker-apk`.

> APK release saat ini ditandatangani dengan debug key agar mudah dipasang. Untuk rilis Play Store, ganti `signingConfig` di `app/build.gradle.kts` dengan keystore Anda.

## Catatan

- Logo bank/e-wallet adalah merek dagang pemiliknya. Aplikasi memakai badge inisial + warna sebagai penanda.
- Foto struk tersimpan di penyimpanan internal aplikasi dan tidak ikut dalam file backup.
- Notifikasi di Android 13+ perlu izin: buka Pengaturan → Notifikasi → Izinkan.
