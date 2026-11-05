package com.moneytracker.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.core.Fmt
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class BarSeries(val name: String, val color: Color, val values: List<Long>)
data class Slice(val label: String, val value: Long, val color: Color)

@Composable
private fun rememberEntrance(key: Any?): Float {
    val anim = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { anim.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    return anim.value
}

/** Grafik batang berkelompok. Ketuk batang untuk melihat nilai. */
@Composable
fun BarChart(
    labels: List<String>,
    series: List<BarSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
    valueFormatter: (Long) -> String = Fmt::rupiahCompact,
) {
    if (labels.isEmpty() || series.isEmpty()) return EmptyChart(height)
    var selected by remember(labels) { mutableStateOf(labels.lastIndex) }
    val progress = rememberEntrance(labels to series.map { it.values })
    val measurer = rememberTextMeasurer()
    val muted = Fin.colors.muted
    val grid = Fin.colors.stroke
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = muted, fontSize = 10.sp)

    Column(modifier) {
        ChartHeader(labels.getOrNull(selected).orEmpty(), series.map { Triple(it.name, it.color, valueFormatter(it.values.getOrElse(selected) { 0 })) })
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(height).pointerInput(labels.size) {
                detectTapGestures { o -> selected = ((o.x / size.width) * labels.size).toInt().coerceIn(0, labels.lastIndex) }
            },
        ) {
            val bottomPad = 18.dp.toPx()
            val chartH = size.height - bottomPad
            val maxV = max(1L, series.maxOf { s -> s.values.maxOfOrNull { abs(it) } ?: 0 })
            // grid
            for (i in 0..3) {
                val y = chartH * i / 3f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            val groupW = size.width / labels.size
            val inner = groupW * 0.62f
            val barW = inner / series.size
            labels.forEachIndexed { i, label ->
                val gx = i * groupW + (groupW - inner) / 2f
                if (i == selected) drawRoundRect(grid.copy(alpha = 0.5f), Offset(i * groupW + 2f, 0f), Size(groupW - 4f, chartH), CornerRadius(10f, 10f))
                series.forEachIndexed { si, s ->
                    val v = abs(s.values.getOrElse(i) { 0 })
                    val h = chartH * (v.toFloat() / maxV) * progress
                    val alpha = if (i == selected) 1f else 0.75f
                    drawRoundRect(
                        s.color.copy(alpha = alpha), Offset(gx + si * barW + barW * 0.1f, chartH - h),
                        Size(barW * 0.8f, h), CornerRadius(barW * 0.3f, barW * 0.3f),
                    )
                }
                // Tampilkan label secukupnya agar tidak bertumpuk
                val step = max(1, labels.size / 7)
                if (i % step == 0 || i == labels.lastIndex) {
                    val layout = measurer.measure(label, labelStyle)
                    drawText(layout, topLeft = Offset(i * groupW + groupW / 2 - layout.size.width / 2, chartH + 4.dp.toPx()))
                }
            }
        }
    }
}

/** Grafik garis dengan area gradien. Geser/ketuk untuk melihat titik. */
@Composable
fun LineChart(
    labels: List<String>,
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    title: String = "",
    valueFormatter: (Double) -> String = { Fmt.rupiahCompact(it.toLong()) },
) {
    if (values.size < 2) return EmptyChart(height, "Data belum cukup untuk grafik")
    var selected by remember(values.size) { mutableStateOf(values.lastIndex) }
    val progress = rememberEntrance(values)
    val measurer = rememberTextMeasurer()
    val muted = Fin.colors.muted
    val grid = Fin.colors.stroke
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = muted, fontSize = 10.sp)
    val bg = MaterialTheme.colorScheme.surface

    Column(modifier) {
        ChartHeader(labels.getOrNull(selected).orEmpty(), listOf(Triple(title, color, valueFormatter(values[selected]))))
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(height)
                .pointerInput(values.size) { detectTapGestures { o -> selected = ((o.x / size.width) * (values.size - 1) + 0.5f).toInt().coerceIn(0, values.lastIndex) } }
                .pointerInput(values.size) { detectHorizontalDragGestures { change, _ -> selected = ((change.position.x / size.width) * (values.size - 1) + 0.5f).toInt().coerceIn(0, values.lastIndex) } },
        ) {
            val bottomPad = 18.dp.toPx()
            val top = 8.dp.toPx()
            val chartH = size.height - bottomPad - top
            val minV = min(0.0, values.min())
            val maxV = values.max().let { if (it == minV) it + 1 else it }
            fun x(i: Int) = size.width * i / (values.size - 1).toFloat()
            fun y(v: Double) = top + chartH - (chartH * ((v - minV) / (maxV - minV))).toFloat() * progress
            for (i in 0..3) {
                val gy = top + chartH * i / 3f
                drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            if (minV < 0) drawLine(muted.copy(alpha = 0.6f), Offset(0f, y(0.0)), Offset(size.width, y(0.0)), 1.5f)
            val line = Path()
            values.forEachIndexed { i, v ->
                val px = x(i); val py = y(v)
                if (i == 0) line.moveTo(px, py) else {
                    val prevX = x(i - 1); val prevY = y(values[i - 1])
                    val cx = (prevX + px) / 2
                    line.cubicTo(cx, prevY, cx, py, px, py)
                }
            }
            val fill = Path().apply { addPath(line); lineTo(x(values.lastIndex), top + chartH); lineTo(0f, top + chartH); close() }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f)), startY = top, endY = top + chartH))
            drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val sx = x(selected); val sy = y(values[selected])
            drawLine(color.copy(alpha = 0.4f), Offset(sx, top), Offset(sx, top + chartH), 1.5f)
            drawCircle(bg, 7.dp.toPx(), Offset(sx, sy))
            drawCircle(color, 5.dp.toPx(), Offset(sx, sy))
            val step = max(1, labels.size / 6)
            labels.forEachIndexed { i, l ->
                if (i % step == 0 || i == labels.lastIndex) {
                    val layout = measurer.measure(l, labelStyle)
                    val lx = (x(i) - layout.size.width / 2).coerceIn(0f, size.width - layout.size.width)
                    drawText(layout, topLeft = Offset(lx, top + chartH + 4.dp.toPx()))
                }
            }
        }
    }
}

