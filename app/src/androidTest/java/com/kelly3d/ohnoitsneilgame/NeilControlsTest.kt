package com.kelly3d.ohnoitsneilgame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises actual multi-pointer MotionEvents through the view and physics loop. */
@RunWith(AndroidJUnit4::class)
class NeilControlsTest {
    private class Harness {
        val game = NeilGame().apply { start() }
        val view = NeilGameView(InstrumentationRegistry.getInstrumentation().targetContext, game)
        private val downTime = SystemClock.uptimeMillis()
        private var frame = 1_000_000_000L

        init {
            // A 1:1 viewport makes touch coordinates match the on-screen controls.
            view.layout(0, 0, 480, 360)
            val bitmap = Bitmap.createBitmap(480, 360, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap.recycle()
            view.resume()
            view.doFrame(frame)
        }

        fun touch(action: Int, vararg fingers: Triple<Int, Float, Float>) {
            val properties = fingers.map { (id, _, _) ->
                MotionEvent.PointerProperties().apply {
                    this.id = id
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }.toTypedArray()
            val coords = fingers.map { (_, x, y) ->
                MotionEvent.PointerCoords().apply {
                    this.x = x; this.y = y; pressure = 1f; size = 1f
                }
            }.toTypedArray()
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action, fingers.size, properties, coords,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            try { view.dispatchTouchEvent(event) } finally { event.recycle() }
        }

        fun key(action: Int, code: Int, repeats: Int = 0) {
            view.dispatchKeyEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), action, code, repeats))
        }

