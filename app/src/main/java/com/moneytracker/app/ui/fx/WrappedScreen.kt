package com.moneytracker.app.ui.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.ui.components.LocalHideBalance
import com.moneytracker.app.ui.today
import com.moneytracker.core.Fmt
import com.moneytracker.core.MonthWrap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private data class Slide(val colors: List<Color>, val content: @Composable ColumnScope.(active: Boolean) -> Unit)

private const val SLIDE_MS = 5500

/** Rekap bulanan ala "Wrapped": slide otomatis bergaya story, ketuk kiri/kanan untuk mundur/maju. */
@Composable
fun WrappedScreen(s: Snapshot, month: YearMonth, onClose: () -> Unit) {
    val w = remember(s, month) { s.wrap(month, today()) }
    val hide = LocalHideBalance.current
    fun rp(v: Long) = if (hide) "Rp•••••" else Fmt.rupiah(v)
    val monthName = Fmt.month(month).substringBefore(' ')

    val slides = buildList {
        add(Slide(listOf(Color(0xFF5B3DF5), Color(0xFF1E0F6B))) { a ->
            Pop(a, 0) { Text("✨", fontSize = 64.sp) }
            Reveal(a, 1) { Big("Rekap\n${Fmt.month(month)}") }
            Reveal(a, 2) { Small("${w.txCount} transaksi tercatat. Yuk lihat ke mana uangmu pergi.") }
        })
        if (!w.hasData) return@buildList
        add(Slide(listOf(Color(0xFFD7263D), Color(0xFF3A0B1A))) { a ->
            Reveal(a, 0) { Small("Total pengeluaran $monthName") }
            Reveal(a, 1) { CountUp(a, w.expense, hide) }
            Reveal(a, 2) { Small("Rata-rata ${rp(w.avgDaily)} per hari") }
            w.prevExpense?.takeIf { it > 0 }?.let { prev ->
                val r = (w.expense - prev).toDouble() / prev
                Reveal(a, 3) {
                    Chip(if (r > 0) "▲ ${Fmt.percent(r)} dari bulan lalu" else "▼ ${Fmt.percent(-r)} lebih hemat dari bulan lalu 🎉", if (r > 0) Color(0xFFFFC1C1) else Color(0xFFB8F5DD))
                }
            }
        })
        w.topCategory?.let { (catId, amt) ->
            val cat = catId?.let { s.categoryMap[it] }
            val share = if (w.expense > 0) amt.toFloat() / w.expense else 0f
            add(Slide(listOf(Color(0xFFFF8A4C), Color(0xFF5A1E00))) { a ->
                Reveal(a, 0) { Small("Kategori juara bulan ini") }
                Box(contentAlignment = Alignment.Center) {
                    ArcMeter(a, share)
                    Pop(a, 1) { Text(cat?.icon ?: "💸", fontSize = 56.sp) }
                }
                Reveal(a, 2) { Big(cat?.name ?: "Tanpa kategori") }
                Reveal(a, 3) { Small("${rp(amt)} · ${Fmt.percent(share.toDouble())} dari semua pengeluaran") }
            })
        }
        w.biggest?.let { b ->
            add(Slide(listOf(Color(0xFF7C3AED), Color(0xFF240A4D))) { a ->
                Reveal(a, 0) { Small("Pengeluaran terbesar dalam sekali bayar") }
                Pop(a, 1) { Text("💳", fontSize = 60.sp) }
                Reveal(a, 2) { CountUp(a, b.amount, hide) }
                Reveal(a, 3) { Small("${b.description.ifBlank { s.categoryLabel(b.categoryId) }}\n${Fmt.dateWithDay(Fmt.toDate(b.dateTime))}") }
            })
        }
        if (w.priciestDay != null || w.thriftiestDay != null) add(Slide(listOf(Color(0xFF0E9F77), Color(0xFF032B22))) { a ->
            Reveal(a, 0) { Small("Hari-hari $monthName") }
            w.thriftiestDay?.let { (d, v) ->
                Reveal(a, 1) { DayCard("🧘 Hari paling hemat", Fmt.dateWithDay(d), rp(v), Color(0xFFB8F5DD)) }
            }
            w.priciestDay?.let { (d, v) ->
                Reveal(a, 2) { DayCard("🔥 Hari paling boros", Fmt.dateWithDay(d), rp(v), Color(0xFFFFC1C1)) }
            }
            if (w.noSpendDays > 0) Reveal(a, 3) { Chip("🌱 ${w.noSpendDays} hari tanpa pengeluaran sama sekali", Color(0xFFE6FFF5)) }
        })
        w.topMerchant?.let { (name, count, total) ->
            add(Slide(listOf(Color(0xFF3D8BFF), Color(0xFF0B2350))) { a ->
                Reveal(a, 0) { Small("Langganan setia kamu") }
                Pop(a, 1) { Text("🏆", fontSize = 60.sp) }
                Reveal(a, 2) { Big(name) }
                Reveal(a, 3) { Small("$count kali · total ${rp(total)}") }
            })
        }
        if (w.fuelFills > 0) add(Slide(listOf(Color(0xFFFFB020), Color(0xFF4A2A00))) { a ->
            Reveal(a, 0) { Small("Urusan bensin") }
            Pop(a, 1) { Text("⛽", fontSize = 60.sp) }
            Reveal(a, 2) { CountUp(a, w.fuelCost, hide) }
            Reveal(a, 3) { Small("${Fmt.decimal(w.fuelLiters, 1)} liter · ${w.fuelFills}x isi BBM") }
            w.prevFuelCost?.takeIf { it > 0 }?.let { prev ->
                val r = (w.fuelCost - prev).toDouble() / prev
                Reveal(a, 4) { Chip((if (r > 0) "▲ " else "▼ ") + Fmt.percent(abs(r)) + " dibanding bulan lalu", Color(0xFFFFE9A8)) }
            }
        })
        add(Slide(listOf(Color(0xFFB57CFF), Color(0xFF2B1150))) { a ->
            Reveal(a, 0) { Small("Pemasukan vs pengeluaran") }
            Reveal(a, 1) { Big("${rp(w.income)}\nvs ${rp(w.expense)}") }
            val rate = w.savingsRate
            Reveal(a, 2) {
                Chip(
                    when {
                        rate == null -> "Belum ada pemasukan tercatat bulan ini"
                        rate >= 0.2 -> "💪 Kamu menyisihkan ${Fmt.percent(rate)} — luar biasa!"
                        rate >= 0 -> "Kamu menyisihkan ${Fmt.percent(rate)}. Target umum 20%."
                        else -> "Pengeluaran melebihi pemasukan ${rp(w.expense - w.income)}"
                    },
                    Color(0xFFEBDDFF),
                )
            }
            if (w.savings > 0) Reveal(a, 3) { Small("🐷 ${rp(w.savings)} masuk tabungan") }
        })
        add(Slide(listOf(Color(0xFF5B3DF5), Color(0xFF0B0F1A))) { a ->
            Pop(a, 0) { Text("🎯", fontSize = 64.sp) }
            Reveal(a, 1) { Big("Sampai jumpa\ndi rekap berikutnya!") }
            Reveal(a, 2) {
                Small(
                    w.topCategory?.let { (c, v) ->
                        val n = c?.let { s.categoryMap[it]?.name } ?: "kategori terbesar"
                        "Tantangan bulan depan: kurangi $n 10% → hemat ±${rp(v / 10)}"
                    } ?: "Terus catat transaksimu setiap hari 🔥",
                )
            }
        })
    }

    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pager.currentPage) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(SLIDE_MS, easing = LinearEasing))
        if (pager.currentPage < slides.lastIndex) pager.animateScrollToPage(pager.currentPage + 1)
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val slide = slides[page]
            val active = pager.currentPage == page
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(slide.colors))) {
                Bubbles(slide.colors.first(), active)
                Column(
                    Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 32.dp, vertical = 72.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { slide.content(this, active) }
            }
        }
        // Zona ketuk kiri/kanan (seperti story)
        Row(Modifier.fillMaxSize().padding(top = 90.dp)) {
            Box(Modifier.weight(1f).fillMaxSize().pointerInput(Unit) {
                detectTapGestures { scope.launch { if (pager.currentPage > 0) pager.animateScrollToPage(pager.currentPage - 1) } }
            })
            Box(Modifier.weight(2f).fillMaxSize().pointerInput(slides.size) {
                detectTapGestures { scope.launch { if (pager.currentPage < slides.lastIndex) pager.animateScrollToPage(pager.currentPage + 1) else onClose() } }
            })
        }
        // Bar progres + tombol tutup
        Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                slides.indices.forEach { i ->
                    val f = when { i < pager.currentPage -> 1f; i == pager.currentPage -> progress.value; else -> 0f }
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                        Box(Modifier.fillMaxWidth(f).height(3.dp).background(Color.White))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MoneyTracker · ${Fmt.month(month)}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Tutup", tint = Color.White) }
            }
        }
    }
}

