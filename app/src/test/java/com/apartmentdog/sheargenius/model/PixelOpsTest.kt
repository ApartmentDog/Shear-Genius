package com.apartmentdog.sheargenius.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelOpsTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val black = 0xFF000000.toInt()

    // ---- blending

    @Test
    fun opaqueSourceReplacesDestination() {
        assertEquals(red, PixelOps.blendOver(red, blue))
    }

    @Test
    fun transparentSourceKeepsDestination() {
        assertEquals(blue, PixelOps.blendOver(0x00FF0000, blue))
    }

    @Test
    fun anythingOverTransparentIsUnchanged() {
        val half = 0x80FF0000.toInt()
        assertEquals(half, PixelOps.blendOver(half, 0))
    }

    @Test
    fun halfWhiteOverBlackIsMidGray() {
        assertEquals(0xFF808080.toInt(), PixelOps.blendOver(0x80FFFFFF.toInt(), black))
    }

    @Test
    fun twoTranslucentColorsBlend() {
        assertEquals(0xBFAA0055.toInt(), PixelOps.blendOver(0x80FF0000.toInt(), 0x800000FF.toInt()))
    }

    // ---- compositing

    @Test
    fun compositeStacksBottomToTop() {
        val bottom = IntArray(SkinLayout.COUNT) { red }
        val top = IntArray(SkinLayout.COUNT)
        top[5] = blue
        val out = PixelOps.composite(listOf(bottom to true, top to true))
        assertEquals(blue, out[5])
        assertEquals(red, out[6])
    }

    @Test
    fun compositeSkipsHiddenLayers() {
        val bottom = IntArray(SkinLayout.COUNT) { red }
        val top = IntArray(SkinLayout.COUNT) { blue }
        val out = PixelOps.composite(listOf(bottom to true, top to false))
        assertTrue(out.all { it == red })
    }

    @Test
    fun compositeOfNothingIsTransparent() {
        assertTrue(PixelOps.composite(emptyList()).all { it == 0 })
    }

    @Test
    fun mergeOntoMatchesComposite() {
        val below = IntArray(SkinLayout.COUNT) { if (it % 3 == 0) red else 0 }
        val top = IntArray(SkinLayout.COUNT) { if (it % 2 == 0) 0x80FFFFFF.toInt() else 0 }
        val merged = PixelOps.mergeOnto(below, top)
        assertArrayEquals(PixelOps.composite(listOf(below to true, top to true)), merged)
    }

    @Test
    fun mergeOntoDoesNotModifyInputs() {
        val below = IntArray(SkinLayout.COUNT)
        val top = IntArray(SkinLayout.COUNT) { red }
        PixelOps.mergeOnto(below, top)
        assertTrue(below.all { it == 0 })
    }

    // ---- flood fill

    private val map = SkinLayout.regionMap(false)
    private val head = SkinLayout.boxById(false, "head")
    private val hat = SkinLayout.boxById(false, "hat")

    @Test
    fun fillCoversTheWholeBoxAndNothingElse() {
        val px = IntArray(SkinLayout.COUNT)
        assertTrue(PixelOps.floodFill(px, map, head.pixel(Face.FRONT, 3, 3), red))
        var headCount = 0
        head.forEachPixel { _, _, _, i -> assertEquals(red, px[i]); headCount++ }
        assertEquals(headCount, px.count { it == red })
        hat.forEachPixel { _, _, _, i -> assertEquals(0, px[i]) }
    }

    @Test
    fun fillStopsAtDifferentColors() {
        val px = IntArray(SkinLayout.COUNT)
        // Wall off one pixel of the head front face.
        val center = head.pixel(Face.FRONT, 4, 4)
        for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
            px[head.pixel(Face.FRONT, 4 + dx, 4 + dy)] = black
        }
        assertTrue(PixelOps.floodFill(px, map, center, red))
        assertEquals(1, px.count { it == red })
    }

    @Test
    fun fillWithTheSameColorChangesNothing() {
        val px = IntArray(SkinLayout.COUNT)
        val start = head.pixel(Face.FRONT, 0, 0)
        px[start] = red
        assertFalse(PixelOps.floodFill(px, map, start, red))
    }
}
