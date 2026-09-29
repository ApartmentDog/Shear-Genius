package com.apartmentdog.sheargenius.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apartmentdog.sheargenius.AppState
import com.apartmentdog.sheargenius.ShadeMode
import kotlin.math.roundToInt

/** Shade-tool mode picker and amount slider. Shared by the Editor and the 3D Preview's paint mode. */
@Composable
fun ShadeControls(state: AppState) {
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

/** Current color swatch, save-to-palette, dye colors, and the palette grid. Shared by the Editor and the 3D Preview's paint mode. */
@Composable
fun ColorPanel(state: AppState, onEditColor: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Slot(size = 48.dp, onClick = onEditColor) {
                Box(Modifier.size(28.dp).background(Color(state.color)))
            }
            PixelText(hexOf(state.color), 16.sp, modifier = Modifier.weight(1f))
            BlockButton(onClick = { state.addToPalette(state.color) }, icon = PixelIcons.Plus, label = "Save")
        }
        Spacer(Modifier.height(8.dp))
        BlockButton(onClick = { state.addDyeColors() }, label = "Add dye colors", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        PaletteGrid(
            state.palette,
            state.color,
            onPick = { state.color = it },
            onRemove = { state.removeFromPalette(it) }
        )
    }
}
