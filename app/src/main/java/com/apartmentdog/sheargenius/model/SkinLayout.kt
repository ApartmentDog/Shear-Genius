package com.apartmentdog.sheargenius.model

enum class Part(val guide: Int) {
    HEAD(0xFFAFA9EC.toInt()),
    BODY(0xFF5DCAA5.toInt()),
    ARM(0xFFF0997B.toInt()),
    LEG(0xFF85B7EB.toInt())
}

enum class Face { TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK }

data class FacePos(val face: Face, val fx: Int, val fy: Int)

/** One cuboid of the Java skin UV layout. Side strip order: right, front, left, back. */
data class SkinBox(
    val id: String,
    val u: Int,
    val v: Int,
    val w: Int,
    val h: Int,
    val d: Int,
    val part: Part,
    val overlay: Boolean,
    val mirrorId: String
) {
    fun faceWidth(f: Face): Int = if (f == Face.RIGHT || f == Face.LEFT) d else w
    fun faceHeight(f: Face): Int = if (f == Face.TOP || f == Face.BOTTOM) d else h

    fun pixel(f: Face, fx: Int, fy: Int): Int {
        val x = when (f) {
            Face.TOP -> u + d + fx
            Face.BOTTOM -> u + d + w + fx
            Face.RIGHT -> u + fx
            Face.FRONT -> u + d + fx
            Face.LEFT -> u + d + w + fx
            Face.BACK -> u + 2 * d + w + fx
        }
        val y = if (f == Face.TOP || f == Face.BOTTOM) v + fy else v + d + fy
        return y * SkinLayout.SIZE + x
    }

    fun locate(x: Int, y: Int): FacePos? {
        val lx = x - u
        val ly = y - v
        if (lx < 0 || ly < 0) return null
        if (ly < d) {
            return when {
                lx >= d && lx < d + w -> FacePos(Face.TOP, lx - d, ly)
                lx >= d + w && lx < d + 2 * w -> FacePos(Face.BOTTOM, lx - d - w, ly)
                else -> null
            }
        }
        if (ly < d + h) {
            val fy = ly - d
            return when {
                lx < d -> FacePos(Face.RIGHT, lx, fy)
                lx < d + w -> FacePos(Face.FRONT, lx - d, fy)
                lx < 2 * d + w -> FacePos(Face.LEFT, lx - d - w, fy)
                lx < 2 * d + 2 * w -> FacePos(Face.BACK, lx - 2 * d - w, fy)
                else -> null
            }
        }
        return null
    }

    fun forEachPixel(action: (Face, Int, Int, Int) -> Unit) {
        for (f in Face.values()) {
            for (fy in 0 until faceHeight(f)) {
                for (fx in 0 until faceWidth(f)) {
                    action(f, fx, fy, pixel(f, fx, fy))
                }
            }
        }
    }
}

object SkinLayout {
    const val SIZE = 64
    const val COUNT = SIZE * SIZE
    private val ARM_IDS = listOf("rarm", "rsleeve", "larm", "lsleeve")

    private val classicBoxes: List<SkinBox> = build(false)
    private val slimBoxes: List<SkinBox> = build(true)
    private val classicMap: IntArray = computeMap(classicBoxes)
    private val slimMap: IntArray = computeMap(slimBoxes)

    fun boxes(slim: Boolean): List<SkinBox> = if (slim) slimBoxes else classicBoxes
    fun regionMap(slim: Boolean): IntArray = if (slim) slimMap else classicMap
    fun boxById(slim: Boolean, id: String): SkinBox = boxes(slim).first { it.id == id }

