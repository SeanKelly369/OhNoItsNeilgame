package com.kelly3d.ohnoitsneilgame

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OriginalWorldDataTest {
    @Test
    fun parsesMinimalWorldJson() {
        val json = """
            {"rooms":[{"id":0,"name":"The Tower","collision":[50,0,10]}],"used":[0,62],"objects":[[97,63,150,120,0,0,0]],"bobs":[],"objectNames":["Brass Key"]}
        """.trimIndent()
        val (rooms, items) = OriginalWorldData.parse(json)
        assertEquals(100, rooms.size)
        assertTrue(rooms[0].occupied)
        assertTrue(rooms[62].occupied)
        assertEquals("The Tower", rooms[0].name)
        assertEquals(50, rooms[0].collision[0])
        assertEquals(1, items.size)
        assertEquals(97, items[0].sprite)
        assertEquals(62, items[0].initialRoom)
        assertEquals("Brass Key", items[0].name)
    }
}
