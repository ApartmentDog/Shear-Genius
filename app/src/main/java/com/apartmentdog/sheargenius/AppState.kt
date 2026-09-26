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

enum class Tool { PENCIL, ERASER, LINE, FILL, EYEDROPPER, SHADE, SELECT, MOVE }

/** A rectangular pixel selection in skin-texture coordinates, inclusive bounds, clamped to 0..63. */
data class SelRect(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
    val w: Int get() = x1 - x0 + 1
    val h: Int get() = y1 - y0 + 1
    fun clamped(): SelRect = SelRect(x0.coerceIn(0, 63), y0.coerceIn(0, 63), x1.coerceIn(0, 63), y1.coerceIn(0, 63))
}

enum class ShadeMode { LIGHTEN, DARKEN, SHINE, NOISE, NOISY_PEN, DITHER }

enum class Screen { EDITOR, REFERENCE, PREVIEW, FILES }

data class ProjectInfo(val id: String, val name: String, val updated: Long)

class RefImage(val file: File, val bitmap: Bitmap)

/** One paint layer. Index 0 in [AppState.layers] is the bottom of the stack. */
data class Layer(val id: Long, val name: String, val visible: Boolean, val pixels: IntArray)

private class UndoStep(val layers: List<Layer>, val active: Int, val slim: Boolean)

const val MAX_LAYERS = 12

private val DEFAULT_PALETTE = listOf(
    0xFF4A1B0C.toInt(), 0xFF712B13.toInt(), 0xFF993C1D.toInt(), 0xFFD85A30.toInt(),
    0xFFF0997B.toInt(), 0xFFF5C4B3.toInt(), 0xFF2C2C2A.toInt(), 0xFFF1EFE8.toInt()
)

class AppState(private val context: Context) {
    val layers = mutableStateListOf(Layer(1L, "Base", true, IntArray(SkinLayout.COUNT)))
    var activeLayer by mutableIntStateOf(0)
        private set

    /** Pixels of the layer being painted. Tools edit this array in place. */
    val pixels: IntArray get() = layers[activeLayer.coerceIn(0, layers.lastIndex)].pixels
    var version by mutableIntStateOf(0)
        private set
    var slim by mutableStateOf(false)
        private set
    var overlayVisible by mutableStateOf(true)
    var tool by mutableStateOf(Tool.PENCIL)
    var mirror by mutableStateOf(false)
    var shadeMode by mutableStateOf(ShadeMode.LIGHTEN)
    var selection by mutableStateOf<SelRect?>(null)
    var selectMoveArmed by mutableStateOf(false)
    var hasClipboard by mutableStateOf(false)
        private set
    private var clipboard: IntArray? = null
    private var clipW = 0
    private var clipH = 0
    var shadeAmount by mutableFloatStateOf(0.5f)
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
    var paintOnModel by mutableStateOf(false)
    var walk by mutableStateOf(false)
    var walkPhase by mutableFloatStateOf(0f)
    val previewSwing: Float get() = if (walk) kotlin.math.sin(walkPhase) * 0.6f else 0f
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
        shadeAmount = prefs.getFloat("shadeAmount", 0.5f)
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

    /** Shade tool: each pixel is affected at most once per stroke (tracked in [visited]). */
    fun shade(i: Int, visited: MutableSet<Int>): Boolean {
        var changed = false
        fun one(j: Int) {
            if (j < 0 || !isEditable(j) || !visited.add(j)) return
            val p = pixels[j]
            if (shadeMode == ShadeMode.DITHER) {
                val x = j % SkinLayout.SIZE
                val y = j / SkinLayout.SIZE
                if ((x + y) % 2 == 0 && p != color) {
                    pixels[j] = color
                    changed = true
                }
                return
            }
            if (shadeMode == ShadeMode.NOISY_PEN) {
                pixels[j] = noisy(color)
                changed = true
                return
            }
            if ((p ushr 24) == 0) return
            val np = adjustShade(p)
            if (np != p) {
                pixels[j] = np
                changed = true
            }
        }
        one(i)
        if (mirror) one(SkinLayout.mirror(slim, i))
        return changed
    }

