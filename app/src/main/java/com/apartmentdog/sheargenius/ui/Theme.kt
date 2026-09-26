package com.apartmentdog.sheargenius.ui

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

object Blocky {
    val Panel = Color(0xFFC6C6C6)
    val PanelLight = Color(0xFFFFFFFF)
    val PanelDark = Color(0xFF555555)
    val Button = Color(0xFF8B8B8B)
    val ButtonLight = Color(0xFFC6C6C6)
    val ButtonDark = Color(0xFF373737)
    val SlotSelected = Color(0xFFA8A8A8)
    val Green = Color(0xFF4F8A2F)
    val GreenLight = Color(0xFF7FCC52)
    val GreenDark = Color(0xFF2E5A1A)
    val Text = Color(0xFF404040)
    val IconDark = Color(0xFF2C2C2A)
    val Disabled = Color(0xFFB0B0B0)
    val Outline = Color.Black
}

val LocalPixelFont = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

@Composable
fun rememberPixelFont(): FontFamily {
    val context = LocalContext.current
    return remember {
        try {
            FontFamily(Typeface.createFromAsset(context.assets, "fonts/pixelify.ttf"))
        } catch (e: Exception) {
            FontFamily.Monospace
        }
    }
}

@Composable
fun PixelText(
    text: String,
    size: TextUnit = 14.sp,
    color: Color = Blocky.Text,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    shadow: Boolean = false
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontFamily = LocalPixelFont.current,
        maxLines = maxLines,
        lineHeight = lineHeight,
        textAlign = textAlign,
        overflow = TextOverflow.Ellipsis,
        style = if (shadow) {
            LocalTextStyle.current.copy(shadow = Shadow(Color(0x99000000), Offset(3f, 3f), 0f))
        } else {
            LocalTextStyle.current
        }
    )
}

object Textures {
    private val stoneColors = intArrayOf(
        0xFF5A5A5A.toInt(), 0xFF535353.toInt(), 0xFF4C4C4C.toInt(), 0xFF606060.toInt(), 0xFF575757.toInt()
    )
    private val grassColors = intArrayOf(0xFF5E9E3A.toInt(), 0xFF4F8A2F.toInt(), 0xFF6DB443.toInt())
    private val dirtColors = intArrayOf(
        0xFF79553A.toInt(), 0xFF6B4A30.toInt(), 0xFF8B6040.toInt(), 0xFF593D29.toInt()
    )

    val stone: ImageBitmap by lazy {
        tile(7) { _, _, r -> stoneColors[r.nextInt(stoneColors.size)] }
    }

    val grass: ImageBitmap by lazy {
        val r = Random(11)
        val depth = IntArray(16) {
            3 + (if (r.nextFloat() < 0.4f) 1 else 0) + (if (r.nextFloat() < 0.2f) 1 else 0)
        }
        tile(12) { x, y, rr ->
            if (y < depth[x]) grassColors[rr.nextInt(grassColors.size)] else dirtColors[rr.nextInt(dirtColors.size)]
        }
    }

    /**
     * Original soft woven texture: each row is short horizontal strands in five
     * close shades, with row starts offset to give a gentle diagonal flow.
     */
    val wool: ImageBitmap by lazy { woolTile(0xFFEB781E.toInt()) }

    fun woolTile(base: Int): ImageBitmap {
        val r = Random(21)
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(base, hsv)
        val shades = IntArray(5) { i ->
            val step = i - 2
            val c = floatArrayOf(
                hsv[0],
                (hsv[1] - step * 0.02f).coerceIn(0f, 1f),
                (hsv[2] + step * 0.035f).coerceIn(0f, 1f)
            )
            android.graphics.Color.HSVToColor(c)
        }
        val weights = intArrayOf(1, 3, 4, 3, 1)
        val lengths = intArrayOf(2, 3, 3, 4, 5)
        val b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (y in 0 until 16) {
            var x = (y * 3) % 16
            var filled = 0
            while (filled < 16) {
                val len = lengths[r.nextInt(lengths.size)]
                var pick = r.nextInt(12)
                var shade = 0
                while (pick >= weights[shade]) {
                    pick -= weights[shade]
                    shade++
                }
                for (k in 0 until len) {
                    if (filled >= 16) break
                    b.setPixel((x + k) % 16, y, shades[shade])
                    filled++
                }
                x += len
            }
        }
        return b.asImageBitmap()
    }

    private fun tile(seed: Int, fn: (Int, Int, Random) -> Int): ImageBitmap {
        val r = Random(seed)
        val b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (y in 0 until 16) for (x in 0 until 16) b.setPixel(x, y, fn(x, y, r))
        return b.asImageBitmap()
    }
}

/** Repeats a pixel texture with hard edges. */
fun Modifier.tiled(tile: ImageBitmap, tileSize: Dp): Modifier = this.drawBehind {
    val t = tileSize.roundToPx().coerceAtLeast(1)
    var y = 0
    while (y < size.height) {
        var x = 0
        while (x < size.width) {
            drawImage(
                image = tile,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(tile.width, tile.height),
                dstOffset = IntOffset(x, y),
                dstSize = IntSize(t, t),
                filterQuality = FilterQuality.None
            )
            x += t
        }
        y += t
    }
}

/** Repeats a non-square pixel texture; [texel] is the on-screen size of one texture pixel. */
fun Modifier.pixelTiled(tile: ImageBitmap, texel: Dp): Modifier = this.drawBehind {
    val tw = (texel.toPx() * tile.width).toInt().coerceAtLeast(1)
    val th = (texel.toPx() * tile.height).toInt().coerceAtLeast(1)
    var y = 0
    while (y < size.height) {
        var x = 0
        while (x < size.width) {
            drawImage(
                image = tile,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(tile.width, tile.height),
                dstOffset = IntOffset(x, y),
                dstSize = IntSize(tw, th),
                filterQuality = FilterQuality.None
            )
            x += tw
        }
        y += th
    }
}

/** Blocky bevel: light top-left edge, dark bottom-right edge, optional outline. */
fun Modifier.bevel(fill: Color, light: Color, dark: Color, width: Dp, outline: Color? = Blocky.Outline): Modifier =
    this.drawBehind {
        val o = if (outline != null) 2.dp.toPx() else 0f
        if (outline != null) drawRect(outline)
        val iw = size.width - 2 * o
        val ih = size.height - 2 * o
        val w = width.toPx()
        drawRect(fill, Offset(o, o), Size(iw, ih))
        drawRect(light, Offset(o, o), Size(iw, w))
        drawRect(light, Offset(o, o), Size(w, ih))
        drawRect(dark, Offset(o, o + ih - w), Size(iw, w))
        drawRect(dark, Offset(o + iw - w, o), Size(w, ih))
    }

fun Modifier.insetFrame(): Modifier =
    this.bevel(Blocky.IconDark, Blocky.ButtonDark, Color.White, 2.dp, null).padding(2.dp)
