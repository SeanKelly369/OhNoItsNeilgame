package com.kelly3d.ohnoitsneilgame

import kotlin.math.abs

/** Fixed-step simulation with a shared interpolated position for Neil and the camera. */
class NeilGameLoop(private val game: NeilGame) {
    private val step = 1.0 / 120.0
    private var remainder = 0.0
    private var previousX = game.x
    private var previousY = game.y
    var renderX = game.x
        private set
    var renderY = game.y
        private set

    fun reset() {
        remainder = 0.0
        previousX = game.x; previousY = game.y
        renderX = game.x; renderY = game.y
    }

    fun advance(seconds: Double, direction: Int) {
        remainder += seconds.coerceIn(0.0, 0.25)
        while (remainder >= step) {
            previousX = game.x; previousY = game.y
            game.step(step.toFloat(), direction)
            // Respawns are teleports, not a movement to interpolate across the map.
            if (abs(game.x - previousX) > 8f || abs(game.y - previousY) > 8f) {
                previousX = game.x; previousY = game.y
            }
            remainder -= step
        }
        val alpha = (remainder / step).toFloat()
        renderX = previousX + (game.x - previousX) * alpha
        renderY = previousY + (game.y - previousY) * alpha
    }
}
