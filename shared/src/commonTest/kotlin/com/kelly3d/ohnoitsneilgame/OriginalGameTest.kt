package com.kelly3d.ohnoitsneilgame

import kotlin.test.*

class OriginalGameTest {
    private fun world(tile: (Int,Int)->Int = {_,y->if(y==11)50 else 0}, items:List<OriginalGame.Item> = emptyList()) =
        OriginalGame(List(100) { OriginalGame.Room(it,"Room $it",List(240) {i->tile(i%20,i/20)},true) },items)
    @Test fun saveRestoresInventoryDroppedItemsBonusesAndVisitedRooms() {
        fun items() = listOf(
            OriginalGame.Item(0, 97, "Key", 62, 152f, 134f, 0),
            OriginalGame.Item(1, 98, "Plank", 62, 152f, 134f, 0),
            OriginalGame.Item(2, 99, "Star", 62, 152f, 134f, 2)
        )
        val original = world(items = items())
        original.objects.forEach(original::collect)
        original.enter(15, 80f, 120f)
        original.drop(1)
        original.hurt()
        val restored = world(items = items())
        assertTrue(restored.restoreState(original.saveState()))
        assertEquals(original.saveState(), restored.saveState())
        assertEquals(listOf(0), restored.inventory)
        assertEquals(15, restored.objects[1].room)
        assertEquals(-1, restored.objects[2].room)
        assertEquals(1, restored.stars)
        assertEquals(100, restored.score)
        assertEquals(2, restored.lives)
        assertEquals(setOf(62, 15), restored.visited)
    }

    @Test fun malformedSaveDoesNotPartiallyMutateTheGame() {
        val game = world()
        val before = game.saveState()
        assertFalse(game.restoreState(before.replace("152.0", "NaN")))
        assertFalse(game.restoreState("NEIL1\n15,1,2,0,0,3\n999\n15\n"))
        assertEquals(before, game.saveState())
    }

    @Test fun landsOnSolidFloorAndCannotWalkThroughWalls() {
        val g=world({x,y->if(y==11 || x==12)50 else 0})
        repeat(240) {g.step(1f/120,1)}
        assertEquals(144f,g.y,.01f)
        assertTrue(g.x<181);assertTrue(g.grounded)
    }
    @Test fun initialPositionIsMovedOutOfSolidTiles() {
        val g = world({ x, y -> if (y == 11 || (x == 10 && y in 8..10)) 50 else 0 })
        val start = g.x
        repeat(30) { g.step(1f / 120, -1) }
        assertTrue(g.x < start)
    }
    @Test fun jumpsThroughOneWayPlatformAndLandsOnTop() {
        val g=world({_,y->when(y){11->50;8->51;else->0}})
        g.enter(62,100f,144f)
        repeat(10) {g.step(1f/120,0)}
        g.jump()
        var highest=g.y
        repeat(160) {g.step(1f/120,0);highest=minOf(highest,g.y)}
        assertTrue(highest<112)
        assertEquals(96f,g.y,.01f)
    }
    @Test fun climbsLadderAndMovesBetweenRooms() {
        val g=world({x,y->if(y==11)50 else if(x==10)10 else 0})
        g.enter(62,152f,134f)
        repeat(180) {g.step(1f/120,0,-1)}
        assertTrue(g.y<50)
        g.enter(62,300f,100f)
        repeat(60) {g.step(1f/120,1)}
        assertEquals(63,g.room)
    }
    @Test fun inventoryLimitDropAndRestart() {
        val items=List(4) {OriginalGame.Item(it,97,"Item $it",62,152f,134f,0)}
        val g=world(items=items)
        items.forEach {g.collect(it)}
        assertEquals(3,g.inventory.size);assertEquals(62,items[3].room)
        g.drop(1);assertEquals(2,g.inventory.size);assertEquals(62,items[1].room)
        g.collect(items[3]);assertEquals(3,g.inventory.size)
        g.restart();assertTrue(g.inventory.isEmpty());assertTrue(items.all {it.room==62})
    }
    @Test fun emptyRoomBoundaryDoesNotWrapIntoUnrelatedRow() {
        val g=world()
        g.enter(59,302f,100f)
        repeat(120) {g.step(1f/120,1)}
        assertEquals(59,g.room)
    }
    @Test fun modernSpriteFramesFollowMovementState() {
        val g=world()
        g.enter(62,100f,100f)
        g.step(1f/120,0)
        assertEquals(NeilSpriteFrames.FALL,g.spriteFrame)
        repeat(120) {g.step(1f/120,0)}
        assertTrue(g.grounded)
        repeat(30) {g.step(1f/120,0)}
        assertEquals(NeilSpriteFrames.IDLE_RIGHT,g.spriteFrame)
        g.step(1f/120,-1)
        assertEquals(-1,g.facing)
        assertTrue(g.spriteFrame in NeilSpriteFrames.WALK_START until NeilSpriteFrames.WALK_START+NeilSpriteFrames.WALK_COUNT)
        g.jump();g.step(1f/120,0)
        assertEquals(NeilSpriteFrames.JUMP,g.spriteFrame)
    }
    @Test fun holdsPositionOnLadderAndShowsClimbFrames() {
        val g=world({x,y->if(y==11)50 else if(x==10)10 else 0})
        g.enter(62,152f,144f)
        repeat(60) {g.step(1f/120,0,-1)}
        assertTrue(g.climbing);assertTrue(g.frameFacesCamera)
        assertTrue(g.spriteFrame==NeilSpriteFrames.CLIMB_LEFT || g.spriteFrame==NeilSpriteFrames.CLIMB_RIGHT)
        val held=g.y
        repeat(60) {g.step(1f/120,0)}
        assertEquals(held,g.y,.5f)
        assertEquals(NeilSpriteFrames.CLIMB_REST,g.spriteFrame)
        repeat(120) {g.step(1f/120,1)}
        assertFalse(g.climbing)
    }
    @Test fun entersAndExitsRoomsThroughDoors() {
        val rooms = List(100) { OriginalGame.Room(it, if (it == 15) "OUTSIDE THE TOWER" else "Room $it", List(240) { i -> if (i / 20 == 11) 50 else 0 }, it == 62 || it == 15) }
        val doors = listOf(
            OriginalGame.Door(62, 224, 128, 256, 175, 15, 224f, 128f, "OUTSIDE THE TOWER"),
            OriginalGame.Door(15, 208, 112, 255, 175, 62, 232f, 144f, "THE BEGINNING")
        )
        val g = OriginalGame(rooms, emptyList(), doors)
        assertNull(g.nearbyDoor())
        assertFalse(g.interact())

        g.enter(62, 232f, 144f)
        val door = g.nearbyDoor()
        assertNotNull(door)
        assertEquals(15, door.toRoom)
        assertTrue(g.interact())
        assertEquals(15, g.room)
        assertEquals(224f, g.x, 0.01f)
        assertTrue(15 in g.visited)

        assertNotNull(g.nearbyDoor())
        assertTrue(g.enterNearbyDoor())
        assertEquals(62, g.room)
        assertEquals(232f, g.x, 0.01f)
    }
}
