package com.kelly3d.ohnoitsneilgame

import android.content.res.AssetManager
import android.graphics.*
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

/** Uses exact iOS-generated poses, including the front and quarter turns. */
internal class HarryAnimator(assets: AssetManager) {
    private data class Frame(val bitmap: Bitmap, val width: Float, val height: Float, val anchor: Float, val baseline: Float)
    private val frames: List<Frame> = assets.open("parity/frames.json").bufferedReader().use {
        val entries = JSONObject(it.readText()).getJSONArray("harry")
        List(entries.length()) { i ->
            val f = entries.getJSONObject(i)
            val bitmap = assets.open("parity/" + f.getString("file")).use(BitmapFactory::decodeStream)
            Frame(requireNotNull(bitmap), f.getDouble("width").toFloat(), f.getDouble("height").toFloat(),
                f.getDouble("anchor").toFloat(), f.getDouble("baseline").toFloat())
        }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var facesLeft = true
    private var turnFromLeft = true
    private var turnTimer = 0f
    private var lastTime: Float? = null
    private var wasNearby = false
    private var nextMoveAt = 2f
    private var nextBlinkAt = 2.4f
    private var blinkUntil = -1f
    private var lastGreetAt = -100f
    private var steps = emptyList<Pair<Int, Float>>()
    private var elapsed = 0f
    private var dialogueIndex = 0
    private fun random(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
    private fun sequence(vararg frames: Pair<Int, Float>) { steps = frames.toList(); elapsed = 0f }
    private fun turn(left: Boolean) {
        if (left == facesLeft) return
        turnFromLeft = facesLeft
        facesLeft = left
        turnTimer = if (turnTimer > 0) maxOf(.01f, .195f - turnTimer) else .195f
    }
    fun update(game: OriginalGame) {
        if (game.room != 17) { wasNearby = false; lastTime = null; return }
        val time = game.time
        if (lastTime == null || time < lastTime!!) {
            lastTime = time; steps = emptyList(); blinkUntil = -1f; turnTimer = 0f
            nextMoveAt = time + 1.8f; nextBlinkAt = time + 2.2f; lastGreetAt = -100f
        }
        val dt = (time - lastTime!!).coerceIn(0f, .1f)
        lastTime = time
        turnTimer = (turnTimer - dt).coerceAtLeast(0f)
        val offset = game.x + 7.5f - 240f
        val nearby = abs(offset) < 88 && abs(game.y + 32 - 160) < 38
        if (nearby && abs(offset) > 6 && turnTimer == 0f) turn(offset < 0)
        if (turnTimer == 0f && steps.isNotEmpty()) {
            elapsed += dt
            if (elapsed >= steps.sumOf { it.second.toDouble() }) {
                steps = emptyList(); nextMoveAt = time + random(3f, 5.8f)
            }
        }
        if (nearby && !wasNearby && steps.isEmpty() && time - lastGreetAt > 6) {
            sequence(3 to .09f, 6 to .10f, 2 to .16f, 6 to .12f, 2 to .16f, 6 to .11f, 3 to .10f)
            lastGreetAt = time; nextMoveAt = time + random(3.5f, 6f)
        }
        wasNearby = nearby
        if (steps.isEmpty() && turnTimer == 0f) {
            if (time >= nextBlinkAt) {
                blinkUntil = time + .13f
                nextBlinkAt = time + if (Random.nextFloat() < .25f) .27f else random(3f, 5.8f)
            }
            if (time >= nextMoveAt) {
                if (!nearby && Random.nextBoolean()) turn(!facesLeft)
                if (nearby) when (Random.nextInt(4)) {
                    0 -> sequence(3 to .11f, 6 to .18f, 3 to .12f)
                    1 -> sequence(4 to .35f, 7 to .65f, 4 to .25f)
                    2 -> sequence(3 to .10f, 5 to 1.05f, 3 to .10f)
                    else -> sequence(7 to 1.35f)
                } else when (Random.nextInt(4)) {
                    0 -> sequence(5 to 1.2f)
                    1 -> sequence(7 to 1.45f)
                    2 -> sequence(4 to .95f)
                    else -> sequence(3 to .9f)
                }
                nextMoveAt = time + random(3.8f, 6.8f)
            }
        }
    }
    fun interact(game: OriginalGame): Boolean {
        if (game.room != 17 || abs(game.x + 7.5f - 240f) >= 40 || abs(game.y + 32 - 160) >= 38) return false
        val lines = listOf("Hello! I'm Harry. I work as a lumberjack.",
            "Lovely day for a walk in the forest, isn't it?",
            "I know these woods like the back of my hand.", "Take care out there, Neil!")
        game.message = lines[dialogueIndex++ % lines.size]
        turn(game.x + 7.5f < 240)
        sequence(6 to .09f, 2 to .22f, 6 to .12f, 4 to .26f, 3 to .26f, 4 to .24f,
            6 to .18f, 3 to .24f, 4 to .22f, 0 to .12f)
        lastGreetAt = game.time; nextMoveAt = game.time + random(4f, 7f)
        return true
    }
    fun draw(canvas: Canvas, time: Float) {
        var left = facesLeft
        val index = if (turnTimer > 0) {
            val progress = 1 - turnTimer / .195f
            when {
                progress < 1f / 3 -> { left = turnFromLeft; 8 }
                progress < 2f / 3 -> { left = false; 9 }
                else -> { left = !turnFromLeft; 8 }
            }
        } else if (steps.isNotEmpty()) {
            var total = 0f
            steps.firstOrNull { total += it.second; elapsed < total }?.first ?: steps.last().first
        } else if (time < blinkUntil) 1 else 0
        val frame = frames[index]
        val sx = 69.4f / frames.first().width
        val sy = 78.2f / frames.first().height
        canvas.save()
        canvas.translate(240f, 160f)
        paint.color = Color.argb(71, 0, 0, 0)
        canvas.drawOval(-17f, -2.5f, 17f, 2.5f, paint)
        paint.alpha = 255
        canvas.scale(if (left) -1f else 1f, 1f)
        canvas.drawBitmap(frame.bitmap, null, RectF(-frame.anchor * sx, -frame.baseline * sy,
            (frame.width - frame.anchor) * sx, (frame.height - frame.baseline) * sy), paint)
        canvas.restore()
    }
}
