package com.apartmentdog.sheargenius

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.apartmentdog.sheargenius.model.SkinBox
import com.apartmentdog.sheargenius.model.SkinLayout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import kotlin.concurrent.thread

enum class Tool { PENCIL, ERASER, LINE, FILL, EYEDROPPER }

enum class Screen { EDITOR, REFERENCE, PREVIEW, FILES }

data class ProjectInfo(val id: String, val name: String, val updated: Long)

class RefImage(val file: File, val bitmap: Bitmap)

private class UndoStep(val pixels: IntArray, val slim: Boolean)

private val DEFAULT_PALETTE = listOf(
    0xFF4A1B0C.toInt(), 0xFF712B13.toInt(), 0xFF993C1D.toInt(), 0xFFD85A30.toInt(),
    0xFFF0997B.toInt(), 0xFFF5C4B3.toInt(), 0xFF2C2C2A.toInt(), 0xFFF1EFE8.toInt()
)

class AppState(private val context: Context) {
    var pixels: IntArray = IntArray(SkinLayout.COUNT)
        private set
    var version by mutableIntStateOf(0)
        private set
    var slim by mutableStateOf(false)
        private set
    var overlayVisible by mutableStateOf(true)
    var tool by mutableStateOf(Tool.PENCIL)
    var mirror by mutableStateOf(false)
    var color by mutableIntStateOf(0xFFD85A30.toInt())
    val palette = mutableStateListOf<Int>()
    var screen by mutableStateOf(Screen.EDITOR)

    var projectId by mutableStateOf("")
        private set
    var projectName by mutableStateOf("")
        private set
    val projects = mutableStateListOf<ProjectInfo>()

    val references = mutableStateListOf<RefImage>()
    var selectedRef by mutableIntStateOf(0)
    var sampleSize by mutableIntStateOf(1)

    var previewRx by mutableFloatStateOf(0.15f)
    var previewRy by mutableFloatStateOf(-0.5f)
    var previewZoom by mutableFloatStateOf(1f)
    var previewOverlay by mutableStateOf(true)
    var spin by mutableStateOf(false)
    var previewHidden by mutableStateOf(emptySet<Int>())
    var previewBg by mutableIntStateOf(0)
        private set
    var miniPreview by mutableStateOf(false)
        private set
    var showLabels by mutableStateOf(true)
        private set

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private val undoStack = ArrayDeque<UndoStep>()
    private val redoStack = ArrayDeque<UndoStep>()
    private val prefs = context.getSharedPreferences("sheargenius", Context.MODE_PRIVATE)
    private val projectsDir = File(context.filesDir, "projects")

    init {
        projectsDir.mkdirs()
        color = prefs.getInt("color", color)
        miniPreview = prefs.getBoolean("miniPreview", false)
        previewBg = prefs.getInt("previewBg", 0)
        showLabels = prefs.getBoolean("labels", true)
        migrateLegacy()
        refreshProjects()
        val last = prefs.getString("project", null)
        when {
            last != null && projects.any { it.id == last } -> openProject(last)
            projects.isNotEmpty() -> openProject(projects.first().id)
            else -> createProject()
        }
    }

    val regionMap: IntArray get() = SkinLayout.regionMap(slim)
    val boxes: List<SkinBox> get() = SkinLayout.boxes(slim)

    // ---- painting

    fun isEditable(i: Int): Boolean {
        val b = regionMap[i]
        if (b < 0) return false
        return overlayVisible || !boxes[b].overlay
    }

    fun setPixel(i: Int, c: Int): Boolean {
        var changed = false
        if (isEditable(i) && pixels[i] != c) {
            pixels[i] = c
            changed = true
        }
        if (mirror) {
            val m = SkinLayout.mirror(slim, i)
            if (m >= 0 && isEditable(m) && pixels[m] != c) {
                pixels[m] = c
                changed = true
            }
        }
        return changed
    }

    fun fill(start: Int, c: Int): Boolean {
        val a = flood(start, c)
        val m = if (mirror) SkinLayout.mirror(slim, start) else -1
        val b = if (m >= 0) flood(m, c) else false
        return a || b
    }

