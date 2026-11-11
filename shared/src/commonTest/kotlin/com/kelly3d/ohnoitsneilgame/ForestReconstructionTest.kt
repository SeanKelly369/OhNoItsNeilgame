package com.kelly3d.ohnoitsneilgame

import com.kelly3d.ohnoitsneilgame.maps.*
import kotlin.test.*

class ForestReconstructionTest {
    @Test fun threeRoomsHaveContinuousGroundAndClearWalkingSpace() {
        val map = ForestReconstruction.create()
        assertEquals(120, map.width)
        assertEquals(24, map.height)
        assertTrue(map.validationErrors().isEmpty())
        val tw = map.tileWidth.toFloat()
        val th = map.tileHeight.toFloat()
        for (x in 0 until map.width) {
            assertEquals(TileCollision.EMPTY, map.collision(x, ForestReconstruction.GROUND - 1))
            assertEquals(TileCollision.SOLID, map.collision(x, ForestReconstruction.GROUND))
            assertTrue(map.overlapsSolid(x * tw, ForestReconstruction.GROUND * th, x * tw + tw, (ForestReconstruction.GROUND + 1) * th))
            assertFalse(map.overlapsSolid(x * tw, (ForestReconstruction.GROUND - 1) * th, x * tw + tw, ForestReconstruction.GROUND * th))
        }
    }

    @Test fun landmarksAreDecorationNotAccidentalWalls() {
        val map = ForestReconstruction.create()
        assertEquals(29, map.tile(MapLayer.BACKGROUND, 7, 14)) // carved face
        assertEquals(18, map.tile(MapLayer.BACKGROUND, 105, 15)) // door arch
        assertEquals(20, map.tile(MapLayer.BACKGROUND, 103, 3)) // window
        assertEquals(TileCollision.EMPTY, map.collision(105, 18))
        assertEquals(-1, map.tile(MapLayer.BACKGROUND, 60, 5)) // open clearing
    }

    @Test fun generatedMapCanBeEditedAndRoundTrippedIndependently() {
        val original = ForestReconstruction.create()
        val encoded = TileMapCodec.encode(original)
        val edited = TileMapCodec.decode(encoded)
        assertEquals(encoded, TileMapCodec.encode(edited))
        edited.paint(MapLayer.TERRAIN, 50, 19, -1)
        assertEquals(0, original.tile(MapLayer.TERRAIN, 50, 19))
        assertEquals(-1, edited.tile(MapLayer.TERRAIN, 50, 19))
    }
}
