package com.apartmentdog.sheargenius.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.widthIn
import com.apartmentdog.sheargenius.AppState
import com.apartmentdog.sheargenius.ShadeMode
import com.apartmentdog.sheargenius.Screen
import com.apartmentdog.sheargenius.Tool
import com.apartmentdog.sheargenius.model.Part
import com.apartmentdog.sheargenius.model.SkinLayout
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun EditorScreen(state: AppState) {
    var pendingSlim by remember { mutableStateOf<Boolean?>(null) }
    var showColor by remember { mutableStateOf(false) }
    val scale = remember { mutableFloatStateOf(1f) }
    val offset = remember { mutableStateOf(Offset.Zero) }
    val canvasPx = remember { mutableIntStateOf(0) }

    fun zoomBy(f: Float) {
        val s = canvasPx.intValue.toFloat()
        if (s <= 0f) return
        val old = scale.floatValue
        val ns = (old * f).coerceIn(1f, 12f)
        val c = Offset(s / 2f, s / 2f)
        val no = (offset.value - c) * (ns / old) + c
        scale.floatValue = ns
        offset.value = Offset(no.x.coerceIn(s - s * ns, 0f), no.y.coerceIn(s - s * ns, 0f))
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlockButton(
                        onClick = { requestModel(state, false) { pendingSlim = it } },
                        label = "Steve",
                        selected = !state.slim
                    )
                    BlockButton(
                        onClick = { requestModel(state, true) { pendingSlim = it } },
                        label = "Alex",
                        selected = state.slim
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlockButton(
                        onClick = { state.overlayVisible = !state.overlayVisible },
                        label = "Overlay",
                        selected = state.overlayVisible
                    )
                    BlockButton(onClick = { state.toggleLabels() }, label = "Labels", selected = state.showLabels)
                }
            }
            Spacer(Modifier.height(10.dp))
            SkinCanvas(state, scale, offset, canvasPx, Modifier.fillMaxWidth().aspectRatio(1f))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BlockButton(onClick = { zoomBy(1f / 1.5f) }, icon = PixelIcons.Minus, enabled = scale.floatValue > 1f)
                    PixelText(
                        "${(scale.floatValue * 100).roundToInt()}%",
                        14.sp,
                        modifier = Modifier.widthIn(min = 48.dp),
                        textAlign = TextAlign.Center
                    )
                    BlockButton(onClick = { zoomBy(1.5f) }, icon = PixelIcons.Plus, enabled = scale.floatValue < 12f)
                    BlockButton(onClick = {
                        scale.floatValue = 1f
                        offset.value = Offset.Zero
                    }, label = "Fit")
                }
                Spacer(Modifier.weight(1f))
                BlockButton(onClick = { state.toggleMiniPreview() }, label = "3D", selected = state.miniPreview)
            }
            if (state.miniPreview) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkinModelView(
                        state = state,
                        rx = 0.15f,
                        ry = -0.5f,
                        zoom = 1f,
                        showOverlay = true,
                        background = PREVIEW_BACKGROUNDS[state.previewBg.coerceIn(0, PREVIEW_BACKGROUNDS.lastIndex)].second,
                        modifier = Modifier
                            .size(104.dp)
                            .insetFrame()
                            .clipToBounds()
                            .clickable { state.screen = Screen.PREVIEW }
                    )
                    Spacer(Modifier.width(12.dp))
                    PixelText("Tap the model for the full preview", 12.sp, modifier = Modifier.weight(1f))
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
        ) {
            ToolSlot(state, Tool.PENCIL, PixelIcons.Pencil)
            ToolSlot(state, Tool.ERASER, PixelIcons.Eraser)
            ToolSlot(state, Tool.LINE, PixelIcons.Line)
            ToolSlot(state, Tool.FILL, PixelIcons.Bucket)
            ToolSlot(state, Tool.EYEDROPPER, PixelIcons.Dropper)
            ToolSlot(state, Tool.SHADE, PixelIcons.Shade)
            ToolSlot(state, Tool.MOVE, PixelIcons.Move)
            Slot(size = 36.dp, selected = state.mirror, onClick = { state.mirror = !state.mirror }) {
                PixelIconView(PixelIcons.Mirror, Blocky.IconDark, Modifier.size(20.dp))
            }
        }

        if (state.tool == Tool.SHADE) {
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ShadeMode.LIGHTEN to "Lighten",
                        ShadeMode.DARKEN to "Darken",
                        ShadeMode.SHINE to "Shine",
                        ShadeMode.NOISE to "Noise",
                        ShadeMode.NOISY_PEN to "Noisy pen",
                        ShadeMode.DITHER to "Dither"
                    ).chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (mode, label) ->
                                BlockButton(
                                    onClick = { state.shadeMode = mode },
                                    label = label,
                                    selected = state.shadeMode == mode,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                if (state.shadeMode != ShadeMode.DITHER) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelText("Amount", 13.sp)
                        Slider(
                            value = state.shadeAmount,
                            onValueChange = { state.shadeAmount = it },
                            onValueChangeFinished = { state.saveShadeAmount() },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Blocky.Green,
                                activeTrackColor = Blocky.Green,
                                inactiveTrackColor = Blocky.ButtonDark
                            )
                        )
                        PixelText(
                            "${(state.shadeAmount * 100).roundToInt()}%",
                            13.sp,
                            modifier = Modifier.widthIn(min = 40.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Slot(size = 48.dp, onClick = { showColor = true }) {
                    Box(Modifier.size(28.dp).background(Color(state.color)))
                }
                PixelText(hexOf(state.color), 16.sp, modifier = Modifier.weight(1f))
                BlockButton(onClick = { state.addToPalette(state.color) }, icon = PixelIcons.Plus, label = "Save")
            }
            Spacer(Modifier.height(10.dp))
            PaletteGrid(
                state.palette,
                state.color,
                onPick = { state.color = it },
                onRemove = { state.removeFromPalette(it) }
            )
        }
    }

    val target = pendingSlim
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingSlim = null },
            title = { Text(if (target) "Switch to Alex?" else "Switch to Steve?") },
            text = {
                Text(
                    if (target) "Alex arms are 3 pixels wide, so the last column of each arm face is dropped. You can undo this."
                    else "Steve arms are 4 pixels wide, so the last column of each arm face is repeated to fill the gap. You can undo this."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    state.changeModel(target)
                    pendingSlim = null
                }) { Text("Convert") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSlim = null }) { Text("Cancel") }
            }
        )
    }

    if (showColor) {
        ColorDialog(
            initial = state.color,
            onDismiss = { showColor = false },
            onApply = {
                state.color = it
                state.savePrefs()
            },
            onSave = { state.addToPalette(it) }
        )
    }
}

