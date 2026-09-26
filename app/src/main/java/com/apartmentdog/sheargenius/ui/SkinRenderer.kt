package com.apartmentdog.sheargenius.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.apartmentdog.sheargenius.AppState
import com.apartmentdog.sheargenius.model.Face
import com.apartmentdog.sheargenius.model.SkinBox
import com.apartmentdog.sheargenius.model.SkinLayout
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the skin as 12 textured boxes with an orthographic camera.
 * Each face is one textured quad (drawVertices + nearest-neighbour BitmapShader).
 * Boxes are depth-sorted per body part; each overlay is drawn right after its base.
 */
object SkinRenderer {
    private val light = floatArrayOf(0.35f, 0.55f, 0.76f)
    private val indices = shortArrayOf(0, 1, 2, 1, 3, 2)
    private val verts = FloatArray(8)
    private val texs = FloatArray(8)
    private val corners = FloatArray(12)
    private val tmp = FloatArray(3)

    fun newPaint(skin: Bitmap): Paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        shader = BitmapShader(skin, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
            if (Build.VERSION.SDK_INT >= 33) it.filterMode = BitmapShader.FILTER_MODE_NEAREST
        }
    }

    private fun centerOf(id: String, slim: Boolean): FloatArray {
        val armX = 4f + (if (slim) 3f else 4f) / 2f
        return when (id) {
            "head", "hat" -> floatArrayOf(0f, 12f, 0f)
            "body", "jacket" -> floatArrayOf(0f, 2f, 0f)
            "rarm", "rsleeve" -> floatArrayOf(-armX, 2f, 0f)
            "larm", "lsleeve" -> floatArrayOf(armX, 2f, 0f)
            "rleg", "rpants" -> floatArrayOf(-2f, -10f, 0f)
            else -> floatArrayOf(2f, -10f, 0f)
        }
    }

    fun draw(
        canvas: Canvas,
        paint: Paint,
        slim: Boolean,
        showOverlay: Boolean,
        hidden: Set<Int>,
        rx: Float,
        ry: Float,
        zoom: Float,
        width: Float,
        height: Float,
        swing: Float = 0f
    ) {
        val boxes = SkinLayout.boxes(slim)
        val cY = cos(ry)
        val sY = sin(ry)
        val cX = cos(rx)
        val sX = sin(rx)
        val unit = min(width, height) / 40f * zoom
        val ox = width / 2f
        val oy = height / 2f

        fun rot(x: Float, y: Float, z: Float, out: FloatArray) {
            val x1 = x * cY + z * sY
            val z1 = -x * sY + z * cY
            out[0] = x1
            out[1] = y * cX - z1 * sX
            out[2] = y * sX + z1 * cX
        }

        // Limb pose: rotation about the X axis around a pivot at the shoulder or hip.
        var poseA = 0f
        var poseY = 0f
        var poseC = 1f
        var poseS = 0f
        fun setPose(a: Float, pivotY: Float) {
            poseA = a
            poseY = pivotY
            poseC = cos(a)
            poseS = sin(a)
        }
        fun posePoint(p: FloatArray, o: Int) {
            if (poseA == 0f) return
            val y = p[o + 1] - poseY
            val z = p[o + 2]
            p[o + 1] = poseY + y * poseC - z * poseS
            p[o + 2] = y * poseS + z * poseC
        }

        fun drawBox(box: SkinBox, c: FloatArray, inf: Float) {
            val hw = box.w / 2f + inf
            val hh = box.h / 2f + inf
            val hd = box.d / 2f + inf
            val x0 = c[0] - hw
            val x1 = c[0] + hw
            val y0 = c[1] - hh
            val y1 = c[1] + hh
            val z0 = c[2] - hd
            val z1 = c[2] + hd
            val u = box.u
            val v = box.v
            val w = box.w
            val h = box.h
            val d = box.d
            for (f in Face.values()) {
                var nx = 0f
                var ny = 0f
                var nz = 0f
                val tx: Int
                val ty: Int
                val tw: Int
                val th: Int
                when (f) {
                    Face.FRONT -> {
                        nz = 1f
                        set(x0, y1, z1, x1, y1, z1, x0, y0, z1, x1, y0, z1)
                        tx = u + d; ty = v + d; tw = w; th = h
                    }
                    Face.BACK -> {
                        nz = -1f
                        set(x1, y1, z0, x0, y1, z0, x1, y0, z0, x0, y0, z0)
                        tx = u + 2 * d + w; ty = v + d; tw = w; th = h
                    }
                    Face.RIGHT -> {
                        nx = -1f
                        set(x0, y1, z0, x0, y1, z1, x0, y0, z0, x0, y0, z1)
                        tx = u; ty = v + d; tw = d; th = h
                    }
                    Face.LEFT -> {
                        nx = 1f
                        set(x1, y1, z1, x1, y1, z0, x1, y0, z1, x1, y0, z0)
                        tx = u + d + w; ty = v + d; tw = d; th = h
                    }
                    Face.TOP -> {
                        ny = 1f
                        set(x0, y1, z0, x1, y1, z0, x0, y1, z1, x1, y1, z1)
                        tx = u + d; ty = v; tw = w; th = d
                    }
                    Face.BOTTOM -> {
                        ny = -1f
                        set(x0, y0, z1, x1, y0, z1, x0, y0, z0, x1, y0, z0)
                        tx = u + d + w; ty = v; tw = w; th = d
                    }
                }
                if (poseA != 0f) {
                    val py = ny * poseC - nz * poseS
                    val pz = ny * poseS + nz * poseC
                    ny = py
                    nz = pz
                }
                for (k in 0 until 4) posePoint(corners, k * 3)
                rot(nx, ny, nz, tmp)
                if (tmp[2] <= 0.01f) continue
                val dot = max(0f, tmp[0] * light[0] + tmp[1] * light[1] + tmp[2] * light[2])
                val g = ((0.62f + 0.38f * dot) * 255f).toInt().coerceIn(0, 255)
                paint.colorFilter = LightingColorFilter(android.graphics.Color.rgb(g, g, g), 0)
                for (k in 0 until 4) {
                    rot(corners[k * 3], corners[k * 3 + 1], corners[k * 3 + 2], tmp)
                    verts[k * 2] = ox + tmp[0] * unit
                    verts[k * 2 + 1] = oy - tmp[1] * unit
                }
                val e = 0.02f
                val l = tx + e
                val r = tx + tw - e
                val t = ty + e
                val b = ty + th - e
                texs[0] = l; texs[1] = t
                texs[2] = r; texs[3] = t
                texs[4] = l; texs[5] = b
                texs[6] = r; texs[7] = b
                canvas.drawVertices(Canvas.VertexMode.TRIANGLES, 8, verts, 0, texs, 0, null, 0, indices, 0, 6, paint)
            }
        }

        // Pair order: head, body, right arm, left arm, right leg, left leg.
        fun limbAngle(p: Int): Float = when (p) {
            2 -> swing
            3 -> -swing
            4 -> -swing
            5 -> swing
            else -> 0f
        }
        fun pivotY(p: Int): Float = if (p == 2 || p == 3) 8f else -4f

        val order = (0 until 6).sortedBy { p ->
            val c = centerOf(boxes[p * 2].id, slim).copyOf()
            setPose(limbAngle(p), pivotY(p))
            posePoint(c, 0)
            rot(c[0], c[1], c[2], tmp)
            tmp[2]
        }
        for (p in order) {
            if (p in hidden) continue
            val base = boxes[p * 2]
            val over = boxes[p * 2 + 1]
            val c = centerOf(base.id, slim)
            setPose(limbAngle(p), pivotY(p))
            drawBox(base, c, 0f)
            if (showOverlay) drawBox(over, c, if (over.id == "hat") 0.5f else 0.25f)
        }
        paint.colorFilter = null
    }

    private fun set(vararg p: Float) {
        for (i in 0 until 12) corners[i] = p[i]
    }
}

