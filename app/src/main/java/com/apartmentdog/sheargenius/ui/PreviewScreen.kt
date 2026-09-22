package com.apartmentdog.sheargenius.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apartmentdog.sheargenius.AppState

@Composable
fun PreviewScreen(state: AppState) {
    LaunchedEffect(state.spin) {
        if (state.spin) {
            var last = 0L
            while (true) {
                withFrameNanos { t ->
                    if (last != 0L) state.previewRy += (t - last) / 1_000_000_000f * 0.9f
                    last = t
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            SkinModelView(
                state = state,
                rx = state.previewRx,
                ry = state.previewRy,
                zoom = state.previewZoom,
                showOverlay = state.previewOverlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
                    .insetFrame()
                    .clipToBounds()
                    .pointerInput(Unit) { previewGestures(state) }
            )
            Spacer(Modifier.height(6.dp))
            PixelText("Drag to rotate. Pinch to zoom.", 12.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlockButton(
                    onClick = { state.previewOverlay = !state.previewOverlay },
                    label = "Overlay",
                    selected = state.previewOverlay
                )
                BlockButton(onClick = { state.spin = !state.spin }, label = "Spin", selected = state.spin)
                Spacer(Modifier.weight(1f))
                BlockButton(onClick = { state.resetPreview() }, label = "Reset view")
            }
        }
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