private fun requestModel(state: AppState, slim: Boolean, ask: (Boolean) -> Unit) {
    if (state.slim == slim) return
    if (state.armsHaveContent()) ask(slim) else state.changeModel(slim)
}

@Composable
private fun ToolSlot(state: AppState, tool: Tool, icon: PixelIcon) {
    Slot(size = 36.dp, selected = state.tool == tool, onClick = { state.tool = tool }) {
        PixelIconView(icon, Blocky.IconDark, Modifier.size(20.dp))
    }
}

private val ZONE_LABELS = mapOf(
    "head" to "Head", "hat" to "Hat",
    "body" to "Body", "jacket" to "Jacket",
    "rarm" to "R arm", "rsleeve" to "R sleeve",
    "larm" to "L arm", "lsleeve" to "L sleeve",
    "rleg" to "R leg", "rpants" to "R pants",
    "lleg" to "L leg", "lpants" to "L pants"
)

@Composable
private fun SkinCanvas(
    state: AppState,
    scale: MutableFloatState,
    offset: MutableState<Offset>,
    canvasPx: MutableIntState,
    modifier: Modifier
) {
    val bitmap = remember { Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val buffer = remember { IntArray(SkinLayout.COUNT) }
    val context = LocalContext.current
    val typeface = remember {
        try {
            Typeface.createFromAsset(context.assets, "fonts/pixelify.ttf")
        } catch (e: Exception) {
            Typeface.MONOSPACE
        }
    }
    val labelFill = remember(typeface) {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            this.typeface = typeface
        }
    }
    val labelStroke = remember(typeface) {
        Paint().apply {
            isAntiAlias = true
            color = 0xFF2C2C2A.toInt()
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            this.typeface = typeface
        }
    }

    Canvas(
        modifier
            .insetFrame()
            .onSizeChanged { canvasPx.intValue = it.width }
            .clipToBounds()
            .pointerInput(Unit) { editorGestures(state, scale, offset) }
    ) {
        @Suppress("UNUSED_VARIABLE")
        val v = state.version
        val showOverlay = state.overlayVisible
        val map = state.regionMap
        val boxes = state.boxes
        val px = state.pixels
        for (i in 0 until SkinLayout.COUNT) {
            val b = map[i]
            buffer[i] = if (b < 0) {
                0
            } else {
                val box = boxes[b]
                if (box.overlay && !showOverlay) {
                    0x66000000
                } else {
                    val p = px[i]
                    if ((p ushr 24) == 0) {
                        val a = if (box.overlay) 0x40 else 0x80
                        (a shl 24) or (box.part.guide and 0xFFFFFF)
                    } else {
                        p
                    }
                }
            }
        }
        bitmap.setPixels(buffer, 0, 64, 0, 0, 64, 64)

        val s = scale.floatValue
        val o = offset.value
        val full = size.width * s
        val cell = full / 64f

        val light = Color(0xFFF1EFE8)
        val dark = Color(0xFFD3D1C7)
        for (cy in 0 until 16) for (cx in 0 until 16) {
            drawRect(
                if ((cx + cy) % 2 == 0) light else dark,
                Offset(o.x + cx * 4 * cell, o.y + cy * 4 * cell),
                Size(4 * cell + 0.5f, 4 * cell + 0.5f)
            )
        }
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(64, 64),
            dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt()),
            dstSize = IntSize(full.roundToInt(), full.roundToInt()),
            filterQuality = FilterQuality.None
        )
        val minor = Color.Black.copy(alpha = 0.08f)
        val major = Color.Black.copy(alpha = 0.25f)
        for (i in 0..64) {
            val isMajor = i % 8 == 0
            if (!isMajor && cell < 6f) continue
            val c = if (isMajor) major else minor
            val p = i * cell
            drawLine(c, Offset(o.x + p, o.y), Offset(o.x + p, o.y + full), 1f)
            drawLine(c, Offset(o.x, o.y + p), Offset(o.x + full, o.y + p), 1f)
        }
        if (state.showLabels) {
            val ts = 11.sp.toPx()
            labelFill.textSize = ts
            labelStroke.textSize = ts
            labelStroke.strokeWidth = ts / 4f
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                for (b in boxes) {
                    val label = ZONE_LABELS[b.id] ?: continue
                    val lx = o.x + (b.u + b.d + b.w) * cell
                    val ly = o.y + (b.v + (b.d + b.h) / 2f) * cell + ts / 3f
                    nc.drawText(label, lx, ly, labelStroke)
                    nc.drawText(label, lx, ly, labelFill)
                }
            }
        }
    }
}