        fun advance() {
            repeat(6) {
                frame += 16_666_667L
                view.doFrame(frame)
            }
        }
    }

    private fun withControls(test: Harness.() -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val harness = Harness()
            try { harness.test() } finally { harness.view.pause() }
        }
    }

    @Test fun thumbDriftAndReentryKeepMovingUntilRelease() = withControls {
        touch(MotionEvent.ACTION_DOWN, Triple(7, 115f, 315f))
        advance()
        var before = game.x
        touch(MotionEvent.ACTION_MOVE, Triple(7, 153f, 343f))
        advance()
        assertTrue("Small drift must not stop movement", game.x > before + 10f)
        before = game.x
        touch(MotionEvent.ACTION_MOVE, Triple(7, 115f, 315f))
        advance()
        assertTrue("Returning to the arrow must still move", game.x > before + 10f)
        touch(MotionEvent.ACTION_UP, Triple(7, 115f, 315f))
        before = game.x
        advance()
        assertEquals("Release stops movement", before, game.x, 0.001f)
    }

    @Test fun crossingTheGapDoesNotFlickerAndDeliberateSlideReverses() = withControls {
        touch(MotionEvent.ACTION_DOWN, Triple(3, 115f, 315f))
        for (x in listOf(84f, 80f, 76f, 80f)) {
            val before = game.x
            touch(MotionEvent.ACTION_MOVE, Triple(3, x, 315f))
            advance()
            assertTrue("Movement stays right near the shared edge", game.x > before)
        }
        val before = game.x
        touch(MotionEvent.ACTION_MOVE, Triple(3, 50f, 315f))
        advance()
        assertTrue("Sliding well into left reverses direction", game.x < before)
        touch(MotionEvent.ACTION_CANCEL, Triple(3, 50f, 315f))
        val cancelled = game.x
        advance()
        assertEquals("Cancellation clears held movement", cancelled, game.x, 0.001f)
    }

    @Test fun latestArrowWinsAndReleasingItRestoresTheHeldArrowDuringJump() = withControls {
        touch(MotionEvent.ACTION_DOWN, Triple(7, 50f, 315f))
        advance()
        var before = game.x
        touch(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            Triple(7, 50f, 315f), Triple(12, 115f, 315f)
        )
        advance()
        assertTrue("The newly pressed right arrow must move right", game.x > before + 10f)
        assertEquals(1, game.facing)

        touch(
            MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            Triple(7, 50f, 315f), Triple(12, 115f, 315f), Triple(3, 425f, 315f)
        )
        before = game.x
        advance()
        assertTrue("Jump must preserve the selected direction", game.x > before + 10f)
        assertTrue("Neil jumps while both arrows are held", game.y < 198f)

        touch(
            MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            Triple(7, 50f, 315f), Triple(12, 115f, 315f), Triple(3, 425f, 315f)
        )
        before = game.x
        advance()
        assertTrue("Releasing right resumes the still-held left arrow in the air", game.x < before - 10f)
        assertEquals(-1, game.facing)
    }

    @Test fun jumpingWorksInEitherDirectionAndReleasingMovementStopsOnlyHorizontalMotion() {
        for (direction in listOf(-1, 1)) withControls {
            val arrowX = if (direction < 0) 50f else 115f
            val before = game.x
            touch(MotionEvent.ACTION_DOWN, Triple(7, arrowX, 315f))
            touch(
                MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                Triple(7, arrowX, 315f), Triple(12, 425f, 315f)
            )
            advance()
            assertTrue("Neil moves in the held direction while jumping", (game.x - before) * direction > 10f)
            assertEquals(direction, game.facing)
            assertTrue(game.y < 198f)
            touch(MotionEvent.ACTION_POINTER_UP, Triple(7, arrowX, 315f), Triple(12, 425f, 315f))
            val stoppedX = game.x
            val airborneY = game.y
            advance()
            assertEquals("Releasing movement stops horizontal motion", stoppedX, game.x, 0.001f)
            assertTrue("Jump continues after movement is released", game.y < airborneY)
        }
    }

    @Test fun movementCanStartAfterJumpInEitherDirection() {
        for (direction in listOf(-1, 1)) withControls {
            touch(MotionEvent.ACTION_DOWN, Triple(12, 425f, 315f))
            val before = game.x
            touch(
                MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                Triple(12, 425f, 315f), Triple(7, if (direction < 0) 50f else 115f, 315f)
            )
            advance()
            assertTrue("A second finger steers the jump", (game.x - before) * direction > 10f)
            assertTrue(game.y < 198f)
        }
    }

    @Test fun keyboardReversalAndJumpPreserveTheOrderOfHeldKeys() = withControls {
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_D)
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE)
        var before = game.x
        advance()
        assertTrue("The last arrow moves right while Space jumps", game.x > before + 10f)
        assertTrue(game.y < 198f)

        key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_D, repeats = 1)
        before = game.x
        advance()
        assertTrue("Releasing the newest arrow restores A; key repeats cannot override it", game.x < before - 10f)
        assertEquals(-1, game.facing)

        key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A)
        key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE)
        before = game.x
        advance()
        assertTrue("D remains held after releasing A and Space", game.x > before + 10f)
        key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_D)
        before = game.x
        advance()
        assertEquals(before, game.x, 0.001f)
    }

    @Test fun touchMovementCombinesWithKeyboardJumpAndRestoresHeldKeyboardDirection() = withControls {
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_D)
        touch(MotionEvent.ACTION_DOWN, Triple(7, 50f, 315f))
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE)
        var before = game.x
        advance()
        assertTrue("Touch left overrides the earlier right key", game.x < before - 10f)
        assertTrue("Space jumps while touch movement is held", game.y < 198f)
        touch(MotionEvent.ACTION_UP, Triple(7, 50f, 315f))
        before = game.x
        advance()
        assertTrue("Releasing touch restores the held right key", game.x > before + 10f)
    }

    @Test fun losingWindowFocusClearsTouchAndKeyboardMovement() = withControls {
        touch(MotionEvent.ACTION_DOWN, Triple(7, 50f, 315f))
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_D)
        advance()
        view.onWindowFocusChanged(false)
        val stopped = game.x
        advance()
        assertEquals("Neither touch nor keyboard input may stick after focus loss", stopped, game.x, 0.001f)
        view.onWindowFocusChanged(true)
        advance()
        assertEquals("Regaining focus must not restore stale inputs", stopped, game.x, 0.001f)
    }

    @Test fun jumpFingerReleaseDoesNotReleaseMovementFinger() = withControls {
        touch(MotionEvent.ACTION_DOWN, Triple(7, 115f, 315f))
        touch(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            Triple(7, 115f, 315f), Triple(12, 425f, 315f)
        )
        advance()
        assertTrue("Second finger jumps", game.y < 198f)
        // Change pointer order to verify IDs, rather than array indices, own controls.
        touch(MotionEvent.ACTION_POINTER_UP, Triple(12, 425f, 315f), Triple(7, 115f, 315f))
        val before = game.x
        advance()
        assertTrue("Movement continues after Jump is released", game.x > before + 10f)
        touch(MotionEvent.ACTION_UP, Triple(7, 115f, 315f))
        val released = game.x
        advance()
        assertEquals(released, game.x, 0.001f)
    }
}
