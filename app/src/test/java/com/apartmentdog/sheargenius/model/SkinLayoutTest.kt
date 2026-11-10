package com.apartmentdog.sheargenius.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SkinLayoutTest {

    private fun area(b: SkinBox): Int = 2 * b.w * b.d + 2 * (b.w + b.d) * b.h

    @Test
    fun boxesStayInsideTheTexture() {
        for (slim in listOf(false, true)) {
            for (b in SkinLayout.boxes(slim)) {
                b.forEachPixel { _, _, _, i ->
                    assertTrue("${b.id} (slim=$slim) writes outside 64x64", i in 0 until SkinLayout.COUNT)
                }
            }
        }
    }

    @Test
    fun boxesDoNotOverlap() {
        for (slim in listOf(false, true)) {
            val mapped = SkinLayout.regionMap(slim).count { it >= 0 }
            val total = SkinLayout.boxes(slim).sumOf { area(it) }
            assertEquals("boxes overlap (slim=$slim)", total, mapped)
        }
    }

    @Test
    fun locateIsTheInverseOfPixel() {
        for (slim in listOf(false, true)) {
            for (b in SkinLayout.boxes(slim)) {
                b.forEachPixel { f, fx, fy, i ->
                    assertEquals(FacePos(f, fx, fy), b.locate(i % SkinLayout.SIZE, i / SkinLayout.SIZE))
                }
            }
        }
    }

    @Test
    fun mirrorTwiceReturnsTheSamePixel() {
        for (slim in listOf(false, true)) {
            for (i in 0 until SkinLayout.COUNT) {
                val m = SkinLayout.mirror(slim, i)
                if (SkinLayout.regionMap(slim)[i] < 0) {
                    assertEquals(-1, m)
                } else {
                    assertTrue("pixel $i has no mirror (slim=$slim)", m >= 0)
                    assertEquals("mirror is not symmetric at $i (slim=$slim)", i, SkinLayout.mirror(slim, m))
                }
            }
        }
    }

    @Test
    fun mirrorSwapsLeftAndRightLimbs() {
        val rarm = SkinLayout.boxById(false, "rarm")
        val larm = SkinLayout.boxById(false, "larm")
        val front = rarm.pixel(Face.FRONT, 0, 0)
        assertEquals(larm.pixel(Face.FRONT, larm.w - 1, 0), SkinLayout.mirror(false, front))
        val outer = rarm.pixel(Face.RIGHT, 1, 5)
        assertEquals(larm.pixel(Face.LEFT, larm.d - 2, 5), SkinLayout.mirror(false, outer))
    }

    private fun randomOnRegions(slim: Boolean, seed: Int): IntArray {
        val r = Random(seed)
        val map = SkinLayout.regionMap(slim)
        return IntArray(SkinLayout.COUNT) { if (map[it] >= 0) r.nextInt() or (0xFF shl 24) else 0 }
    }

    @Test
    fun slimToClassicAndBackIsLossless() {
        val slim = randomOnRegions(true, 1)
        val roundTrip = SkinLayout.convertArms(SkinLayout.convertArms(slim, true, false), false, true)
        assertTrue(slim.contentEquals(roundTrip))
    }

    @Test
    fun convertingArmsLeavesTheRestAlone() {
        val px = randomOnRegions(false, 2)
        val out = SkinLayout.convertArms(px, false, true)
        for (id in listOf("head", "hat", "body", "jacket", "rleg", "lleg", "rpants", "lpants")) {
            SkinLayout.boxById(false, id).forEachPixel { _, _, _, i -> assertEquals(px[i], out[i]) }
        }
    }

    @Test
    fun armsHaveContentDetectsPaint() {
        val px = IntArray(SkinLayout.COUNT)
        assertFalse(SkinLayout.armsHaveContent(px, false))
        px[SkinLayout.boxById(false, "lsleeve").pixel(Face.FRONT, 0, 0)] = 0xFF00FF00.toInt()
        assertTrue(SkinLayout.armsHaveContent(px, false))
    }

    @Test
    fun legacyUpgradeKeepsTopHalfAndMirrorsLimbs() {
        val legacy = IntArray(SkinLayout.COUNT)
        val r = Random(3)
        for (i in 0 until SkinLayout.SIZE * 32) legacy[i] = r.nextInt() or (0xFF shl 24)
        val up = SkinLayout.upgradeLegacy(legacy)
        for (i in 0 until SkinLayout.SIZE * 32) assertEquals(legacy[i], up[i])
        for (id in listOf("lleg", "larm")) {
            SkinLayout.boxById(false, id).forEachPixel { _, _, _, i ->
                assertEquals(up[SkinLayout.mirror(false, i)], up[i])
            }
        }
        SkinLayout.boxById(false, "lsleeve").forEachPixel { _, _, _, i -> assertEquals(0, up[i]) }
    }

    @Test
    fun looksSlimGuessesFromTheArmColumns() {
        val px = IntArray(SkinLayout.COUNT)
        assertFalse("blank skin", SkinLayout.looksSlim(px))
        px[20 * SkinLayout.SIZE + 44] = 0xFFFFFFFF.toInt()
        assertTrue("paint only in slim columns", SkinLayout.looksSlim(px))
        px[20 * SkinLayout.SIZE + 54] = 0xFFFFFFFF.toInt()
        assertFalse("paint in classic-only column", SkinLayout.looksSlim(px))
    }
}
