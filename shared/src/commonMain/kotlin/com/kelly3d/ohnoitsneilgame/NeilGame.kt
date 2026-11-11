package com.kelly3d.ohnoitsneilgame

import kotlin.math.abs
import kotlin.math.sin

/** Platform physics use world pixels and seconds, independent of the display. */
class NeilGame {
    companion object {
        const val WALK_SPEED = 168f
    }

    data class Platform(val x: Float, val y: Float, val width: Float)
    enum class Phase { TITLE, PLAYING, WON }
    val platforms = listOf(
        Platform(0f, 222f, 650f), Platform(790f, 222f, 650f),
        Platform(170f, 184f, 75f), Platform(275f, 148f, 80f),
        Platform(395f, 116f, 85f), Platform(910f, 184f, 80f),
        Platform(1020f, 145f, 85f), Platform(1135f, 112f, 90f)
    )
    private val bridgedPlatforms = platforms + Platform(650f, 222f, 140f)
    var phase = Phase.TITLE
    var x = 60f
    var y = 198f
    var vy = 0f
    var grounded = true
    var facing = 1
    var hearts = 3
    var plank = false
    var bridge = false
    var key = false
    var time = 0f
    var invulnerable = 0f
    var moving = false
        private set
    var actionTime = 0f
        private set
    var landingTime = 0f
        private set
    val spriteFrame: Int get() = when {
        invulnerable > 1.65f -> NeilSpriteFrames.HURT
        !grounded -> if (vy < 0f) NeilSpriteFrames.JUMP else NeilSpriteFrames.FALL
        landingTime > 0f -> NeilSpriteFrames.LAND
        actionTime > 0f -> NeilSpriteFrames.POINT_RIGHT
        moving -> NeilSpriteFrames.WALK_START +
            (time * 10f).toInt() % NeilSpriteFrames.WALK_COUNT
        else -> NeilSpriteFrames.IDLE_RIGHT
    }
    var message = "Find the plank in the treetops."
    var messageTime = 0f
    val collected = mutableSetOf<Int>()
    val stars = listOf(210f to 166f, 310f to 130f, 440f to 98f, 580f to 200f,
        850f to 200f, 950f to 166f, 1060f to 127f, 1180f to 94f)
    val slimeX: Float get() = 500f + sin(time * 1.5f) * 65f
    val secondSlimeX: Float get() = 1250f + sin(time * 1.8f) * 45f
    val objective: String get() = when {
        !plank && !bridge -> "Find the wooden plank"
        !bridge -> "Use the plank beside the river"
        !key -> "Climb the ruins • find the key"
        else -> "Use the key at Neil's cottage"
    }
    val prompt: String get() = when {
        x in 580f..650f && !bridge -> if (plank) "Repair bridge" else "Inspect river"
        x > 1290f -> if (key) "Unlock cottage" else "Inspect door"
        else -> "Look"
    }

    fun start() {
        phase = Phase.PLAYING
        x = 60f; y = 198f; vy = 0f; grounded = true; facing = 1
        hearts = 3; plank = false; bridge = false; key = false
        time = 0f; invulnerable = 0f; collected.clear()
        moving = false; actionTime = 0f; landingTime = 0f
        say("Oh no! Neil's locked out. Find a way home.")
    }

    fun say(text: String) { message = text; messageTime = 4f }
    fun jump() {
        if (phase == Phase.PLAYING && grounded) {
            vy = -300f
            grounded = false
            landingTime = 0f
        }
    }

    fun interact() {
        if (phase != Phase.PLAYING) return
        actionTime = 0.48f
        when {
            x in 580f..650f && !bridge -> {
                if (plank) {
                    bridge = true; plank = false
                    say("A perfect fit! Cross to the old ruins.")
                } else say("The bridge is missing a plank. Try the treetops!")
            }
            x > 1290f -> {
                if (key) phase = Phase.WON
                else say("Locked! A brass key glints above the ruins.")
            }
            else -> say(objective)
        }
    }

    fun step(dt: Float, direction: Int) {
        if (phase != Phase.PLAYING) return
        val delta = dt.coerceIn(0f, 1f / 30f)
        time += delta
        actionTime = (actionTime - delta).coerceAtLeast(0f)
        landingTime = (landingTime - delta).coerceAtLeast(0f)
        val wasGrounded = grounded
        val oldX = x
        messageTime = (messageTime - delta).coerceAtLeast(0f)
        invulnerable = (invulnerable - delta).coerceAtLeast(0f)
        if (direction != 0) facing = direction
        x = (x + direction * WALK_SPEED * delta).coerceIn(0f, 1420f)
        moving = x != oldX
        val oldBottom = y + 24f
        vy += 930f * delta
        y += vy * delta
        grounded = false
        val surfaces = if (bridge) bridgedPlatforms else platforms
        if (vy >= 0f) {
            for (p in surfaces) {
                if (x + 17f > p.x && x + 3f < p.x + p.width &&
                    oldBottom <= p.y + 0.5f && y + 24f >= p.y) {
                    y = p.y - 24f; vy = 0f; grounded = true
                    break
                }
            }
        }
        if (!wasGrounded && grounded) landingTime = 0.12f
        if (!plank && !bridge && abs(x - 429f) < 24 && abs(y - 92f) < 27) {
            plank = true; say("Found a plank! Take it to the river.")
        }
        if (!key && abs(x - 1170f) < 24 && abs(y - 88f) < 27) {
            key = true; say("Neil's key! The cottage is just ahead.")
        }
        stars.forEachIndexed { i, (sx, sy) ->
            if (abs(x + 10f - sx) < 18 && abs(y + 12f - sy) < 22) collected.add(i)
        }
        if (y > 285f) hurt()
        else if (invulnerable == 0f && y + 24f > 209f &&
            (abs(x + 10f - slimeX) < 20 || abs(x + 10f - secondSlimeX) < 20)) hurt()
    }

    private fun hurt() {
        hearts--
        moving = false; actionTime = 0f; landingTime = 0f
        if (hearts <= 0) {
            hearts = 3
            say("Oh no, Neil! A fresh start from the riverbank.")
        } else say("Ouch! Mind the moss monsters.")
        x = if (bridge && x > 720f) 810f else 60f
        y = 198f; vy = 0f; grounded = true; invulnerable = 2f
    }
}
