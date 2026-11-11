package com.kelly3d.ohnoitsneilgame

import com.kelly3d.ohnoitsneilgame.maps.*
import kotlin.test.*

class TileMapTest {
    private fun map() = TileMap(4, 3, 16, 8, 3, 2, "tiles/forest.png")

    @Test fun roundTripPreservesIndependentLayersCollisionAndSpawn() {
        val original = map().apply {
            paint(MapLayer.BACKGROUND, 2, 1, 0)
            paint(MapLayer.TERRAIN, 2, 1, 5)
            paint(MapLayer.FOREGROUND, 2, 1, 2)
            markCollision(2, 1, TileCollision.LADDER)
            markCollision(3, 2, TileCollision.ONE_WAY)
            setSpawn(0, 2)
        }
        val decoded = TileMapCodec.decode(TileMapCodec.encode(original).replace("\n", "\r\n"))
        assertEquals(TileMapCodec.encode(original), TileMapCodec.encode(decoded))
        decoded.paint(MapLayer.TERRAIN, 2, 1, -1)
        assertEquals(0, decoded.tile(MapLayer.BACKGROUND, 2, 1))
        assertEquals(2, decoded.tile(MapLayer.FOREGROUND, 2, 1))
        assertEquals(TileCollision.LADDER, decoded.collision(2, 1))
        assertEquals(5, original.tile(MapLayer.TERRAIN, 2, 1))
    }

    @Test fun atlasCoordinatesAndBounds() {
        val map = map()
        assertEquals(32, map.sourceX(5)); assertEquals(8, map.sourceY(5))
        assertFailsWith<IllegalArgumentException> { map.sourceX(-1) }
        assertFailsWith<IllegalArgumentException> { map.paint(MapLayer.TERRAIN, 0, 0, 6) }
        assertFailsWith<IllegalArgumentException> { map.paint(MapLayer.TERRAIN, -1, 0, 0) }
        assertFailsWith<IllegalArgumentException> { map.setSpawn(4, 0) }
    }

    @Test fun collisionUsesHalfOpenBoundsAndDoesNotTreatLaddersAsSolids() {
        val map = map().apply {
            markCollision(1, 1, TileCollision.SOLID)
            markCollision(2, 1, TileCollision.LADDER)
        }
        assertFalse(map.overlapsSolid(0f, 0f, 16f, 8f))
        assertTrue(map.overlapsSolid(15f, 7f, 17f, 9f))
        assertFalse(map.overlapsSolid(32f, 8f, 48f, 16f))
        assertFalse(map.overlapsSolid(-100f, -100f, -1f, -1f))
        assertFalse(map.overlapsSolid(100f, 100f, 200f, 200f))
        assertFalse(map.overlapsSolid(16f, 8f, 16f, 16f))
        assertFailsWith<IllegalArgumentException> { map.overlapsSolid(Float.NaN, 0f, 1f, 1f) }
    }

    @Test fun spawnValidation() {
        val map = map()
        map.markCollision(map.spawnX, map.spawnY, TileCollision.SOLID)
        assertEquals(1, map.validationErrors().size)
        map.setSpawn(0, 0)
        assertTrue(map.validationErrors().isEmpty())
    }

    @Test fun malformedFilesFailBeforeUse() {
        val text = TileMapCodec.encode(map())
        for (invalid in listOf(
            text.replace("NEILMAP 1", "NEILMAP 2"),
            text.replace("4 3 16 8 3 2", "100000 100000 16 8 3 2"),
            text.replace("tiles/forest.png", "../forest.png"),
            text.substringBefore("COLLISION"),
            text + "junk",
            text.replaceFirst("-1 -1 -1 -1", "6 -1 -1 -1"),
            text.replaceFirst("-1 -1 -1 -1", "-1 -1"),
            text.replace("COLLISION\n0 0 0 0", "COLLISION\n9 0 0 0")
        )) {
            assertFailsWith<IllegalArgumentException> { TileMapCodec.decode(invalid) }
        }
    }
}
