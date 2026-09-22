package com.apartmentdog.sheargenius.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apartmentdog.sheargenius.AppState
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun ReferenceScreen(state: AppState) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            state.addReference(uri)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }
    }
    var sampled by remember { mutableStateOf<Int?>(null) }
    val bmp = state.references.getOrNull(state.selectedRef)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PixelText("Reference", 16.sp)
                BlockButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    icon = PixelIcons.Photo,
                    label = "Import"
                )
            }
            Spacer(Modifier.height(10.dp))
            if (bmp == null) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(4f / 3f).insetFrame(),
                    contentAlignment = Alignment.Center
                ) {
                    PixelText("Import an image to pick colors from it", 13.sp, Color.White)
                }
            } else {
                key(bmp) {
                    ReferenceCanvas(bmp, state.sampleSize) { c ->
                        sampled = c
                        state.color = c
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PixelText("Sample", 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "1px", 3 to "3×3", 5 to "5×5").forEach { (n, label) ->
                        BlockButton(onClick = { state.sampleSize = n }, label = label, selected = state.sampleSize == n)
                    }
                }
            }
            if (state.references.size > 1) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.references.forEachIndexed { i, b ->
                        key(b) {
                            val thumb = remember(b) { b.asImageBitmap() }
                            Slot(
                                size = 52.dp,
                                selected = i == state.selectedRef,
                                onClick = { state.selectedRef = i },
                                onLongClick = { state.removeReference(i) }
                            ) {
                                Image(
                                    bitmap = thumb,
                                    contentDescription = null,
                                    modifier = Modifier.size(38.dp),
                                    contentScale = ContentScale.Crop,
                                    filterQuality = FilterQuality.None
                                )
                            }
                        }
                    }
                }
                PixelText("Long-press a thumbnail to remove it", 11.sp)
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            val shown = sampled ?: state.color
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Slot(size = 52.dp) {
                    Box(Modifier.size(30.dp).background(Color(shown)))
                }
                Column(Modifier.weight(1f)) {
                    PixelText(hexOf(shown), 16.sp)
                    PixelText(hsvText(shown), 12.sp)
                }
                BlockButton(onClick = { state.addToPalette(shown) }, icon = PixelIcons.Plus, label = "Palette")
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PixelText("Project palette", 14.sp)
                BlockButton(
                    onClick = { bmp?.let { state.replacePalette(extractPalette(it)) } },
                    icon = PixelIcons.Wand,
                    label = "Extract",
                    enabled = bmp != null
                )
            }
            Spacer(Modifier.height(10.dp))
            PaletteGrid(
                state.palette,
                state.color,
                onPick = {
                    state.color = it
                    sampled = it
                },
                onRemove = { state.removeFromPalette(it) }
            )
        }
    }
}

private fun fitOf(bw: Int, bh: Int, w: Float, h: Float): Triple<Float, Float, Float> {
    val fit = minOf(w / bw, h / bh)
    return Triple(fit, (w - bw * fit) / 2f, (h - bh * fit) / 2f)
}

@Composable
private fun ReferenceCanvas(bitmap: Bitmap, sampleSize: Int, onSample: (Int) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val scale = remember { mutableFloatStateOf(1f) }
    val offset = remember { mutableStateOf(Offset.Zero) }
    var mark by remember { mutableStateOf<IntOffset?>(null) }
    val n by rememberUpdatedState(sampleSize)
    val sample by rememberUpdatedState(onSample)

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .insetFrame()
            .clipToBounds()
            .pointerInput(bitmap) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    var moved = false
                    var multi = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) multi = true
                        if (!moved) {
                            val first = event.changes.firstOrNull { it.id == down.id }
                            if (first != null && (first.position - down.position).getDistance() > slop) moved = true
                        }
                        if (pressed >= 1 && (moved || multi)) {
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (centroid.isSpecified) {
                                val pan = event.calculatePan()
                                val zoom = if (pressed >= 2) event.calculateZoom() else 1f
                                val old = scale.floatValue
                                val ns = (old * zoom).coerceIn(1f, 16f)
                                var no = (offset.value - centroid) * (ns / old) + centroid + pan
                                val fw = size.width * ns
                                val fh = size.height * ns
                                no = Offset(no.x.coerceIn(size.width - fw, 0f), no.y.coerceIn(size.height - fh, 0f))
                                scale.floatValue = ns
                                offset.value = no
                            }
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } while (event.changes.any { it.pressed })

                    if (!moved && !multi) {
                        val (fit, fox, foy) = fitOf(bitmap.width, bitmap.height, size.width.toFloat(), size.height.toFloat())
                        val s = scale.floatValue
                        val o = offset.value
                        val ix = floor(((down.position.x - o.x) / s - fox) / fit).toInt()
                        val iy = floor(((down.position.y - o.y) / s - foy) / fit).toInt()
                        if (ix in 0 until bitmap.width && iy in 0 until bitmap.height) {
                            mark = IntOffset(ix, iy)
                            sample(sampleBitmap(bitmap, ix, iy, n))
                        }
                    }
                }
            }
    ) {
        drawRect(Blocky.IconDark)
        val (fit, fox, foy) = fitOf(bitmap.width, bitmap.height, size.width, size.height)
        val s = scale.floatValue
        val o = offset.value
        val x0 = o.x + fox * s
        val y0 = o.y + foy * s
        val w = bitmap.width * fit * s
        val h = bitmap.height * fit * s
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstOffset = IntOffset(x0.roundToInt(), y0.roundToInt()),
            dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.None
        )
        val m = mark
        if (m != null) {
            val cell = fit * s
            val half = sampleSize / 2
            val left = x0 + (m.x - half) * cell
            val top = y0 + (m.y - half) * cell
            val side = sampleSize * cell
            drawRect(Color.White, Offset(left - 2f, top - 2f), Size(side + 4f, side + 4f), style = Stroke(2f))
            drawRect(Color.Black, Offset(left, top), Size(side, side), style = Stroke(2f))
        }
    }
}

private fun sampleBitmap(b: Bitmap, cx: Int, cy: Int, n: Int): Int {
    val half = n / 2
    var r = 0L
    var g = 0L
    var bl = 0L
    var count = 0
    for (y in cy - half..cy + half) {
        for (x in cx - half..cx + half) {
            if (x < 0 || y < 0 || x >= b.width || y >= b.height) continue
            val p = b.getPixel(x, y)
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            bl += p and 0xFF
            count++
        }
    }
    if (count == 0) return 0xFF000000.toInt()
    return (0xFF shl 24) or ((r / count).toInt() shl 16) or ((g / count).toInt() shl 8) or (bl / count).toInt()
}

/** Most common colors, bucketed to 4 bits per channel and averaged within each bucket. */
private fun extractPalette(b: Bitmap): List<Int> {
    val total = b.width * b.height
    val stride = maxOf(1, total / 60000)
    val buckets = HashMap<Int, LongArray>()
    var i = 0
    while (i < total) {
        val p = b.getPixel(i % b.width, i / b.width)
        if ((p ushr 24) >= 128) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val bl = p and 0xFF
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (bl shr 4)
            val acc = buckets.getOrPut(key) { LongArray(4) }
            acc[0] += r.toLong()
            acc[1] += g.toLong()
            acc[2] += bl.toLong()
            acc[3] += 1L
        }
        i += stride
    }
    return buckets.values
        .sortedByDescending { it[3] }
        .take(8)
        .map { a ->
            (0xFF shl 24) or ((a[0] / a[3]).toInt() shl 16) or ((a[1] / a[3]).toInt() shl 8) or (a[2] / a[3]).toInt()
        }
        .distinct()
}
