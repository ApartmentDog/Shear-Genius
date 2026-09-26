package com.apartmentdog.sheargenius.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.apartmentdog.sheargenius.AppState
import com.apartmentdog.sheargenius.Layer
import com.apartmentdog.sheargenius.MAX_LAYERS

@Composable
fun LayersDialog(state: AppState, onDismiss: () -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val active = state.activeLayer

    Dialog(onDismissRequest = onDismiss) {
        Panel(Modifier.width(320.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PixelText("Layers", 16.sp)
                PixelText("${state.layers.size}/$MAX_LAYERS", 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            PixelText("Top of the list is the top of the stack.", 11.sp)
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in state.layers.indices.reversed()) {
                    val layer = state.layers[i]
                    key(layer.id) {
                        LayerRow(
                            layer = layer,
                            active = i == active,
                            onSelect = { state.selectLayer(i) },
                            onToggle = { state.toggleLayerVisible(i) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (renaming) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(24) },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = LocalPixelFont.current, fontSize = 16.sp, color = Blocky.Text),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bevel(Color.White, Blocky.ButtonDark, Color.White, 2.dp, null)
                        .padding(10.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    BlockButton(onClick = { renaming = false }, label = "Cancel")
                    BlockButton(onClick = {
                        state.renameLayer(active, text)
                        renaming = false
                    }, label = "Save", selected = true)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BlockButton(onClick = { state.addLayer() }, icon = PixelIcons.Plus, label = "New", enabled = state.layers.size < MAX_LAYERS)
                    BlockButton(onClick = { state.duplicateLayer() }, label = "Copy", enabled = state.layers.size < MAX_LAYERS)
                    BlockButton(onClick = {
                        text = state.layers.getOrNull(active)?.name ?: ""
                        renaming = true
                    }, label = "Rename")
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BlockButton(onClick = { state.moveLayer(true) }, icon = PixelIcons.Up, enabled = active < state.layers.lastIndex)
                    BlockButton(onClick = { state.moveLayer(false) }, icon = PixelIcons.Down, enabled = active > 0)
                    BlockButton(onClick = { state.mergeDown() }, label = "Merge down", enabled = active > 0)
                    BlockButton(onClick = { state.deleteLayer() }, label = "Delete", enabled = state.layers.size > 1)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    BlockButton(onClick = onDismiss, label = "Done", selected = true)
                }
            }
        }
    }
}

@Composable
private fun LayerRow(layer: Layer, active: Boolean, onSelect: () -> Unit, onToggle: () -> Unit) {
    val select by rememberUpdatedState(onSelect)
    val fill = if (active) Blocky.Green else Blocky.Button
    val light = if (active) Blocky.GreenLight else Blocky.ButtonLight
    val dark = if (active) Blocky.GreenDark else Blocky.ButtonDark
    Row(
        Modifier
            .fillMaxWidth()
            .bevel(fill, light, dark, 2.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { select() }) }
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Slot(size = 34.dp, onClick = onToggle) {
            PixelIconView(if (layer.visible) PixelIcons.Eye else PixelIcons.EyeOff, Blocky.IconDark, Modifier.size(18.dp))
        }
        PixelText(layer.name, 14.sp, if (layer.visible) Color.White else Blocky.Disabled, Modifier.weight(1f), maxLines = 1)
    }
}
