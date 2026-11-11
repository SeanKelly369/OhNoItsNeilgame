package com.kelly3d.ohnoitsneilgame

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/** Reference browser: artwork only; it does not invent collision or exit data. */
internal class AmigaRoomGallery(private val resources: Resources) {
    var visible = false
        private set
    private var index = 15
    private var bitmap: Bitmap? = null
    private val paint = Paint().apply { isFilterBitmap = false; typeface = Typeface.MONOSPACE }
    private val bounds = RectF()

    fun open() { visible = true; load() }
    private fun load() {
        bitmap?.recycle()
        bitmap = try {
            resources.assets.open(AmigaRooms.room(index).assetName).use { BitmapFactory.decodeStream(it) }
        } catch (_: java.io.IOException) { null }
    }

    fun move(direction: Int) {
        index = (index + direction + AmigaRooms.COUNT) % AmigaRooms.COUNT
        load()
    }

    fun close() { visible = false }

    fun tap(x: Float, y: Float) {
        if (y !in 291f..339f) return
        when (x) {
            in 12f..150f -> move(-1)
            in 180f..300f -> close()
            in 330f..468f -> move(1)
        }
    }

    fun draw(canvas: Canvas) {
        canvas.drawColor(0xFF0E1828.toInt())
        fun text(value: String, x: Float, y: Float, size: Float = 10f) {
            paint.color = 0xFFF9E8BE.toInt(); paint.textSize = size
            canvas.drawText(value, x, y, paint)
        }
        text("AMIGA ROOM REFERENCE", 12f, 24f, 16f)
        val room = AmigaRooms.room(index)
        text("${room.id.toString().padStart(2, '0')}  ${room.label.uppercase()}", 12f, 46f)
        val image = bitmap
        if (image != null) {
            val scale = minOf(480f / image.width, 214f / image.height)
            val w = image.width * scale
            val h = image.height * scale
            bounds.set((480f - w) / 2, 55f + (214f - h) / 2, (480f + w) / 2, 55f + (214f + h) / 2)
            canvas.drawBitmap(image, null, bounds, paint)
        } else {
            text("Original room artwork has not been imported.", 12f, 145f)
            text("See art/AMIGA_IMPORT.md for the import task.", 12f, 164f)
        }
        text("REFERENCE ORDER — ROOM EXITS ARE NOT YET MAPPED", 12f, 283f, 8f)
        listOf(12f to "PREVIOUS", 180f to "BACK", 330f to "NEXT").forEach { (x, label) ->
            paint.color = 0xFF23384C.toInt()
            canvas.drawRect(x, 291f, x + 120f, 339f, paint)
            text(label, x + 12f, 320f)
        }
        text("ARROWS: BROWSE    ESC / R: BACK", 12f, 352f, 8f)
    }
}
