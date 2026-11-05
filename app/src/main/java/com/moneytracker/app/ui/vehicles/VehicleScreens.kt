package com.moneytracker.app.ui.vehicles

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.moneytracker.app.data.FuelLogEntity
import com.moneytracker.app.data.Providers
import com.moneytracker.app.data.Repository
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.data.VehicleEntity
import com.moneytracker.app.ui.Routes
import com.moneytracker.app.ui.components.BarChart
import com.moneytracker.app.ui.components.BarSeries
import com.moneytracker.app.ui.components.BasisNote
import com.moneytracker.app.ui.components.ConfirmDialog
import com.moneytracker.app.ui.components.DateField
import com.moneytracker.app.ui.components.EmptyState
import com.moneytracker.app.ui.components.FinCard
import com.moneytracker.app.ui.components.FinTextField
import com.moneytracker.app.ui.components.FinTopBar
import com.moneytracker.app.ui.components.KeyValue
import com.moneytracker.app.ui.components.LineChart
import com.moneytracker.app.ui.components.ListDivider
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.components.MoneyField
import com.moneytracker.app.ui.components.Option
import com.moneytracker.app.ui.components.SectionHeader
import com.moneytracker.app.ui.components.SegmentedTabs
import com.moneytracker.app.ui.components.SelectField
import com.moneytracker.app.ui.components.Thumb
import com.moneytracker.app.ui.components.TimeField
import com.moneytracker.app.ui.components.WalletBadge
import com.moneytracker.app.ui.components.money
import com.moneytracker.app.ui.components.toAmount
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.app.ui.today
import com.moneytracker.core.Fmt
import com.moneytracker.core.FuelAnalytics
import com.moneytracker.core.VehicleType
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun VehiclesScreen(s: Snapshot, nav: NavHostController) {
    val vehicles = s.vehicles.filter { !it.archived }
    val reports = remember(s) { vehicles.associate { it.id to s.fuelReport(it) } }
    Column(Modifier.fillMaxSize()) {
        FinTopBar("Kendaraan & BBM") { IconButton(onClick = { nav.navigate(Routes.vehicleForm()) }) { Icon(Icons.Filled.Add, "Tambah kendaraan") } }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (vehicles.isEmpty()) item {
                EmptyState("⛽", "Belum ada kendaraan", "Tambahkan motor atau mobil untuk menganalisis konsumsi BBM, biaya per hari, dan estimasi isi ulang.",
                    action = "Tambah kendaraan", onAction = { nav.navigate(Routes.vehicleForm()) })
            }
            items(vehicles, key = { it.id }) { v ->
                Column(Modifier.clickable { nav.navigate(Routes.vehicle(v.id)) }) {
                    LastRefuelPanel(s, v, compact = true, onAddFuel = { nav.navigate(Routes.fuelForm(v.id)) }, report = reports[v.id])
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { nav.navigate(Routes.fuelForm(v.id)) }, modifier = Modifier.weight(1f)) { Text("⛽ Isi BBM") }
                    OutlinedButton(onClick = { nav.navigate(Routes.vehicle(v.id)) }, modifier = Modifier.weight(1f)) { Text("Analisis lengkap") }
                }
            }
            if (vehicles.size >= 2) {
                item { SectionHeader("Perbandingan efisiensi") }
                item {
                    FinCard {
                        Row { listOf("Kendaraan" to 1.6f, "km/L" to 1f, "Rp/km" to 1f, "Rp/hari" to 1.2f).forEach { (h, w) -> Text(h, style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted, modifier = Modifier.weight(w)) } }
                        Spacer(Modifier.height(6.dp))
                        vehicles.forEach { v ->
                            val r = reports[v.id]
                            Row(Modifier.padding(vertical = 6.dp)) {
                                Text(v.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.6f))
                                Text(r?.kmPerLiter?.let { Fmt.decimal(it.value, 1) } ?: "-", modifier = Modifier.weight(1f))
                                Text(r?.costPerKm?.let { Fmt.groupDigits(it.value.toLong()) } ?: "-", modifier = Modifier.weight(1f))
                                Text(r?.costPerDay?.let { Fmt.rupiahCompact(it.value.toLong()) } ?: "-", modifier = Modifier.weight(1.2f))
                            }
                        }
                        BasisNote("Efisiensi dari siklus full-to-full bila tersedia; selain itu estimasi.")
                    }
                }
            }
        }
    }
}

