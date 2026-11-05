package com.moneytracker.app.ui.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneytracker.app.LocalActivity
import com.moneytracker.app.ui.components.FinTextField
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.theme.Fin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PIN_LENGTH = 6

@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val app = LocalApp.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val lockedUntil = app.security.lockedUntil()

    LaunchedEffect(Unit) {
        if (app.security.biometricEnabled && activity.canUseBiometric()) activity.authenticateBiometric(onUnlocked)
    }
    LaunchedEffect(lockedUntil, now) {
        if (lockedUntil > now) { delay(1000); now = System.currentTimeMillis() }
    }

    fun submit(p: String) {
        scope.launch {
            val ok = withContext(Dispatchers.Default) { app.security.verifyPin(p) }
            if (ok) onUnlocked() else {
                pin = ""
                now = System.currentTimeMillis()
                error = if (app.security.lockedUntil() > System.currentTimeMillis()) "Terlalu banyak percobaan. Coba lagi nanti." else "PIN salah (${app.security.failedAttempts()}x)"
                for (x in listOf(24f, -24f, 16f, -16f, 0f)) shake.animateTo(x)
            }
        }
    }

    PinLayout(
        title = "Masukkan PIN",
        subtitle = if (lockedUntil > now) "Terkunci ${(lockedUntil - now) / 1000 + 1} detik" else error ?: "Data keuangan Anda terkunci",
        pin = pin,
        shakeOffset = shake.value,
        enabled = lockedUntil <= now,
        showBiometric = app.security.biometricEnabled && activity.canUseBiometric(),
        onBiometric = { activity.authenticateBiometric(onUnlocked) },
        onDigit = { d -> if (pin.length < PIN_LENGTH) { pin += d; if (pin.length == PIN_LENGTH) submit(pin) } },
        onDelete = { pin = pin.dropLast(1) },
    )
}

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val app = LocalApp.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var bio by remember { mutableStateOf(false) }

    when (step) {
        0 -> Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Box(Modifier.size(84.dp).background(Fin.colors.heroGradient, RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
                Text("₨", fontSize = 40.sp, color = Color.White)
            }
            Spacer(Modifier.height(24.dp))
            Text("MoneyTracker", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Kelola rekening bank, e-wallet, tunai, anggaran, tabungan, utang, dan BBM kendaraan dalam satu aplikasi.",
                style = MaterialTheme.typography.bodyMedium, color = Fin.colors.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(32.dp))
            Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    "🔒" to "Data tersimpan terenkripsi di perangkat Anda",
                    "🏦" to "Pencatatan manual & impor CSV/Excel — tanpa meminta password bank",
                    "⛽" to "Analisis BBM full-to-full dengan tingkat akurasi",
                ).forEach { (e, t) ->
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(e, fontSize = 22.sp); Spacer(Modifier.width(12.dp)); Text(t, style = MaterialTheme.typography.bodyMedium) }
                }
                Spacer(Modifier.height(16.dp))
                FinTextField(name, { name = it }, "Nama Anda")
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = name.isNotBlank()) { Text("Lanjut — Buat PIN") }
            }
        }
        1, 2 -> PinLayout(
            title = if (step == 1) "Buat PIN $PIN_LENGTH digit" else "Ulangi PIN",
            subtitle = error ?: "PIN hanya disimpan sebagai hash terenkripsi",
            pin = if (step == 1) pin else confirm,
            onDigit = { d ->
                if (step == 1) {
                    if (pin.length < PIN_LENGTH) pin += d
                    if (pin.length == PIN_LENGTH) { step = 2; error = null }
                } else {
                    if (confirm.length < PIN_LENGTH) confirm += d
                    if (confirm.length == PIN_LENGTH) {
                        if (confirm == pin) step = 3 else { error = "PIN tidak sama, ulangi"; pin = ""; confirm = ""; step = 1 }
                    }
                }
            },
            onDelete = { if (step == 1) pin = pin.dropLast(1) else confirm = confirm.dropLast(1) },
        )
        else -> Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Fingerprint, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            Text("Kunci biometrik", style = MaterialTheme.typography.titleLarge)
            Text(
                if (activity.canUseBiometric()) "Buka aplikasi dengan sidik jari/wajah. PIN tetap bisa dipakai." else "Perangkat ini belum mendukung atau belum mendaftarkan biometrik.",
                style = MaterialTheme.typography.bodyMedium, color = Fin.colors.muted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            if (activity.canUseBiometric()) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aktifkan biometrik", modifier = Modifier.weight(1f, fill = false)); Spacer(Modifier.width(12.dp)); Switch(bio, { bio = it })
            }
            Spacer(Modifier.height(32.dp))
            Button(onClick = {
                scope.launch {
                    withContext(Dispatchers.Default) { app.security.setPin(pin) }
                    app.security.biometricEnabled = bio
                    app.repository.setUserName(name)
                    app.repository.ensureSeeded()
                    onDone()
                }
            }, modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).height(52.dp)) { Text("Mulai") }
        }
    }
}

@Composable
fun PinLayout(
    title: String,
    subtitle: String,
    pin: String,
    shakeOffset: Float = 0f,
    enabled: Boolean = true,
    showBiometric: Boolean = false,
    onBiometric: () -> Unit = {},
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).background(Fin.colors.heroGradient, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, null, tint = Color.White)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Fin.colors.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(28.dp))
        Row(Modifier.offset { IntOffset(shakeOffset.toInt(), 0) }, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(PIN_LENGTH) { i ->
                Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) MaterialTheme.colorScheme.primary else Fin.colors.stroke))
            }
        }
        Spacer(Modifier.height(40.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier.size(74.dp).clip(CircleShape)
                                .background(if (k == "bio" || k == "del") Color.Transparent else Fin.colors.cardElevated)
                                .clickable(enabled = enabled && (k != "bio" || showBiometric)) {
                                    when (k) { "del" -> onDelete(); "bio" -> onBiometric(); else -> onDigit(k) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (k) {
                                "del" -> Icon(Icons.AutoMirrored.Filled.Backspace, "Hapus")
                                "bio" -> if (showBiometric) Icon(Icons.Filled.Fingerprint, "Biometrik", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                else -> Text(k, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
