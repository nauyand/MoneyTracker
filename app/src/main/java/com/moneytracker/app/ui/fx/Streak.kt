package com.moneytracker.app.ui.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneytracker.app.ui.components.FinCard
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.core.StreakInfo
import kotlin.math.sin

/** Waktu berjalan (detik) yang diperbarui setiap frame — mengikuti refresh rate layar. */
@Composable
fun rememberFrameTime(running: Boolean = true): Float {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = withFrameNanos { it } - (t * 1e9f).toLong()
        while (true) withFrameNanos { t = (it - start) / 1_000_000_000f }
    }
    return t
}

/**
 * Api animasi: tiga lapis lidah api yang bergoyang + percikan naik.
 * [intensity] 0..1 — makin panjang streak makin besar & terang; [dim] untuk streak yang terancam putus.
 */
@Composable
fun AnimatedFlame(size: Dp, intensity: Float = 1f, dim: Boolean = false, animate: Boolean = true) {
    val t = rememberFrameTime(animate)
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val k = 0.75f + 0.25f * intensity.coerceIn(0f, 1f)
        fun flame(scale: Float, phase: Float, colors: List<Color>) {
            val sway = sin(t * 7f + phase) * w * 0.05f
            val flick = 1f + sin(t * 11f + phase * 2f) * 0.04f
            val top = h * (1f - 0.92f * scale * k * flick)
            val base = h * 0.96f
            val half = w * 0.36f * scale
            val cx = w / 2f
            val p = Path().apply {
                moveTo(cx, base)
                cubicTo(cx - half * 1.25f, base, cx - half * 1.1f, h * 0.55f, cx - half * 0.35f + sway * 0.5f, h * 0.42f)
                cubicTo(cx - half * 0.2f + sway, top + h * 0.18f, cx + sway * 1.4f, top + h * 0.08f, cx + sway * 1.6f, top)
                cubicTo(cx + half * 0.5f + sway, top + h * 0.2f, cx + half * 1.2f, h * 0.5f, cx + half * 1.1f, h * 0.7f)
                cubicTo(cx + half, base, cx + half * 0.4f, base, cx, base)
                close()
            }
            drawPath(p, Brush.verticalGradient(colors, startY = top, endY = base))
        }
        val a = if (dim) 0.45f else 1f
        // cahaya
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF7A1A).copy(alpha = 0.35f * a), Color.Transparent), Offset(w / 2, h * 0.7f), w * 0.6f), w * 0.6f, Offset(w / 2, h * 0.7f))
        flame(1f, 0f, listOf(Color(0xFFFFB020).copy(alpha = a), Color(0xFFFF4D1A).copy(alpha = a), Color(0xFFD7263D).copy(alpha = a)))
        flame(0.68f, 1.7f, listOf(Color(0xFFFFE27A).copy(alpha = a), Color(0xFFFF9A1F).copy(alpha = a)))
        flame(0.38f, 3.1f, listOf(Color.White.copy(alpha = 0.95f * a), Color(0xFFFFE9A8).copy(alpha = a)))
        // percikan
        if (!dim) for (i in 0 until 5) {
            val life = ((t * 0.9f + i * 0.21f) % 1f)
            val x = w / 2f + sin(i * 2.1f + t * 3f) * w * 0.2f
            val y = h * (0.45f - life * 0.45f)
            drawCircle(Color(0xFFFFC14D).copy(alpha = (1f - life) * 0.9f), w * 0.025f * (1f - life * 0.5f), Offset(x, y))
        }
    }
}

/** Kartu streak di dashboard. */
@Composable
fun StreakCard(info: StreakInfo, animate: Boolean, onClick: () -> Unit) {
    val fin = Fin.colors
    val intensity = (info.current / 30f).coerceIn(0.2f, 1f)
    FinCard(onClick = onClick, color = if (info.current > 0) Color(0xFFFF6A1A).copy(alpha = 0.10f) else fin.card) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedFlame(58.dp, intensity, dim = info.current == 0 || info.atRisk, animate = animate)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (info.current > 0) "${info.current} hari beruntun" else "Mulai streak hari ini",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = if (info.current > 0) Color(0xFFFF8A3D) else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    when {
                        info.atRisk -> "Belum mencatat hari ini — catat sekarang agar streak tidak putus!"
                        info.current == 0 -> "Catat minimal 1 transaksi setiap hari untuk menyalakan api 🔥"
                        else -> info.nextMilestone?.let { "${it - info.current} hari lagi menuju $it hari · rekor ${info.best}" } ?: "Rekor ${info.best} hari"
                    },
                    style = MaterialTheme.typography.bodySmall, color = if (info.atRisk) fin.warning else fin.muted,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val dayNames = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            info.last7.forEachIndexed { i, (d, done) ->
                val isToday = i == info.last7.lastIndex
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(30.dp).background(
                            if (done) Brush.verticalGradient(listOf(Color(0xFFFFB020), Color(0xFFFF4D1A)))
                            else Brush.verticalGradient(listOf(fin.stroke, fin.stroke)), CircleShape,
                        ),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (done) "🔥" else if (isToday) "·" else "", style = MaterialTheme.typography.labelSmall) }
                    Text(dayNames[d.dayOfWeek.value - 1], style = MaterialTheme.typography.labelSmall, color = if (isToday) MaterialTheme.colorScheme.primary else fin.muted)
                }
            }
        }
    }
}
