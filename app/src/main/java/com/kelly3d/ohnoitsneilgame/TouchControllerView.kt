package com.kelly3d.ohnoitsneilgame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Multi-touch gamepad overlay for moving Neil (D-pad) and triggering room entry,
 * jumping, item interaction, and the room atlas.
 */
class TouchControllerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Control { LEFT, RIGHT, UP, DOWN, JUMP, USE, MAP }

    interface Listener {
        fun onMoveChanged(direction: Int, climb: Int)
        fun onJumpPressed()
        fun onUsePressed()
        fun onUpPressed()
        fun onMapPressed()
    }

    private data class HeldPointer(val control: Control, val order: Long)

    var listener: Listener? = null

    var controlsEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) clearInputs()
            visibility = if (value) VISIBLE else INVISIBLE
            invalidate()
        }

    /** Non-null when Neil is standing at a doorway and can enter the connected room. */
    var nearbyDoorLabel: String? = null
        set(value) {
            if (field == value) return
            field = value
            updateAccessibility()
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val pointers = mutableMapOf<Int, HeldPointer>()
    private var pressOrder = 0L

    private var dpadCenter = PointF()
    private var dpadRadius = 0f
    private var dpadBounds = RectF()
    private val directionRects = mutableMapOf<Control, RectF>()
    private val buttonCenters = mutableMapOf<Control, PointF>()
    private val buttonRadii = mutableMapOf<Control, Float>()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val arrowPath = Path()

    private val ink = Color.rgb(12, 20, 32)
    private val panelBg = Color.argb(210, 18, 31, 47)
    private val panelRim = Color.argb(215, 76, 108, 121)
    private val keyIdle = Color.rgb(34, 54, 74)
    private val keyPressed = Color.rgb(74, 107, 130)
    private val cream = Color.rgb(244, 231, 198)
    private val gold = Color.rgb(244, 192, 79)
    private val teal = Color.rgb(103, 221, 177)

    init {
        isClickable = false
        isFocusable = false
        visibility = INVISIBLE
        updateAccessibility()
    }

    val currentDirection: Int
        get() = latestValue {
            when (it) {
                Control.LEFT -> -1
                Control.RIGHT -> 1
                else -> 0
            }
        }

    val currentClimb: Int
        get() = latestValue {
            when (it) {
                Control.UP -> -1
                Control.DOWN -> 1
                else -> 0
            }
        }

    private fun latestValue(mapper: (Control) -> Int): Int {
        var bestOrder = -1L
        var bestValue = 0
        for (held in pointers.values) {
            val v = mapper(held.control)
            if (v != 0 && held.order > bestOrder) {
                bestOrder = held.order
                bestValue = v
            }
        }
        return bestValue
    }

    internal fun centerOf(control: Control): PointF? {
        directionRects[control]?.let { return PointF(it.centerX(), it.centerY()) }
        return buttonCenters[control]?.let { PointF(it.x, it.y) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutControls(w.toFloat(), h.toFloat())
    }

    private fun layoutControls(w: Float, h: Float) {
        val usableW = (w - paddingLeft - paddingRight).coerceAtLeast(1f)
        val usableH = (h - paddingTop - paddingBottom).coerceAtLeast(1f)
        val unit = min(usableW / 4.2f, usableH * 0.42f).coerceIn(dp(44f), dp(96f))

        // Left thumb D-pad
        dpadRadius = unit * 0.88f
        val padMarginX = paddingLeft + dp(18f) + dpadRadius
        val padMarginY = h - paddingBottom - dp(16f) - dpadRadius
        dpadCenter.set(padMarginX, padMarginY)
        dpadBounds.set(
            dpadCenter.x - dpadRadius,
            dpadCenter.y - dpadRadius,
            dpadCenter.x + dpadRadius,
            dpadCenter.y + dpadRadius
        )

        val armThick = dpadRadius * 0.62f
        val armLen = dpadRadius * 0.94f
        directionRects[Control.LEFT] = RectF(
            dpadCenter.x - armLen, dpadCenter.y - armThick / 2f,
            dpadCenter.x - armThick * 0.25f, dpadCenter.y + armThick / 2f
        )
        directionRects[Control.RIGHT] = RectF(
            dpadCenter.x + armThick * 0.25f, dpadCenter.y - armThick / 2f,
            dpadCenter.x + armLen, dpadCenter.y + armThick / 2f
        )
        directionRects[Control.UP] = RectF(
            dpadCenter.x - armThick / 2f, dpadCenter.y - armLen,
            dpadCenter.x + armThick / 2f, dpadCenter.y - armThick * 0.25f
        )
        directionRects[Control.DOWN] = RectF(
            dpadCenter.x - armThick / 2f, dpadCenter.y + armThick * 0.25f,
            dpadCenter.x + armThick / 2f, dpadCenter.y + armLen
        )

        // Right thumb action cluster (JUMP, USE/ENTER, MAP)
        val jumpR = unit * 0.44f
        val useR = unit * 0.40f
        val mapR = unit * 0.28f
        val rightEdge = w - paddingRight - dp(20f)
        val bottomEdge = h - paddingBottom - dp(18f)

        val jumpCx = rightEdge - jumpR
        val jumpCy = bottomEdge - jumpR
        buttonCenters[Control.JUMP] = PointF(jumpCx, jumpCy)
        buttonRadii[Control.JUMP] = jumpR

        val useCx = jumpCx - jumpR - useR - dp(12f)
        val useCy = bottomEdge - useR - dp(8f)
        buttonCenters[Control.USE] = PointF(useCx, useCy)
        buttonRadii[Control.USE] = useR

        val mapCx = jumpCx - dp(4f)
        val mapCy = jumpCy - jumpR - mapR - dp(16f)
        buttonCenters[Control.MAP] = PointF(mapCx, mapCy)
        buttonRadii[Control.MAP] = mapR
    }

    private fun updateAccessibility() {
        val doorHint = nearbyDoorLabel?.let { " Near door to $it." } ?: ""
        contentDescription = "Neil touchscreen controller. D-pad on left to move and climb. " +
            "Use or Enter, Jump, and Rooms map on right.$doorHint"
    }

    fun clearInputs() {
        if (pointers.isEmpty()) return
        pointers.clear()
        publishMovement()
        invalidate()
    }

    private fun publishMovement() {
        listener?.onMoveChanged(currentDirection, currentClimb)
    }

    private fun isDpadControl(control: Control) =
        control == Control.LEFT || control == Control.RIGHT || control == Control.UP || control == Control.DOWN

    private fun hitDpad(x: Float, y: Float, maxFactor: Float, current: Control? = null): Control? {
        val dx = x - dpadCenter.x
        val dy = y - dpadCenter.y
        val dist = hypot(dx, dy)
        if (dist > dpadRadius * maxFactor) return null
        val deadzone = dpadRadius * 0.16f
        if (dist < deadzone) return current

        // Small hysteresis when already holding a direction so diagonal boundary never flickers.
        val bias = if (current != null) dpadRadius * 0.08f else 0f
        return when {
            abs(dx) > abs(dy) + bias -> if (dx < 0f) Control.LEFT else Control.RIGHT
            abs(dy) > abs(dx) + bias -> if (dy < 0f) Control.UP else Control.DOWN
            current != null -> current
            abs(dx) >= abs(dy) -> if (dx < 0f) Control.LEFT else Control.RIGHT
            else -> if (dy < 0f) Control.UP else Control.DOWN
        }
    }

    private fun hitActionButton(x: Float, y: Float): Control? {
        for (control in listOf(Control.JUMP, Control.USE, Control.MAP)) {
            val c = buttonCenters[control] ?: continue
            val r = buttonRadii[control] ?: continue
            if (hypot(x - c.x, y - c.y) <= r * 1.18f) return control
        }
        return null
    }

    private fun hitTestDown(x: Float, y: Float): Control? {
        return hitDpad(x, y, maxFactor = 1.22f) ?: hitActionButton(x, y)
    }

    private fun triggerAction(control: Control) {
        when (control) {
            Control.UP -> listener?.onUpPressed()
            Control.JUMP -> listener?.onJumpPressed()
            Control.USE -> listener?.onUsePressed()
            Control.MAP -> listener?.onMapPressed()
            else -> Unit
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!controlsEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.getX(0)
                val y = event.getY(0)
                val control = hitTestDown(x, y) ?: return false
                val id = event.getPointerId(0)
                pointers[id] = HeldPointer(control, ++pressOrder)
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                publishMovement()
                triggerAction(control)
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val x = event.getX(index)
                val y = event.getY(index)
                val control = hitTestDown(x, y) ?: return true
                val id = event.getPointerId(index)
                pointers[id] = HeldPointer(control, ++pressOrder)
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                publishMovement()
                triggerAction(control)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                var changed = false
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val held = pointers[id] ?: continue
                    if (!isDpadControl(held.control)) continue
                    val next = hitDpad(event.getX(i), event.getY(i), maxFactor = 1.65f, current = held.control)
                    if (next != null && next != held.control) {
                        pointers[id] = HeldPointer(next, ++pressOrder)
                        if (next == Control.UP) listener?.onUpPressed()
                        changed = true
                    }
                }
                if (changed) {
                    publishMovement()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                if (pointers.remove(id) != null) {
                    publishMovement()
                    invalidate()
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                clearInputs()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) clearInputs()
    }

    override fun onDetachedFromWindow() {
        clearInputs()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!controlsEnabled || width <= 0 || height <= 0) return

        drawDpad(canvas)
        drawActionButton(
            canvas = canvas,
            control = Control.JUMP,
            label = "JUMP",
            accent = gold,
            highlighted = false
        )
        val atDoor = nearbyDoorLabel != null
        drawActionButton(
            canvas = canvas,
            control = Control.USE,
            label = if (atDoor) "ENTER" else "USE",
            accent = if (atDoor) teal else cream,
            highlighted = atDoor
        )
        drawActionButton(
            canvas = canvas,
            control = Control.MAP,
            label = "ROOMS",
            accent = cream,
            highlighted = false
        )
    }

    private fun isPressed(control: Control) = pointers.values.any { it.control == control }

    private fun drawDpad(canvas: Canvas) {
        // Outer baseplate
        fillPaint.color = panelBg
        canvas.drawCircle(dpadCenter.x, dpadCenter.y, dpadRadius * 1.06f, fillPaint)
        strokePaint.color = panelRim
        strokePaint.strokeWidth = dp(1.5f)
        canvas.drawCircle(dpadCenter.x, dpadCenter.y, dpadRadius * 1.06f, strokePaint)

        val armThick = dpadRadius * 0.62f
        val armLen = dpadRadius * 0.94f
        val corner = dp(8f)

        // Cross shadow
        fillPaint.color = ink
        val hShadow = RectF(
            dpadCenter.x - armLen, dpadCenter.y - armThick / 2f + dp(2.5f),
            dpadCenter.x + armLen, dpadCenter.y + armThick / 2f + dp(2.5f)
        )
        val vShadow = RectF(
            dpadCenter.x - armThick / 2f, dpadCenter.y - armLen + dp(2.5f),
            dpadCenter.x + armThick / 2f, dpadCenter.y + armLen + dp(2.5f)
        )
        canvas.drawRoundRect(hShadow, corner, corner, fillPaint)
        canvas.drawRoundRect(vShadow, corner, corner, fillPaint)

        // Cross body
        fillPaint.color = keyIdle
        val hBody = RectF(
            dpadCenter.x - armLen, dpadCenter.y - armThick / 2f,
            dpadCenter.x + armLen, dpadCenter.y + armThick / 2f
        )
        val vBody = RectF(
            dpadCenter.x - armThick / 2f, dpadCenter.y - armLen,
            dpadCenter.x + armThick / 2f, dpadCenter.y + armLen
        )
        canvas.drawRoundRect(hBody, corner, corner, fillPaint)
        canvas.drawRoundRect(vBody, corner, corner, fillPaint)

        // Highlight individual pressed arms
        for (dir in listOf(Control.LEFT, Control.RIGHT, Control.UP, Control.DOWN)) {
            val rect = directionRects[dir] ?: continue
            val held = isPressed(dir)
            if (held) {
                fillPaint.color = keyPressed
                canvas.drawRoundRect(rect, dp(6f), dp(6f), fillPaint)
            }
            drawChevron(canvas, dir, rect, if (held) gold else cream)
        }

        // Center pivot dimple
        fillPaint.color = Color.argb(110, 12, 20, 32)
        canvas.drawCircle(dpadCenter.x, dpadCenter.y, dpadRadius * 0.17f, fillPaint)
    }

    private fun drawChevron(canvas: Canvas, dir: Control, rect: RectF, color: Int) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val sz = min(rect.width(), rect.height()) * 0.24f
        arrowPath.rewind()
        when (dir) {
            Control.LEFT -> {
                arrowPath.moveTo(cx - sz, cy)
                arrowPath.lineTo(cx + sz * 0.65f, cy - sz)
                arrowPath.lineTo(cx + sz * 0.65f, cy + sz)
            }
            Control.RIGHT -> {
                arrowPath.moveTo(cx + sz, cy)
                arrowPath.lineTo(cx - sz * 0.65f, cy - sz)
                arrowPath.lineTo(cx - sz * 0.65f, cy + sz)
            }
            Control.UP -> {
                arrowPath.moveTo(cx, cy - sz)
                arrowPath.lineTo(cx - sz, cy + sz * 0.65f)
                arrowPath.lineTo(cx + sz, cy + sz * 0.65f)
            }
            Control.DOWN -> {
                arrowPath.moveTo(cx, cy + sz)
                arrowPath.lineTo(cx - sz, cy - sz * 0.65f)
                arrowPath.lineTo(cx + sz, cy - sz * 0.65f)
            }
            else -> return
        }
        arrowPath.close()
        fillPaint.color = color
        canvas.drawPath(arrowPath, fillPaint)
    }

    private fun drawActionButton(
        canvas: Canvas,
        control: Control,
        label: String,
        accent: Int,
        highlighted: Boolean
    ) {
        val center = buttonCenters[control] ?: return
        val radius = buttonRadii[control] ?: return
        val held = isPressed(control)
        val pressOffset = if (held) dp(2f) else 0f

        // Base shadow
        fillPaint.color = ink
        canvas.drawCircle(center.x, center.y + dp(3f), radius, fillPaint)

        // Button face
        fillPaint.color = when {
            held -> keyPressed
            highlighted -> Color.rgb(26, 72, 74)
            else -> keyIdle
        }
        canvas.drawCircle(center.x, center.y + pressOffset, radius, fillPaint)

        // Rim
        strokePaint.color = if (held || highlighted || control == Control.JUMP) accent else panelRim
        strokePaint.strokeWidth = if (highlighted) dp(2.2f) else dp(1.5f)
        canvas.drawCircle(center.x, center.y + pressOffset, radius - dp(1f), strokePaint)

        // Label
        textPaint.color = if (held && control != Control.JUMP) gold else accent
        textPaint.textSize = when (control) {
            Control.MAP -> radius * 0.44f
            else -> radius * 0.40f
        }
        val baseline = center.y + pressOffset - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, center.x, baseline, textPaint)
    }
}
