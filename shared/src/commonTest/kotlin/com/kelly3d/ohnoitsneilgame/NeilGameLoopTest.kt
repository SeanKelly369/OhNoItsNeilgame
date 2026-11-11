package com.kelly3d.ohnoitsneilgame

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NeilGameLoopTest {
    @Test fun walkingIsSmoothAtDifferentRefreshRates() {
        for (hz in listOf(60, 90, 120, 144)) {
            val game = NeilGame().apply { start() }
            val loop = NeilGameLoop(game)
            // Prime the previous/current pair (one physics tick of render latency).
            repeat(2) { loop.advance(1.0 / hz, 1) }
            repeat(hz / 2) {
                val before = loop.renderX
                loop.advance(1.0 / hz, 1)
                assertEquals(NeilGame.WALK_SPEED / hz, loop.renderX - before, 0.002f,
                    "Even displacement at $hz Hz")
            }
        }
    }

    @Test fun irregularFramesKeepRealTimeSpeed() {
        val game = NeilGame().apply { start() }
        val loop = NeilGameLoop(game)
        val frames = listOf(0.012, 0.024, 0.009, 0.041, 0.014)
        var elapsed = 0.0
        repeat(5) {
            frames.forEach { dt ->
                elapsed += dt
                loop.advance(dt, 1)
                val expected = 60 + NeilGame.WALK_SPEED * (elapsed - 1.0 / 120)
                assertEquals(expected.toFloat(), loop.renderX, 0.002f)
            }
        }
    }

    @Test fun reversalAndReleaseSettleWithinOnePhysicsStep() {
        val game = NeilGame().apply { start() }
        val loop = NeilGameLoop(game)
        repeat(20) { loop.advance(1.0 / 60, 1) }
        val before = loop.renderX
        loop.advance(1.0 / 60, -1)
        assertTrue(loop.renderX <= before + 0.001f)
        loop.advance(1.0 / 60, -1)
        assertTrue(loop.renderX < before)
        loop.advance(1.0 / 60, 0)
        val stopped = loop.renderX
        repeat(20) { loop.advance(1.0 / 60, 0) }
        assertEquals(stopped, loop.renderX, 0.001f)
    }

    @Test fun respawnDoesNotSweepCameraAcrossTheMap() {
        val game = NeilGame().apply { start(); x = 700f; y = 290f }
        val loop = NeilGameLoop(game)
        loop.advance(0.01, 0)
        assertEquals(60f, loop.renderX, 0.001f)
        assertTrue(abs(loop.renderY - 198f) < 0.01f)
    }
}
