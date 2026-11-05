package com.moneytracker.app.ui.more

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.moneytracker.app.ui.components.BasisNote
import com.moneytracker.app.ui.components.FinCard
import com.moneytracker.app.ui.components.TimeField
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.app.work.Reminders
import com.moneytracker.core.Fmt
import com.moneytracker.core.ReminderConfig
import com.moneytracker.core.ReminderKind
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

private fun hhmm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

/** Pengaturan pengingat pencatatan: jam-jam harian & tanggal-tanggal tertentu tiap bulan. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cfg by remember { mutableStateOf(Reminders.load(ctx)) }
    var addingTime by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(ctx, "Izin notifikasi ditolak — pengingat tidak akan muncul", Toast.LENGTH_LONG).show()
    }

    fun update(c: ReminderConfig) {
        val turningOn = (c.dailyEnabled && !cfg.dailyEnabled) || (c.monthlyEnabled && !cfg.monthlyEnabled)
        cfg = c
        Reminders.save(ctx, c)
        if (turningOn && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    FinCard {
        Text("Pengingat pencatatan", style = MaterialTheme.typography.titleMedium)
        Text("Notifikasi agar tidak lupa mencatat transaksi.", style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted)

        // ---------- Harian ----------
        ToggleRow("Pengingat harian", "Setiap hari pada jam yang dipilih", cfg.dailyEnabled) { update(cfg.copy(dailyEnabled = it)) }
        if (cfg.dailyEnabled) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cfg.dailyTimes.forEach { t ->
                    InputChip(
                        selected = true, onClick = {},
                        label = { Text(hhmm(t)) },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, "Hapus jam", Modifier.size(16.dp).clickable { update(cfg.copy(dailyTimes = cfg.dailyTimes - t)) })
                        },
                    )
                }
                AssistChip(onClick = { addingTime = true }, label = { Text("Tambah jam") }, leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) })
            }
            if (cfg.dailyTimes.isEmpty()) Text("Belum ada jam. Tambahkan minimal satu.", style = MaterialTheme.typography.labelSmall, color = Fin.colors.warning)
            ToggleRow("Lewati jika sudah mencatat", "Tidak mengingatkan bila sudah ada transaksi baru sejak pengingat sebelumnya", cfg.skipIfRecorded) {
                update(cfg.copy(skipIfRecorded = it))
            }
        }

        // ---------- Tanggal tertentu ----------
        ToggleRow("Pengingat tanggal tertentu", "Mis. tanggal gajian atau akhir bulan untuk cek & rekonsiliasi saldo", cfg.monthlyEnabled) {
            update(cfg.copy(monthlyEnabled = it))
        }
        if (cfg.monthlyEnabled) {
            Text("Pilih tanggal", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
            // Grid 1–31, 7 kolom seperti kalender
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..31).chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        week.forEach { d ->
                            val sel = d in cfg.monthlyDays
                            Box(
                                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) MaterialTheme.colorScheme.primary else Fin.colors.cardElevated)
                                    .clickable { update(cfg.copy(monthlyDays = if (sel) cfg.monthlyDays - d else cfg.monthlyDays + d)) },
                                contentAlignment = Alignment.Center,
                            ) { Text("$d", color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TimeField("Jam pengingat tanggal", cfg.monthlyTime, { update(cfg.copy(monthlyTime = it)) })
            BasisNote("Tanggal 29–31 otomatis dipindah ke hari terakhir pada bulan yang lebih pendek.")
        }

        // ---------- Ringkasan ----------
        val next = remember(cfg) { Reminders.next(ctx) }
        Spacer(Modifier.height(8.dp))
        Text(
            next?.let { "Pengingat berikutnya: ${Fmt.dateWithDay(it.at.toLocalDate())}, ${hhmm(it.at.toLocalTime())}" } ?: "Tidak ada pengingat aktif",
            style = MaterialTheme.typography.labelLarge, color = if (next != null) MaterialTheme.colorScheme.primary else Fin.colors.muted,
        )
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else scope.launch {
                    Reminders.fire(ctx, setOf(ReminderKind.DAILY), LocalDateTime.now(Fmt.zone), test = true)
                }
            }) { Text("Kirim notifikasi uji") }
        }
        BasisNote("Android dapat menunda notifikasi beberapa menit saat mode hemat daya. Bila pengingat tidak muncul, nonaktifkan optimasi baterai untuk MoneyTracker.")
    }

    if (addingTime) {
        val st = rememberTimePickerState(20, 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { addingTime = false },
            title = { Text("Tambah jam pengingat") },
            text = { TimePicker(st) },
            confirmButton = {
                TextButton(onClick = {
                    update(cfg.copy(dailyTimes = (cfg.dailyTimes + LocalTime.of(st.hour, st.minute)).distinct().sorted()))
                    addingTime = false
                }) { Text("Tambah") }
            },
            dismissButton = { TextButton(onClick = { addingTime = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onChange)
    }
}