    private fun towards(h: Float, target: Float, amount: Float): Float {
        var diff = target - h
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        val step = if (kotlin.math.abs(diff) < amount) diff else kotlin.math.sign(diff) * amount
        return (h + step + 360f) % 360f
    }

    private fun rnd(): Float = (kotlin.random.Random.nextFloat() - 0.5f) * 2f

    /** Current color with random variation in value, hue and saturation, scaled by the amount slider. */
    private fun noisy(c: Int): Int {
        val a = shadeAmount
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        hsv[2] = (hsv[2] + rnd() * (0.02f + 0.23f * a)).coerceIn(0f, 1f)
        hsv[1] = (hsv[1] + rnd() * 0.08f * a).coerceIn(0f, 1f)
        hsv[0] = (hsv[0] + rnd() * 8f * a + 360f) % 360f
        return android.graphics.Color.HSVToColor(0xFF, hsv)
    }

    private fun adjustShade(p: Int): Int {
        val a = shadeAmount
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(p, hsv)
        val step = 0.02f + 0.14f * a
        when (shadeMode) {
            ShadeMode.LIGHTEN -> {
                hsv[2] = (hsv[2] + step).coerceAtMost(1f)
                hsv[1] = (hsv[1] - step / 2f).coerceAtLeast(0f)
                if (hsv[1] > 0.05f) hsv[0] = towards(hsv[0], 60f, 2f + 8f * a)
            }
            ShadeMode.DARKEN -> {
                hsv[2] = (hsv[2] - step).coerceAtLeast(0f)
                hsv[1] = (hsv[1] + step / 2f).coerceAtMost(1f)
                if (hsv[1] > 0.05f) hsv[0] = towards(hsv[0], 240f, 2f + 8f * a)
            }
            ShadeMode.SHINE -> {
                val t = 0.1f + 0.7f * a
                hsv[2] = hsv[2] + (1f - hsv[2]) * t
                hsv[1] = hsv[1] + (hsv[1] * 0.25f - hsv[1]) * t
                if (hsv[1] > 0.03f) hsv[0] = towards(hsv[0], 55f, 12f * t)
            }
            ShadeMode.NOISE -> {
                hsv[2] = (hsv[2] + rnd() * (0.02f + 0.23f * a)).coerceIn(0f, 1f)
            }
            ShadeMode.NOISY_PEN, ShadeMode.DITHER -> {}
        }
        return android.graphics.Color.HSVToColor(p ushr 24, hsv)
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
        val p = composite()[i]
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
        System.arraycopy(p, 0, pixels, 0, SkinLayout.COUNT)
        version++
    }

    fun commit(before: IntArray) {
        val snap = snapshot()
        val layersBefore = snap.layers.toMutableList()
        val i = snap.active
        layersBefore[i] = layersBefore[i].copy(pixels = before)
        push(UndoStep(layersBefore, i, slim))
        save()
    }

    private fun snapshot(): UndoStep =
        UndoStep(layers.map { it.copy(pixels = it.pixels.copyOf()) }, activeLayer, slim)

    private fun applyStep(step: UndoStep) {
        layers.clear()
        layers.addAll(step.layers)
        activeLayer = step.active.coerceIn(0, layers.lastIndex)
        slim = step.slim
        version++
    }

    /** Visible layers flattened bottom to top with normal alpha blending. */
    fun composite(): IntArray {
        val out = IntArray(SkinLayout.COUNT)
        for (layer in layers) {
            if (!layer.visible) continue
            val src = layer.pixels
            for (i in 0 until SkinLayout.COUNT) {
                val s = src[i]
                if ((s ushr 24) == 0) continue
                out[i] = blendOver(s, out[i])
            }
        }
        return out
    }

