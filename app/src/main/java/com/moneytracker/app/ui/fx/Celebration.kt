package com.moneytracker.app.ui.fx

import com.moneytracker.core.Streaks
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneytracker.app.data.WalletEntity
import com.moneytracker.app.ui.components.LocalHideBalance
import com.moneytracker.app.ui.components.WalletBadge
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.core.Fmt
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class CelebKind { INCOME, EXPENSE, TRANSFER, SAVINGS, DEBT, FUEL, OTHER }

/** Data umpan balik setelah transaksi disimpan. */
data class Celebration(
    val kind: CelebKind,
    val amount: Long,
    val title: String,
    val from: WalletEntity? = null,
    val to: WalletEntity? = null,
    /** Mis. "Saldo GoPay sekarang Rp120.000". */
    val subtitle: String? = null,
    /** Progres target tabungan sebelum & sesudah (0..1). */
    val goalBefore: Float? = null,
    val goalAfter: Float? = null,
    val goalName: String? = null,
    /** Peringatan anggaran yang terlampaui/hampir habis karena transaksi ini. */
    val warning: String? = null,
    /** Streak baru bila transaksi ini adalah catatan pertama hari ini (≥2 hari). */
    val streak: Int? = null,
    val id: Long = System.nanoTime(),
)

private data class Particle(val angle: Float, val speed: Float, val size: Float, val spin: Float, val color: Color, val delay: Float, val shape: Int)

/**
 * Overlay animasi setelah transaksi tersimpan. Durasi ±1,8 detik, ketuk untuk menutup.
 * Semua partikel digambar per frame (ikut refresh rate layar).
 */
