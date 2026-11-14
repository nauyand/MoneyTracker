package com.moneytracker.app.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.theme.Fin

/**
 * Bingkai bersama untuk lembar yang dibuka dari widget: latar gelap transparan di atas wallpaper
 * (ketuk di luar = tutup) dan lembar bawah yang naik bersama keyboard.
 */
@Composable
internal fun WidgetSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(visible, enter = slideInVertically { it / 2 } + fadeIn()) {
            Surface(
                Modifier.fillMaxWidth().widthIn(max = 560.dp)
                    // Menahan ketukan agar tidak dianggap "di luar lembar".
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Tutup") }
                    }
                    content()
                }
            }
        }
    }
}

/**
 * Menampilkan [content] hanya bila aplikasi sudah siap dipakai (sudah onboarding, data termuat, ada wallet).
 * Bila belum, tampilkan pesan + tombol membuka aplikasi.
 */
@Composable
internal fun WidgetGate(onOpenApp: () -> Unit, content: @Composable (Snapshot) -> Unit) {
    val app = LocalApp.current
    val snapshot by app.repository.snapshot.collectAsState()
    val s = snapshot
    when {
        !app.security.hasPin -> SetupNeeded("Selesaikan pengaturan awal di aplikasi terlebih dahulu.", onOpenApp)
        s == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        s.activeWallets.isEmpty() -> SetupNeeded("Tambahkan wallet di aplikasi terlebih dahulu.", onOpenApp)
        else -> content(s)
    }
}

@Composable
private fun SetupNeeded(message: String, onOpenApp: () -> Unit) {
    Text(message, style = MaterialTheme.typography.bodyMedium, color = Fin.colors.muted)
    Button(onClick = onOpenApp, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Buka MoneyTracker") }
}
