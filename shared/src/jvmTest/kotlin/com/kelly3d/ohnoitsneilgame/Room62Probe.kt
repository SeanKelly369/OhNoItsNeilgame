package com.kelly3d.ohnoitsneilgame

import java.io.File
import kotlin.test.Test

class Room62Probe {
    @Test fun probe() {
        val text = File("../art/sprites/amiga/world.json").readText()
        val rooms = Regex("\"collision\":\\[([0-9,]*)]").findAll(text).mapIndexed { i, m ->
            OriginalGame.Room(i, "R$i", m.groupValues[1].split(',').map { it.toInt() }, true)
        }.toList()
        val g = OriginalGame(rooms, emptyList())
        g.enter(62, 164f, 144f)
        repeat(30) { g.step(1f / 120, 0) }
        println("start x=${g.x} y=${g.y} ladder=${g.onLadder()} grounded=${g.grounded}")
        repeat(360) { i ->
            g.step(1f / 120, 0, -1)
            if (i % 30 == 0) println("t=$i room=${g.room} x=${g.x} y=${g.y} vy=${g.vy} climbing=${g.climbing} ladder=${g.onLadder()}")
        }
    }
}
