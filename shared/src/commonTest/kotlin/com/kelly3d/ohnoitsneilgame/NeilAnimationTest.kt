package com.kelly3d.ohnoitsneilgame

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NeilAnimationTest {
    @Test fun walkingUsesAllSixNewFramesInBothDirections() {
        for (direction in listOf(-1, 1)) {
            val game = NeilGame().apply { start(); x = 200f }
            val seen = mutableSetOf<Int>()
            repeat(72) {
                game.step(1f / 120f, direction)
                seen += game.spriteFrame
            }
            assertEquals((17..22).toSet(), seen)
            game.step(1f / 120f, 0)
            assertEquals(16, game.spriteFrame)
        }
    }


    @Test fun walkingStopsWhenBlockedOrReleased() {
        val game = NeilGame().apply { start() }
        game.step(1f / 60f, -1)
        assertTrue(game.spriteFrame in 17..22)
        assertEquals(-1, game.facing)
        game.step(1f / 60f, 0)
        assertEquals(16, game.spriteFrame)
        game.x = 0f
        game.step(1f / 60f, -1)
        assertFalse(game.moving)
    }

    @Test fun jumpFallAndLandingFollowPhysics() {
        val game = NeilGame().apply { start(); jump() }
        assertEquals(8, game.spriteFrame)
        var sawFall = false
        repeat(120) {
            game.step(1f / 120f, 0)
            if (!game.grounded && game.vy > 0f) {
                sawFall = true
                assertEquals(9, game.spriteFrame)
            }
            if (game.grounded) return@repeat
        }
        assertTrue(sawFall)
        assertTrue(game.grounded)
        // Trigger a short drop to test the landing pose at the contact step.
        game.y = 197f; game.grounded = false; game.vy = 100f
        game.step(1f / 60f, 0)
        assertEquals(10, game.spriteFrame)
        repeat(20) { game.step(1f / 60f, 0) }
        assertEquals(16, game.spriteFrame)
    }

    @Test fun useHurtAndRestartResetAnimation() {
        val game = NeilGame().apply { start(); interact() }
        assertEquals(26, game.spriteFrame)
        repeat(8) { game.step(1f / 60f, 0) }
        assertEquals(26, game.spriteFrame)
        game.y = 300f
        game.step(1f / 60f, 0)
        assertEquals(11, game.spriteFrame)
        game.start()
        assertEquals(16, game.spriteFrame)
        assertEquals(0f, game.actionTime, 0f)
        assertEquals(0f, game.landingTime, 0f)
    }
}
