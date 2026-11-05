package com.moneytracker.app.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneytracker.app.ui.theme.Fin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Memuat gambar (file internal atau Uri) dengan downsampling agar hemat memori. */
private fun decode(ctx: Context, file: File?, uri: Uri?, maxPx: Int): ImageBitmap? = runCatching {
    fun open() = file?.inputStream() ?: ctx.contentResolver.openInputStream(uri!!)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > maxPx * 2 || bounds.outHeight / sample > maxPx * 2) sample *= 2
    open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }?.asImageBitmap()
}.getOrNull()

@Composable
fun Thumb(file: File? = null, uri: Uri? = null, size: Dp = 84.dp, modifier: Modifier = Modifier, maxPx: Int = 400) {
    val ctx = LocalContext.current
    var bmp by remember(file, uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file, uri) { bmp = withContext(Dispatchers.IO) { decode(ctx, file, uri, maxPx) } }
    Box(modifier.size(size).clip(RoundedCornerShape(14.dp)).background(Fin.colors.cardElevated), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}
