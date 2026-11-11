package com.kelly3d.ohnoitsneilgame

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AmigaRoomsTest {
    @Test fun sheetOrderAndAssetNamesAreStable() {
        assertEquals(100, AmigaRooms.rooms.size)
        assertEquals(100, AmigaRooms.rooms.map { it.assetName }.toSet().size)
        assertEquals("amiga/room_00.png", AmigaRooms.room(0).assetName)
        assertEquals("amiga/room_99.png", AmigaRooms.room(99).assetName)
        assertEquals(4, AmigaRooms.room(99).column)
        assertEquals(19, AmigaRooms.room(99).row)
        assertEquals("The Tree House", AmigaRooms.room(2).label)
        assertEquals("[unnamed]", AmigaRooms.room(99).label)
    }

    @Test fun invalidRoomIdsAreRejected() {
        assertFailsWith<IllegalArgumentException> { AmigaRooms.room(-1) }
        assertFailsWith<IllegalArgumentException> { AmigaRooms.room(100) }
    }
}
