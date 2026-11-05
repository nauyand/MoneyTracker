package com.moneytracker.app.ui.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneytracker.app.AppInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animasi pembuka (±2,3 detik, ketuk untuk melewati):
 * 1. Latar bercahaya + partikel melayang
 * 2. Logo kartu memantul masuk, garis tren tergambar, titik "pop"
 * 3. Huruf "MoneyTracker" muncul satu per satu, tagline, lalu nama pembuat
 */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val logoScale = remember { Animatable(0.3f) }
    val logoAlpha = remember { Animatable(0f) }
    val line = remember { Animatable(0f) }
    val dot = remember { Animatable(0f) }
    val letters = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    val credit = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }
    var t by remember { mutableFloatStateOf(0f) }
    val title = AppInfo.APP_NAME

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (exit.value > 0f) withFrameNanos { t = (it - start) / 1_000_000_000f }
    }
    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(300)) }
        logoScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) {
        delay(350); line.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        dot.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(Unit) { delay(650); letters.animateTo(1f, tween(700, easing = LinearEasing)) }
    LaunchedEffect(Unit) { delay(1250); tagline.animateTo(1f, tween(450)) }
    LaunchedEffect(Unit) { delay(1500); credit.animateTo(1f, tween(500)) }
    LaunchedEffect(Unit) { delay(2300); exit.animateTo(0f, tween(350)); onDone() }

    val bg = Color(0xFF0B0F1A) // intro selalu gelap agar logo & teks putih kontras di tema apa pun
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = exit.value; scaleX = 1f + (1f - exit.value) * 0.08f; scaleY = scaleX }
            .background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        // Latar: cahaya berdenyut + partikel melayang
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.42f)
            val pulse = 0.9f + 0.1f * sin(t * 2.4f)
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF7C5CFF).copy(alpha = 0.38f), Color(0xFF3D8BFF).copy(alpha = 0.12f), Color.Transparent), c, size.minDimension * 0.7f * pulse),
                size.minDimension * 0.7f * pulse, c,
            )
            for (i in 0 until 26) {
                val a = i * 2.39996f // sudut emas
                val r = size.minDimension * (0.18f + (i % 7) * 0.07f)
                val drift = t * (0.15f + (i % 5) * 0.05f)
                val p = Offset(c.x + cos(a + drift) * r, c.y + sin(a + drift) * r * 1.3f - t * 18f * (i % 3))
                val alpha = (0.15f + 0.25f * ((sin(t * 3f + i) + 1f) / 2f)) * logoAlpha.value
                drawCircle(if (i % 3 == 0) Color(0xFF2DD4A4) else Color.White, (1.5f + i % 3).dp.toPx(), p, alpha = alpha)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            // Logo
            Canvas(Modifier.size(132.dp).graphicsLayer { scaleX = logoScale.value; scaleY = logoScale.value; alpha = logoAlpha.value; rotationZ = (1f - logoScale.value) * -12f }) {
                val w = size.width; val h = size.height
                val cardTL = Offset(w * 0.08f, h * 0.22f)
                val cardSize = Size(w * 0.84f, h * 0.56f)
                drawRoundRect(Color(0xFF7C5CFF).copy(alpha = 0.35f), Offset(cardTL.x + 6f, cardTL.y + 14f), cardSize, CornerRadius(w * 0.1f))
                drawRoundRect(Brush.linearGradient(listOf(Color(0xFF8B6BFF), Color(0xFF5B3DF5), Color(0xFF3D6BFF)), cardTL, Offset(cardTL.x + cardSize.width, cardTL.y + cardSize.height)), cardTL, cardSize, CornerRadius(w * 0.1f))
                // kilau melintas
                val shine = ((t - 0.9f) / 0.7f).coerceIn(0f, 1f)
                if (shine in 0.01f..0.99f) {
                    val x = cardTL.x + cardSize.width * shine
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.25f), Color.Transparent), x - 30f, x + 30f), Offset(x - 30f, cardTL.y), Size(60f, cardSize.height))
                }
                // garis tren
                val path = Path().apply {
                    moveTo(w * 0.2f, h * 0.64f); lineTo(w * 0.4f, h * 0.48f); lineTo(w * 0.55f, h * 0.57f); lineTo(w * 0.78f, h * 0.36f)
                }
                val m = PathMeasure().apply { setPath(path, false) }
                val seg = Path(); m.getSegment(0f, m.length * line.value, seg, true)
                drawPath(seg, Color.White, style = Stroke(w * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (dot.value > 0f) {
                    drawCircle(Color(0xFF3DDBB0).copy(alpha = 0.35f), w * 0.09f * dot.value, Offset(w * 0.78f, h * 0.36f))
                    drawCircle(Color(0xFF3DDBB0), w * 0.045f * dot.value, Offset(w * 0.78f, h * 0.36f))
                }
            }
            Spacer(Modifier.height(18.dp))
            // Nama aplikasi, huruf demi huruf
            Row {
                title.forEachIndexed { i, ch ->
                    val p = ((letters.value * title.length) - i).coerceIn(0f, 1f)
                    Text(
                        ch.toString(), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = if (i < 5) Color.White else Color(0xFF9D87FF),
                        modifier = Modifier.graphicsLayer { alpha = p; translationY = (1f - p) * 28f; scaleX = 0.7f + 0.3f * p; scaleY = scaleX },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                AppInfo.TAGLINE, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.graphicsLayer { alpha = tagline.value; translationY = (1f - tagline.value) * 12f },
            )
        }

        // Nama pembuat
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 40.dp)
                .graphicsLayer { alpha = credit.value; translationY = (1f - credit.value) * 16f },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("dibuat oleh", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), letterSpacing = 2.sp)
            Text(AppInfo.CREATOR, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
            // garis gradien kecil di bawah nama
            Box(Modifier.padding(top = 6.dp).size(width = (48 * credit.value).dp, height = 3.dp).background(Brush.horizontalGradient(listOf(Color(0xFF7C5CFF), Color(0xFF2DD4A4))), androidx.compose.foundation.shape.CircleShape))
        }
    }
}