/** Donut + legenda. Ketuk legenda/irisan untuk menyorot. */
@Composable
fun DonutChart(slices: List<Slice>, modifier: Modifier = Modifier, centerLabel: String = "Total", size: Dp = 150.dp) {
    val data = slices.filter { it.value > 0 }
    if (data.isEmpty()) return EmptyChart(size, "Belum ada pengeluaran")
    val total = data.sumOf { it.value }
    var selected by remember(data) { mutableStateOf(-1) }
    val progress = rememberEntrance(data)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.size(size).pointerInput(data) {
                    detectTapGestures { o ->
                        val c = Offset(this.size.width / 2f, this.size.height / 2f)
                        val dx = o.x - c.x; val dy = o.y - c.y
                        if (sqrt(dx * dx + dy * dy) < this.size.width * 0.25f) { selected = -1; return@detectTapGestures }
                        var ang = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f
                        if (ang < 0) ang += 360f
                        var acc = 0f
                        data.forEachIndexed { i, s ->
                            val sweep = 360f * s.value / total
                            if (ang >= acc && ang < acc + sweep) { selected = if (selected == i) -1 else i; return@detectTapGestures }
                            acc += sweep
                        }
                    }
                },
            ) {
                val stroke = this.size.width * 0.16f
                var start = -90f
                data.forEachIndexed { i, s ->
                    val sweep = 360f * s.value / total * progress
                    val w = if (i == selected) stroke * 1.25f else stroke
                    drawArc(
                        s.color.copy(alpha = if (selected == -1 || selected == i) 1f else 0.35f), start + 1f, max(0f, sweep - 2f), false,
                        topLeft = Offset(stroke * 0.65f, stroke * 0.65f), size = Size(this.size.width - stroke * 1.3f, this.size.height - stroke * 1.3f),
                        style = Stroke(w, cap = StrokeCap.Butt),
                    )
                    start += 360f * s.value / total
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val sel = data.getOrNull(selected)
                Text(sel?.label ?: centerLabel, style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(size * 0.55f))
                Text(Fmt.rupiahCompact(sel?.value ?: total), style = MaterialTheme.typography.titleSmall)
                if (sel != null) Text(Fmt.percent(sel.value.toDouble() / total), style = MaterialTheme.typography.labelSmall, color = sel.color)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            data.take(7).forEachIndexed { i, s ->
                Row(
                    Modifier.fillMaxWidth().clickable { selected = if (selected == i) -1 else i }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).background(s.color, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(s.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(Fmt.percent(s.value.toDouble() / total), style = MaterialTheme.typography.labelMedium, color = Fin.colors.muted)
                }
            }
            if (data.size > 7) Text("+${data.size - 7} lainnya", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
        }
    }
}

/** Indikator tangki BBM berbentuk busur. */
@Composable
fun FuelGauge(ratio: Float?, color: Color, modifier: Modifier = Modifier, size: Dp = 140.dp, content: @Composable () -> Unit) {
    val progress = rememberEntrance(ratio)
    val track = Fin.colors.stroke
    val warn = Fin.colors.expense
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.width * 0.09f
            val tl = Offset(stroke / 2, stroke / 2)
            val sz = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 135f, 270f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            if (ratio != null) {
                val c = if (ratio < 0.2f) warn else color
                drawArc(
                    Brush.sweepGradient(listOf(c.copy(alpha = 0.6f), c, c.copy(alpha = 0.6f))), 135f, 270f * ratio.coerceIn(0f, 1f) * progress, false, tl, sz,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

@Composable
private fun ChartHeader(label: String, values: List<Triple<String, Color, String>>) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Fin.colors.muted, modifier = Modifier.weight(1f))
        values.forEach { (name, color, v) ->
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(4.dp))
            if (name.isNotEmpty()) Text("$name ", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
            Text(v, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(10.dp))
        }
    }
}

@Composable
fun EmptyChart(height: Dp, text: String = "Belum ada data") {
    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted)
    }
}
