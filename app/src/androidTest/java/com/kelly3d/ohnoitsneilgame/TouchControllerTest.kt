package com.kelly3d.ohnoitsneilgame

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises TouchControllerView multi-touch D-pad, jumping, and entering rooms through doors. */
@RunWith(AndroidJUnit4::class)
class TouchControllerTest {
    private class Harness {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gameView = OriginalGameView(context)
        val controller = TouchControllerView(context)
        private val downTime = SystemClock.uptimeMillis()
        private var frame = 1_000_000_000L

        init {
            gameView.game.restart()
            controller.listener = object : TouchControllerView.Listener {
                override fun onMoveChanged(direction: Int, climb: Int) = gameView.setControllerMovement(direction, climb)
                override fun onJumpPressed() = gameView.pressJump()
                override fun onUsePressed() = gameView.pressUse()
                override fun onUpPressed() = gameView.pressUpAction()
                override fun onMapPressed() = gameView.toggleMap()
            }
            gameView.onControlsStateChanged = { enabled, doorLabel ->
                controller.controlsEnabled = enabled
                controller.nearbyDoorLabel = doorLabel
            }
            val root = FrameLayout(context).apply {
                addView(gameView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                addView(controller, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }
            root.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1280, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, 1280, 720)
            gameView.resume()
            // Dismiss title screen via a tap in the center of the viewport.
            dispatchTouch(gameView, MotionEvent.ACTION_DOWN, Triple(1, 640f, 360f))
            dispatchTouch(gameView, MotionEvent.ACTION_UP, Triple(1, 640f, 360f))
            advance(2)
        }

        fun dispatchTouch(target: android.view.View, action: Int, vararg fingers: Triple<Int, Float, Float>): Boolean {
            val properties = fingers.map { (id, _, _) ->
                MotionEvent.PointerProperties().apply {
                    this.id = id
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }.toTypedArray()
            val coords = fingers.map { (_, x, y) ->
                MotionEvent.PointerCoords().apply {
                    this.x = x
                    this.y = y
                    pressure = 1f
                    size = 1f
                }
            }.toTypedArray()
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action, fingers.size, properties, coords,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            return try {
                target.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }

        fun touchController(action: Int, vararg fingers: Triple<Int, Float, Float>) =
            dispatchTouch(controller, action, *fingers)

        fun advance(frames: Int = 8) {
            repeat(frames) {
                frame += 16_666_667L
                gameView.doFrame(frame)
            }
        }
    }

    private fun withController(test: Harness.() -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val harness = Harness()
            try {
                harness.test()
            } finally {
                harness.gameView.pause()
            }
        }
    }

    @Test
    fun dpadMovesSlidesAndCombinesWithJump() = withController {
        val right = requireNotNull(controller.centerOf(TouchControllerView.Control.RIGHT))
        val left = requireNotNull(controller.centerOf(TouchControllerView.Control.LEFT))
        val jump = requireNotNull(controller.centerOf(TouchControllerView.Control.JUMP))

        val startX = gameView.game.x
        assertTrue(touchController(MotionEvent.ACTION_DOWN, Triple(1, right.x, right.y)))
        advance(10)
        assertTrue("D-pad right moves Neil right", gameView.game.x > startX + 8f)

        // Multi-touch jump while holding right
        assertTrue(
            touchController(
                MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                Triple(1, right.x, right.y),
                Triple(2, jump.x, jump.y)
            )
        )
        advance(4)
        assertTrue("Neil jumps while moving right", gameView.game.y < 144f)
        touchController(
            MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            Triple(1, right.x, right.y),
            Triple(2, jump.x, jump.y)
        )

        // Slide thumb across D-pad from right to left without lifting
        val peakX = gameView.game.x
        touchController(MotionEvent.ACTION_MOVE, Triple(1, left.x, left.y))
        advance(10)
        assertTrue("Sliding to D-pad left reverses movement", gameView.game.x < peakX - 6f)

        touchController(MotionEvent.ACTION_UP, Triple(1, left.x, left.y))
        val stoppedX = gameView.game.x
        advance(6)
        assertEquals("Releasing D-pad stops horizontal movement", stoppedX, gameView.game.x, 0.01f)
    }

    @Test
    fun walkingToTowerDoorAndPressingEnterOrUpEntersRooms() = withController {
        val right = requireNotNull(controller.centerOf(TouchControllerView.Control.RIGHT))
        val use = requireNotNull(controller.centerOf(TouchControllerView.Control.USE))
        val up = requireNotNull(controller.centerOf(TouchControllerView.Control.UP))

        assertEquals(62, gameView.game.room)
        touchController(MotionEvent.ACTION_DOWN, Triple(1, right.x, right.y))
        advance(90)
        touchController(MotionEvent.ACTION_UP, Triple(1, right.x, right.y))

        assertNotNull("Door prompt should activate near tower exit", controller.nearbyDoorLabel)
        assertTrue(touchController(MotionEvent.ACTION_DOWN, Triple(2, use.x, use.y)))
        touchController(MotionEvent.ACTION_UP, Triple(2, use.x, use.y))
        advance(2)
        assertEquals("Tapping ENTER/USE enters Outside the Tower", 15, gameView.game.room)

        // Pressing UP at the outside tower door returns to Room 62
        assertNotNull(controller.nearbyDoorLabel)
        assertTrue(touchController(MotionEvent.ACTION_DOWN, Triple(3, up.x, up.y)))
        touchController(MotionEvent.ACTION_UP, Triple(3, up.x, up.y))
        advance(2)
        assertEquals("Pressing UP at doorway returns to The Beginning", 62, gameView.game.room)
    }
}
