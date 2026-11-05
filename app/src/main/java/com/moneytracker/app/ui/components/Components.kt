package com.moneytracker.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneytracker.app.MoneyTrackerApp
import com.moneytracker.app.data.Providers
import com.moneytracker.app.data.WalletEntity
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.core.Confidence
import com.moneytracker.core.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

val LocalApp = staticCompositionLocalOf<MoneyTrackerApp> { error("App tidak tersedia") }
val LocalHideBalance = staticCompositionLocalOf { false }

@Composable
fun FinCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    color: Color = Fin.colors.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    // Efek tekan: kartu sedikit mengecil lalu memantul kembali.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onClick != null) 0.97f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    Surface(
        modifier = modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick) else Modifier),
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, Fin.colors.stroke.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(padding).animateContentSize(), content = content)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}

/** Menampilkan nominal; disamarkan bila mode sembunyikan saldo aktif. */
@Composable
fun money(value: Long, withSign: Boolean = false): String = if (LocalHideBalance.current) "Rp•••••" else Fmt.rupiah(value, withSign)

/**
 * Nominal yang "berjalan" (count up/down) saat nilainya berubah, mis. saldo setelah transaksi.
 * Tetap persis sama dengan nilai akhir; animasi bisa dimatikan di Pengaturan.
 */
@Composable
fun AnimatedMoney(
    value: Long,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    withSign: Boolean = false,
) {
    val motion = com.moneytracker.app.LocalUi.current.motion
    var from by remember { mutableStateOf(value) }
    var target by remember { mutableStateOf(value) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value == target) return@LaunchedEffect
        from = if (progress.value < 1f) from + ((target - from) * progress.value.toDouble()).toLong() else target
        target = value
        if (!motion) { progress.snapTo(1f); return@LaunchedEffect }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val shown = if (progress.value >= 1f) target else from + ((target - from) * progress.value.toDouble()).toLong()
    val delta = target - from
    // Kilatan warna singkat: hijau bila naik, merah bila turun
    val flash = if (progress.value < 1f && delta != 0L) (1f - progress.value) else 0f
    val base = if (color == Color.Unspecified) androidx.compose.material3.LocalContentColor.current else color
    val tint = if (flash > 0f) androidx.compose.ui.graphics.lerp(base, if (delta > 0) Fin.colors.income else Fin.colors.expense, flash * 0.8f) else base
    Text(money(shown, withSign), style = style, color = tint, fontWeight = fontWeight, modifier = modifier, maxLines = 1)
}

@Composable
fun StatTile(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier, sub: String? = null, onClick: (() -> Unit)? = null) {
    FinCard(modifier, onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).background(tint.copy(alpha = 0.15f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Fin.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun WalletBadge(wallet: WalletEntity?, size: Dp = 40.dp) {
    val p = Providers.byCode(wallet?.provider ?: "CUSTOM")
    val color = Color(wallet?.color ?: p.color)
    val text = if (wallet?.provider == "CUSTOM") wallet.name.take(2).uppercase() else p.initials
    Box(Modifier.size(size).background(color, RoundedCornerShape(size * 0.3f)), contentAlignment = Alignment.Center) {
        Text(
            text, color = if (color.luminanceApprox() > 0.6f) Color(0xFF111111) else Color.White,
            fontWeight = FontWeight.Bold, fontSize = (size.value * (if (text.length > 3) 0.24f else 0.3f)).sp, maxLines = 1,
        )
    }
}

private fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

@Composable
fun EmojiIcon(emoji: String, color: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).background(color.copy(alpha = 0.18f), RoundedCornerShape(size * 0.3f)), contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = (size.value * 0.45f).sp)
    }
}

@Composable
fun FinProgress(ratio: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val anim by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(600), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Fin.colors.stroke)) {
        Box(Modifier.fillMaxWidth(anim).fillMaxHeight().clip(CircleShape).background(color))
    }
}