// ---------------- Elemen slide ----------------

/** Muncul dari bawah dengan jeda bertahap sesuai [order]. */
@Composable
private fun Reveal(active: Boolean, order: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) { a.snapTo(0f); delay(150L + order * 260L); a.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) } else a.snapTo(0f)
    }
    Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 60f }, contentAlignment = Alignment.Center) { content() }
}

/** Muncul dengan efek pantul (emoji). */
@Composable
private fun Pop(active: Boolean, order: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) { a.snapTo(0f); delay(150L + order * 260L); a.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow)) } else a.snapTo(0f)
    }
    Box(Modifier.graphicsLayer { scaleX = a.value; scaleY = a.value; rotationZ = (1f - a.value) * -25f }) { content() }
}

@Composable
private fun CountUp(active: Boolean, value: Long, hide: Boolean) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(active) { if (active) { a.snapTo(0f); delay(400); a.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) } }
    Text(
        if (hide) "Rp•••••" else Fmt.rupiah(if (a.value >= 1f) value else (value * a.value.toDouble()).toLong()),
        color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
    )
}

@Composable
private fun Big(text: String) = Text(text, color = Color.White, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)

@Composable
private fun Small(text: String) = Text(text, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)

@Composable
private fun Chip(text: String, color: Color) = Text(
    text, color = Color(0xFF14102A), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(color).padding(horizontal = 14.dp, vertical = 8.dp),
)

