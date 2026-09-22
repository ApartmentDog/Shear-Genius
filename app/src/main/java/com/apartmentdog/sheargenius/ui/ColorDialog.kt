package com.apartmentdog.sheargenius.ui

import android.graphics.Color as AColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

@Composable
fun ColorDialog(initial: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit, onSave: (Int) -> Unit) {
    val start = remember { FloatArray(3).also { AColor.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var bright by remember { mutableFloatStateOf(start[2]) }
    var hexText by remember { mutableStateOf(hexOf(initial).drop(1)) }
    val current = AColor.HSVToColor(floatArrayOf(hue, sat, bright))

    fun refreshHex(h: Float, s: Float, v: Float) {
        hexText = hexOf(AColor.HSVToColor(floatArrayOf(h, s, v))).drop(1)
    }

    Dialog(onDismissRequest = onDismiss) {
        Panel(Modifier.width(320.dp)) {
            PixelText("Color", 18.sp)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(48.dp).insetFrame().background(Color(current)))
            Spacer(Modifier.height(10.dp))
            ColorSlider("Hue ${hue.roundToInt()}", hue, 0f..360f) {
                hue = it
                refreshHex(it, sat, bright)
            }
            ColorSlider("Saturation ${(sat * 100).roundToInt()}", sat, 0f..1f) {
                sat = it
                refreshHex(hue, it, bright)
            }
            ColorSlider("Value ${(bright * 100).roundToInt()}", bright, 0f..1f) {
                bright = it
                refreshHex(hue, sat, it)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelText("Hex #", 14.sp)
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = hexText,
                    onValueChange = { t ->
                        val clean = t.filter { it.isLetterOrDigit() }.take(6).uppercase()
                        hexText = clean
                        if (clean.length == 6) {
                            val parsed = runCatching { AColor.parseColor("#$clean") }.getOrNull()
                            if (parsed != null) {
                                val a = FloatArray(3)
                                AColor.colorToHSV(parsed, a)
                                hue = a[0]
                                sat = a[1]
                                bright = a[2]
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = LocalPixelFont.current, fontSize = 16.sp, color = Blocky.Text),
                    modifier = Modifier
                        .width(120.dp)
                        .bevel(Color.White, Blocky.ButtonDark, Color.White, 2.dp, null)
                        .padding(8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                BlockButton(onClick = onDismiss, label = "Cancel")
                BlockButton(onClick = { onSave(current) }, label = "Save")
                BlockButton(onClick = {
                    onApply(current)
                    onDismiss()
                }, label = "Use", selected = true)
            }
        }
    }
}

@Composable
private fun ColorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    PixelText(label, 13.sp)
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = Blocky.Green,
            activeTrackColor = Blocky.Green,
            inactiveTrackColor = Blocky.ButtonDark
        )
    )
}
