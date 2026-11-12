package com.kelly3d.ohnoitsneilgame

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import org.json.JSONObject

/** Player animation atlas, decoded once with nearest-neighbor rendering. */
internal class NeilSprites(resources: Resources) {
    private val atlas = try {
        resources.assets.open("neil_sprite_atlas.png").use { BitmapFactory.decodeStream(it) }
    } catch (_: java.io.IOException) {
        null
    }
    private data class Frame(val source: Rect, val baseline: Int)
    private val frames = if (atlas == null) emptyList() else {
        val metadata = resources.assets.open("neil_sprite_atlas.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONArray("frames")
        }
        require(metadata.length() == NeilSpriteFrames.COUNT)
        List(metadata.length()) { index ->
            val entry = metadata.getJSONObject(index)
            val x = entry.getInt("x")
            val y = entry.getInt("y")
            val width = entry.getInt("width")
            val height = entry.getInt("height")
            val baseline = entry.getInt("baseline")
            require(x >= 0 && y >= 0 && width > 0 && height > 0)
            require(x + width <= atlas.width && y + height <= atlas.height)
            require(baseline in 1..height)
            Frame(Rect(x, y, x + width, y + height), baseline)
        }
    }
    private val amigaSprites = (1..12).map { id ->
        try {
            resources.assets.open("amiga/sprites/%03d.png".format(id)).use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) { null }
    }
    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val destination = RectF()

    private fun drawFallback(canvas: Canvas, game: NeilGame, x: Float, y: Float) {
        fun block(dx: Float, dy: Float, w: Float, h: Float, color: Long) {
            paint.color = color.toInt()
            canvas.drawRect(x + dx, y + dy, x + dx + w, y + dy + h, paint)
        }
        val dir = game.facing.toFloat()
        val stride = if (game.moving && game.grounded) kotlin.math.sin(game.time * 18) * 2f else 0f

        // Cobalt blue hair cascading down back
        block(if (dir > 0) -2f else 12f, 6f, 10f, 16f, 0xFF1D5FE0)
        block(if (dir > 0) -4f else 14f, 10f, 6f, 10f, 0xFF103B94)

        // Dark brown pants
        block(4f, 17f, 4f, 6f + stride, 0xFF3B2213)
        block(12f, 17f, 4f, 6f - stride, 0xFF3B2213)
        // Cream shoes
        block(2f, 22f + stride, 6f, 3f, 0xFFF7E0B5)
        block(12f, 22f - stride, 6f, 3f, 0xFFF7E0B5)

        // Ochre tunic coat
        block(3f, 10f, 14f, 9f, 0xFFE8821E)
        // Brown satchel strap & pouch
        block(5f, 10f, 10f, 2f, 0xFF593619)
        block(if (dir > 0) 2f else 14f, 14f, 4f, 4f, 0xFF593619)

        // Peach face and nose
        block(5f, 4f, 10f, 7f, 0xFFFAC496)
        block(if (dir > 0) 14f else 1f, 6f, 5f, 3f, 0xFFFAC496) // Nose
        block(if (dir > 0) 11f else 7f, 6f, 2f, 2f, 0xFF121A28) // Eye

        // Floppy hat with orange band
        block(4f, 0f, 12f, 4f, 0xFF261E47) // Crown
        block(3f, 4f, 14f, 2f, 0xFFF08418) // Orange band
        block(1f, 5f, 18f, 2f, 0xFF261E47) // Brim
    }

    fun draw(canvas: Canvas, game: NeilGame, x: Float, y: Float, useOriginalArt: Boolean = false) {
        if (useOriginalArt) {
            val spriteIdx = if (game.moving) (if (game.facing > 0) 1 else 7) + (game.time * 10).toInt() % 6
                            else if (game.facing > 0) 1 else 7
            val amigaBmp = amigaSprites.getOrNull(spriteIdx - 1)
            if (amigaBmp != null) {
                destination.set(x - 6f, y - 8f, x + 26f, y + 24f)
                canvas.drawBitmap(amigaBmp, null, destination, paint)
                return
            }
        }
        val bitmap = atlas
        if (bitmap == null) {
            drawFallback(canvas, game, x, y)
            return
        }
        drawFrame(canvas, game.spriteFrame, game.facing, x + 10f, y + 24f, 38f, paint)
    }

    /**
     * Draws one atlas frame with its feet at ([centerX], [baselineY]) in canvas units.
     * [height] is the full frame height (the figure itself is shorter, because frames are padded).
     * Returns false when the atlas is unavailable so callers can draw their own fallback.
     */
    fun drawFrame(
        canvas: Canvas, frameIndex: Int, facing: Int, centerX: Float, baselineY: Float,
        height: Float, framePaint: Paint = paint
    ): Boolean {
        val bitmap = atlas ?: return false
        val frame = frames.getOrNull(frameIndex) ?: return false
        val scale = height / frame.source.height()
        val width = frame.source.width() * scale
        val top = -frame.baseline * scale
        destination.set(-width / 2f, top, width / 2f, top + height)
        canvas.save()
        canvas.translate(centerX, baselineY)
        canvas.scale(if (facing < 0) -1f else 1f, 1f)
        canvas.drawBitmap(bitmap, frame.source, destination, framePaint)
        canvas.restore()
        return true
    }
}