/** Live 3D view of the current project's skin. */
@Composable
fun SkinModelView(
    state: AppState,
    rx: Float,
    ry: Float,
    zoom: Float,
    showOverlay: Boolean,
    modifier: Modifier = Modifier,
    hidden: Set<Int> = emptySet(),
    background: Int = 0xFF2C2C2A.toInt(),
    swing: Float = 0f
) {
    val bitmap = remember { Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888) }
    val paint = remember(bitmap) { SkinRenderer.newPaint(bitmap) }
    androidx.compose.foundation.Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE")
        val v = state.version
        bitmap.setPixels(state.composite(), 0, 64, 0, 0, 64, 64)
        val slim = state.slim
        if ((background ushr 24) == 0) {
            val sq = 12.dp.toPx()
            var yy = 0
            var row = 0
            while (yy * sq < size.height) {
                var xx = 0
                while (xx * sq < size.width) {
                    drawRect(
                        if ((xx + row) % 2 == 0) androidx.compose.ui.graphics.Color(0xFFD3D1C7) else androidx.compose.ui.graphics.Color(0xFFF1EFE8),
                        androidx.compose.ui.geometry.Offset(xx * sq, yy * sq),
                        androidx.compose.ui.geometry.Size(sq, sq)
                    )
                    xx++
                }
                yy++
                row++
            }
        } else {
            drawRect(androidx.compose.ui.graphics.Color(background))
        }
        drawIntoCanvas {
            SkinRenderer.draw(it.nativeCanvas, paint, slim, showOverlay, hidden, rx, ry, zoom, size.width, size.height, swing)
        }
    }
}