    private fun flood(start: Int, c: Int): Boolean {
        if (!isEditable(start)) return false
        val target = pixels[start]
        if (target == c) return false
        val map = regionMap
        val region = map[start]
        val stack = ArrayDeque<Int>()
        stack.addLast(start)
        var changed = false
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (pixels[i] != target || map[i] != region) continue
            pixels[i] = c
            changed = true
            val x = i % SkinLayout.SIZE
            val y = i / SkinLayout.SIZE
            if (x > 0) stack.addLast(i - 1)
            if (x < SkinLayout.SIZE - 1) stack.addLast(i + 1)
            if (y > 0) stack.addLast(i - SkinLayout.SIZE)
            if (y < SkinLayout.SIZE - 1) stack.addLast(i + SkinLayout.SIZE)
        }
        return changed
    }

    fun pick(i: Int) {
        val p = pixels[i]
        if ((p ushr 24) != 0) {
            color = p or (0xFF shl 24)
            tool = Tool.PENCIL
            savePrefs()
        }
    }

    fun touched() {
        version++
    }

    fun copyPixels(): IntArray = pixels.copyOf()

    fun restore(p: IntArray) {
        pixels = p.copyOf()
        version++
    }

    fun commit(before: IntArray) {
        push(UndoStep(before, slim))
        save()
    }

    // ---- undo / redo

    private fun push(step: UndoStep) {
        undoStack.addLast(step)
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        updateFlags()
    }

    fun undo() {
        val step = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(UndoStep(pixels.copyOf(), slim))
        pixels = step.pixels
        slim = step.slim
        version++
        updateFlags()
        save()
    }

    fun redo() {
        val step = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(UndoStep(pixels.copyOf(), slim))
        pixels = step.pixels
        slim = step.slim
        version++
        updateFlags()
        save()
    }

    private fun updateFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    // ---- model

    fun armsHaveContent(): Boolean = SkinLayout.armsHaveContent(pixels, slim)

    fun changeModel(target: Boolean) {
        if (target == slim) return
        push(UndoStep(pixels.copyOf(), slim))
        pixels = SkinLayout.convertArms(pixels, slim, target)
        slim = target
        version++
        save()
    }

    // ---- palette (per project)

    fun addToPalette(c: Int) {
        if (c !in palette) {
            palette.add(c)
            if (palette.size > 35) palette.removeAt(0)
            saveMeta()
        }
    }

    fun removeFromPalette(c: Int) {
        palette.remove(c)
        saveMeta()
    }

    fun replacePalette(colors: List<Int>) {
        if (colors.isEmpty()) return
        palette.clear()
        palette.addAll(colors.distinct())
        saveMeta()
    }

    // ---- projects

    private fun dirOf(id: String) = File(projectsDir, id)
    private fun refsDirOf(id: String) = File(dirOf(id), "refs")

    fun refreshProjects() {
        val list = projectsDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            val meta = readMeta(dir.name) ?: return@mapNotNull null
            ProjectInfo(dir.name, meta.optString("name", "Untitled"), meta.optLong("updated", dir.lastModified()))
        } ?: emptyList()
        projects.clear()
        projects.addAll(list.sortedByDescending { it.updated })
    }

    private fun readMeta(id: String): JSONObject? {
        val f = File(dirOf(id), "meta.json")
        if (!f.exists()) return null
        return try {
            JSONObject(f.readText())
        } catch (e: Exception) {
            null
        }
    }

    private fun nextName(base: String): String {
        val names = projects.map { it.name }.toSet()
        if (base !in names) return base
        var n = 2
        while ("$base $n" in names) n++
        return "$base $n"
    }

    fun createProject(
        name: String? = null,
        startPixels: IntArray = IntArray(SkinLayout.COUNT),
        startSlim: Boolean = false,
        startPalette: List<Int> = DEFAULT_PALETTE
    ) {
        val id = System.currentTimeMillis().toString()
        dirOf(id).mkdirs()
        projectId = id
        projectName = nextName(name ?: "Skin")
        pixels = startPixels.copyOf()
        slim = startSlim
        palette.clear()
        palette.addAll(startPalette)
        references.clear()
        selectedRef = 0
        undoStack.clear()
        redoStack.clear()
        updateFlags()
        version++
        save()
        refreshProjects()
    }

    fun openProject(id: String) {
        val meta = readMeta(id) ?: return
        projectId = id
        projectName = meta.optString("name", "Untitled")
        slim = meta.optBoolean("slim", false)
        palette.clear()
        val arr = meta.optJSONArray("palette")
        if (arr != null && arr.length() > 0) {
            for (i in 0 until arr.length()) palette.add(arr.getInt(i))
        } else {
            palette.addAll(DEFAULT_PALETTE)
        }
        val loaded = IntArray(SkinLayout.COUNT)
        val f = File(dirOf(id), "skin.png")
        if (f.exists()) {
            val opts = BitmapFactory.Options().apply { inPremultiplied = false }
            val b = BitmapFactory.decodeFile(f.path, opts)
            if (b != null && b.width == 64 && b.height == 64) b.getPixels(loaded, 0, 64, 0, 0, 64, 64)
        }
        pixels = loaded
        references.clear()
        refsDirOf(id).listFiles()?.sortedBy { it.name }?.forEach { rf ->
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            BitmapFactory.decodeFile(rf.path, opts)?.let { references.add(RefImage(rf, it)) }
        }
        selectedRef = 0
        undoStack.clear()
        redoStack.clear()
        updateFlags()
        version++
        savePrefs()
    }

    fun renameProject(id: String, name: String) {
        val clean = name.trim().ifEmpty { return }
        val meta = readMeta(id) ?: return
        meta.put("name", clean)
        File(dirOf(id), "meta.json").writeText(meta.toString())
        if (id == projectId) projectName = clean
        refreshProjects()
    }

    fun deleteProject(id: String) {
        dirOf(id).deleteRecursively()
        refreshProjects()
        if (id == projectId) {
            if (projects.isNotEmpty()) openProject(projects.first().id) else createProject()
        }
    }

    fun thumbnailFile(id: String): File = File(dirOf(id), "skin.png")

    private fun migrateLegacy() {
        val old = File(context.filesDir, "current.png")
        if (!old.exists()) return
        val hasProjects = projectsDir.listFiles()?.any { it.isDirectory } == true
        if (!hasProjects) {
            val px = IntArray(SkinLayout.COUNT)
            val opts = BitmapFactory.Options().apply { inPremultiplied = false }
            val b = BitmapFactory.decodeFile(old.path, opts)
            if (b != null && b.width == 64 && b.height == 64) b.getPixels(px, 0, 64, 0, 0, 64, 64)
            val oldPalette = prefs.getString("palette", null)
                ?.split(",")?.mapNotNull { it.toIntOrNull() }.orEmpty()
            createProject(
                name = "Skin",
                startPixels = px,
                startSlim = prefs.getBoolean("slim", false),
                startPalette = oldPalette.ifEmpty { DEFAULT_PALETTE }
            )
        }
        old.delete()
    }

    // ---- references (saved inside the project)

    fun addReference(uri: Uri): String? {
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val m = maxOf(w, h)
                if (m > 2048) {
                    val s = 2048f / m
                    decoder.setTargetSize((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1))
                }
            }
            val dir = refsDirOf(projectId)
            dir.mkdirs()
            val file = File(dir, "${System.currentTimeMillis()}.png")
            references.add(RefImage(file, bmp))
            selectedRef = references.lastIndex
            thread {
                try {
                    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } catch (e: Exception) {
                    file.delete()
                }
            }
            null
        } catch (e: Exception) {
            "Couldn't open that image."
        }
    }

    fun removeReference(i: Int) {
        if (i in references.indices) {
            val r = references.removeAt(i)
            r.file.delete()
            selectedRef = selectedRef.coerceIn(0, maxOf(0, references.lastIndex))
        }
    }

    // ---- files

    /** Imports a skin PNG as a new project named after the file. */
    fun importSkin(uri: Uri): String? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inPremultiplied = false
                inScaled = false
            }
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return "Couldn't read that file."
            val result = when {
                bmp.width == 64 && bmp.height == 64 -> {
                    val a = IntArray(SkinLayout.COUNT)
                    bmp.getPixels(a, 0, 64, 0, 0, 64, 64)
                    a
                }
                bmp.width == 64 && bmp.height == 32 -> {
                    val a = IntArray(64 * 32)
                    bmp.getPixels(a, 0, 64, 0, 0, 64, 32)
                    SkinLayout.upgradeLegacy(a)
                }
                else -> return "Skins must be 64×64 or 64×32. That image is ${bmp.width}×${bmp.height}."
            }
            createProject(
                name = displayName(uri) ?: "Imported skin",
                startPixels = result,
                startSlim = SkinLayout.looksSlim(result),
                startPalette = palette.toList()
            )
            null
        } catch (e: Exception) {
            "Couldn't read that file."
        }
    }

    private fun displayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun exportSkin(uri: Uri): Boolean {
        return try {
            val out = context.contentResolver.openOutputStream(uri) ?: return false
            out.use { writePng(it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun writePng(out: OutputStream) {
        val b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        b.isPremultiplied = false
        b.setPixels(pixels, 0, 64, 0, 0, 64, 64)
        b.compress(Bitmap.CompressFormat.PNG, 100, out)
    }

    fun save() {
        if (projectId.isEmpty()) return
        try {
            dirOf(projectId).mkdirs()
            File(dirOf(projectId), "skin.png").outputStream().use { writePng(it) }
        } catch (e: Exception) {
        }
        saveMeta()
    }

    private fun saveMeta() {
        if (projectId.isEmpty()) return
        val now = System.currentTimeMillis()
        val meta = JSONObject()
            .put("name", projectName)
            .put("slim", slim)
            .put("updated", now)
            .put("palette", JSONArray(palette.toList()))
        try {
            File(dirOf(projectId), "meta.json").writeText(meta.toString())
        } catch (e: Exception) {
        }
        val idx = projects.indexOfFirst { it.id == projectId }
        if (idx >= 0) projects[idx] = projects[idx].copy(name = projectName, updated = now)
        savePrefs()
    }

    fun resetPreview() {
        previewRx = 0.15f
        previewRy = -0.5f
        previewZoom = 1f
    }

    fun togglePart(part: Int) {
        previewHidden = if (part in previewHidden) previewHidden - part else previewHidden + part
    }

    fun changePreviewBg(index: Int) {
        previewBg = index
        prefs.edit().putInt("previewBg", index).apply()
    }

    fun toggleLabels() {
        showLabels = !showLabels
        prefs.edit().putBoolean("labels", showLabels).apply()
    }

    fun toggleMiniPreview() {
        miniPreview = !miniPreview
        prefs.edit().putBoolean("miniPreview", miniPreview).apply()
    }

    fun savePrefs() {
        prefs.edit()
            .putInt("color", color)
            .putString("project", projectId)
            .apply()
    }
}
