package com.apartmentdog.sheargenius.model

/**
 * Pure pixel operations on 64x64 ARGB skin arrays. Nothing here touches Android,
 * so it can be unit tested on a plain JVM.
 */
object PixelOps {

    /** Source-over blend of [s] atop [d], both ARGB. */
    fun blendOver(s: Int, d: Int): Int {
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

    /**
     * Layers flattened bottom to top with normal alpha blending.
     * [layers] pairs each layer's pixels with whether it is visible; index 0 is the bottom.
     */
    fun composite(layers: List<Pair<IntArray, Boolean>>): IntArray {
        val out = IntArray(SkinLayout.COUNT)
        for ((src, visible) in layers) {
            if (!visible) continue
            for (i in 0 until SkinLayout.COUNT) {
                val s = src[i]
                if ((s ushr 24) == 0) continue
                out[i] = blendOver(s, out[i])
            }
        }
        return out
    }

    /** Draws [top] onto [below] and returns the result; [below] is not modified. */
    fun mergeOnto(below: IntArray, top: IntArray): IntArray {
        val merged = below.copyOf()
        for (i in 0 until SkinLayout.COUNT) {
            if ((top[i] ushr 24) == 0) continue
            merged[i] = blendOver(top[i], merged[i])
        }
        return merged
    }

    /**
     * 4-way flood fill of [color] from [start], in place. The fill only spreads through pixels
     * that match the start pixel's color and belong to the same box in [regionMap].
     * Returns true if any pixel changed.
     */
    fun floodFill(pixels: IntArray, regionMap: IntArray, start: Int, color: Int): Boolean {
        val target = pixels[start]
        if (target == color) return false
        val region = regionMap[start]
        val size = SkinLayout.SIZE
        val stack = ArrayDeque<Int>()
        stack.addLast(start)
        var changed = false
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (pixels[i] != target || regionMap[i] != region) continue
            pixels[i] = color
            changed = true
            val x = i % size
            val y = i / size
            if (x > 0) stack.addLast(i - 1)
            if (x < size - 1) stack.addLast(i + 1)
            if (y > 0) stack.addLast(i - size)
            if (y < size - 1) stack.addLast(i + size)
        }
        return changed
    }
}
