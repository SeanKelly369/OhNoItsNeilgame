package com.kelly3d.ohnoitsneilgame.editor

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Builds high-resolution versions of the decoded Amiga rooms and sprites.
 *
 * Two edge-directed 2x passes (xBR level-1 rules) produce 4x artwork. The first pass is
 * palette-exact and reshapes diagonals; the second blends only along detected edges, giving
 * smooth, anti-aliased contours while flat areas, straight edges and single-pixel details stay
 * untouched. Inputs are the PNGs written by tools/import_original.py and are never modified.
 */
internal object AmigaHdArt {
    const val SCALE = 4

    fun upscale(source: BufferedImage): BufferedImage {
        val w = source.width
        val h = source.height
        val pixels = source.getRGB(0, 0, w, h, null, 0, w)
        val once = xbr2x(pixels, w, h, blend = false)
        val twice = xbr2x(once, w * 2, h * 2, blend = true)
        return BufferedImage(w * SCALE, h * SCALE, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(0, 0, w * SCALE, h * SCALE, twice, 0, w * SCALE)
        }
    }

    private fun xbr2x(src: IntArray, w: Int, h: Int, blend: Boolean): IntArray {
        val ow = w * 2
        val out = IntArray(ow * h * 2)
        fun px(x: Int, y: Int) = src[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]
        for (y in 0 until h) for (x in 0 until w) {
            val e = src[y * w + x]
            for (sy in CORNERS) for (sx in CORNERS) {
                // Neighbourhood mirrored so that F is towards the corner horizontally, H vertically.
                fun n(ox: Int, oy: Int) = px(x + ox * sx, y + oy * sy)
                val b = n(0, -1); val c = n(1, -1); val d = n(-1, 0); val f = n(1, 0)
                val g = n(-1, 1); val hh = n(0, 1); val i = n(1, 1)
                val f4 = n(2, 0); val i4 = n(2, 1); val h5 = n(0, 2); val i5 = n(1, 2)
                var result = e
                val allowed = e != f && e != hh &&
                    ((f != b && hh != d) || (e == i && f != i4 && hh != i5) || e == g || e == c)
                if (allowed) {
                    val wd1 = dist(e, c) + dist(e, g) + dist(i, f4) + dist(i, h5) + 4 * dist(hh, f)
                    val wd2 = dist(hh, d) + dist(hh, i5) + dist(f, i4) + dist(f, b) + 4 * dist(e, i)
                    if (wd1 < wd2) {
                        val nearest = if (dist(e, f) <= dist(e, hh)) f else hh
                        result = if (blend) mix(e, nearest) else nearest
                    }
                }
                val ox = x * 2 + if (sx > 0) 1 else 0
                val oy = y * 2 + if (sy > 0) 1 else 0
                out[oy * ow + ox] = result
            }
        }
        return out
    }

    private val CORNERS = intArrayOf(-1, 1)

    /** Perceptual (YUV-weighted) distance; alpha differences count as strong edges. */
    private fun dist(a: Int, b: Int): Int {
        if (a == b) return 0
        val aa = a ushr 24
        val ba = b ushr 24
        if (aa == 0 && ba == 0) return 0
        val r = ((a shr 16) and 255) - ((b shr 16) and 255)
        val g = ((a shr 8) and 255) - ((b shr 8) and 255)
        val bl = (a and 255) - (b and 255)
        val y = abs(0.299 * r + 0.587 * g + 0.114 * bl)
        val u = abs(-0.169 * r - 0.331 * g + 0.5 * bl)
        val v = abs(0.5 * r - 0.419 * g - 0.081 * bl)
        return (48 * y + 7 * u + 6 * v).toInt() + abs(aa - ba) * 48
    }

    /** Alpha-weighted 50% mix, so transparent neighbours don't darken sprite edges. */
    private fun mix(a: Int, b: Int): Int {
        val aa = a ushr 24
        val ba = b ushr 24
        val alpha = (aa + ba) / 2
        if (alpha == 0) return 0
        fun channel(shift: Int) =
            (((a shr shift) and 255) * aa + ((b shr shift) and 255) * ba) / (aa + ba)
        return (alpha shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun export(projectRoot: File) {
        val amiga = File(projectRoot, "art/sprites/amiga")
        val world = File(amiga, "world.json")
        check(world.isFile) { "Missing ${world.path}; run python3 tools/import_original.py first" }
        val used = Regex("\"used\":\\[([0-9,]*)]").find(world.readText())
            ?.groupValues?.get(1)?.split(',')?.filter { it.isNotBlank() }?.map { it.toInt() }
            ?: error("world.json has no 'used' room list")
        val hd = File(amiga, "hd")
        fun convert(input: File, output: File) {
            val image = ImageIO.read(input) ?: error("Cannot decode ${input.path}")
            check(output.parentFile.isDirectory || output.parentFile.mkdirs())
            check(ImageIO.write(upscale(image), "png", output)) { "Cannot write ${output.path}" }
        }
        used.forEach { id ->
            val name = "room_%03d.png".format(id)
            convert(File(amiga, "rooms/$name"), File(hd, "rooms/$name"))
        }
        val sprites = File(amiga, "sprites").listFiles { f -> f.extension == "png" }.orEmpty().sorted()
        sprites.forEach { convert(it, File(hd, "sprites/${it.name}")) }
        println("Wrote ${used.size} rooms and ${sprites.size} sprites at ${SCALE}x to ${hd.path}")
    }
}

fun main(args: Array<String>) {
    AmigaHdArt.export(File(args.firstOrNull() ?: ".").absoluteFile)
}
