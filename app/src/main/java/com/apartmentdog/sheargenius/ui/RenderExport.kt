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
        val paint = skinPaint(state)
        val angles = floatArrayOf(0f, (Math.PI / 2).toFloat(), Math.PI.toFloat(), (-Math.PI / 2).toFloat())
        for (i in 0 until 4) {
            c.save()
            c.translate((i * panelW).toFloat(), 0f)
            SkinRenderer.draw(
                c, paint, state.slim, state.previewOverlay, state.previewHidden,
                0.12f, angles[i], 1f, panelW.toFloat(), panelH.toFloat()
            )
            c.restore()
        }
        return out
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
