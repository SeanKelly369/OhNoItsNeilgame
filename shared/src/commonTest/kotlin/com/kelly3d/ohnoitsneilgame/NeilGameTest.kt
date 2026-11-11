package com.kelly3d.ohnoitsneilgame

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NeilGameTest {
    private fun tick(game: NeilGame, frames: Int, direction: Int = 0) {
        repeat(frames) { game.step(1f / 120f, direction) }
    }

    @Test fun jumpLandsOnRaisedPlatformAndCannotDoubleJump() {
        val game = NeilGame().apply { start(); x = 145f }
        game.jump()
        tick(game, 30, 1)
        val fallingSpeed = game.vy
        game.jump()
        assertEquals(fallingSpeed, game.vy, 0f)
        tick(game, 30, 1)
        tick(game, 60)
        assertEquals(160f, game.y, 0.01f)
        assertTrue(game.grounded)
    }

    @Test fun treetopPlankIsReachableByPlatforming() {
        val game = NeilGame().apply { start(); x = 145f }
        fun moveTo(target: Float) {
            var frames = 0
            while (game.x < target && frames++ < 600) tick(game, 1, 1)
            assertTrue(game.x >= target, "Reached $target")
        }
        game.jump(); moveTo(200f); tick(game, 100)
        moveTo(230f); game.jump(); moveTo(290f); tick(game, 100)
        moveTo(340f); game.jump(); moveTo(430f); tick(game, 100)
        assertTrue(game.plank)
        assertEquals(92f, game.y, 0.01f)
    }

    @Test fun bridgeRequiresPlankAndCreatesWalkableSurface() {
        val game = NeilGame().apply { start(); x = 620f }
        game.interact()
        assertFalse(game.bridge)
        game.plank = true
        game.interact()
        assertTrue(game.bridge)
        assertFalse(game.plank)
        tick(game, 150, 1)
        assertTrue(game.x > 750f)
        assertEquals(198f, game.y, 0.01f)
        assertEquals(3, game.hearts)
    }

    @Test fun fasterMovementStillRequiresTheBridge() {
        val game = NeilGame().apply { start(); x = 646f }
        game.jump()
        tick(game, 120, 1)
        assertFalse(game.bridge)
        assertEquals(2, game.hearts)
        assertTrue(game.x < 650f, "Neil respawns on the near bank")
    }

    @Test fun keyIsReachableFromTheFarBank() {
        val game = NeilGame().apply { start(); bridge = true; x = 885f }
        fun moveTo(target: Float) {
            repeat(600) { if (game.x < target) tick(game, 1, 1) }
            assertTrue(game.x >= target)
        }
        game.jump(); moveTo(940f); tick(game, 100)
        moveTo(975f); game.jump(); moveTo(1040f); tick(game, 100)
        moveTo(1090f); game.jump(); moveTo(1170f); tick(game, 100)
        assertTrue(game.key)
        assertEquals(88f, game.y, 0.01f)
    }

    @Test fun fallingRespawnsAndPreservesPuzzleProgress() {
        val game = NeilGame().apply { start(); plank = true; x = 700f; y = 290f }
        tick(game, 1)
        assertEquals(2, game.hearts)
        assertEquals(60f, game.x, 0f)
        assertTrue(game.plank)
        assertTrue(game.invulnerable > 0f)
    }

    @Test fun cottageRequiresKeyAndRestartClearsProgress() {
        val game = NeilGame().apply { start(); x = 1330f }
        game.interact()
        assertEquals(NeilGame.Phase.PLAYING, game.phase)
        game.key = true
        game.interact()
        assertEquals(NeilGame.Phase.WON, game.phase)
        val wonX = game.x
        tick(game, 120, -1)
        assertEquals(wonX, game.x, 0f)
        game.start()
        assertFalse(game.key)
        assertFalse(game.bridge)
        assertTrue(game.collected.isEmpty())
        assertEquals(3, game.hearts)
    }
}
