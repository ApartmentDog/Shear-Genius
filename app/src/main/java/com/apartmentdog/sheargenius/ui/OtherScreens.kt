package com.apartmentdog.sheargenius.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apartmentdog.sheargenius.AppState

@Composable
fun PreviewScreen() {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            PixelText("3D preview", 16.sp)
            Spacer(Modifier.height(6.dp))
            PixelText("Coming in phase 2: a model you can rotate, with the overlay layer raised off the body.", 13.sp)
        }
    }
}

@Composable
fun ExportScreen(state: AppState) {
    val context = LocalContext.current
    var confirmNew by remember { mutableStateOf(false) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) {
            val ok = state.exportSkin(uri)
            Toast.makeText(context, if (ok) "Skin exported" else "Couldn't export to that location", Toast.LENGTH_SHORT).show()
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val err = state.importSkin(uri)
            Toast.makeText(context, err ?: "Skin imported", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Panel(Modifier.fillMaxWidth()) {
            PixelText("Skin file", 16.sp)
            Spacer(Modifier.height(10.dp))
            BlockButton(onClick = { saver.launch("skin.png") }, modifier = Modifier.fillMaxWidth(), icon = PixelIcons.Disk, label = "Export PNG")
            Spacer(Modifier.height(12.dp))
            BlockButton(onClick = { opener.launch(arrayOf("image/png")) }, modifier = Modifier.fillMaxWidth(), icon = PixelIcons.Photo, label = "Import PNG")
            Spacer(Modifier.height(12.dp))
            BlockButton(onClick = { confirmNew = true }, modifier = Modifier.fillMaxWidth(), icon = PixelIcons.Plus, label = "New skin")
            Spacer(Modifier.height(10.dp))
            PixelText("Exports are 64×64 PNGs ready to upload to Minecraft: Java Edition. Your work saves automatically as you paint.", 12.sp)
        }
    }

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text("Start a new skin?") },
            text = { Text("This clears the canvas. You can undo it from the editor.") },
            confirmButton = {
                TextButton(onClick = {
                    state.newSkin()
                    confirmNew = false
                }) { Text("Clear canvas") }
            },
            dismissButton = {
                TextButton(onClick = { confirmNew = false }) { Text("Cancel") }
            }
        )
    }
}