@Composable
fun VehicleDetailScreen(s: Snapshot, nav: NavHostController, id: Long) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val v = s.vehicleMap[id]
    var confirm by remember { mutableStateOf(false) }
    var odoDialog by remember { mutableStateOf(false) }
    var chartTab by remember { mutableIntStateOf(0) }
    var deleteLog by remember { mutableStateOf<FuelLogEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        FinTopBar(v?.name ?: "Kendaraan", onBack = { nav.popBackStack() }) {
            if (v != null) {
                IconButton(onClick = { odoDialog = true }) { Icon(Icons.Outlined.Speed, "Update odometer") }
                IconButton(onClick = { nav.navigate(Routes.vehicleForm(v.id)) }) { Icon(Icons.Outlined.Edit, "Edit") }
                IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.Delete, "Hapus") }
            }
        }
        if (v == null) { EmptyState("🚗", "Kendaraan tidak ditemukan", ""); return }
        val r = remember(s, v) { s.fuelReport(v) }
        val logs = remember(s, v) { s.fuelLogsOf(v.id).sortedByDescending { it.dateTime } }
        val asc = logs.reversed()

        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (v.photoPath != null) item { Thumb(file = app.repository.file(v.photoPath), size = 180.dp, modifier = Modifier.fillMaxWidth(), maxPx = 900) }
            item { LastRefuelPanel(s, v, onAddFuel = { nav.navigate(Routes.fuelForm(v.id)) }, report = r) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { nav.navigate(Routes.fuelForm(v.id)) }, modifier = Modifier.weight(1f)) { Text("⛽ Catat pengisian") }
                    OutlinedButton(onClick = { odoDialog = true }, modifier = Modifier.weight(1f)) { Text("Update odometer") }
                }
            }
            item {
                FinCard {
                    Text("Ringkasan", style = MaterialTheme.typography.titleMedium)
                    KeyValue("Total biaya BBM", money(r.totalCost))
                    KeyValue("Total liter dibeli", Fmt.decimal(r.totalLiters, 1) + " L")
                    KeyValue("Total jarak tercatat", Fmt.groupDigits(r.totalDistanceKm) + " km")
                    KeyValue("Odometer terakhir", s.lastOdometer(v.id)?.let { Fmt.groupDigits(it) + " km" } ?: "-")
                    KeyValue("Siklus full-to-full", "${r.cycles.size}")
                    val thisMonthCost = r.monthlyCost.lastOrNull()?.takeIf { it.first == java.time.YearMonth.from(today()) }?.second ?: 0
                    KeyValue("BBM bulan ini", money(thisMonthCost))
                    if (r.monthlyCost.size >= 2) KeyValue("Rata-rata per bulan", money(r.monthlyCost.sumOf { it.second } / r.monthlyCost.size))
                    r.yearlyCost.forEach { (y, c) -> KeyValue("Total $y", money(c)) }
                }
            }
            if (logs.isNotEmpty()) item {
                FinCard {
                    SegmentedTabs(listOf("Biaya", "L/hari", "Km", "Harga"), chartTab, { chartTab = it })
                    Spacer(Modifier.height(12.dp))
                    when (chartTab) {
                        0 -> BarChart(r.monthlyCost.map { Fmt.monthShortYear(it.first) }, listOf(BarSeries("BBM", MaterialTheme.colorScheme.primary, r.monthlyCost.map { it.second })))
                        1 -> if (r.cycles.isEmpty()) BasisNote("Butuh minimal 2 pengisian full tank untuk grafik konsumsi.") else
                            LineChart(r.cycles.map { Fmt.dayMonth(it.endDate) }, r.cycles.map { it.litersPerDay }, Fin.colors.warning, title = "L/hari", valueFormatter = { Fmt.decimal(it, 2) + " L" })
                        2 -> LineChart(asc.map { Fmt.dayMonth(Fmt.toDate(it.dateTime)) }, asc.map { it.odometerKm.toDouble() }, Fin.colors.transfer, title = "Odometer", valueFormatter = { Fmt.groupDigits(it.toLong()) + " km" })
                        else -> LineChart(asc.map { Fmt.dayMonth(Fmt.toDate(it.dateTime)) }, asc.map { it.pricePerLiter.toDouble() }, Fin.colors.income, title = "Harga/L", valueFormatter = { Fmt.rupiah(it.toLong()) })
                    }
                }
            }
            if (r.monthlyLiters.size >= 2) item {
                FinCard {
                    Text("Perbandingan antarbulan", style = MaterialTheme.typography.titleMedium)
                    r.monthlyCost.takeLast(6).reversed().forEachIndexed { i, (ym, cost) ->
                        val liters = r.monthlyLiters.firstOrNull { it.first == ym }?.second ?: 0.0
                        val prev = r.monthlyCost.getOrNull(r.monthlyCost.size - 2 - i)?.second
                        val change = prev?.takeIf { it > 0 }?.let { (cost - it).toDouble() / it }
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(Fmt.month(ym), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("${Fmt.decimal(liters, 1)} L", modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money(cost), style = MaterialTheme.typography.bodyMedium)
                                change?.let { Text((if (it >= 0) "▲ " else "▼ ") + Fmt.percent(kotlin.math.abs(it)), style = MaterialTheme.typography.labelSmall, color = if (it > 0) Fin.colors.expense else Fin.colors.income) }
                            }
                        }
                    }
                }
            }
            if (r.cycles.isNotEmpty()) item {
                FinCard {
                    Text("Siklus full-to-full", style = MaterialTheme.typography.titleMedium)
                    r.cycles.takeLast(8).reversed().forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("${Fmt.dayMonth(c.startDate)} → ${Fmt.dayMonth(c.endDate)} (${c.days} hari)", style = MaterialTheme.typography.bodyMedium)
                                Text("${Fmt.groupDigits(c.distanceKm)} km · ${Fmt.decimal(c.litersUsed, 2)} L · ${money(c.cost)}", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                            }
                            Text("${Fmt.decimal(c.kmPerLiter, 1)} km/L", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            item { SectionHeader("Riwayat pengisian (${logs.size})") }
            items(logs, key = { it.id }) { l ->
                FinCard(onClick = { nav.navigate(Routes.fuelForm(v.id, l.id)) }, padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Fmt.dateWithDay(Fmt.toDate(l.dateTime)), style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${l.fuelType} · ${Fmt.liters(l.volumeMl)} · ${money(l.pricePerLiter)}/L · ${Fmt.groupDigits(l.odometerKm)} km" + if (l.fullTank) " · full" else "",
                                style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted,
                            )
                            if (l.station.isNotBlank()) Text("📍 ${l.station}", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                        }
                        Text(money(l.totalCost), style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { deleteLog = l }) { Icon(Icons.Outlined.Delete, "Hapus", tint = Fin.colors.muted) }
                    }
                }
            }
        }
    }
    if (confirm && v != null) ConfirmDialog(
        "Hapus ${v.name}?", "Riwayat pengisian & odometer kendaraan dihapus. Transaksi pengeluaran BBM tetap tersimpan karena uangnya memang sudah keluar.",
        onDismiss = { confirm = false },
    ) { scope.launch { app.repository.deleteVehicle(v); nav.popBackStack() } }
    deleteLog?.let { l ->
        ConfirmDialog("Hapus pengisian?", "Pengisian ${Fmt.dateShort(l.dateTime)} beserta transaksi ${money(l.totalCost)} akan dihapus.", onDismiss = { deleteLog = null }) {
            scope.launch { app.repository.deleteFuelLog(l.id) }
        }
    }
    if (odoDialog && v != null) {
        var km by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { odoDialog = false },
            title = { Text("Update odometer") },
            text = {
                Column {
                    Text("Mencatat odometer tanpa isi BBM membuat estimasi sisa BBM & tanggal isi ulang lebih akurat.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    FinTextField(km, { km = it.filter(Char::isDigit) }, "Odometer saat ini", keyboardType = KeyboardType.Number, suffix = "km",
                        supporting = s.lastOdometer(v.id)?.let { "Terakhir: ${Fmt.groupDigits(it)} km" })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = km.toLongOrNull()
                    if (value != null && value >= (s.lastOdometer(v.id) ?: 0)) scope.launch { app.repository.addOdometer(v.id, value, System.currentTimeMillis()) }
                    odoDialog = false
                }, enabled = km.toLongOrNull()?.let { it >= (s.lastOdometer(v.id) ?: 0) } == true) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { odoDialog = false }) { Text("Batal") } },
        )
    }
}