@Composable
fun CelebrationOverlay(c: Celebration, onDone: () -> Unit) {
    val fin = Fin.colors
    val haptic = LocalHapticFeedback.current
    val hide = LocalHideBalance.current
    val color = when (c.kind) {
        CelebKind.INCOME -> fin.income
        CelebKind.EXPENSE -> fin.expense
        CelebKind.TRANSFER -> fin.transfer
        CelebKind.SAVINGS -> fin.savings
        CelebKind.DEBT -> fin.warning
        CelebKind.FUEL -> fin.warning
        CelebKind.OTHER -> MaterialTheme.colorScheme.primary
    }
    val goalReached = c.goalAfter != null && c.goalAfter >= 1f && (c.goalBefore ?: 0f) < 1f
    val particles = remember(c.id) {
        val rnd = Random(c.id)
        val palette = if (goalReached) fin.chart else listOf(color, color.copy(alpha = 0.8f), Color(0xFFFFD54F), Color.White)
        List(if (goalReached) 90 else 42) {
            Particle(
                angle = rnd.nextFloat() * 2f * PI.toFloat(), speed = 0.35f + rnd.nextFloat() * 0.65f, size = 4f + rnd.nextFloat() * 7f,
                spin = rnd.nextFloat() * 720f - 360f, color = palette[rnd.nextInt(palette.size)], delay = rnd.nextFloat() * 0.25f, shape = rnd.nextInt(3),
            )
        }
    }
    val scrim = remember(c.id) { Animatable(0f) }
    val card = remember(c.id) { Animatable(0.6f) }
    val check = remember(c.id) { Animatable(0f) }
    val count = remember(c.id) { Animatable(0f) }
    val goal = remember(c.id) { Animatable(c.goalBefore ?: 0f) }
    var t by remember(c.id) { mutableFloatStateOf(0f) }

    LaunchedEffect(c.id) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scrim.animateTo(1f, tween(180))
    }
    LaunchedEffect(c.id) { card.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
    LaunchedEffect(c.id) { delay(120); check.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
    LaunchedEffect(c.id) { delay(150); count.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }
    LaunchedEffect(c.id) { if (c.goalAfter != null) { delay(350); goal.animateTo(c.goalAfter.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing)) } }
    LaunchedEffect(c.id) {
        val start = withFrameNanos { it }
        while (t < 3f) withFrameNanos { t = (it - start) / 1_000_000_000f }
    }
    LaunchedEffect(c.id) {
        delay(if (c.warning != null || goalReached || c.streak != null) 2600 else 1800)
        scrim.animateTo(0f, tween(220))
        onDone()
    }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = scrim.value }
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        // ---------- Partikel ----------
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f - 90.dp.toPx())
            val reach = size.minDimension * 0.55f
            when (c.kind) {
                CelebKind.TRANSFER -> drawTransferFlow(t, center, color)
                else -> particles.forEach { p ->
                    val lt = (t - p.delay).coerceAtLeast(0f)
                    if (lt <= 0f) return@forEach
                    val life = (lt / 1.6f).coerceIn(0f, 1f)
                    val alpha = (1f - life).coerceIn(0f, 1f)
                    val pos = when (c.kind) {
                        // Pemasukan: koin menyembur ke atas lalu jatuh (gravitasi)
                        CelebKind.INCOME, CelebKind.OTHER -> Offset(
                            center.x + cos(p.angle) * reach * p.speed * life,
                            center.y - sin(p.angle).let { kotlin.math.abs(it) } * reach * p.speed * 1.2f * life + 900f * life * life,
                        )
                        // Tabungan: koin jatuh dari atas ke celengan (atau confetti bila target tercapai)
                        CelebKind.SAVINGS -> if (goalReached) Offset(
                            center.x + cos(p.angle) * reach * p.speed * life,
                            center.y + sin(p.angle) * reach * p.speed * life + 400f * life * life,
                        ) else Offset(
                            center.x + cos(p.angle) * 40f * (1f - life),
                            center.y - reach * 0.9f * (1f - life) * p.speed,
                        )
                        // Pengeluaran / BBM / utang: uang "keluar" menyebar ke bawah & samping
                        else -> Offset(
                            center.x + cos(p.angle) * reach * 0.8f * p.speed * life,
                            center.y + kotlin.math.abs(sin(p.angle)) * reach * 0.5f * p.speed * life + 500f * life * life,
                        )
                    }
                    rotate(p.spin * life, pos) { drawParticle(p, pos, alpha) }
                }
            }
        }

        // ---------- Kartu ----------
        Column(
            Modifier.widthIn(max = 340.dp).padding(24.dp)
                .graphicsLayer { scaleX = card.value; scaleY = card.value; alpha = ((card.value - 0.6f) / 0.4f).coerceIn(0f, 1f) }
                .clip(RoundedCornerShape(28.dp)).background(Fin.colors.cardElevated).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Cincin denyut
                Canvas(Modifier.size(96.dp)) {
                    val pulse = ((t % 1.2f) / 1.2f)
                    drawCircle(color.copy(alpha = 0.25f * (1f - pulse)), radius = size.minDimension / 2f * (0.7f + 0.3f * pulse))
                    drawCircle(color.copy(alpha = 0.18f), radius = size.minDimension * 0.36f)
                }
                Box(Modifier.size(64.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                    Text(emojiFor(c.kind, goalReached), fontSize = 26.sp, modifier = Modifier.graphicsLayer { alpha = 1f - check.value })
                    Canvas(Modifier.size(34.dp).graphicsLayer { alpha = check.value }) { drawCheck(check.value) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(c.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            val sign = when (c.kind) { CelebKind.INCOME -> "+"; CelebKind.EXPENSE, CelebKind.FUEL, CelebKind.DEBT -> "−"; else -> "" }
            Text(
                if (hide) "Rp•••••" else sign + Fmt.rupiah((c.amount * count.value).toLong().let { if (count.value >= 1f) c.amount else it }),
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color,
            )
            if (c.kind == CelebKind.TRANSFER && c.from != null && c.to != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    WalletBadge(c.from, 34.dp)
                    Text("  →  ", color = color, style = MaterialTheme.typography.titleMedium)
                    WalletBadge(c.to, 34.dp)
                }
            }
            c.subtitle?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted, textAlign = TextAlign.Center)
            }
            if (c.goalAfter != null) {
                Spacer(Modifier.height(14.dp))
                Text(if (goalReached) "🎉 Target ${c.goalName} tercapai!" else "Progres ${c.goalName}", style = MaterialTheme.typography.labelLarge, color = if (goalReached) Fin.colors.income else Fin.colors.muted)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Fin.colors.stroke)) {
                    Box(Modifier.fillMaxWidth(goal.value.coerceIn(0f, 1f)).height(10.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))))
                }
                Text(Fmt.percent(goal.value.toDouble()), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            }
            c.streak?.let { n ->
                Spacer(Modifier.height(12.dp))
                val pop = ((t - 0.5f) / 0.35f).coerceIn(0f, 1f)
                val milestone = Streaks.isMilestone(n)
                Row(
                    Modifier.graphicsLayer { scaleX = 0.6f + 0.4f * pop + (if (pop >= 1f) sin(t * 6f) * 0.02f else 0f); scaleY = scaleX; alpha = pop }
                        .clip(RoundedCornerShape(14.dp)).background(Color(0xFFFF6A1A).copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedFlame(30.dp, (n / 30f).coerceIn(0.3f, 1f))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (milestone) "Pencapaian! $n hari beruntun" else "Streak $n hari",
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFFFF8A3D),
                    )
                }
            }
            c.warning?.let {
                Spacer(Modifier.height(12.dp))
                val shake = if (t in 0.4f..0.9f) sin(t * 60f) * 6f else 0f
                Text(
                    "⚠️ $it", style = MaterialTheme.typography.labelLarge, color = Fin.colors.warning, textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { translationX = shake }.clip(RoundedCornerShape(10.dp)).background(Fin.colors.warning.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private fun emojiFor(k: CelebKind, goalReached: Boolean) = when {
    goalReached -> "🏆"
    else -> when (k) {
        CelebKind.INCOME -> "💰"; CelebKind.EXPENSE -> "💸"; CelebKind.TRANSFER -> "🔁"; CelebKind.SAVINGS -> "🐷"
        CelebKind.DEBT -> "🤝"; CelebKind.FUEL -> "⛽"; CelebKind.OTHER -> "✨"
    }
}

private fun DrawScope.drawParticle(p: Particle, pos: Offset, alpha: Float) {
    val s = p.size.dp.toPx()
    when (p.shape) {
        0 -> { // koin
            drawCircle(p.color.copy(alpha = alpha), s, pos)
            drawCircle(Color.White.copy(alpha = alpha * 0.35f), s * 0.45f, Offset(pos.x - s * 0.3f, pos.y - s * 0.3f))
        }
        1 -> drawRect(p.color.copy(alpha = alpha), Offset(pos.x - s, pos.y - s * 0.5f), androidx.compose.ui.geometry.Size(s * 2f, s)) // confetti
        else -> { // bintang kecil
            val path = Path()
            for (i in 0 until 8) {
                val r = if (i % 2 == 0) s * 1.2f else s * 0.45f
                val a = i * PI.toFloat() / 4f
                val x = pos.x + cos(a) * r; val y = pos.y + sin(a) * r
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, p.color.copy(alpha = alpha))
        }
    }
}

/** Koin bergerak melengkung dari wallet asal (kiri) ke tujuan (kanan). */
private fun DrawScope.drawTransferFlow(t: Float, center: Offset, color: Color) {
    val start = Offset(center.x - size.width * 0.32f, center.y + 40f)
    val end = Offset(center.x + size.width * 0.32f, center.y + 40f)
    for (i in 0 until 12) {
        val lt = ((t - i * 0.08f) / 0.9f).coerceIn(0f, 1f)
        if (lt <= 0f || lt >= 1f) continue
        val x = start.x + (end.x - start.x) * lt
        val y = start.y - sin(lt * PI.toFloat()) * 160f
        drawCircle(color.copy(alpha = 0.9f * (1f - lt * 0.5f)), 7.dp.toPx() * (1f - lt * 0.3f), Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.35f), 2.5.dp.toPx(), Offset(x - 2.dp.toPx(), y - 2.dp.toPx()))
    }
}

/** Tanda centang yang tergambar sesuai [progress] (0..1). */
private fun DrawScope.drawCheck(progress: Float) {
    val path = Path().apply {
        moveTo(size.width * 0.18f, size.height * 0.52f)
        lineTo(size.width * 0.42f, size.height * 0.74f)
        lineTo(size.width * 0.84f, size.height * 0.28f)
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val seg = Path()
    measure.getSegment(0f, measure.length * progress, seg, true)
    drawPath(seg, Color.White, style = Stroke(width = size.width * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