@Composable
private fun DayCard(title: String, date: String, value: String, accent: Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = accent, style = MaterialTheme.typography.titleSmall)
        Text(date, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

/** Busur persentase yang terisi. */
@Composable
private fun ArcMeter(active: Boolean, share: Float) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(active) { if (active) { a.snapTo(0f); delay(300); a.animateTo(share.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing)) } }
    Canvas(Modifier.size(170.dp)) {
        val st = 14.dp.toPx()
        drawArc(Color.White.copy(alpha = 0.18f), -90f, 360f, false, Offset(st / 2, st / 2), Size(size.width - st, size.height - st), style = Stroke(st))
        drawArc(Color.White, -90f, 360f * a.value, false, Offset(st / 2, st / 2), Size(size.width - st, size.height - st), style = Stroke(st, cap = StrokeCap.Round))
    }
}

/** Gelembung latar yang melayang pelan. */
@Composable
private fun Bubbles(base: Color, active: Boolean) {
    val t = rememberFrameTime(active)
    Canvas(Modifier.fillMaxSize()) {
        for (i in 0 until 9) {
            val r = size.minDimension * (0.12f + (i % 4) * 0.07f)
            val x = size.width * ((i * 0.37f + 0.1f) % 1f) + cos(t * 0.4f + i) * 30f
            val y = size.height * ((i * 0.23f + 0.15f) % 1f) + sin(t * 0.3f + i * 1.7f) * 40f
            drawCircle(Color.White.copy(alpha = 0.05f + (i % 3) * 0.02f), r, Offset(x, y))
        }
        drawCircle(base.copy(alpha = 0.0f), 1f)
    }
}
