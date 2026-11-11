package com.kelly3d.ohnoitsneilgame.editor

import com.kelly3d.ohnoitsneilgame.maps.*
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Deterministic code-drawn replacement tiles; these are not extracted original artwork. */
internal object ForestAssets {
    fun atlas(): BufferedImage {
        val atlas = BufferedImage(128, 64, BufferedImage.TYPE_INT_ARGB)
        fun noise(x: Int, y: Int, seed: Int): Int {
            val n = (x * 374761393 + y * 668265263 + seed * 1442695041)
            return ((n xor (n ushr 13)) and 0x7fffffff)
        }
        fun rgb(r: Int, g: Int, b: Int) = Color(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255)).rgb
        for (id in 0..31) for (y in 0..15) for (x in 0..15) {
            val n = noise(x, y, id)
            val shade = n % 31 - 15
            val rockEdge = (x + (y / 7) * 7) % 13 < 2 || y % 8 == 0
            val bark = (x + y / 5) % 5 == 0
            var color = when (id) {
                0 -> if (y < 4 + n % 3) rgb(72 + shade, 130 + shade, 16) else rgb(101 + shade, 49 + shade / 2, 10)
                1, 2, 3 -> if (rockEdge) rgb(42, 23, 9) else rgb(146 + shade * 2, 72 + shade, 18 + shade / 3)
                4, 5, 6, 7, 13, 14, 28, 29 -> if (bark) rgb(50, 23, 7) else rgb(127 + shade * 2, 58 + shade, 12)
                8, 9, 10, 11, 12, 15, 27 -> {
                    val vein = (x + y) % 5 == 0
                    if (vein) rgb(102 + shade, 135 + shade, 10) else rgb(17 + n % 36, 58 + n % 58, 13)
                }
                16, 17, 18, 19 -> rgb(5, 7, 4)
                20, 21, 22, 23 -> if ((x + y) % 7 == 0 || (x - y + 16) % 7 == 0) rgb(6, 14, 25)
                    else rgb(12, 40 + n % 60, 130 + n % 80)
                24, 25 -> rgb(112 + shade, 53 + shade / 2, 9)
                else -> 0
            }
            if (id == 5 && x < 2 && n % 3 == 0 || id == 6 && x > 13 && n % 3 == 0) color = 0
            if (id in 9..12) {
                val px = if (id == 9 || id == 11) 15 - x else x
                val py = if (id == 9 || id == 10) 15 - y else y
                if (px * px + py * py > 240 + n % 40) color = 0
            }
            if (id == 13 && kotlin.math.abs(x + y - 15) > 5 ||
                id == 14 && kotlin.math.abs(x - y) > 5) color = 0
            if (id in 16..19) {
                val edge = if (id == 16 || id == 18) x < 3 else x > 12
                if (edge) color = rgb(110 + shade, 50, 10)
                if (id == 18 && x + y < 11 || id == 19 && 15 - x + y < 11) color = rgb(135 + shade, 64, 14)
            }
            if (id in 20..23 && (x < 2 || x > 13 || y < 2 || y > 13)) color = rgb(116, 56, 14)
            if (id == 24 && y in 4..9 && x in 2..13) color = rgb(10, 10, 4)
            if (id == 25 && y in 5..10 && x in 3..12) color = rgb(204, 116, 26)
            if (id == 29 && y in 5..12 && x in 1..14) color =
                if (y < 8 && x % 4 < 2) rgb(222, 179, 90) else rgb(15, 10, 4)
            if (id == 26) color = if (y > 10 && (x + y) % 4 < 2) rgb(77 + shade, 129 + shade, 16) else 0
            atlas.setRGB(id % 8 * 16 + x, id / 8 * 16 + y, color)
        }
        return atlas
    }

    fun render(map: TileMap, atlas: BufferedImage): BufferedImage {
        val image = BufferedImage(map.width * 16, map.height * 16, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color(0, 122, 244); g.fillRect(0, 0, image.width, image.height)
        for (layer in MapLayer.entries) for (y in 0 until map.height) for (x in 0 until map.width) {
            val id = map.tile(layer, x, y)
            if (id >= 0) g.drawImage(atlas, x*16, y*16, x*16+16, y*16+16,
                map.sourceX(id), map.sourceY(id), map.sourceX(id)+16, map.sourceY(id)+16, null)
        }
        g.dispose()
        return image
    }

    /** Initial authoring/export only: refuses to overwrite subsequent editor work. */
    fun export(directory: File) {
        val map = ForestReconstruction.create()
        check(map.validationErrors().isEmpty())
        val outputs = listOf("forest.neilmap", "forest_tiles.png", "forest_preview.png").map { File(directory, it) }
        check(outputs.none { it.exists() }) { "Export destination already contains forest assets; choose an empty directory." }
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create export directory" }
        val atlas = atlas()
        outputs[0].writeText(TileMapCodec.encode(map))
        check(ImageIO.write(atlas, "png", outputs[1]))
        check(ImageIO.write(render(map, atlas), "png", outputs[2]))
        check(TileMapCodec.encode(TileMapCodec.decode(outputs[0].readText())) == outputs[0].readText())
        println("Exported three-room forest reconstruction to ${directory.absolutePath}")
    }
}