    private fun build(slim: Boolean): List<SkinBox> {
        val a = if (slim) 3 else 4
        return listOf(
            SkinBox("head", 0, 0, 8, 8, 8, Part.HEAD, false, "head"),
            SkinBox("hat", 32, 0, 8, 8, 8, Part.HEAD, true, "hat"),
            SkinBox("body", 16, 16, 8, 12, 4, Part.BODY, false, "body"),
            SkinBox("jacket", 16, 32, 8, 12, 4, Part.BODY, true, "jacket"),
            SkinBox("rarm", 40, 16, a, 12, 4, Part.ARM, false, "larm"),
            SkinBox("rsleeve", 40, 32, a, 12, 4, Part.ARM, true, "lsleeve"),
            SkinBox("larm", 32, 48, a, 12, 4, Part.ARM, false, "rarm"),
            SkinBox("lsleeve", 48, 48, a, 12, 4, Part.ARM, true, "rsleeve"),
            SkinBox("rleg", 0, 16, 4, 12, 4, Part.LEG, false, "lleg"),
            SkinBox("rpants", 0, 32, 4, 12, 4, Part.LEG, true, "lpants"),
            SkinBox("lleg", 16, 48, 4, 12, 4, Part.LEG, false, "rleg"),
            SkinBox("lpants", 0, 48, 4, 12, 4, Part.LEG, true, "rpants")
        )
    }

    private fun computeMap(list: List<SkinBox>): IntArray {
        val m = IntArray(COUNT) { -1 }
        list.forEachIndexed { index, box ->
            box.forEachPixel { _, _, _, i -> m[i] = index }
        }
        return m
    }

    /** Pixel index mirrored across the model's vertical center plane, or -1. */
    fun mirror(slim: Boolean, index: Int): Int {
        val bi = regionMap(slim)[index]
        if (bi < 0) return -1
        val box = boxes(slim)[bi]
        val p = box.locate(index % SIZE, index / SIZE) ?: return -1
        val target = boxById(slim, box.mirrorId)
        val face = when (p.face) {
            Face.RIGHT -> Face.LEFT
            Face.LEFT -> Face.RIGHT
            else -> p.face
        }
        return target.pixel(face, target.faceWidth(face) - 1 - p.fx, p.fy)
    }

    fun armsHaveContent(px: IntArray, slim: Boolean): Boolean {
        for (id in ARM_IDS) {
            var found = false
            boxById(slim, id).forEachPixel { _, _, _, i -> if ((px[i] ushr 24) != 0) found = true }
            if (found) return true
        }
        return false
    }

    /** Steve (4px) <-> Alex (3px) arms. Shrinking drops the last column, growing repeats it. */
    fun convertArms(src: IntArray, fromSlim: Boolean, toSlim: Boolean): IntArray {
        val out = src.copyOf()
        for (id in ARM_IDS) boxById(fromSlim, id).forEachPixel { _, _, _, i -> out[i] = 0 }
        for (id in ARM_IDS) {
            val ob = boxById(fromSlim, id)
            val nb = boxById(toSlim, id)
            nb.forEachPixel { f, fx, fy, i ->
                val sfx = minOf(fx, ob.faceWidth(f) - 1)
                out[i] = src[ob.pixel(f, sfx, fy)]
            }
        }
        return out
    }

    /** 64x32 legacy skin to 64x64: left limbs become mirrored copies of the right limbs. */
    fun upgradeLegacy(src: IntArray): IntArray {
        val out = IntArray(COUNT)
        for (i in 0 until SIZE * 32) out[i] = src[i]
        for (id in listOf("lleg", "larm")) {
            boxById(false, id).forEachPixel { _, _, _, i ->
                val m = mirror(false, i)
                if (m >= 0) out[i] = out[m]
            }
        }
        return out
    }

    /** Guess Alex if the right arm has paint but the classic-only columns are empty. */
    fun looksSlim(px: IntArray): Boolean {
        var armPaint = false
        var extraPaint = false
        for (y in 20 until 32) {
            for (x in 40 until 54) if ((px[y * SIZE + x] ushr 24) != 0) armPaint = true
            for (x in 54 until 56) if ((px[y * SIZE + x] ushr 24) != 0) extraPaint = true
        }
        return armPaint && !extraPaint
    }
}
