package com.apartmentdog.sheargenius.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apartmentdog.sheargenius.AppState
import com.apartmentdog.sheargenius.Screen

@Composable
fun ShearGeniusApp(state: AppState) {
    val font = rememberPixelFont()
    MaterialTheme(colorScheme = lightColorScheme(primary = Blocky.Green)) {
        CompositionLocalProvider(LocalPixelFont provides font) {
            Column(Modifier.fillMaxSize().tiled(Textures.stone, 48.dp)) {
                Header(state)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (state.screen) {
                        Screen.EDITOR -> EditorScreen(state)
                        Screen.REFERENCE -> ReferenceScreen(state)
                        Screen.PREVIEW -> PreviewScreen(state)
                        Screen.FILES -> FilesScreen(state)
                    }
                }
                Hotbar(state)
            }
        }
    }
}

@Composable
private fun Header(state: AppState) {
    Row(
        Modifier
            .fillMaxWidth()
            .tiled(Textures.wool, 48.dp)
            .statusBarsPadding()
            .height(60.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            PixelText("Shear Genius", 18.sp, Color.White, maxLines = 1, lineHeight = 21.sp, shadow = true)
            PixelText(state.projectName, 12.sp, Color(0xFFFAECE7), maxLines = 1, lineHeight = 15.sp, shadow = true)
        }
        Spacer(Modifier.width(8.dp))
        if (state.screen == Screen.EDITOR) {
            BlockButton(onClick = { state.undo() }, icon = PixelIcons.Undo, enabled = state.canUndo)
            Spacer(Modifier.width(8.dp))
            BlockButton(onClick = { state.redo() }, icon = PixelIcons.Redo, enabled = state.canRedo)
        }
    }
}

@Composable
private fun Hotbar(state: AppState) {
    val items = listOf(
        Triple(Screen.EDITOR, "Editor", PixelIcons.Pencil),
        Triple(Screen.REFERENCE, "Reference", PixelIcons.Photo),
        Triple(Screen.PREVIEW, "Preview", PixelIcons.Cube),
        Triple(Screen.FILES, "Files", PixelIcons.Disk)
    )
    Row(
        Modifier.navigationBarsPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { (screen, label, icon) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Slot(size = 48.dp, selected = state.screen == screen, onClick = { state.screen = screen }) {
                    PixelIconView(icon, Blocky.IconDark, Modifier.size(24.dp))
                }
                Spacer(Modifier.height(4.dp))
                PixelText(label, 12.sp, Color.White)
            }
        }
    }
}
