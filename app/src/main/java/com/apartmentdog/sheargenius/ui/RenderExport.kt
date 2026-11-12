package com.apartmentdog.sheargenius.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.net.Uri
import android.provider.MediaStore
import com.apartmentdog.sheargenius.AppState
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Offscreen renders of the 3D model, saved to Pictures/Shear Genius. */
object RenderExport {
    private fun skinPaint(state: AppState): android.graphics.Paint {
        val skin = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        skin.setPixels(state.composite(), 0, 64, 0, 0, 64, 64)
        return SkinRenderer.newPaint(skin)
    }

    /** Current view as a square image. [background] null means transparent. */
    fun view(state: AppState, background: Int?, size: Int = 1024): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (background != null) c.drawColor(background)
        SkinRenderer.draw(
            c, skinPaint(state), state.slim, state.previewOverlay, state.previewHidden,
            state.previewRx, state.previewRy, state.previewZoom, size.toFloat(), size.toFloat(),
            state.previewSwing
        )
        return out
    }

    /** Front, right, back and left views side by side. */
    fun turnaround(state: AppState, background: Int?, panelW: Int = 512, panelH: Int = 768): Bitmap {
        val out = Bitmap.createBitmap(panelW * 4, panelH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (background != null) c.drawColor(background)
        drawTurnaroundPanels(c, state, panelW, panelH, null)
        if (state.watermarkOn && state.watermarkText.isNotBlank()) drawWatermark(c, state.watermarkText, out.width, out.height)
        return out
    }

    /**
     * Proof sheet, left to right: head views (left, right, top) stacked in a column,
     * the labelled four-view turnaround, and an optional [reference] image.
     * The project name is the header. [bgImage], if given, is center-cropped to fill
     * the whole sheet; otherwise [background] is used, or white when that is null.
     */
    fun proofSheet(
        state: AppState,
        background: Int?,
        reference: Bitmap? = null,
        bgImage: Bitmap? = null,
        panelW: Int = 512,
        panelH: Int = 768
    ): Bitmap {
        val headerH = (panelH * 0.09f).toInt()
        val footerH = (panelH * 0.09f).toInt()
        val gap = (panelH * 0.03f).toInt()
        val headSide = panelH / 3
        val turnW = panelW * 4
        val refW = if (reference != null && reference.height > 0) {
            (panelH * reference.width.toFloat() / reference.height).toInt().coerceIn(panelH / 2, panelH * 3 / 2)
        } else 0
        val width = headSide + gap + turnW + (if (refW > 0) gap + refW else 0)
        val out = Bitmap.createBitmap(width, headerH + panelH + footerH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)

        when {
            bgImage != null -> drawCover(c, bgImage, out.width, out.height)
            background != null -> c.drawColor(background)
            else -> c.drawColor(android.graphics.Color.WHITE)
        }

        val header = textPaint(headerH * 0.6f)
        c.drawText(state.projectName, out.width / 2f, headerH * 0.7f, header)

        c.save()
        c.translate(0f, headerH.toFloat())

        // Head column
        val paint = skinPaint(state)
        val headLabel = textPaint(panelH * 0.04f)
        val halfPi = (Math.PI / 2).toFloat()
        val headViews = listOf(
            Triple("Head left", 0f, -halfPi),
            Triple("Head right", 0f, halfPi),
            Triple("Head top", halfPi, 0f)
        )
        headViews.forEachIndexed { i, (label, rx, ry) ->
            c.save()
            c.translate(0f, (i * headSide).toFloat())
            drawHeadView(c, paint, state, headSide, rx, ry)
            c.drawText(label, headSide / 2f, headSide * 0.95f, headLabel)
            c.restore()
        }

        // Turnaround
        c.save()
        c.translate((headSide + gap).toFloat(), 0f)
        drawTurnaroundPanels(c, state, panelW, panelH, listOf("Front", "Right side", "Back", "Left side"))
        c.restore()

        // Reference
        if (reference != null && refW > 0) {
            val left = (headSide + gap + turnW + gap).toFloat()
            val areaH = panelH * 0.88f
            val scale = min(refW / reference.width.toFloat(), areaH / reference.height)
            val w = reference.width * scale
            val h = reference.height * scale
            val l = left + (refW - w) / 2f
            val t = (areaH - h) / 2f
            c.drawBitmap(reference, null, RectF(l, t, l + w, t + h), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            c.drawText("Reference", left + refW / 2f, panelH * 0.95f, textPaint(panelH * 0.06f))
        }
        c.restore()

        if (state.watermarkOn && state.watermarkText.isNotBlank()) drawWatermark(c, state.watermarkText, out.width, out.height)
        return out
    }

    /** Head only, framed to fill a [side] x [side] square, leaving room for a label underneath. */
    private fun drawHeadView(c: Canvas, paint: android.graphics.Paint, state: AppState, side: Int, rx: Float, ry: Float) {
        val zoom = 2.6f
        val unit = side / 40f * zoom
        c.save()
        // The head's center sits 12 units above the model origin; bring it to the middle.
        c.translate(0f, 12f * cos(rx) * unit - side * 0.06f)
        SkinRenderer.draw(
            c, paint, state.slim, state.previewOverlay, setOf(1, 2, 3, 4, 5),
            rx, ry, zoom, side.toFloat(), side.toFloat()
        )
        c.restore()
    }

    /** Scales [img] to cover a [w] x [h] area, cropping the overflow evenly. */
    private fun drawCover(c: Canvas, img: Bitmap, w: Int, h: Int) {
        if (img.width <= 0 || img.height <= 0) return
        val scale = max(w / img.width.toFloat(), h / img.height.toFloat())
        val dw = img.width * scale
        val dh = img.height * scale
        val l = (w - dw) / 2f
        val t = (h - dh) / 2f
        c.drawBitmap(img, null, RectF(l, t, l + dw, t + dh), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    }

    /** Dark centered text with a soft light halo so it stays readable on image backgrounds. */
    private fun textPaint(size: Float) = android.graphics.Paint().apply {
        isAntiAlias = true
        color = 0xFF2C2C2A.toInt()
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = size
        setShadowLayer(size * 0.18f, 0f, 0f, android.graphics.Color.argb(200, 255, 255, 255))
    }

    private fun drawTurnaroundPanels(c: Canvas, state: AppState, panelW: Int, panelH: Int, labels: List<String>?) {
        val paint = skinPaint(state)
        val angles = floatArrayOf(0f, (Math.PI / 2).toFloat(), Math.PI.toFloat(), (-Math.PI / 2).toFloat())
        val labelPaint = if (labels != null) textPaint(panelH * 0.06f) else null
        for (i in 0 until 4) {
            c.save()
            c.translate((i * panelW).toFloat(), 0f)
            SkinRenderer.draw(
                c, paint, state.slim, state.previewOverlay, state.previewHidden,
                0.12f, angles[i], 1f, panelW.toFloat(), panelH.toFloat()
            )
            labelPaint?.let { c.drawText(labels!![i], panelW / 2f, panelH * 0.95f, it) }
            c.restore()
        }
    }

    private fun drawWatermark(c: Canvas, text: String, w: Int, h: Int) {
        val size = h * 0.028f
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(160, 255, 255, 255)
            textAlign = android.graphics.Paint.Align.RIGHT
            textSize = size
            setShadowLayer(size * 0.25f, 0f, 0f, android.graphics.Color.argb(160, 0, 0, 0))
        }
        c.drawText(text, w - size * 0.8f, h - size * 0.8f, paint)
    }

    fun save(context: Context, bitmap: Bitmap, baseName: String): Uri? {
        val safe = baseName.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "render" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$safe-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Shear Genius")
        }
        return try {
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            uri
        } catch (e: Exception) {
            null
        }
    }

    fun share(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share render"))
    }
}