/** One finger paints, two fingers zoom and pan. A stroke is rolled back if a second finger lands. */
private suspend fun PointerInputScope.editorGestures(
    state: AppState,
    scale: MutableFloatState,
    offset: MutableState<Offset>
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val before = state.copyPixels()
        var multi = false
        var changed = false
        var last: IntOffset? = null
        val shading = state.tool == Tool.SHADE
        val visited = HashSet<Int>()

        fun toSkin(p: Offset): IntOffset? {
            val cell = size.width * scale.floatValue / 64f
            val x = floor((p.x - offset.value.x) / cell).toInt()
            val y = floor((p.y - offset.value.y) / cell).toInt()
            return if (x in 0..63 && y in 0..63) IntOffset(x, y) else null
        }

        fun stroke(p: Offset) {
            val cur = toSkin(p)
            if (cur == null) {
                last = null
                return
            }
            val c = if (state.tool == Tool.ERASER) 0 else state.color
            val from = last ?: cur
            line(from, cur) { x, y ->
                val idx = y * 64 + x
                val hit = if (shading) state.shade(idx, visited) else state.setPixel(idx, c)
                if (hit) changed = true
            }
            last = cur
            state.touched()
        }

        val pen = state.tool == Tool.PENCIL || state.tool == Tool.ERASER || shading
        val moving = state.tool == Tool.MOVE
        val lineStart = if (state.tool == Tool.LINE) toSkin(down.position) else null

        fun previewLine(p: Offset) {
            val start = lineStart ?: return
            val end = toSkin(p) ?: return
            state.restore(before)
            changed = false
            val c = state.color
            line(start, end) { x, y -> if (state.setPixel(y * 64 + x, c)) changed = true }
            state.touched()
        }

        if (pen) stroke(down.position)
        if (lineStart != null) previewLine(down.position)
        down.consume()

        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) {
                if (!multi) {
                    multi = true
                    if (changed) {
                        state.restore(before)
                        changed = false
                    }
                    visited.clear()
                }
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = true)
                val old = scale.floatValue
                val ns = (old * zoom).coerceIn(1f, 12f)
                var no = (offset.value - centroid) * (ns / old) + centroid + pan
                val full = size.width * ns
                no = Offset(
                    no.x.coerceIn(size.width - full, 0f),
                    no.y.coerceIn(size.height - full, 0f)
                )
                scale.floatValue = ns
                offset.value = no
            } else if (!multi && pressed == 1) {
                if (moving) {
                    val pan = event.calculatePan()
                    val full = size.width * scale.floatValue
                    val no = offset.value + pan
                    offset.value = Offset(
                        no.x.coerceIn(size.width - full, 0f),
                        no.y.coerceIn(size.height - full, 0f)
                    )
                } else {
                    val pos = event.changes.first { it.pressed }.position
                    if (pen) stroke(pos) else if (lineStart != null) previewLine(pos)
                }
            }
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        } while (event.changes.any { it.pressed })

        if (!multi) {
            when (state.tool) {
                Tool.FILL -> toSkin(down.position)?.let {
                    if (state.fill(it.y * 64 + it.x, state.color)) changed = true
                }
                Tool.EYEDROPPER -> toSkin(down.position)?.let { state.pick(it.y * 64 + it.x) }
                else -> {}
            }
        }
        if (changed) {
            state.touched()
            state.commit(before)
        }
    }
}

private fun line(a: IntOffset, b: IntOffset, plot: (Int, Int) -> Unit) {
    var x0 = a.x
    var y0 = a.y
    val dx = abs(b.x - x0)
    val dy = -abs(b.y - y0)
    val sx = if (x0 < b.x) 1 else -1
    val sy = if (y0 < b.y) 1 else -1
    var err = dx + dy
    while (true) {
        plot(x0, y0)
        if (x0 == b.x && y0 == b.y) break
        val e2 = 2 * err
        if (e2 >= dy) {
            err += dy
            x0 += sx
        }
        if (e2 <= dx) {
            err += dx
            y0 += sy
        }
    }
}
