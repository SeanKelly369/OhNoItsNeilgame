package com.kelly3d.ohnoitsneilgame

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.min
import kotlin.math.sin

/** Code-drawn world with a bundled, reference-derived Neil sprite atlas. */
class NeilGameView(context: Context, private val game: NeilGame) : SurfaceView(context), Choreographer.FrameCallback, SurfaceHolder.Callback {
    private val paint = Paint().apply { isAntiAlias = false }
    private val neilSprites = NeilSprites(resources)
    private val amigaRooms = AmigaRoomGallery(resources)
    private val logo = requireNotNull(BitmapFactory.decodeResource(resources, R.drawable.neil_logo))
    private val logoPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val logoBounds = RectF()
    private var surfaceReady = false
    private val polygon = Path()
    private val loop = NeilGameLoop(game)
    private val mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private data class HeldControl(val action: String, val order: Long)
    private val pointers = mutableMapOf<Int, HeldControl>()
    private val keys = mutableMapOf<Int, HeldControl>()
    private var pressOrder = 0L

    private fun hold(action: String) = HeldControl(action, ++pressOrder)

    private fun movementDirection(): Int {
        var latestOrder = -1L
        var direction = 0
        fun consider(control: HeldControl) {
            val candidate = when (control.action) {
                "left" -> -1
                "right" -> 1
                else -> return
            }
            if (control.order > latestOrder) {
                latestOrder = control.order
                direction = candidate
            }
        }
        // Opposite arrows may overlap during a reversal. The newest press wins;
        // releasing it restores the remaining held direction. Jump never replaces it.
        pointers.values.forEach(::consider)
        keys.values.forEach(::consider)
        return direction
    }
    private var running = false
    private var paused = false
    private var previousFrame = 0L
    private var scale = 1f
    private var originX = 0f
    private var originY = 0f
    private val ink = Color.rgb(14, 24, 40)
    private val cream = Color.rgb(249, 232, 190)
    private val gold = Color.rgb(255, 199, 91)
    private val teal = Color.rgb(103, 221, 177)
    private val controls = linkedMapOf(
        "left" to RectF(12f, 291f, 77f, 339f),
        "right" to RectF(85f, 291f, 150f, 339f),
        "use" to RectF(288f, 291f, 373f, 339f),
        "jump" to RectF(381f, 291f, 468f, 339f),
        "pause" to RectF(434f, 5f, 470f, 33f)
    )

    init {
        holder.addCallback(this)
        setWillNotDraw(false)
        isFocusableInTouchMode = true
        contentDescription = "Oh No! It's Neil. Retro platform adventure. Move with left and right, jump with space, use items with E, pause with P."
        requestFocus()
    }