@Composable
fun VehicleFormScreen(s: Snapshot, nav: NavHostController, id: Long) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val e = s.vehicleMap[id]
    var name by rememberSaveable { mutableStateOf(e?.name ?: "") }
    var type by rememberSaveable { mutableStateOf(e?.type ?: VehicleType.MOTOR) }
    var brand by rememberSaveable { mutableStateOf(e?.brand ?: "") }
    var model by rememberSaveable { mutableStateOf(e?.model ?: "") }
    var year by rememberSaveable { mutableStateOf(e?.year?.toString() ?: "") }
    var fuel by rememberSaveable { mutableStateOf(e?.fuelType ?: "Pertalite") }
    var tank by rememberSaveable { mutableStateOf(e?.tankCapacityMl?.let { Fmt.decimal(it / 1000.0, 1) } ?: "") }
    var ref by rememberSaveable { mutableStateOf(e?.refKmPerLiter?.let { Fmt.decimal(it, 1) } ?: "") }
    var odo by rememberSaveable { mutableStateOf(e?.initialOdometer?.toString() ?: "") }
    var archived by rememberSaveable { mutableStateOf(e?.archived ?: false) }
    var photo by remember { mutableStateOf(e?.photoPath) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { photo = app.repository.saveVehiclePhoto(uri) ?: photo }
    }

    Column(Modifier.fillMaxSize()) {
        FinTopBar(if (e == null) "Tambah kendaraan" else "Edit kendaraan", onBack = { nav.popBackStack() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FinTextField(name, { name = it }, "Nama kendaraan", supporting = "mis. Motor pribadi, Avanza kantor")
            SegmentedTabs(VehicleType.entries.map { it.label }, type.ordinal, { type = VehicleType.entries[it] })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FinTextField(brand, { brand = it }, "Merek", Modifier.weight(1f))
                FinTextField(model, { model = it }, "Model", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FinTextField(year, { year = it.filter(Char::isDigit).take(4) }, "Tahun", Modifier.weight(1f), keyboardType = KeyboardType.Number)
                FinTextField(tank, { tank = it }, "Kapasitas tangki", Modifier.weight(1f), keyboardType = KeyboardType.Decimal, suffix = "L")
            }
            SelectField("Jenis BBM utama", Providers.fuelTypes.map { Option(it, it) }, fuel) { fuel = it }
            FinTextField(ref, { ref = it }, "Konsumsi rata-rata (opsional)", keyboardType = KeyboardType.Decimal, suffix = "km/L",
                supporting = "Dipakai hanya sebagai cadangan sampai riwayat full-to-full tersedia")
            FinTextField(odo, { odo = it.filter(Char::isDigit) }, "Kilometer terakhir (odometer)", keyboardType = KeyboardType.Number, suffix = "km")
            Row(verticalAlignment = Alignment.CenterVertically) {
                photo?.let { Thumb(file = app.repository.file(it)); Spacer(Modifier.width(12.dp)) }
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if (photo == null) "Tambah foto" else "Ganti foto") }
            }
            if (e != null) Row(verticalAlignment = Alignment.CenterVertically) { Text("Arsipkan kendaraan", Modifier.weight(1f)); Switch(archived, { archived = it }) }
            Button(onClick = {
                scope.launch {
                    val tankMl = Fmt.parseDecimal(tank)?.let { (it * 1000).toLong() }
                    val newId = app.repository.saveVehicle(
                        VehicleEntity(
                            id = e?.id ?: 0, name = name.trim(), type = type, brand = brand.trim(), model = model.trim(), year = year.toIntOrNull(),
                            fuelType = fuel, tankCapacityMl = tankMl, refKmPerLiter = Fmt.parseDecimal(ref), initialOdometer = odo.toLongOrNull(),
                            photoPath = photo, archived = archived, createdAt = e?.createdAt ?: System.currentTimeMillis(),
                        ),
                    )
                    if (e == null) odo.toLongOrNull()?.let { app.repository.addOdometer(newId, it, System.currentTimeMillis()) }
                    nav.popBackStack()
                }
            }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Simpan") }
        }
    }
}