@Composable
fun ConfidenceChip(c: Confidence) {
    val color = when (c) { Confidence.HIGH -> Fin.colors.income; Confidence.MEDIUM -> Fin.colors.warning; Confidence.LOW -> Fin.colors.expense }
    Row(
        Modifier.background(color.copy(alpha = 0.15f), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text("Akurasi ${c.label.lowercase()}", style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = color,
        modifier = modifier.background(color.copy(alpha = 0.14f), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun EmptyState(emoji: String, title: String, message: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Fin.colors.muted, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun BasisNote(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Info, null, tint = Fin.colors.muted, modifier = Modifier.size(13.dp).padding(top = 2.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
    }
}

@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Fin.colors.muted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor, textAlign = TextAlign.End)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

// ---------------- Input ----------------

/** Pemisah ribuan saat mengetik nominal (state hanya berisi digit). */
object ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        if (digits.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val neg = digits.startsWith("-")
        val body = digits.removePrefix("-")
        val formatted = (if (neg) "-" else "") + (body.toLongOrNull()?.let { Fmt.groupDigits(it) } ?: body)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var seen = 0
                for (i in formatted.indices) {
                    if (seen == offset) return i
                    if (formatted[i] != '.') seen++
                }
                return formatted.length
            }
            override fun transformedToOriginal(offset: Int): Int =
                formatted.take(offset).count { it != '.' }.coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = false,
    large: Boolean = false,
    supporting: String? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val neg = allowNegative && raw.startsWith("-")
            val d = raw.filter { it.isDigit() }.trimStart('0').take(15)
            onValueChange((if (neg) "-" else "") + d)
        },
        label = { Text(label) },
        prefix = { Text("Rp ") },
        singleLine = true,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        visualTransformation = ThousandsTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

fun String.toAmount(): Long = this.toLongOrNull() ?: 0L

@Composable
fun FinTextField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, supporting: String? = null, suffix: String? = null, isError: Boolean = false,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType), shape = RoundedCornerShape(16.dp),
        supportingText = supporting?.let { { Text(it) } }, suffix = suffix?.let { { Text(it) } }, isError = isError,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Field yang membuka pemilih (bottom sheet / dialog). */
@Composable
fun PickerField(label: String, value: String, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ExpandMore, null, tint = Fin.colors.muted)
        }
    }
}

data class Option<T>(val value: T, val label: String, val sub: String? = null, val leading: (@Composable () -> Unit)? = null, val indent: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OptionSheet(title: String, options: List<Option<T>>, selected: T?, onDismiss: () -> Unit, onSelect: (T) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = options.size > 8)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            items(options) { o ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(o.value); onDismiss() }
                        .padding(start = if (o.indent) 44.dp else 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (o.leading != null) { o.leading.invoke(); Spacer(Modifier.width(12.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(o.label, style = if (o.indent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
                        if (o.sub != null) Text(o.sub, style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
                    }
                    if (o.value == selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun <T> SelectField(label: String, options: List<Option<T>>, selected: T?, modifier: Modifier = Modifier, placeholder: String = "Pilih", onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == selected }
    PickerField(label, current?.label ?: placeholder, modifier, leading = current?.leading) { open = true }
    if (open) OptionSheet(label, options, selected, { open = false }, onSelect)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate?, onChange: (LocalDate?) -> Unit, modifier: Modifier = Modifier, allowClear: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, date?.let { Fmt.dateWithDay(it) } ?: "Tidak diatur", modifier) { open = true }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("Pilih") }
            },
            dismissButton = {
                Row {
                    if (allowClear) TextButton(onClick = { onChange(null); open = false }) { Text("Hapus") }
                    TextButton(onClick = { open = false }) { Text("Batal") }
                }
            },
        ) { DatePicker(st) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, time: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, "%02d:%02d".format(time.hour, time.minute), modifier) { open = true }
    if (open) {
        val st = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { onChange(LocalTime.of(st.hour, st.minute)); open = false }) { Text("Pilih") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Batal") } },
            text = { TimePicker(st) },
        )
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String = "Hapus", destructive: Boolean = true, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun ListDivider() = HorizontalDivider(color = Fin.colors.stroke.copy(alpha = 0.6f), thickness = 1.dp)

@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Fin.colors.cardElevated).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, style = MaterialTheme.typography.labelMedium, color = if (sel) Color.White else Fin.colors.muted, maxLines = 1)
            }
        }
    }
}
