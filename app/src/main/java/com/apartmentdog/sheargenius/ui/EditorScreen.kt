package com.apartmentdog.sheargenius.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerType
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.key
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.pointer.PointerEvent
import com.apartmentdog.sheargenius.AppState
import com.apartmentdog.sheargenius.SelRect
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
    var showLayers by remember { mutableStateOf(false) }
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

    var sheet by remember { mutableStateOf<EditorSheet?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Zoom, current tool, and view settings
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BlockButton(onClick = { zoomBy(1f / 1.5f) }, icon = PixelIcons.Minus, enabled = scale.floatValue > 1f)
                PixelText(
                    "${(scale.floatValue * 100).roundToInt()}%",
                    14.sp,
                    modifier = Modifier.widthIn(min = 44.dp),
                    textAlign = TextAlign.Center
                )
                BlockButton(onClick = { zoomBy(1.5f) }, icon = PixelIcons.Plus, enabled = scale.floatValue < 12f)
                BlockButton(onClick = {
                    scale.floatValue = 1f
                    offset.value = Offset.Zero
                }, label = "Fit")
                PixelText(
                    TOOL_NAMES[state.tool] ?: "",
                    12.sp,
                    Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    shadow = true
                )
                BlockButton(onClick = { sheet = EditorSheet.VIEW }, icon = PixelIcons.Eye, label = "View")
            }

            // Canvas: the largest square that fits the free space
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                SkinCanvas(state, scale, offset, canvasPx, Modifier.size(side))
                if (state.miniPreview) {
                    SkinModelView(
                        state = state,
                        rx = 0.15f,
                        ry = -0.5f,
                        zoom = 1f,
                        showOverlay = true,
                        background = PREVIEW_BACKGROUNDS[state.previewBg.coerceIn(0, PREVIEW_BACKGROUNDS.lastIndex)].second,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(88.dp)
                            .insetFrame()
                            .clipToBounds()
                            .clickable { state.screen = Screen.PREVIEW }
                    )
                }
            }

            // Tools: all eight always visible, sized to the screen width
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 4.dp
                val slot = ((maxWidth - gap * 7) / 8).coerceIn(36.dp, 56.dp)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
                ) {
                    ToolSlot(state, Tool.PENCIL, PixelIcons.Pencil, slot)
                    ToolSlot(state, Tool.ERASER, PixelIcons.Eraser, slot)
                    ToolSlot(state, Tool.LINE, PixelIcons.Line, slot)
                    ToolSlot(state, Tool.FILL, PixelIcons.Bucket, slot)
                    ToolSlot(state, Tool.EYEDROPPER, PixelIcons.Dropper, slot)
                    ToolSlot(state, Tool.SHADE, PixelIcons.Shade, slot)
                    ToolSlot(state, Tool.SELECT, PixelIcons.Select, slot)
                    ToolSlot(state, Tool.MOVE, PixelIcons.Move, slot)
                }
            }

            // Current color, palette strip, full palette
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slot(size = 48.dp, onClick = { showColor = true }) {
                    Box(Modifier.size(30.dp).background(Color(state.color)))
                }
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.palette.forEach { c ->
                        key(c) {
                            Slot(size = 44.dp, selected = c == state.color, onClick = { state.color = c }) {
                                Box(Modifier.size(28.dp).background(Color(c)))
                            }
                        }
                    }
                }
                BlockButton(onClick = { sheet = EditorSheet.PALETTE }, label = "Palette")
            }

            // Mirror, layers, and options for the current tool
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BlockButton(
                    onClick = { state.mirror = !state.mirror },
                    icon = PixelIcons.Mirror,
                    label = "Mirror",
                    selected = state.mirror
                )
                val layer = state.layers.getOrNull(state.activeLayer)
                val layerName = layer?.let { if (it.visible) it.name else it.name + " (hidden)" } ?: ""
                BlockButton(
                    onClick = { showLayers = true },
                    icon = PixelIcons.Layers,
                    label = layerName,
                    modifier = Modifier.weight(1f)
                )
                when (state.tool) {
                    Tool.SHADE -> BlockButton(onClick = { sheet = EditorSheet.TOOL }, label = "Shade")
                    Tool.SELECT -> BlockButton(
                        onClick = { sheet = EditorSheet.TOOL },
                        label = state.selection?.let { "${it.w}\u00d7${it.h}" } ?: "Select"
                    )
                    else -> {}
                }
            }
        }

        when (sheet) {
            EditorSheet.VIEW -> PullUpPanel("View", onDismiss = { sheet = null }) {
                PixelText("Model", 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlockButton(
                        onClick = { requestModel(state, false) { pendingSlim = it } },
                        label = "Steve",
                        selected = !state.slim,
                        modifier = Modifier.weight(1f)
                    )
                    BlockButton(
                        onClick = { requestModel(state, true) { pendingSlim = it } },
                        label = "Alex",
                        selected = state.slim,
                        modifier = Modifier.weight(1f)
                    )
                }
                PixelText("Canvas", 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlockButton(
                        onClick = { state.overlayVisible = !state.overlayVisible },
                        label = "Overlay",
                        selected = state.overlayVisible,
                        modifier = Modifier.weight(1f)
                    )
                    BlockButton(
                        onClick = { state.toggleLabels() },
                        label = "Labels",
                        selected = state.showLabels,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlockButton(
                        onClick = { state.toggleMiniPreview() },
                        icon = PixelIcons.Cube,
                        label = "3D corner",
                        selected = state.miniPreview,
                        modifier = Modifier.weight(1f)
                    )
                    BlockButton(
                        onClick = { state.toggleStylusOnly() },
                        icon = PixelIcons.Stylus,
                        label = "Stylus only",
                        selected = state.stylusOnly,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            EditorSheet.PALETTE -> PullUpPanel("Colors", onDismiss = { sheet = null }) {
                ColorPanel(state, onEditColor = { showColor = true })
                PixelText("Long-press a palette color to remove it.", 11.sp)
            }
            EditorSheet.TOOL -> PullUpPanel(
                if (state.tool == Tool.SELECT) "Selection" else "Shade",
                onDismiss = { sheet = null }
            ) {
                if (state.tool == Tool.SELECT) {
                    SelectControls(state, onMoveArmed = { sheet = null })
                } else {
                    ShadeControls(state)
                }
            }
            null -> {}
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

    if (showLayers) LayersDialog(state, onDismiss = { showLayers = false })

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
private fun ToolSlot(state: AppState, tool: Tool, icon: PixelIcon, size: Dp) {
    Slot(size = size, selected = state.tool == tool, onClick = { state.tool = tool }) {
        PixelIconView(icon, Blocky.IconDark, Modifier.size(size * 0.55f))
    }
}

private enum class EditorSheet { VIEW, PALETTE, TOOL }

/** Panel that slides over the bottom of the editor. Tap outside, press Done, or press back to close. */
@Composable
private fun PullUpPanel(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
    ) {
        Panel(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .wrapContentHeight(Alignment.Bottom)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixelText(title, 16.sp, modifier = Modifier.weight(1f))
                BlockButton(onClick = onDismiss, label = "Done")
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

/** Copy, paste, delete, flip and move for the rectangular selection. */
@Composable
private fun SelectControls(state: AppState, onMoveArmed: () -> Unit) {
    val sel = state.selection
    PixelText(
        if (sel != null) "Selection: ${sel.w}\u00d7${sel.h}" else "Close this panel and drag on the canvas to select a rectangle",
        13.sp
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BlockButton(onClick = { state.copySelection() }, label = "Copy", enabled = sel != null, modifier = Modifier.weight(1f))
        BlockButton(onClick = { state.pasteSelection() }, label = "Paste", enabled = state.hasClipboard, modifier = Modifier.weight(1f))
        BlockButton(onClick = { state.deleteSelection() }, label = "Delete", enabled = sel != null, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BlockButton(onClick = { state.flipSelection(true) }, label = "Flip H", enabled = sel != null, modifier = Modifier.weight(1f))
        BlockButton(onClick = { state.flipSelection(false) }, label = "Flip V", enabled = sel != null, modifier = Modifier.weight(1f))
        BlockButton(
            onClick = {
                state.armMove()
                onMoveArmed()
            },
            label = "Move",
            selected = state.selectMoveArmed,
            enabled = sel != null,
            modifier = Modifier.weight(1f)
        )
    }
    if (sel != null) {
        BlockButton(onClick = { state.clearSelection() }, label = "Deselect", modifier = Modifier.fillMaxWidth())
    }
    if (state.selectMoveArmed) {
        PixelText("Drag anywhere on the canvas to move it.", 11.sp)
    }
}

private val TOOL_NAMES = mapOf(
    Tool.PENCIL to "Pencil", Tool.ERASER to "Eraser", Tool.LINE to "Line",
    Tool.FILL to "Fill", Tool.EYEDROPPER to "Eyedropper", Tool.SHADE to "Shade",
    Tool.SELECT to "Select", Tool.MOVE to "Move canvas"
)

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
    val marquee = remember { mutableStateOf<SelRect?>(null) }
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
            .pointerInput(Unit) { editorGestures(state, scale, offset, marquee) }
    ) {
        @Suppress("UNUSED_VARIABLE")
        val v = state.version
        val showOverlay = state.overlayVisible
        val map = state.regionMap
        val boxes = state.boxes
        val px = state.composite()
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
        val shownSel = marquee.value ?: state.selection
        if (shownSel != null && state.tool == Tool.SELECT) {
            val rx0 = o.x + shownSel.x0 * cell
            val ry0 = o.y + shownSel.y0 * cell
            val rw = shownSel.w * cell
            val rh = shownSel.h * cell
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            drawRect(Color.White, Offset(rx0, ry0), Size(rw, rh), style = stroke)
            drawRect(
                Color.Black,
                Offset(rx0 + 2f, ry0 + 2f),
                Size((rw - 4f).coerceAtLeast(0f), (rh - 4f).coerceAtLeast(0f)),
                style = stroke
            )
        }
        if (state.showLabels) {
            val borderColor = Color.Black.copy(alpha = 0.55f)
            for (gy in 0..64) {
                for (gx in 0 until 64) {
                    val above = if (gy > 0) map[(gy - 1) * 64 + gx] else -1
                    val below = if (gy < 64) map[gy * 64 + gx] else -1
                    if (above != below) {
                        val lx0 = o.x + gx * cell
                        val ly = o.y + gy * cell
                        drawLine(borderColor, Offset(lx0, ly), Offset(lx0 + cell, ly), 1.4f)
                    }
                }
            }
            for (gx in 0..64) {
                for (gy in 0 until 64) {
                    val left = if (gx > 0) map[gy * 64 + (gx - 1)] else -1
                    val right = if (gx < 64) map[gy * 64 + gx] else -1
                    if (left != right) {
                        val lx = o.x + gx * cell
                        val ly0 = o.y + gy * cell
                        drawLine(borderColor, Offset(lx, ly0), Offset(lx, ly0 + cell), 1.4f)
                    }
                }
            }
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
suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitRelevantDown(stylusOnly: Boolean): androidx.compose.ui.input.pointer.PointerInputChange {
    if (!stylusOnly) return awaitFirstDown(requireUnconsumed = false)
    while (true) {
        val event = awaitPointerEvent()
        val d = event.changes.firstOrNull { it.type == PointerType.Stylus && it.pressed }
        if (d != null) return d
    }
}

private suspend fun PointerInputScope.editorGestures(
    state: AppState,
    scale: MutableFloatState,
    offset: MutableState<Offset>,
    marquee: MutableState<SelRect?>
) {
    awaitEachGesture {
        val down = awaitRelevantDown(state.stylusOnly)
        val before = state.copyPixels()
        var multi = false
        var changed = false
        var last: IntOffset? = null
        val shading = state.tool == Tool.SHADE
        val visited = HashSet<Int>()
        // For two-finger undo / three-finger redo taps
        val scale0 = scale.floatValue
        val offset0 = offset.value
        var maxPressed = 1
        var multiTravel = 0f
        var multiZoom = 1f
        var lastTime = down.uptimeMillis

        fun toSkin(p: Offset): IntOffset? {
            val cell = size.width * scale.floatValue / 64f
            val x = floor((p.x - offset.value.x) / cell).toInt()
            val y = floor((p.y - offset.value.y) / cell).toInt()
            return if (x in 0..63 && y in 0..63) IntOffset(x, y) else null
        }

        fun clampSkin(p: Offset): IntOffset {
            val cell = size.width * scale.floatValue / 64f
            val x = floor((p.x - offset.value.x) / cell).toInt().coerceIn(0, 63)
            val y = floor((p.y - offset.value.y) / cell).toInt().coerceIn(0, 63)
            return IntOffset(x, y)
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

        val selecting = state.tool == Tool.SELECT
        val moveArmed = selecting && state.selectMoveArmed
        val moveStart = state.selection
        val moveOriginal = if (moveArmed && moveStart != null) state.copyPixels() else null
        val marqueeOrigin = if (selecting && !moveArmed) clampSkin(down.position) else null
        var moveDelta = IntOffset.Zero

        fun screenDelta(p: Offset): IntOffset {
            val cell = size.width * scale.floatValue / 64f
            val start = down.position
            return IntOffset(((p.x - start.x) / cell).roundToInt(), ((p.y - start.y) / cell).roundToInt())
        }

        if (marqueeOrigin != null) {
            marquee.value = SelRect(marqueeOrigin.x, marqueeOrigin.y, marqueeOrigin.x, marqueeOrigin.y)
        }

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

        // Long-press with a painting tool picks the color under the finger instead.
        // Finger mode only. Ends early on movement past touch slop, a second finger, or lift.
        var pending: PointerEvent? = null
        val holdEligible = !state.stylusOnly &&
            (pen || lineStart != null || state.tool == Tool.FILL)
        if (holdEligible) {
            pending = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var ev: PointerEvent
                do {
                    ev = awaitPointerEvent()
                    val one = ev.changes.count { it.pressed } == 1
                    val moved = ev.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }
                } while (one && !moved)
                ev
            }
            if (pending == null) {
                if (changed || lineStart != null) state.restore(before)
                toSkin(down.position)?.let { p ->
                    val keep = state.tool
                    state.pick(p.y * 64 + p.x)
                    if (keep != Tool.ERASER) state.tool = keep
                }
                do {
                    val ev = awaitPointerEvent()
                    ev.changes.forEach { it.consume() }
                } while (ev.changes.any { it.pressed })
                return@awaitEachGesture
            }
        }

        do {
            val event = pending ?: awaitPointerEvent()
            pending = null
            lastTime = event.changes.maxOf { it.uptimeMillis }
            // Two clearly separate paths: when Stylus mode is off, this is byte-for-byte
            // the original finger/stylus-agnostic logic, touched by nothing added below.
            if (!state.stylusOnly) {
                val pressed = event.changes.count { it.pressed }
                if (pressed > maxPressed) maxPressed = pressed
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
                    multiTravel += pan.getDistance()
                    multiZoom *= zoom
                    val centroid = event.calculateCentroid(useCurrent = true)
                    val source = centroid - pan
                    val old = scale.floatValue
                    val ns = (old * zoom).coerceIn(1f, 12f)
                    var no = (offset.value - source) * (ns / old) + centroid
                    val full = size.width * ns
                    no = Offset(
                        no.x.coerceIn(size.width - full, 0f),
                        no.y.coerceIn(size.height - full, 0f)
                    )
                    scale.floatValue = ns
                    offset.value = no
                } else if (!multi && pressed == 1) {
                    if (moveArmed && moveStart != null && moveOriginal != null) {
                        moveDelta = screenDelta(event.changes.first { it.pressed }.position)
                        state.previewMove(moveOriginal, moveStart, moveDelta.x, moveDelta.y)
                    } else if (marqueeOrigin != null) {
                        val cur = clampSkin(event.changes.first { it.pressed }.position)
                        marquee.value = SelRect(
                            minOf(marqueeOrigin.x, cur.x), minOf(marqueeOrigin.y, cur.y),
                            maxOf(marqueeOrigin.x, cur.x), maxOf(marqueeOrigin.y, cur.y)
                        )
                    } else if (moving) {
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
            } else {
                val fingerCount = event.changes.count { it.pressed && it.type == PointerType.Touch }
                val relevant = event.changes.filter { it.type == PointerType.Stylus }
                val pressed = relevant.count { it.pressed }
                if (fingerCount >= 2) {
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
                    val source = centroid - pan
                    val old = scale.floatValue
                    val ns = (old * zoom).coerceIn(1f, 12f)
                    var no = (offset.value - source) * (ns / old) + centroid
                    val full = size.width * ns
                    no = Offset(
                        no.x.coerceIn(size.width - full, 0f),
                        no.y.coerceIn(size.height - full, 0f)
                    )
                    scale.floatValue = ns
                    offset.value = no
                } else if (!multi && pressed == 1) {
                    if (moveArmed && moveStart != null && moveOriginal != null) {
                        moveDelta = screenDelta(relevant.first { it.pressed }.position)
                        state.previewMove(moveOriginal, moveStart, moveDelta.x, moveDelta.y)
                    } else if (marqueeOrigin != null) {
                        val cur = clampSkin(relevant.first { it.pressed }.position)
                        marquee.value = SelRect(
                            minOf(marqueeOrigin.x, cur.x), minOf(marqueeOrigin.y, cur.y),
                            maxOf(marqueeOrigin.x, cur.x), maxOf(marqueeOrigin.y, cur.y)
                        )
                    } else if (moving) {
                        val pan = event.calculatePan()
                        val full = size.width * scale.floatValue
                        val no = offset.value + pan
                        offset.value = Offset(
                            no.x.coerceIn(size.width - full, 0f),
                            no.y.coerceIn(size.height - full, 0f)
                        )
                    } else {
                        val pos = relevant.first { it.pressed }.position
                        if (pen) stroke(pos) else if (lineStart != null) previewLine(pos)
                    }
                }
            }
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        } while (event.changes.any { it.pressed })

        if (!multi) {
            when {
                moveArmed && moveStart != null && moveOriginal != null -> {
                    state.disarmMove()
                    if (moveDelta != IntOffset.Zero) state.commit(moveOriginal) else state.restore(moveOriginal)
                }
                marqueeOrigin != null -> {
                    state.updateSelection(marquee.value)
                    marquee.value = null
                }
                state.tool == Tool.FILL -> toSkin(down.position)?.let {
                    if (state.fill(it.y * 64 + it.x, state.color)) changed = true
                }
                state.tool == Tool.EYEDROPPER -> toSkin(down.position)?.let { state.pick(it.y * 64 + it.x) }
                else -> {}
            }
        } else {
            marquee.value = null
            // A quick, nearly motionless multi-finger tap: 2 fingers undo, 3 redo.
            val quick = lastTime - down.uptimeMillis <= MULTI_TAP_MS
            val still = multiTravel < viewConfiguration.touchSlop * 1.5f && abs(multiZoom - 1f) < 0.08f
            if (!state.stylusOnly && quick && still) {
                scale.floatValue = scale0
                offset.value = offset0
                if (maxPressed >= 3) state.redo() else if (maxPressed == 2) state.undo()
            }
        }
        if (changed) {
            state.touched()
            state.commit(before)
        }
    }
}

private const val MULTI_TAP_MS = 300L

fun line(a: IntOffset, b: IntOffset, plot: (Int, Int) -> Unit) {
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
