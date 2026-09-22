package com.apartmentdog.sheargenius.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .bevel(Blocky.Panel, Blocky.PanelLight, Blocky.PanelDark, 3.dp)
            .padding(12.dp),
        content = content
    )
}

@Composable
fun BlockButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: PixelIcon? = null,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    val fill = if (selected) Blocky.Green else Blocky.Button
    val light = if (selected) Blocky.GreenLight else Blocky.ButtonLight
    val dark = if (selected) Blocky.GreenDark else Blocky.ButtonDark
    val fg = if (enabled) Color.White else Blocky.Disabled
    Row(
        modifier = modifier
            .heightIn(min = 38.dp)
            .widthIn(min = 38.dp)
            .bevel(fill, light, dark, 2.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        if (icon != null) PixelIconView(icon, fg, Modifier.size(16.dp))
        if (label != null) PixelText(label, 14.sp, fg)
    }
}

@Composable
fun Slot(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    var m = modifier
        .size(size)
        .bevel(
            if (selected) Blocky.SlotSelected else Blocky.Button,
            Blocky.ButtonDark,
            Color.White,
            2.dp,
            if (selected) Color.White else Blocky.Outline
        )
    if (onClick != null || onLongClick != null) {
        m = m.pointerInput(Unit) {
            detectTapGestures(
                onTap = { click?.invoke() },
                onLongPress = { longClick?.invoke() }
            )
        }
    }
    Box(m, contentAlignment = Alignment.Center, content = content)
}

@Composable
fun PaletteGrid(colors: List<Int>, selected: Int?, onPick: (Int) -> Unit, onRemove: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        colors.chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { c ->
                    key(c) {
                        Slot(
                            size = 36.dp,
                            selected = c == selected,
                            onClick = { onPick(c) },
                            onLongClick = { onRemove(c) }
                        ) {
                            Box(Modifier.size(22.dp).background(Color(c)))
                        }
                    }
                }
            }
        }
    }
}

fun hexOf(c: Int): String = String.format("#%06X", c and 0xFFFFFF)

fun hsvText(c: Int): String {
    val a = FloatArray(3)
    android.graphics.Color.colorToHSV(c, a)
    return "H ${Math.round(a[0])}  S ${Math.round(a[1] * 100)}  V ${Math.round(a[2] * 100)}"
}