    /** Source-over blend of [s] atop [d], both ARGB. */
    private fun blendOver(s: Int, d: Int): Int {
        val sa = s ushr 24
        if (sa == 255 || (d ushr 24) == 0) return s
        if (sa == 0) return d
        val da = d ushr 24
        val outA = sa + da * (255 - sa) / 255
        if (outA == 0) return 0
        fun ch(shift: Int): Int {
            val sc = (s shr shift) and 0xFF
            val dc = (d shr shift) and 0xFF
            return ((sc * sa + dc * da * (255 - sa) / 255) / outA).coerceIn(0, 255)
        }
        return (outA shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    // ---- selection

    fun setSelection(r: SelRect?) {
        selection = r?.clamped()
        selectMoveArmed = false
    }

    fun copySelection() {
        val r = selection ?: return
        val buf = IntArray(r.w * r.h)
        for (y in 0 until r.h) for (x in 0 until r.w) buf[y * r.w + x] = pixels[(r.y0 + y) * SkinLayout.SIZE + (r.x0 + x)]
        clipboard = buf
        clipW = r.w
        clipH = r.h
        hasClipboard = true
    }

    fun pasteSelection() {
        val cb = clipboard ?: return
        val r = selection
        val ox = r?.x0 ?: 0
        val oy = r?.y0 ?: 0
        val before = copyPixels()
        var changed = false
        for (y in 0 until clipH) for (x in 0 until clipW) {
            val s = cb[y * clipW + x]
            if ((s ushr 24) == 0) continue
            val px = ox + x
            val py = oy + y
            if (px !in 0 until SkinLayout.SIZE || py !in 0 until SkinLayout.SIZE) continue
            val idx = py * SkinLayout.SIZE + px
            if (!isEditable(idx)) continue
            val np = blendOver(s, pixels[idx])
            if (pixels[idx] != np) {
                pixels[idx] = np
                changed = true
            }
        }
        selection = SelRect(ox, oy, ox + clipW - 1, oy + clipH - 1).clamped()
        if (changed) {
            touched()
            commit(before)
        }
    }

    fun deleteSelection() {
        val r = selection ?: return
        val before = copyPixels()
        var changed = false
        for (y in r.y0..r.y1) for (x in r.x0..r.x1) {
            val idx = y * SkinLayout.SIZE + x
            if (isEditable(idx) && pixels[idx] != 0) {
                pixels[idx] = 0
                changed = true
            }
        }
        if (changed) {
            touched()
            commit(before)
        }
    }

    fun flipSelection(horizontal: Boolean) {
        val r = selection ?: return
        val before = copyPixels()
        val buf = IntArray(r.w * r.h)
        for (y in 0 until r.h) for (x in 0 until r.w) buf[y * r.w + x] = pixels[(r.y0 + y) * SkinLayout.SIZE + (r.x0 + x)]
        var changed = false
        for (y in 0 until r.h) for (x in 0 until r.w) {
            val sx = if (horizontal) r.w - 1 - x else x
            val sy = if (horizontal) y else r.h - 1 - y
            val idx = (r.y0 + y) * SkinLayout.SIZE + (r.x0 + x)
            if (!isEditable(idx)) continue
            val v = buf[sy * r.w + sx]
            if (pixels[idx] != v) {
                pixels[idx] = v
                changed = true
            }
        }
        if (changed) {
            touched()
            commit(before)
        }
    }

    fun armMove() {
        if (selection != null) selectMoveArmed = true
    }

    fun disarmMove() {
        selectMoveArmed = false
    }

    /**
     * Redraws the move preview from [original] (a snapshot taken when the drag started):
     * clears [start]'s area and repaints its content offset by ([dx],[dy]) pixels. Cheap
     * enough to call on every pointer move since it always starts from the same snapshot.
     */
    fun previewMove(original: IntArray, start: SelRect, dx: Int, dy: Int) {
        System.arraycopy(original, 0, pixels, 0, SkinLayout.COUNT)
        val buf = IntArray(start.w * start.h)
        for (y in 0 until start.h) for (x in 0 until start.w) {
            buf[y * start.w + x] = original[(start.y0 + y) * SkinLayout.SIZE + (start.x0 + x)]
        }
        for (y in 0 until start.h) for (x in 0 until start.w) {
            val idx = (start.y0 + y) * SkinLayout.SIZE + (start.x0 + x)
            if (isEditable(idx)) pixels[idx] = 0
        }
        val nx0 = start.x0 + dx
        val ny0 = start.y0 + dy
        for (y in 0 until start.h) for (x in 0 until start.w) {
            val px = nx0 + x
            val py = ny0 + y
            if (px !in 0 until SkinLayout.SIZE || py !in 0 until SkinLayout.SIZE) continue
            val idx = py * SkinLayout.SIZE + px
            if (!isEditable(idx)) continue
            val s = buf[y * start.w + x]
            if ((s ushr 24) != 0) pixels[idx] = s
        }
        selection = SelRect(nx0, ny0, nx0 + start.w - 1, ny0 + start.h - 1)
        touched()
    }

    fun clearSelection() {
        selection = null
        selectMoveArmed = false
    }

    // ---- layers

    private fun newLayerId(): Long = (layers.maxOfOrNull { it.id } ?: 0L) + 1L

    fun selectLayer(i: Int) {
        if (i in layers.indices) {
            activeLayer = i
            version++
        }
    }

    fun addLayer() {
        if (layers.size >= MAX_LAYERS) return
        push(snapshot())
        val id = newLayerId()
        layers.add(activeLayer + 1, Layer(id, "Layer $id", true, IntArray(SkinLayout.COUNT)))
        activeLayer += 1
        version++
        save()
    }

    fun duplicateLayer() {
        if (layers.size >= MAX_LAYERS) return
        push(snapshot())
        val src = layers[activeLayer]
        layers.add(activeLayer + 1, Layer(newLayerId(), src.name + " copy", src.visible, src.pixels.copyOf()))
        activeLayer += 1
        version++
        save()
    }

    fun deleteLayer() {
        if (layers.size <= 1) return
        push(snapshot())
        layers.removeAt(activeLayer)
        activeLayer = activeLayer.coerceAtMost(layers.lastIndex)
        version++
        save()
    }

    /** Moves the active layer up (toward the top of the stack) when [up] is true. */
    fun moveLayer(up: Boolean) {
        val to = if (up) activeLayer + 1 else activeLayer - 1
        if (to !in layers.indices) return
        push(snapshot())
        val l = layers.removeAt(activeLayer)
        layers.add(to, l)
        activeLayer = to
        version++
        save()
    }

    /** Merges the active layer into the one below it. */
    fun mergeDown() {
        if (activeLayer == 0) return
        push(snapshot())
        val top = layers[activeLayer]
        val below = layers[activeLayer - 1]
        val merged = below.pixels.copyOf()
        if (top.visible) {
            for (i in 0 until SkinLayout.COUNT) {
                val s = top.pixels[i]
                val sa = s ushr 24
                if (sa == 0) continue
                if (sa == 255 || (merged[i] ushr 24) == 0) merged[i] = s
                else {
                    val d = merged[i]
                    val da = d ushr 24
                    val outA = sa + da * (255 - sa) / 255
                    fun ch(shift: Int): Int {
                        val sc = (s shr shift) and 0xFF
                        val dc = (d shr shift) and 0xFF
                        return ((sc * sa + dc * da * (255 - sa) / 255) / outA).coerceIn(0, 255)
                    }
                    merged[i] = (outA shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
                }
            }
        }
        layers[activeLayer - 1] = below.copy(pixels = merged)
        layers.removeAt(activeLayer)
        activeLayer -= 1
        version++
        save()
    }

    fun renameLayer(i: Int, name: String) {
        val clean = name.trim().ifEmpty { return }
        if (i !in layers.indices) return
        layers[i] = layers[i].copy(name = clean.take(24))
        save()
    }

    fun toggleLayerVisible(i: Int) {
        if (i !in layers.indices) return
        layers[i] = layers[i].copy(visible = !layers[i].visible)
        version++
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
        redoStack.addLast(snapshot())
        applyStep(step)
        updateFlags()
        save()
    }

    fun redo() {
        val step = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot())
        applyStep(step)
        updateFlags()
        save()
    }

    private fun updateFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    // ---- model

    fun armsHaveContent(): Boolean = layers.any { SkinLayout.armsHaveContent(it.pixels, slim) }

    fun changeModel(target: Boolean) {
        if (target == slim) return
        push(snapshot())
        for (i in layers.indices) {
            layers[i] = layers[i].copy(pixels = SkinLayout.convertArms(layers[i].pixels, slim, target))
        }
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
    private fun layersDirOf(id: String) = File(dirOf(id), "layers")

    private fun readSkinFile(f: File): IntArray {
        val out = IntArray(SkinLayout.COUNT)
        if (f.exists()) {
            val opts = BitmapFactory.Options().apply { inPremultiplied = false }
            val b = BitmapFactory.decodeFile(f.path, opts)
            if (b != null && b.width == 64 && b.height == 64) b.getPixels(out, 0, 64, 0, 0, 64, 64)
        }
        return out
    }

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
        layers.clear()
        layers.add(Layer(1L, "Base", true, startPixels.copyOf()))
        activeLayer = 0
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
        val loadedLayers = mutableListOf<Layer>()
        val layerMeta = meta.optJSONArray("layers")
        if (layerMeta != null && layerMeta.length() > 0) {
            for (i in 0 until layerMeta.length()) {
                val o = layerMeta.getJSONObject(i)
                val lid = o.optLong("id", (i + 1).toLong())
                loadedLayers.add(
                    Layer(lid, o.optString("name", "Layer $lid"), o.optBoolean("visible", true),
                        readSkinFile(File(layersDirOf(id), "$lid.png")))
                )
            }
        } else {
            loadedLayers.add(Layer(1L, "Base", true, readSkinFile(File(dirOf(id), "skin.png"))))
        }
        layers.clear()
        layers.addAll(loadedLayers)
        activeLayer = meta.optInt("active", layers.lastIndex).coerceIn(0, layers.lastIndex)
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

    /** Imports a downloaded skin (PNG bytes) as a new project. */
    fun importSkinBytes(bytes: ByteArray, name: String, slimHint: Boolean): String? {
        val opts = BitmapFactory.Options().apply {
            inPremultiplied = false
            inScaled = false
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return "That skin file couldn't be read."
        val px = when {
            bmp.width == 64 && bmp.height == 64 -> IntArray(SkinLayout.COUNT).also { bmp.getPixels(it, 0, 64, 0, 0, 64, 64) }
            bmp.width == 64 && bmp.height == 32 -> {
                val a = IntArray(64 * 32)
                bmp.getPixels(a, 0, 64, 0, 0, 64, 32)
                SkinLayout.upgradeLegacy(a)
            }
            else -> return "That skin is ${bmp.width}×${bmp.height}, which isn't a standard Java skin size."
        }
        createProject(name = name, startPixels = px, startSlim = slimHint && bmp.height == 64, startPalette = palette.toList())
        return null
    }

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

    private fun writePng(out: OutputStream, px: IntArray = composite()) {
        val b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        b.isPremultiplied = false
        b.setPixels(px, 0, 64, 0, 0, 64, 64)
        b.compress(Bitmap.CompressFormat.PNG, 100, out)
    }

    fun save() {
        if (projectId.isEmpty()) return
        try {
            dirOf(projectId).mkdirs()
            File(dirOf(projectId), "skin.png").outputStream().use { writePng(it) }
            val ld = layersDirOf(projectId)
            ld.mkdirs()
            val keep = layers.map { "${it.id}.png" }.toSet()
            for (l in layers) File(ld, "${l.id}.png").outputStream().use { writePng(it, l.pixels) }
            ld.listFiles()?.forEach { if (it.name !in keep) it.delete() }
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
            .put("active", activeLayer)
            .put("layers", JSONArray().also { arr ->
                layers.forEach { l ->
                    arr.put(JSONObject().put("id", l.id).put("name", l.name).put("visible", l.visible))
                }
            })
        try {
            File(dirOf(projectId), "meta.json").writeText(meta.toString())
        } catch (e: Exception) {
        }
        val idx = projects.indexOfFirst { it.id == projectId }
        if (idx >= 0) projects[idx] = projects[idx].copy(name = projectName, updated = now)
        savePrefs()
    }

    fun togglePaintOnModel() {
        paintOnModel = !paintOnModel
        if (paintOnModel) {
            spin = false
            walk = false
            walkPhase = 0f
        }
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

    fun saveShadeAmount() {
        prefs.edit().putFloat("shadeAmount", shadeAmount).apply()
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
