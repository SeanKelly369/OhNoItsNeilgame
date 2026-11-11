package com.kelly3d.ohnoitsneilgame

import kotlin.test.*

class OriginalGameTest {
    private fun world(tile: (Int,Int)->Int = {_,y->if(y==11)50 else 0}, items:List<OriginalGame.Item> = emptyList()) =
        OriginalGame(List(100) { OriginalGame.Room(it,"Room $it",List(240) {i->tile(i%20,i/20)},true) },items)
    @Test fun landsOnSolidFloorAndCannotWalkThroughWalls() {
        val g=world({x,y->if(y==11 || x==12)50 else 0})
        repeat(240) {g.step(1f/120,1)}
        assertEquals(144f,g.y,.01f)
        assertTrue(g.x<181);assertTrue(g.grounded)
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
}
