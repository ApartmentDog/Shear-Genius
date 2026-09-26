package com.apartmentdog.sheargenius.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apartmentdog.sheargenius.AppState

/** Name to ARGB. Index is what gets saved in prefs. */
val PREVIEW_BACKGROUNDS = listOf(
    "Sky" to 0xFF9DB9D8.toInt(),
    "Stone" to 0xFFB4B2A9.toInt(),
    "Grass" to 0xFF8FB86A.toInt(),
    "Dark" to 0xFF2C2C2A.toInt(),
    "Clear" to 0x00000000
)

private val PARTS = listOf("Head", "Body", "R arm", "L arm", "R leg", "L leg")

@Composable
fun PreviewScreen(state: AppState) {
    LaunchedEffect(state.spin, state.walk) {
        if (state.spin || state.walk) {
            var last = 0L
            while (true) {
                withFrameNanos { t ->
                    if (last != 0L) {
                        val dt = (t - last) / 1_000_000_000f
                        if (state.spin) state.previewRy += dt * 0.9f
                        if (state.walk) state.walkPhase += dt * 5.5f
                    }
                    last = t
                }
            }
        }
    }
    val bgIndex = state.previewBg.coerceIn(0, PREVIEW_BACKGROUNDS.lastIndex)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Panel(Modifier.fillMaxWidth()) {
            SkinModelView(
                state = state,
                rx = state.previewRx,
                ry = state.previewRy,
                zoom = state.previewZoom,
                showOverlay = state.previewOverlay,
                hidden = state.previewHidden,
                background = PREVIEW_BACKGROUNDS[bgIndex].second,
                swing = state.previewSwing,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .insetFrame()
                    .clipToBounds()
                    .pointerInput(Unit) { previewGestures(state) }
            )
            Spacer(Modifier.height(6.dp))
            PixelText("Drag to rotate. Pinch to zoom.", 12.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BlockButton(
                    onClick = { state.previewOverlay = !state.previewOverlay },
                    label = "Overlay",
                    selected = state.previewOverlay
                )
                BlockButton(onClick = { state.spin = !state.spin }, label = "Spin", selected = state.spin)
                BlockButton(onClick = {
                    state.walk = !state.walk
                    if (!state.walk) state.walkPhase = 0f
                }, label = "Walk", selected = state.walk)
                Spacer(Modifier.weight(1f))
                BlockButton(onClick = { state.resetPreview() }, label = "Reset")
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            PixelText("Body parts", 14.sp)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PARTS.withIndex().chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (i, name) ->
                            BlockButton(
                                onClick = { state.togglePart(i) },
                                label = name,
                                selected = i !in state.previewHidden,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            PixelText("R and L are the character's right and left.", 11.sp)
        }

        Panel(Modifier.fillMaxWidth()) {
            PixelText("Background", 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PREVIEW_BACKGROUNDS.forEachIndexed { i, (_, argb) ->
                    Slot(size = 40.dp, selected = i == bgIndex, onClick = { state.changePreviewBg(i) }) {
                        Box(Modifier.size(26.dp).background(Color(argb)))
                    }
                }
                Spacer(Modifier.size(4.dp))
                PixelText(PREVIEW_BACKGROUNDS[bgIndex].first, 14.sp)
            }
        }

        RenderPanel(state, PREVIEW_BACKGROUNDS[bgIndex].second)
    }
}

@Composable
private fun RenderPanel(state: AppState, background: Int) {
    val context = LocalContext.current
    var shareAfter by remember { mutableStateOf(false) }
    fun deliver(bitmap: android.graphics.Bitmap, suffix: String) {
        val uri = RenderExport.save(context, bitmap, "${state.projectName} $suffix")
        if (uri == null) {
            Toast.makeText(context, "Couldn't save the image", Toast.LENGTH_SHORT).show()
        } else if (shareAfter) {
            RenderExport.share(context, uri)
        } else {
            Toast.makeText(context, "Saved to Pictures/Shear Genius", Toast.LENGTH_SHORT).show()
        }
    }
    val bg = if ((background ushr 24) == 0) null else background
    Panel(Modifier.fillMaxWidth()) {
        PixelText("Render", 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BlockButton(onClick = { deliver(RenderExport.view(state, bg), "view") }, label = "Save view")
            BlockButton(onClick = { deliver(RenderExport.turnaround(state, bg), "turnaround") }, label = "Save turnaround")
        }
        Spacer(Modifier.height(8.dp))
        BlockButton(onClick = { shareAfter = !shareAfter }, label = "Share after saving", selected = shareAfter)
        Spacer(Modifier.height(6.dp))
        PixelText("Uses the current angle, zoom, parts and background. Pick Clear for a transparent PNG.", 11.sp)
    }
}

private suspend fun PointerInputScope.previewGestures(state: AppState) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) {
                state.previewZoom = (state.previewZoom * event.calculateZoom()).coerceIn(0.5f, 3f)
            } else if (pressed == 1) {
                val pan = event.calculatePan()
                state.previewRy += pan.x * 0.012f
                state.previewRx = (state.previewRx + pan.y * 0.012f).coerceIn(-1.4f, 1.4f)
            }
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
