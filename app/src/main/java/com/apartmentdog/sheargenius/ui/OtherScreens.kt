package com.apartmentdog.sheargenius.ui

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.apartmentdog.sheargenius.AppState
import com.apartmentdog.sheargenius.MojangSkins
import com.apartmentdog.sheargenius.ProjectInfo
import com.apartmentdog.sheargenius.Screen
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FilesScreen(state: AppState) {
    val context = LocalContext.current
    var menuFor by remember { mutableStateOf<ProjectInfo?>(null) }
    var renameFor by remember { mutableStateOf<ProjectInfo?>(null) }
    var deleteFor by remember { mutableStateOf<ProjectInfo?>(null) }
    var showPlayer by remember { mutableStateOf(false) }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) {
            val ok = state.exportSkin(uri)
            Toast.makeText(context, if (ok) "Skin exported" else "Couldn't export to that location", Toast.LENGTH_SHORT).show()
        }
    }
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val ok = try {
                context.contentResolver.openOutputStream(uri)?.use { state.backupAll(it) }
                true
            } catch (e: Exception) {
                false
            }
            Toast.makeText(context, if (ok) "Backup saved" else "Couldn't save the backup", Toast.LENGTH_SHORT).show()
        }
    }
    val backupOpener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val count = try {
                context.contentResolver.openInputStream(uri)?.use { state.restoreAll(it) } ?: 0
            } catch (e: Exception) {
                -1
            }
            Toast.makeText(
                context,
                if (count > 0) "Restored $count project${if (count == 1) "" else "s"}"
                else if (count == 0) "That backup had nothing to restore" else "Couldn't read that backup",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    var exportIssues by remember { mutableStateOf<List<String>>(emptyList()) }
    fun requestExport() {
        val issues = state.transparencyIssues()
        if (issues.isEmpty()) {
            saver.launch("${state.projectName}.png")
        } else {
            exportIssues = issues
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val err = state.importSkin(uri)
            if (err != null) {
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            } else {
                state.screen = Screen.EDITOR
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Panel(Modifier.fillMaxWidth()) {
            PixelText(state.projectName, 16.sp, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlockButton(
                    onClick = { requestExport() },
                    icon = PixelIcons.Disk,
                    label = "Export PNG"
                )
                BlockButton(
                    onClick = { renameFor = ProjectInfo(state.projectId, state.projectName, 0L) },
                    label = "Rename"
                )
            }
            Spacer(Modifier.height(8.dp))
            PixelText("Exports are 64×64 PNGs ready to upload to Minecraft: Java Edition.", 12.sp)
        }

        Panel(Modifier.fillMaxWidth()) {
            PixelText("Backup", 16.sp)
            Spacer(Modifier.height(4.dp))
            PixelText("Save every project, its layers and its reference images to one file, or bring them back from one.", 12.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlockButton(onClick = { backupSaver.launch("ShearGenius-backup.zip") }, label = "Backup all")
                BlockButton(onClick = { backupOpener.launch(arrayOf("application/zip")) }, label = "Restore")
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            PixelText("Projects", 16.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlockButton(onClick = {
                    state.createProject()
                    state.screen = Screen.EDITOR
                }, icon = PixelIcons.Plus, label = "New")
                BlockButton(onClick = { opener.launch(arrayOf("image/png")) }, icon = PixelIcons.Photo, label = "Import")
                BlockButton(onClick = { showPlayer = true }, label = "Player")
                BlockButton(onClick = { state.startOnboarding() }, label = "Tour")
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.projects.forEach { p ->
                    key(p.id) {
                        ProjectRow(
                            info = p,
                            current = p.id == state.projectId,
                            thumb = state.thumbnailFile(p.id),
                            onOpen = {
                                if (p.id != state.projectId) state.openProject(p.id)
                                state.screen = Screen.EDITOR
                            },
                            onMenu = { menuFor = p }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            PixelText("Tap a project to open it. Long-press to rename or delete.", 11.sp)
        }
    }

    menuFor?.let { p ->
        Dialog(onDismissRequest = { menuFor = null }) {
            Panel(Modifier.width(280.dp)) {
                PixelText(p.name, 16.sp, maxLines = 1)
                Spacer(Modifier.height(12.dp))
                BlockButton(onClick = {
                    menuFor = null
                    renameFor = p
                }, modifier = Modifier.fillMaxWidth(), label = "Rename")
                Spacer(Modifier.height(10.dp))
                BlockButton(onClick = {
                    menuFor = null
                    deleteFor = p
                }, modifier = Modifier.fillMaxWidth(), label = "Delete")
                Spacer(Modifier.height(10.dp))
                BlockButton(onClick = { menuFor = null }, modifier = Modifier.fillMaxWidth(), label = "Cancel")
            }
        }
    }

    renameFor?.let { p ->
        var text by remember(p.id) { mutableStateOf(p.name) }
        Dialog(onDismissRequest = { renameFor = null }) {
            Panel(Modifier.width(300.dp)) {
                PixelText("Rename project", 16.sp)
                Spacer(Modifier.height(10.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = LocalPixelFont.current, fontSize = 16.sp, color = Blocky.Text),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bevel(Color.White, Blocky.ButtonDark, Color.White, 2.dp, null)
                        .padding(10.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    BlockButton(onClick = { renameFor = null }, label = "Cancel")
                    BlockButton(onClick = {
                        state.renameProject(p.id, text)
                        renameFor = null
                    }, label = "Save", selected = true)
                }
            }
        }
    }

    if (showPlayer) {
        PlayerSkinDialog(state, onDone = {
            showPlayer = false
            state.screen = Screen.EDITOR
        }, onDismiss = { showPlayer = false })
    }

    if (exportIssues.isNotEmpty()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { exportIssues = emptyList() },
            title = { androidx.compose.material3.Text("Transparent pixels found") },
            text = {
                androidx.compose.material3.Text(
                    "These zones have see-through pixels on the base layer, which some launchers and servers render oddly: " +
                        exportIssues.joinToString(", ") + ". Export anyway?"
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    saver.launch("${state.projectName}.png")
                    exportIssues = emptyList()
                }) { androidx.compose.material3.Text("Export anyway") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { exportIssues = emptyList() }) { androidx.compose.material3.Text("Cancel") }
            }
        )
    }

    deleteFor?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Delete ${p.name}?") },
            text = { Text("This deletes the skin and its reference images. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    state.deleteProject(p.id)
                    deleteFor = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteFor = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProjectRow(info: ProjectInfo, current: Boolean, thumb: File, onOpen: () -> Unit, onMenu: () -> Unit) {
    val fill = if (current) Blocky.Green else Blocky.Button
    val light = if (current) Blocky.GreenLight else Blocky.ButtonLight
    val dark = if (current) Blocky.GreenDark else Blocky.ButtonDark
    Row(
        Modifier
            .fillMaxWidth()
            .bevel(fill, light, dark, 2.dp)
            .pointerInput(info.id) {
                detectTapGestures(onTap = { onOpen() }, onLongPress = { onMenu() })
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Slot(size = 44.dp) { HeadThumb(thumb, info.updated) }
        Column(Modifier.weight(1f)) {
            PixelText(info.name, 15.sp, Color.White, maxLines = 1)
            PixelText(
                "Edited " + DateUtils.getRelativeTimeSpanString(info.updated).toString().lowercase(),
                12.sp,
                Color.White
            )
        }
        Slot(size = 36.dp, onClick = onMenu) {
            PixelText("\u22ee", 16.sp, Color.White)
        }
    }
}

/** Front of the head with the hat layer on top. */
@Composable
private fun HeadThumb(file: File, stamp: Long) {
    val img = remember(file.path, stamp) {
        if (file.exists()) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null
    }
    Canvas(Modifier.size(28.dp)) {
        if (img != null && img.width == 64 && img.height == 64) {
            val dst = IntSize(size.width.toInt(), size.height.toInt())
            drawImage(img, srcOffset = IntOffset(8, 8), srcSize = IntSize(8, 8), dstSize = dst, filterQuality = FilterQuality.None)
            drawImage(img, srcOffset = IntOffset(40, 8), srcSize = IntSize(8, 8), dstSize = dst, filterQuality = FilterQuality.None)
        }
    }
}

@Composable
private fun PlayerSkinDialog(state: AppState, onDone: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun download() {
        if (busy || name.isBlank()) return
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Result.success(MojangSkins.fetch(name))
                } catch (e: MojangSkins.LookupException) {
                    Result.failure(e)
                } catch (e: Exception) {
                    Result.failure(Exception("Couldn't reach Minecraft's servers. Check your connection."))
                }
            }
            busy = false
            result.fold(
                onSuccess = { skin ->
                    val err = state.importSkinBytes(skin.bytes, skin.name, skin.slim)
                    if (err == null) onDone() else error = err
                },
                onFailure = { error = it.message }
            )
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Panel(Modifier.width(300.dp)) {
            PixelText("Download a player's skin", 16.sp)
            Spacer(Modifier.height(4.dp))
            PixelText("Java Edition username. It opens as a new project.", 12.sp)
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = name,
                onValueChange = { t -> name = t.filter { it.isLetterOrDigit() || it == '_' }.take(16) },
                singleLine = true,
                enabled = !busy,
                textStyle = TextStyle(fontFamily = LocalPixelFont.current, fontSize = 16.sp, color = Blocky.Text),
                modifier = Modifier
                    .fillMaxWidth()
                    .bevel(Color.White, Blocky.ButtonDark, Color.White, 2.dp, null)
                    .padding(10.dp)
            )
            val err = error
            if (busy) {
                Spacer(Modifier.height(8.dp))
                PixelText("Looking up $name…", 12.sp)
            } else if (err != null) {
                Spacer(Modifier.height(8.dp))
                PixelText(err, 12.sp, Color(0xFF993C1D))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                BlockButton(onClick = onDismiss, label = "Cancel", enabled = !busy)
                BlockButton(onClick = { download() }, label = "Download", selected = true, enabled = !busy && name.isNotBlank())
            }
        }
    }
}
