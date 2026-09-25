package com.apartmentdog.sheargenius.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/** Tiny pixel-art icon: '#' = solid, 'o' = half-tone, anything else = empty. */
class PixelIcon(val rows: List<String>) {
    val w: Int = rows.maxOf { it.length }
    val h: Int = rows.size
    fun mirrored(): PixelIcon = PixelIcon(rows.map { it.padEnd(w, '.').reversed() })
}

@Composable
fun PixelIconView(icon: PixelIcon, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = minOf(size.width / icon.w, size.height / icon.h)
        val ox = (size.width - cell * icon.w) / 2f
        val oy = (size.height - cell * icon.h) / 2f
        val soft = color.copy(alpha = 0.5f)
        icon.rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                val c = when (ch) {
                    '#' -> color
                    'o' -> soft
                    else -> null
                }
                if (c != null) drawRect(c, Offset(ox + x * cell, oy + y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

object PixelIcons {
    val Pencil = PixelIcon(listOf(
        "........##",
        ".......#o#",
        "......#o#.",
        ".....#o#..",
        "....#o#...",
        "...#o#....",
        "..#o#.....",
        ".#o#......",
        ".##.......",
        "#........."
    ))
    val Line = PixelIcon(listOf(
        "........##",
        ".......##.",
        "......##..",
        ".....##...",
        "....##....",
        "...##.....",
        "..##......",
        ".##.......",
        "##........"
    ))
    val Eraser = PixelIcon(listOf(
        "..........",
        "##########",
        "#oooo#...#",
        "#oooo#...#",
        "#oooo#...#",
        "##########",
        "..........",
        "#.#.#....."
    ))
    val Bucket = PixelIcon(listOf(
        "....#.....",
        "...#.#....",
        "..#...#...",
        ".#ooooo#..",
        "#ooooooo#.",
        ".#ooooo#o.",
        "..#ooo#.o.",
        "...###..o.",
        "........o."
    ))
    val Dropper = PixelIcon(listOf(
        ".......###",
        "......####",
        ".....####.",
        ".....#o#..",
        "....#o#...",
        "...#o#....",
        "..#o#.....",
        ".#o#......",
        ".##.......",
        "o........."
    ))
    val Mirror = PixelIcon(listOf(
        "....##....",
        "#...##...#",
        "##..##..##",
        "#o#.##.#o#",
        "##..##..##",
        "#...##...#",
        "....##...."
    ))
    val Undo = PixelIcon(listOf(
        "..#.......",
        ".##.......",
        "#########.",
        ".##......#",
        "..#......#",
        ".........#",
        "....######"
    ))
    val Redo = Undo.mirrored()
    val Photo = PixelIcon(listOf(
        "##########",
        "#........#",
        "#.oo.....#",
        "#.oo...#.#",
        "#.....####",
        "#..#.#####",
        "##########"
    ))
    val Cube = PixelIcon(listOf(
        "....##....",
        "..##oo##..",
        "##oooooo##",
        "#.##oo##.#",
        "#...##...#",
        "#...##...#",
        "##..##..##",
        "..######..",
        "....##...."
    ))
    val Disk = PixelIcon(listOf(
        "#########.",
        "#.oooo.#.#",
        "#.oooo.#.#",
        "#.......##",
        "#........#",
        "#.######.#",
        "#.#....#.#",
        "#.#....#.#",
        "##########"
    ))
    val Plus = PixelIcon(listOf(
        "...##...",
        "...##...",
        "########",
        "########",
        "...##...",
        "...##..."
    ))
    val Shade = PixelIcon(listOf(
        "####oo..",
        "####oo..",
        "###ooo..",
        "###oo...",
        "##ooo...",
        "##oo...."
    ))
    val Move = PixelIcon(listOf(
        "....#....",
        "...###...",
        "....#....",
        ".#..#..#.",
        "#########",
        ".#..#..#.",
        "....#....",
        "...###...",
        "....#...."
    ))
    val Minus = PixelIcon(listOf(
        "........",
        "........",
        "########",
        "########",
        "........",
        "........"
    ))
    val Wand = PixelIcon(listOf(
        "........#.",
        ".......#o#",
        "........#.",
        "......#...",
        ".....#....",
        "....#.....",
        "...#......",
        "..#.......",
        ".#........",
        "#........."
    ))
}
