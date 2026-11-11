package com.kelly3d.ohnoitsneilgame.editor

import com.kelly3d.ohnoitsneilgame.maps.*
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** High-resolution code-drawn replacement tiles to match modern character graphics. */
internal object ForestAssets {
    const val TILE_SIZE = 32

    fun atlas(): BufferedImage {
        val atlas = BufferedImage(TILE_SIZE * 8, TILE_SIZE * 4, BufferedImage.TYPE_INT_ARGB)
        fun noise(x: Int, y: Int, seed: Int): Int {
            val n = (x * 374761393 + y * 668265263 + seed * 1442695041)
            return ((n xor (n ushr 13)) and 0x7fffffff)
        }
        fun rgb(r: Int, g: Int, b: Int) = Color(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255)).rgb

        for (id in 0..31) {
            val tileOriginX = (id % 8) * TILE_SIZE
            val tileOriginY = (id / 8) * TILE_SIZE

            for (y in 0 until TILE_SIZE) {
                for (x in 0 until TILE_SIZE) {
                    val n = noise(x, y, id)
                    val shade = n % 21 - 10
                    val rockEdge = (x / 2 + (y / 14) * 14) % 13 < 2 || y % 16 < 2
                    val bark = (x / 2 + (y / 2) / 5) % 5 == 0

                    var color = when (id) {
                        0 -> if (y < 8 + n % 4) {
                            val blade = (x % 4 == 0 && y < 6)
                            if (blade) rgb(110 + shade, 190 + shade, 35)
                            else rgb(72 + shade, 145 + shade, 20)
                        } else {
                            rgb(101 + shade, 52 + shade / 2, 14)
                        }
                        1, 2, 3 -> if (rockEdge) rgb(45, 25, 12) else rgb(155 + shade * 2, 78 + shade, 22 + shade / 3)
                        4, 5, 6, 7, 13, 14, 28, 29 -> if (bark) rgb(55, 26, 9) else rgb(135 + shade * 2, 64 + shade, 16)
                        8, 9, 10, 11, 12, 15, 27 -> {
                            val leafHighlight = (x + y) % 7 == 0
                            if (leafHighlight) rgb(115 + shade, 165 + shade, 25)
                            else rgb(24 + n % 36, 78 + n % 48, 20)
                        }
                        16, 17, 18, 19 -> {
                            val seam = y % 8 == 0 || x % 16 == 0
                            if (seam) rgb(35, 18, 8) else rgb(125 + shade, 62 + shade / 2, 18)
                        }
                        20, 21, 22, 23 -> {
                            val leadLine = (x % 8 == 0) || (y % 8 == 0)
                            if (leadLine) rgb(15, 22, 35)
                            else rgb(20 + n % 40, 90 + n % 60, 180 + n % 75)
                        }
                        24, 25 -> {
                            val frame = x < 4 || x > 27 || y < 4
                            if (frame) rgb(75, 36, 12) else rgb(180 + shade, 95 + shade / 2, 25)
                        }
                        26 -> {
                            val isFlower = (x in 10..14 && y in 8..12) || (x in 20..24 && y in 14..18)
                            if (isFlower) rgb(245, 200, 60)
                            else if (y > 20 && (x + y) % 4 < 2) rgb(80 + shade, 150 + shade, 22)
                            else 0
                        }
                        else -> 0
                    }

                    if (id == 5 && x < 4 && n % 3 == 0 || id == 6 && x > 27 && n % 3 == 0) color = 0
                    if (id in 9..12) {
                        val px = if (id == 9 || id == 11) 31 - x else x
                        val py = if (id == 9 || id == 10) 31 - y else y
                        if (px * px + py * py > 900 + n % 80) color = 0
                    }
                    if (id == 13 && kotlin.math.abs(x + y - 31) > 10 ||
                        id == 14 && kotlin.math.abs(x - y) > 10) color = 0
                    if (id in 20..23 && (x < 4 || x > 27 || y < 4 || y > 27)) color = rgb(128, 62, 18)
                    if (id == 24 && y in 8..18 && x in 4..27) color = rgb(20, 18, 12)
                    if (id == 25 && y in 10..20 && x in 6..25) color = rgb(215, 128, 30)

                    atlas.setRGB(tileOriginX + x, tileOriginY + y, color)
                }
            }
        }
        return atlas
    }

    fun render(map: TileMap, atlas: BufferedImage): BufferedImage {
        val image = BufferedImage(map.width * TILE_SIZE, map.height * TILE_SIZE, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color(0, 122, 244); g.fillRect(0, 0, image.width, image.height)
        for (layer in MapLayer.entries) for (y in 0 until map.height) for (x in 0 until map.width) {
            val id = map.tile(layer, x, y)
            if (id >= 0) g.drawImage(
                atlas,
                x * TILE_SIZE, y * TILE_SIZE, (x + 1) * TILE_SIZE, (y + 1) * TILE_SIZE,
                map.sourceX(id), map.sourceY(id), map.sourceX(id) + TILE_SIZE, map.sourceY(id) + TILE_SIZE,
                null
            )
        }
        g.dispose()
        return image
    }

    fun export(directory: File) {
        val map = ForestReconstruction.create()
        check(map.validationErrors().isEmpty())
        val outputs = listOf("forest.neilmap", "forest_tiles.png", "forest_preview.png").map { File(directory, it) }
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create export directory" }
        val atlas = atlas()
        outputs[0].writeText(TileMapCodec.encode(map))
        check(ImageIO.write(atlas, "png", outputs[1]))
        check(ImageIO.write(render(map, atlas), "png", outputs[2]))
        check(TileMapCodec.encode(TileMapCodec.decode(outputs[0].readText())) == outputs[0].readText())
        println("Exported high-resolution forest reconstruction to ${directory.absolutePath}")
    }
}