@Composable
fun FuelFormScreen(s: Snapshot, nav: NavHostController, vehicleArg: Long, id: Long) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val existing = remember(id) { s.fuelLogs.firstOrNull { it.id == id } }
    var vehicleId by rememberSaveable { mutableStateOf(existing?.vehicleId ?: vehicleArg.takeIf { it != 0L } ?: s.vehicles.firstOrNull { !it.archived }?.id) }
    val vehicle = vehicleId?.let { s.vehicleMap[it] }
    val lastLog = remember(s, vehicleId) { s.fuelLogs.filter { it.vehicleId == vehicleId && it.id != id }.maxByOrNull { it.dateTime } }
    val dt = existing?.let { Fmt.toDateTime(it.dateTime) } ?: LocalDateTime.now(Fmt.zone)
    var date by rememberSaveable { mutableStateOf(dt.toLocalDate()) }
    var time by rememberSaveable { mutableStateOf(LocalTime.of(dt.hour, dt.minute)) }
    var odometer by rememberSaveable { mutableStateOf(existing?.odometerKm?.toString() ?: "") }
    var fuelType by rememberSaveable { mutableStateOf(existing?.fuelType ?: lastLog?.fuelType ?: vehicle?.fuelType ?: "Pertalite") }
    var price by rememberSaveable { mutableStateOf((existing?.pricePerLiter ?: lastLog?.pricePerLiter)?.toString() ?: "") }
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 = input total bayar, 1 = input liter
    var total by rememberSaveable { mutableStateOf(existing?.totalCost?.toString() ?: "") }
    var liters by rememberSaveable { mutableStateOf(existing?.volumeMl?.let { Fmt.decimal(it / 1000.0, 3) } ?: "") }
    var station by rememberSaveable { mutableStateOf(existing?.station ?: lastLog?.station ?: "") }
    var walletId by rememberSaveable { mutableStateOf(existing?.walletId ?: s.transactions.firstOrNull { it.id in s.fuelTxIds }?.walletId ?: s.activeWallets.firstOrNull()?.id) }
    var full by rememberSaveable { mutableStateOf(existing?.fullTank ?: true) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    val priceL = price.toAmount()
    val volumeMl: Long = if (mode == 0) FuelAnalytics.litersFromPayment(total.toAmount(), priceL) else ((Fmt.parseDecimal(liters) ?: 0.0) * 1000).toLong()
    val cost: Long = if (mode == 0) total.toAmount() else FuelAnalytics.costFromLiters(volumeMl, priceL)
    val minOdo = s.fuelLogs.filter { it.vehicleId == vehicleId && it.id != id && it.dateTime < Fmt.toMillis(date.atTime(time)) }.maxOfOrNull { it.odometerKm }

    Column(Modifier.fillMaxSize()) {
        FinTopBar(if (existing == null) "Isi BBM" else "Edit pengisian", onBack = { nav.popBackStack() })
        if (s.vehicles.isEmpty()) {
            EmptyState("🚗", "Tambahkan kendaraan dulu", "", action = "Tambah kendaraan", onAction = { nav.navigate(Routes.vehicleForm()) }); return
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectField("Kendaraan", s.vehicles.filter { !it.archived || it.id == vehicleId }.map { Option<Long?>(it.id, "${vehicleEmoji(it)} ${it.name}") }, vehicleId) { vehicleId = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField("Tanggal", date, { if (it != null) date = it }, Modifier.weight(1.4f))
                TimeField("Jam", time, { time = it }, Modifier.weight(1f))
            }
            FinTextField(
                odometer, { odometer = it.filter(Char::isDigit) }, "Odometer", keyboardType = KeyboardType.Number, suffix = "km",
                supporting = minOdo?.let { "Pengisian sebelumnya: ${Fmt.groupDigits(it)} km" } ?: "Kilometer pada speedometer saat mengisi",
                isError = odometer.toLongOrNull()?.let { minOdo != null && it < minOdo } == true,
            )
            SelectField("Jenis BBM", Providers.fuelTypes.map { Option(it, it) }, fuelType) { fuelType = it }
            MoneyField(price, { price = it }, "Harga per liter")
            SegmentedTabs(listOf("Input total bayar", "Input liter"), mode, { mode = it })
            if (mode == 0) MoneyField(total, { total = it }, "Total bayar", large = true)
            else FinTextField(liters, { liters = it }, "Jumlah liter", keyboardType = KeyboardType.Decimal, suffix = "L")
            FinCard(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), padding = PaddingValues(14.dp)) {
                KeyValue("Liter", if (volumeMl > 0) Fmt.liters(volumeMl) else "-")
                KeyValue("Total", money(cost))
                BasisNote(if (mode == 0) "Liter = total bayar ÷ harga per liter" else "Total = liter × harga per liter")
            }
            SelectField("Dibayar dari", s.activeWallets.map { w -> Option<Long?>(w.id, w.name, money(s.balance(w.id)), leading = { WalletBadge(w, 30.dp) }) }, walletId) { walletId = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Isi penuh (full tank)", style = MaterialTheme.typography.bodyLarge)
                    Text("Aktifkan bila tangki diisi sampai penuh — kunci akurasi metode full-to-full", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                }
                Switch(full, { full = it })
            }
            FinTextField(station, { station = it }, "Lokasi SPBU", supporting = "mis. SPBU 34.123 Jl. Sudirman")
            FinTextField(note, { note = it }, "Catatan", singleLine = false)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val v = vehicleId; val w = walletId; val odo = odometer.toLongOrNull()
                error = when {
                    v == null -> "Pilih kendaraan"
                    w == null -> "Pilih wallet pembayaran"
                    odo == null -> "Isi odometer"
                    minOdo != null && odo < minOdo -> "Odometer tidak boleh lebih kecil dari pengisian sebelumnya (${Fmt.groupDigits(minOdo)} km)"
                    priceL <= 0 -> "Isi harga per liter"
                    volumeMl <= 0 || cost <= 0 -> "Isi total bayar atau jumlah liter"
                    else -> null
                }
                if (error != null) return@Button
                scope.launch {
                    try {
                        app.repository.saveFuelLog(
                            FuelLogEntity(
                                id = existing?.id ?: 0, vehicleId = v!!, transactionId = existing?.transactionId, dateTime = Fmt.toMillis(date.atTime(time)),
                                odometerKm = odo!!, fuelType = fuelType, pricePerLiter = priceL, totalCost = cost, volumeMl = volumeMl,
                                station = station.trim(), walletId = w!!, fullTank = full, note = note.trim(), createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                            ),
                        )
                        nav.popBackStack()
                    } catch (ex: Repository.ValidationException) { error = ex.message }
                }
            }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Simpan pengisian") }
            BasisNote("Pengisian otomatis dicatat sebagai pengeluaran Transportasi › ${fuelType.takeIf { it.startsWith("Pert") || it.contains("Solar") || it.contains("Dex") } ?: "Bensin"} dan mengurangi saldo wallet.")
        }
    }
}