    fun resume() {
        if (!running) {
            running = true
            previousFrame = 0
            loop.reset()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun pause() {
        running = false
        pointers.clear(); keys.clear()
        previousFrame = 0; loop.reset()
        if (game.phase == NeilGame.Phase.PLAYING) paused = true
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun onDetachedFromWindow() { pause(); super.onDetachedFromWindow() }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            pointers.clear(); keys.clear()
            previousFrame = 0; loop.reset()
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (previousFrame != 0L && !paused) {
            loop.advance((frameTimeNanos - previousFrame) / 1_000_000_000.0, movementDirection())
        }
        previousFrame = frameTimeNanos
        drawSurface()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        previousFrame = 0
        resizeSurface()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        drawSurface()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        previousFrame = 0
        pointers.clear(); keys.clear()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resizeSurface()
    }

    private fun resizeSurface() {
        if (width <= 0 || height <= 0) return
        val ratio = min(1f, 960f / maxOf(width, height))
        holder.setFixedSize((width * ratio).toInt().coerceAtLeast(1),
            (height * ratio).toInt().coerceAtLeast(1))
    }

    private fun drawSurface() {
        if (!surfaceReady || !holder.surface.isValid) return
        val canvas = holder.lockCanvas() ?: return
        try { render(canvas) } finally { holder.unlockCanvasAndPost(canvas) }
    }

    private fun rect(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        paint.color = color; c.drawRect(x, y, x + w, y + h, paint)
    }
    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int = cream) {
        paint.color = color; paint.textSize = size; paint.typeface = mono
        c.drawText(s, x, y, paint)
    }
    private fun centered(c: Canvas, s: String, y: Float, size: Float, color: Int = cream) {
        paint.textSize = size; paint.typeface = mono
        text(c, s, (480f - paint.measureText(s)) / 2, y, size, color)
    }
    /** Fit without cropping; the bitmap and destination are reused across frames. */
    private fun drawLogo(c: Canvas, x: Float, y: Float, width: Float, height: Float) {
        val fit = min(width / logo.width, height / logo.height)
        val w = logo.width * fit
        val h = logo.height * fit
        val left = x + (width - w) / 2
        val top = y + (height - h) / 2
        logoBounds.set(left, top, left + w, top + h)
        c.drawBitmap(logo, null, logoBounds, logoPaint)
    }

    private fun poly(c: Canvas, color: Int, vararg points: Float) {
        val path = polygon
        path.rewind()
        path.moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
        path.close(); paint.color = color; c.drawPath(path, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!surfaceReady) render(canvas)
    }

    private fun render(canvas: Canvas) {
        canvas.drawColor(ink)
        val usableWidth = width - paddingLeft - paddingRight
        val usableHeight = height - paddingTop - paddingBottom
        scale = min(usableWidth / 480f, usableHeight / 360f).coerceAtLeast(0.01f)
        originX = paddingLeft + (usableWidth - 480 * scale) / 2
        originY = paddingTop + (usableHeight - 360 * scale) / 2
        canvas.save()
        canvas.scale(canvas.width.toFloat() / width.coerceAtLeast(1),
            canvas.height.toFloat() / height.coerceAtLeast(1))
        canvas.translate(originX, originY)
        canvas.scale(scale, scale)
        if (amigaRooms.visible) amigaRooms.draw(canvas) else drawFrame(canvas)
        canvas.restore()
    }

    private fun drawFrame(canvas: Canvas) {
        rect(canvas, 0f, 0f, 480f, 360f, ink)
        drawLogo(canvas, 12f, 1f, 198f, 38f)
        text(canvas, "LIFE " + "♥".repeat(game.hearts), 222f, 17f, 10f, 0xFFFF8390.toInt())
        text(canvas, "STARS ${game.collected.size}/8", 335f, 17f, 9f, gold)
        text(canvas, if (game.bridge) "BRIDGE: OK" else if (game.plank) "BAG: PLANK" else "BAG: EMPTY", 222f, 31f, 7f)
        text(canvas, if (game.key) "KEY: YES" else "KEY: --", 335f, 31f, 7f)
        drawWorld(canvas)
        text(canvas, if (game.phase == NeilGame.Phase.TITLE) "A LITTLE ADVENTURE. A BIG OH NO." else game.objective.uppercase(), 12f, 283f, 8f, teal)
        controls.forEach { (name, bounds) ->
            val held = pointers.values.any { it.action == name } || keys.values.any { it.action == name }
            rect(canvas, bounds.left, bounds.top + 3, bounds.width(), bounds.height(), 0xFF080F1C.toInt())
            rect(canvas, bounds.left, bounds.top, bounds.width(), bounds.height() - 3, if (held) 0xFF426574.toInt() else 0xFF23384C.toInt())
            rect(canvas, bounds.left, bounds.top, bounds.width(), 2f, if (name == "jump") gold else 0xFF4C6C79.toInt())
            val label = when (name) { "left" -> "<"; "right" -> ">"; "jump" -> "JUMP"; "use" -> "USE"; else -> if (paused) ">" else "II" }
            paint.textSize = if (name == "left" || name == "right") 21f else 11f; paint.typeface = mono
            text(canvas, label, bounds.centerX() - paint.measureText(label) / 2, bounds.centerY() + 4, paint.textSize, if (name == "jump") gold else cream)
        }
        text(canvas, "HOLD TO MOVE", 24f, 352f, 7f, 0xFF89A5B5.toInt())
        text(canvas, "A/D  MOVE   SPACE  JUMP   E  USE", 190f, 352f, 7f, 0xFF89A5B5.toInt())
        if (game.phase != NeilGame.Phase.PLAYING || paused) drawOverlay(canvas)
    }

    private fun drawWorld(c: Canvas) {
        c.save()
        c.clipRect(0f, 40f, 480f, 270f)
        c.translate(0f, 40f)
        rect(c, 0f, 0f, 480f, 230f, 0xFF172B46.toInt())
        val camera = (loop.renderX - 220f).coerceIn(0f, 960f)
        for (i in 0..35) {
            val sx = ((i * 73f - camera * 0.12f) % 500 + 500) % 500
            rect(c, sx, (i * 31 % 96).toFloat(), 1f, 1f, 0xFF6E91A3.toInt())
        }
        rect(c, 354f - camera * 0.08f, 18f, 27f, 27f, 0xFFF1DAB0.toInt())
        rect(c, 348f - camera * 0.08f, 15f, 20f, 22f, 0xFF172B46.toInt())
        for (i in -1..6) {
            val bx = i * 130f - camera * 0.25f % 130
            poly(c, 0xFF244453.toInt(), bx, 180f, bx + 65, 65f, bx + 145, 180f)
        }
        for (i in -1..8) {
            val tx = i * 86f - camera * 0.48f % 86
            rect(c, tx + 23, 100f, 10f, 122f, 0xFF264D50.toInt())
            poly(c, 0xFF2D5755.toInt(), tx - 12, 150f, tx + 28, 52f, tx + 66, 150f)
            poly(c, 0xFF32605B.toInt(), tx - 6, 118f, tx + 28, 43f, tx + 58, 118f)
        }
        c.save(); c.translate(-camera, 0f)
        // Water and broken bridge posts.
        rect(c, 650f, 220f, 140f, 30f, 0xFF3D8AA0.toInt())
        for (i in 0..8) rect(c, 655f + i * 16 + sin(game.time * 2 + i) * 4, 226f + i % 2 * 5, 9f, 2f, 0xFF8CE3D4.toInt())
        for (px in listOf(636f, 794f)) {
            rect(c, px, 190f, 6f, 35f, 0xFFAD8157.toInt())
            rect(c, px - 2, 189f, 10f, 4f, gold)
        }
        if (game.bridge) for (i in 0..13) {
            rect(c, 650f + i * 10, 216f, 9f, 8f, 0xFFC79862.toInt())
        }
        // Stone ruins.
        for (rx in listOf(1028f, 1145f, 1210f)) {
            rect(c, rx, 110f, 12f, 112f, 0xFF52666B.toInt())
            for (row in 0..10) rect(c, rx, 113f + row * 10, 9f, 1f, 0xFF8B9790.toInt())
        }
        rect(c, 1134f, 105f, 92f, 8f, 0xFF87948A.toInt())
        // Neil's cottage.
        rect(c, 1313f, 147f, 99f, 75f, 0xFFBFA67A.toInt())
        rect(c, 1390f, 116f, 12f, 35f, 0xFF946A65.toInt())
        poly(c, 0xFF905C64.toInt(), 1302f, 150f, 1360f, 104f, 1423f, 150f)
        poly(c, 0xFFC38077.toInt(), 1302f, 150f, 1360f, 104f, 1360f, 114f, 1314f, 150f)
        rect(c, 1346f, 179f, 25f, 43f, 0xFF364452.toInt())
        rect(c, 1363f, 199f, 3f, 3f, gold)
        for (wx in listOf(1323f, 1380f)) {
            rect(c, wx, 164f, 20f, 22f, gold)
            rect(c, wx + 9, 164f, 2f, 22f, 0xFF745855.toInt())
            rect(c, wx, 174f, 20f, 2f, 0xFF745855.toInt())
        }
        game.platforms.forEach { p ->
            rect(c, p.x, p.y, p.width, if (p.y == 222f) 35f else 12f, 0xFF66564E.toInt())
            rect(c, p.x, p.y, p.width, 4f, 0xFF88BE77.toInt())
            rect(c, p.x, p.y + 4, p.width, 4f, 0xFF436F56.toInt())
            for (i in 0 until (p.width / 16).toInt()) rect(c, p.x + i * 16 + 3, p.y + 12, 5f, 3f, 0xFF8A7058.toInt())
        }
        // Small flowers and trail signs.
        for (i in 0..22) {
            val fx = i * 59f + 20
            if (fx !in 640f..800f) {
                rect(c, fx, 215f, 2f, 7f, teal)
                rect(c, fx - 2, 213f, 6f, 3f, if (i % 2 == 0) gold else 0xFFE89DA1.toInt())
            }
        }
        sign(c, 90f, "WOODS"); sign(c, 564f, "RIVER"); sign(c, 840f, "RUINS")
        game.stars.forEachIndexed { index, (x, y) ->
            if (index !in game.collected) {
                val bob = sin(game.time * 3 + index) * 2
                rect(c, x - 2, y - 6 + bob, 4f, 12f, gold)
                rect(c, x - 6, y - 2 + bob, 12f, 4f, gold)
                rect(c, x - 1, y - 2 + bob, 2f, 3f, cream)
            }
        }
        if (!game.plank && !game.bridge) {
            rect(c, 429f, 105f, 26f, 6f, gold)
            rect(c, 433f, 107f, 18f, 1f, 0xFF946945.toInt())
        }
        if (!game.key) {
            rect(c, 1170f, 92f, 9f, 9f, gold)
            rect(c, 1173f, 95f, 3f, 3f, ink)
            rect(c, 1178f, 95f, 13f, 3f, gold)
            rect(c, 1187f, 98f, 3f, 4f, gold)
        }
        slime(c, game.slimeX); slime(c, game.secondSlimeX)
        if (game.invulnerable == 0f || (game.time * 12).toInt() % 2 == 0) neil(c)
        c.restore()
        text(c, when { game.x < 650 -> "01 / MOSSWOOD"; game.x < 1000 -> "02 / THE CROOKED CROSSING"; else -> "03 / HOME, ALMOST" }, 12f, 15f, 8f, teal)
        if (game.messageTime > 0 && game.phase == NeilGame.Phase.PLAYING) {
            rect(c, 8f, 28f, 464f, 22f, 0xEC142334.toInt())
            text(c, game.message, 16f, 42f, 7.5f)
        }
        if (game.prompt != "Look" && game.phase == NeilGame.Phase.PLAYING) {
            rect(c, 160f, 201f, 160f, 20f, ink)
            centered(c, "[USE] " + game.prompt, 214f, 8f, gold)
        }
        c.restore()
    }

    private fun sign(c: Canvas, x: Float, label: String) {
        rect(c, x + 17, 196f, 4f, 26f, 0xFF9A7653.toInt())
        rect(c, x, 188f, 41f, 14f, 0xFFBC9566.toInt())
        text(c, label, x + 4, 198f, 6f, ink)
    }
    private fun slime(c: Canvas, x: Float) {
        val bob = if ((game.time * 5).toInt() % 2 == 0) 0f else 2f
        rect(c, x - 11, 211f + bob, 22f, 11f - bob, 0xFF7AA77B.toInt())
        rect(c, x - 7, 207f + bob, 14f, 9f, 0xFF9CD58B.toInt())
        rect(c, x - 6, 210f + bob, 3f, 3f, ink)
        rect(c, x + 3, 210f + bob, 3f, 3f, ink)
    }
    private fun neil(c: Canvas) {
        neilSprites.draw(c, game, loop.renderX, loop.renderY)
    }

    private fun drawOverlay(c: Canvas) {
        rect(c, 0f, 40f, 480f, 230f, 0xB80B1627.toInt())
        rect(c, 49f, 62f, 382f, 188f, ink)
        rect(c, 49f, 62f, 382f, 3f, gold)
        if (paused || game.phase == NeilGame.Phase.WON) {
            drawLogo(c, 152f, 68f, 176f, 48f)
            centered(c, if (paused) "TAKE A BREATHER" else "NEIL MADE IT HOME!", 134f, 18f, gold)
            centered(c, if (paused) "Your adventure is right where you left it." else "Kettle on. Feet up. Crisis averted.", 153f, 8f, teal)
            centered(c, if (paused) "Tap below or press P to continue." else "Stars found: ${game.collected.size} / 8", 174f, 9f)
        } else {
            drawLogo(c, 64f, 67f, 352f, 88f)
            centered(c, "THE MOSSWOOD MIX-UP", 166f, 10f, teal)
            centered(c, "Explore, jump, collect & use items to get home.", 182f, 8f)
        }
        rect(c, 133f, 191f, 214f, 34f, gold)
        centered(c, if (paused) "RESUME" else if (game.phase == NeilGame.Phase.WON) "PLAY AGAIN" else "LET'S FIND THAT KEY", 213f, 11f, ink)
        centered(c, if (game.phase == NeilGame.Phase.TITLE) "AMIGA ROOMS  /  R" else "TAP TO PLAY  /  ENTER", 239f, 7f, 0xFF89A5B5.toInt())
    }

    private fun activate(action: String) {
        when (action) {
            "jump" -> game.jump()
            "use" -> game.interact()
            "pause" -> { paused = !paused; pointers.clear(); keys.clear(); loop.reset() }
        }
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = event.actionIndex
        val px = (event.getX(index) - originX) / scale
        val py = (event.getY(index) - originY) / scale
        if (amigaRooms.visible) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                amigaRooms.tap(px, py)
                performClick()
            }
            return true
        }
        if (game.phase == NeilGame.Phase.TITLE && event.actionMasked == MotionEvent.ACTION_DOWN &&
            px in 100f..380f && py in 227f..250f) {
            pointers.clear(); keys.clear()
            amigaRooms.open()
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (paused || game.phase != NeilGame.Phase.PLAYING) {
                    if (px in 49f..431f && py in 62f..250f) {
                        if (paused) paused = false else game.start()
                        pointers.clear(); loop.reset()
                        performClick()
                    }
                } else {
                    controls.entries.firstOrNull { it.value.contains(px, py) }?.key?.let {
                        pointers[event.getPointerId(index)] = hold(it)
                        activate(it)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val action = pointers[id]?.action
                    if (action == "left" || action == "right") {
                        val mx = (event.getX(i) - originX) / scale
                        val my = (event.getY(i) - originY) / scale
                        // Capture a movement finger until release. Small thumb drift must
                        // not cancel movement, especially while another finger taps Jump.
                        // Switch only inside the opposite button, away from its shared edge.
                        val opposite = if (action == "left") "right" else "left"
                        val bounds = controls.getValue(opposite)
                        val inset = 6f
                        if (mx >= bounds.left + inset && mx <= bounds.right - inset &&
                            my >= bounds.top && my <= bounds.bottom) {
                            pointers[id] = hold(opposite)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> { pointers.remove(event.getPointerId(index)); performClick() }
            MotionEvent.ACTION_CANCEL -> pointers.clear()
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (amigaRooms.visible) {
            if (event.repeatCount == 0) when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> amigaRooms.move(-1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> amigaRooms.move(1)
                KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_R -> amigaRooms.close()
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_R && game.phase == NeilGame.Phase.TITLE) {
            pointers.clear(); keys.clear()
            amigaRooms.open()
            return true
        }
        val action = when (keyCode) {
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DPAD_LEFT -> "left"
            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_UP -> "jump"
            KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_ENTER -> "use"
            KeyEvent.KEYCODE_P -> "pause"
            else -> return super.onKeyDown(keyCode, event)
        }
        if (event.repeatCount == 0) {
            if (game.phase != NeilGame.Phase.PLAYING && keyCode == KeyEvent.KEYCODE_ENTER) {
                game.start()
                loop.reset()
            }
            else if (keyCode == KeyEvent.KEYCODE_P || (paused && keyCode == KeyEvent.KEYCODE_ENTER)) activate("pause")
            else if (!paused) {
                keys[keyCode] = hold(action)
                activate(action)
            }
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return if (keys.remove(keyCode) != null) true else super.onKeyUp(keyCode, event)
    }
}
