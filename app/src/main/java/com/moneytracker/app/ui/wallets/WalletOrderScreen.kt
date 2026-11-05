package com.moneytracker.app.ui.wallets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.moneytracker.app.data.Providers
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.data.WalletEntity
import com.moneytracker.app.ui.components.BasisNote
import com.moneytracker.app.ui.components.FinTopBar
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.components.Pill
import com.moneytracker.app.ui.components.WalletBadge
import com.moneytracker.app.ui.components.money
import com.moneytracker.app.ui.theme.Fin
import kotlinx.coroutines.launch

private sealed interface OrderItem { val key: Any }
private data class WalletItem(val w: WalletEntity) : OrderItem { override val key: Any get() = w.id }
private data object Separator : OrderItem { override val key: Any get() = "separator" }

/**
 * Atur urutan wallet dengan drag & drop (tahan ikon ≡ lalu geser).
 * Wallet di atas garis = bagian Utama, di bawahnya = Opsional. Wallet Utama teratas = wallet default.
 */
@Composable
fun WalletOrderScreen(s: Snapshot, nav: NavHostController) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val gapPx = with(LocalDensity.current) { 10.dp.toPx() }
    val items = remember {
        val active = s.wallets.filter { !it.archived }
        mutableStateListOf<OrderItem>().apply {
            addAll(active.filter { it.isMain }.map { WalletItem(it) })
            add(Separator)
            addAll(active.filter { !it.isMain }.map { WalletItem(it) })
        }
    }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var changed by remember { mutableStateOf(false) }

    fun move(from: Int, to: Int) {
        if (from == to || to !in items.indices) return
        items.add(to, items.removeAt(from))
        changed = true
    }

    /** Tukar posisi dengan tetangga bila item sudah digeser melewati setengah tinggi tetangga. */
    fun step() {
        val i = items.indexOfFirst { it.key == draggingKey }
        if (i < 0) return
        if (dragOffset > 0 && i < items.lastIndex) {
            val h = (heights[items[i + 1].key] ?: 0) + gapPx
            if (dragOffset > h / 2f) { move(i, i + 1); dragOffset -= h; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        } else if (dragOffset < 0 && i > 0) {
            val h = (heights[items[i - 1].key] ?: 0) + gapPx
            if (-dragOffset > h / 2f) { move(i, i - 1); dragOffset += h; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        }
    }

    fun save() {
        val sep = items.indexOf(Separator)
        val ordered = items.mapIndexedNotNull { i, it -> (it as? WalletItem)?.let { w -> w.w.id to (i < sep) } }
        // Wallet arsip tetap di belakang dengan urutan lamanya.
        val archived = s.wallets.filter { it.archived }.map { it.id to it.isMain }
        scope.launch { app.repository.reorderWallets(ordered + archived); nav.popBackStack() }
    }

    Column(Modifier.fillMaxSize()) {
        FinTopBar("Atur wallet", onBack = { nav.popBackStack() }) {
            TextButton(onClick = ::save, enabled = changed) { Text("Simpan") }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = draggingKey == null)
                .padding(PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasisNote("Tahan & geser ikon ≡ untuk mengubah urutan. Wallet di atas garis tampil di bagian Utama, sisanya Opsional. Wallet Utama teratas (⭐) menjadi wallet default saat mencatat transaksi.")
            SectionLabel("UTAMA")
            items.forEach { item ->
                key(item.key) {
                    val dragging = draggingKey == item.key
                    val elevation by animateFloatAsState(if (dragging) 12f else 0f, label = "lift")
                    val handle = Modifier.pointerInput(item.key) {
                        detectDragGestures(
                            onDragStart = { draggingKey = item.key; dragOffset = 0f; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDragEnd = { draggingKey = null; dragOffset = 0f },
                            onDragCancel = { draggingKey = null; dragOffset = 0f },
                            onDrag = { change, amount -> change.consume(); dragOffset += amount.y; step() },
                        )
                    }
                    Box(
                        Modifier.fillMaxWidth()
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { translationY = if (dragging) dragOffset else 0f; scaleX = if (dragging) 1.02f else 1f; scaleY = scaleX }
                            .onSizeChanged { heights[item.key] = it.height },
                    ) {
                        when (item) {
                            is Separator -> SeparatorRow(handle)
                            is WalletItem -> {
                                val index = items.indexOf(item)
                                WalletOrderRow(
                                    s, item.w, isPrimary = index == 0, isMain = index < items.indexOf(Separator),
                                    elevation = elevation, handle = handle,
                                    onMakePrimary = { move(index, 0) },
                                )
                            }
                        }
                    }
                }
            }
            if (items.last() == Separator) Text("Seret wallet ke bawah garis untuk menjadikannya Opsional.", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Fin.colors.muted, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun SeparatorRow(handle: Modifier) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = Fin.colors.stroke)
        Text("  OPSIONAL  ", style = MaterialTheme.typography.labelMedium, color = Fin.colors.muted)
        HorizontalDivider(Modifier.weight(1f), color = Fin.colors.stroke)
        Box(handle.size(36.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.DragHandle, "Geser pemisah", tint = Fin.colors.muted) }
    }
}

@Composable
private fun WalletOrderRow(s: Snapshot, w: WalletEntity, isPrimary: Boolean, isMain: Boolean, elevation: Float, handle: Modifier, onMakePrimary: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().shadow(elevation.dp, shape).clip(shape)
            .background(if (isMain) Fin.colors.card else Fin.colors.card.copy(alpha = 0.6f)).padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WalletBadge(w, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.name, style = MaterialTheme.typography.titleSmall)
                if (isPrimary) { Spacer(Modifier.width(6.dp)); Pill("Default", MaterialTheme.colorScheme.primary) }
            }
            Text("${Providers.byCode(w.provider).name} · ${money(s.balance(w.id))}", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
        }
        IconButton(onClick = onMakePrimary, enabled = !isPrimary) {
            Icon(if (isPrimary) Icons.Filled.Star else Icons.Outlined.StarOutline, "Jadikan wallet utama", tint = if (isPrimary) Fin.colors.warning else Fin.colors.muted)
        }
        Box(handle.size(48.dp).height(48.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.DragHandle, "Tahan dan geser", tint = Fin.colors.muted) }
    }
}
