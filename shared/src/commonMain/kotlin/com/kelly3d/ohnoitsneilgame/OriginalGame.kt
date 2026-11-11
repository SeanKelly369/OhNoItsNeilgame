package com.kelly3d.ohnoitsneilgame

import kotlin.math.abs
import kotlin.math.floor

/** Native reconstruction. Tile attributes come from GAME_MAP.VAL.
 * 50 = solid, 51 = one-way platform, 10 = climbable.
 * Puzzle bytecode is preserved separately and is not simulated here.
 */
class OriginalGame(val rooms: List<Room>, val objects: List<Item>) {
    data class Room(val id: Int, val name: String, val collision: List<Int>, val occupied: Boolean)
    data class Item(val id: Int, val sprite: Int, val name: String, val initialRoom: Int,
                    val initialX: Float, val initialY: Float, val type: Int) {
        var room = initialRoom
        var x = initialX
        var y = initialY
    }
    var room = 62
        private set
    var x = 152f
        private set
    var y = 134f
        private set
    var vy = 0f
        private set
    var grounded = false
        private set
    var facing = 1
        private set
    var time = 0f
        private set
    var moving = false
        private set
    var lives = 3
        private set
    var stars = 0
        private set
    var score = 0
        private set
    var message = "Climb out of the tower. Find Neil's family."
    val inventory = mutableListOf<Int>()
    val visited = mutableSetOf(62)
    var invulnerable = 0f
        private set
    val current get() = rooms[room]
    val sprite get() = if (moving) (if (facing > 0) 1 else 7) + (time * 10).toInt() % 6
                      else if (facing > 0) 1 else 7
    private fun attribute(px: Float, py: Float): Int {
        val tx = floor(px / 16).toInt()
        val ty = floor(py / 16).toInt()
        if (tx !in 0..19 || ty !in 0..11) return 0
        return current.collision[ty * 20 + tx]
    }
    fun onLadder() = attribute(x + 8, y + 16) == 10 || attribute(x + 8, y + 30) == 10
    fun jump() { if (grounded || onLadder()) { vy = -220f; grounded = false } }
    fun enter(id: Int, px: Float = 152f, py: Float = 64f) {
        if (id !in rooms.indices || !rooms[id].occupied) return
        room = id; x = px.coerceIn(0f,304f); y = py.coerceIn(0f,160f)
        // A map selection must never strand the player inside masonry.
        if (blocked(x,y)) {
            val safe = (0..10).flatMap { row -> (0..19).map { col -> col*16f to row*16f } }
                .filter { !blocked(it.first,it.second) }
                .minByOrNull { abs(it.first-px)+abs(it.second-py) }
            if (safe != null) { x=safe.first; y=safe.second }
        }
        vy = 0f; grounded = false; visited.add(id); message = current.name
    }
    private fun blocked(px: Float, py: Float) =
        listOf(3f to 2f, 12f to 2f, 3f to 16f, 12f to 16f, 3f to 30f, 12f to 30f)
            .any { attribute(px+it.first,py+it.second)==50 }
    fun step(dt: Float, direction: Int, climb: Int = 0) {
        val d = dt.coerceIn(0f,1f/30)
        time += d; invulnerable = (invulnerable-d).coerceAtLeast(0f)
        val dx = direction.coerceIn(-1,1)*80f*d
        if (direction != 0) facing=if(direction>0) 1 else -1
        moving=dx!=0f
        if (!blocked(x+dx,y)) x+=dx
        if (climb!=0 && onLadder()) { vy=climb.coerceIn(-1,1)*65f; grounded=false }
        else vy=(vy+480f*d).coerceAtMost(220f)
        val oldBottom=y+32
        val next=y+vy*d
        grounded=false
        if (vy>=0) {
            val row=floor((next+32)/16).toInt()
            val surface=row*16f
            val a=attribute(x+3,next+32); val b=attribute(x+12,next+32)
            if ((a==50 || b==50 || a==51 || b==51) && oldBottom<=surface+1 && next+32>=surface) {
                y=surface-32; vy=0f; grounded=true
            } else if (!blocked(x,next)) y=next else vy=0f
        } else if (!blocked(x,next)) y=next else vy=0f
        when {
            x< -8 -> if(room%15>0 && rooms.getOrNull(room-1)?.occupied==true) enter(room-1,302f,y) else x=0f
            x>312 -> if(room%15<14 && rooms.getOrNull(room+1)?.occupied==true) enter(room+1,1f,y) else x=304f
            y< -24 -> if(rooms.getOrNull(room-15)?.occupied==true) enter(room-15,x,158f) else {y=0f;vy=0f}
            y>192 -> if(rooms.getOrNull(room+15)?.occupied==true) enter(room+15,x,0f) else hurt()
        }
        objects.filter { it.room==room && it.type!=0 }.forEach {
            if(abs(it.x-x)<16 && abs(it.y-y)<26) collect(it)
        }
    }
    fun collect(item: Item) {
        if (item.room!=room) return
        if(item.type==0 && inventory.size>=3) {message="I can only carry three objects.";return}
        when(item.type) {
            0 -> inventory.add(item.id)
            2 -> {stars++;score+=100}
            else -> score+=50
        }
        item.room=-1
        message=if(item.type==0) "Picked up "+item.name else "Found a bonus!"
    }
    fun interact(): Boolean {
        val item=objects.filter { it.room==room && it.type==0 && abs(it.x-x)<30 && abs(it.y-y)<38 }
            .minByOrNull { abs(it.x-x)+abs(it.y-y) }
        if(item!=null) { collect(item);return true }
        return false
    }
    fun drop(index: Int) {
        if(index !in inventory.indices) return
        val id=inventory.removeAt(index)
        objects.first {it.id==id}.let {it.room=room;it.x=x;it.y=y+16;message="Dropped "+it.name}
    }
    fun hurt() {
        if(invulnerable>0) return
        lives--;if(lives<=0) lives=3
        enter(room);invulnerable=2f;message="Oh no, Neil! Watch your step."
    }
    fun restart() {
        objects.forEach {it.room=it.initialRoom;it.x=it.initialX;it.y=it.initialY}
        inventory.clear();visited.clear();stars=0;score=0;lives=3;time=0f
        enter(62,152f,134f);invulnerable=0f
    }
}
