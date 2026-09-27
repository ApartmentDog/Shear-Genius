package com.apartmentdog.sheargenius.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.MediaStore
import com.apartmentdog.sheargenius.AppState

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

    /** Turnaround with a name label under each panel and the project name as a header. */
    fun proofSheet(state: AppState, background: Int?, panelW: Int = 512, panelH: Int = 768): Bitmap {
        val headerH = (panelH * 0.09f).toInt()
        val labelH = (panelH * 0.09f).toInt()
        val out = Bitmap.createBitmap(panelW * 4, headerH + panelH + labelH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (background != null) c.drawColor(background) else c.drawColor(android.graphics.Color.WHITE)
        val header = android.graphics.Paint().apply {
            isAntiAlias = true
            color = 0xFF2C2C2A.toInt()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = headerH * 0.6f
        }
        c.drawText(state.projectName, out.width / 2f, headerH * 0.7f, header)
        c.save()
        c.translate(0f, headerH.toFloat())
        drawTurnaroundPanels(c, state, panelW, panelH, listOf("Front", "Right side", "Back", "Left side"))
        c.restore()
        if (state.watermarkOn && state.watermarkText.isNotBlank()) drawWatermark(c, state.watermarkText, out.width, out.height)
        return out
    }

    private fun drawTurnaroundPanels(c: Canvas, state: AppState, panelW: Int, panelH: Int, labels: List<String>?) {
        val paint = skinPaint(state)
        val angles = floatArrayOf(0f, (Math.PI / 2).toFloat(), Math.PI.toFloat(), (-Math.PI / 2).toFloat())
        val labelPaint = if (labels != null) android.graphics.Paint().apply {
            isAntiAlias = true
            color = 0xFF2C2C2A.toInt()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = panelH * 0.06f
        } else null
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
