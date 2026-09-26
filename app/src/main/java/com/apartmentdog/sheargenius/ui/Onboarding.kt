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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.apartmentdog.sheargenius.AppState

private data class Step(val icon: PixelIcon, val title: String, val body: String)

private val STEPS = listOf(
    Step(
        PixelIcons.Pencil, "Editor",
        "Paint your skin here on a 64\u00d764 canvas. Switch Steve and Alex templates, toggle the overlay layer, and use Pencil, Eraser, Line, Fill, Eyedropper, Shade and Select. Pinch or use the + / \u2212 buttons to zoom."
    ),
    Step(
        PixelIcons.Layers, "Layers",
        "The Layer button opens the layer list. Add, duplicate, reorder, merge or hide layers, so hair, base skin and accessories can stay separate while you work."
    ),
    Step(
        PixelIcons.Photo, "Reference",
        "Import reference images and tap them to sample a color, at 1px, 3\u00d73 or 5\u00d75 averaging. Extract a palette from an image in one tap, or add colors to your project palette by hand."
    ),
    Step(
        PixelIcons.Cube, "Preview",
        "See the skin as a rotating 3D model. Toggle body parts and the background, try Spin or Walk, and turn on Paint on model to paint straight onto the 3D view. Save a view or a four-angle turnaround to send a client."
    ),
    Step(
        PixelIcons.Disk, "Files",
        "Every skin is its own project with its own palette and references. Create, rename or delete projects, import or export PNGs, or download a Java Edition player's current skin by username."
    )
)

@Composable
fun OnboardingDialog(state: AppState) {
    var step by remember { mutableIntStateOf(0) }
    val last = step == STEPS.lastIndex
    val s = STEPS[step]

    Dialog(onDismissRequest = { state.dismissOnboarding() }) {
        Panel(Modifier.width(320.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PixelText("What's where", 16.sp)
                BlockButton(onClick = { state.dismissOnboarding() }, label = "Skip")
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slot(size = 48.dp) {
                    PixelIconView(s.icon, Blocky.IconDark, Modifier.size(26.dp))
                }
                Spacer(Modifier.width(10.dp))
                PixelText(s.title, 17.sp)
            }
            Spacer(Modifier.height(10.dp))
            PixelText(s.body, 13.sp)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                STEPS.indices.forEach { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (i == step) 9.dp else 7.dp)
                            .background(if (i == step) Blocky.Green else Blocky.ButtonDark, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BlockButton(onClick = { if (step > 0) step-- }, label = "Back", enabled = step > 0)
                BlockButton(
                    onClick = { if (last) state.dismissOnboarding() else step++ },
                    label = if (last) "Done" else "Next",
                    selected = true
                )
            }
        }
    }
}
