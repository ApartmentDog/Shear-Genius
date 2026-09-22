package com.apartmentdog.sheargenius

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.apartmentdog.sheargenius.model.SkinBox
import com.apartmentdog.sheargenius.model.SkinLayout
import java.io.File
import java.io.OutputStream

enum class Tool { PENCIL, ERASER, FILL, EYEDROPPER }

enum class Screen { EDITOR, REFERENCE, PREVIEW, EXPORT }

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

    val references = mutableStateListOf<Bitmap>()
    var selectedRef by mutableIntStateOf(0)
    var sampleSize by mutableIntStateOf(1)

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private val undoStack = ArrayDeque<UndoStep>()
    private val redoStack = ArrayDeque<UndoStep>()
    private val prefs = context.getSharedPreferences("sheargenius", Context.MODE_PRIVATE)

    init {
        load()
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

    fun setSlim(target: Boolean) {
        if (target == slim) return
        push(UndoStep(pixels.copyOf(), slim))
        pixels = SkinLayout.convertArms(pixels, slim, target)
        slim = target
        version++
        save()
    }

    fun newSkin() {
        push(UndoStep(pixels.copyOf(), slim))
        pixels = IntArray(SkinLayout.COUNT)
        version++
        save()
    }

    // ---- palette

    fun addToPalette(c: Int) {
        if (c !in palette) {
            palette.add(c)
            if (palette.size > 35) palette.removeAt(0)
            savePrefs()
        }
    }

    fun removeFromPalette(c: Int) {
        palette.remove(c)
        savePrefs()
    }

    fun replacePalette(colors: List<Int>) {
        if (colors.isEmpty()) return
        palette.clear()
        palette.addAll(colors.distinct())
        savePrefs()
    }

    // ---- references

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
            references.add(bmp)
            selectedRef = references.lastIndex
            null
        } catch (e: Exception) {
            "Couldn't open that image."
        }
    }

    fun removeReference(i: Int) {
        if (i in references.indices) {
            references.removeAt(i)
            selectedRef = selectedRef.coerceIn(0, maxOf(0, references.lastIndex))
        }
    }

    // ---- files

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
            push(UndoStep(pixels.copyOf(), slim))
            pixels = result
            slim = SkinLayout.looksSlim(result)
            version++
            save()
            null
        } catch (e: Exception) {
            "Couldn't read that file."
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
        try {
            File(context.filesDir, "current.png").outputStream().use { writePng(it) }
        } catch (_: Exception) {
        }
        savePrefs()
    }

    fun savePrefs() {
        prefs.edit()
            .putBoolean("slim", slim)
            .putInt("color", color)
            .putString("palette", palette.joinToString(","))
            .apply()
    }

    private fun load() {
        slim = prefs.getBoolean("slim", false)
        color = prefs.getInt("color", color)
        val saved = prefs.getString("palette", null)
        val list = saved?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        palette.addAll(if (list.isEmpty()) DEFAULT_PALETTE else list)
        val f = File(context.filesDir, "current.png")
        if (f.exists()) {
            val opts = BitmapFactory.Options().apply { inPremultiplied = false }
            val b = BitmapFactory.decodeFile(f.path, opts)
            if (b != null && b.width == 64 && b.height == 64) {
                b.getPixels(pixels, 0, 64, 0, 0, 64, 64)
            }
        }
    }
}
