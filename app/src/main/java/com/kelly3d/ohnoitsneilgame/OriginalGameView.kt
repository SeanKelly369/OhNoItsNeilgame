package com.kelly3d.ohnoitsneilgame

import android.content.Context
import android.graphics.*
import android.util.LruCache
import android.view.*
import org.json.JSONObject
import kotlin.math.min

/**
 * Original Amiga rooms, collision and items with the modern Neil sprite.
 * Rooms and items use the 4x remastered art from art/sprites/amiga/hd when present
 * (see :shared:exportAmigaHd), falling back to the native 320x192 artwork.
 */
class OriginalGameView(context: Context) : View(context), Choreographer.FrameCallback {
    private val data = JSONObject(context.assets.open("amiga/world.json").bufferedReader().use { it.readText() })
    private fun ints(a: org.json.JSONArray) = List(a.length()) { a.getInt(it) }
    private val used = ints(data.getJSONArray("used")).toSet()
    private val rooms = data.getJSONArray("rooms").let { a -> List(a.length()) { i ->
        val r=a.getJSONObject(i)
        OriginalGame.Room(i,r.getString("name"),ints(r.getJSONArray("collision")),i in used)
    } }
    private val names = data.getJSONArray("objectNames")
    val game = OriginalGame(rooms, data.getJSONArray("objects").let { a -> List(a.length()) { i ->
        val r=a.getJSONArray(i); val room=r.getInt(1)
        OriginalGame.Item(i,r.getInt(0),names.optString(i).ifBlank { "OBJECT" },
            if(room>0) room-1 else -2,r.getInt(2).toFloat(),r.getInt(3).toFloat(),r.getInt(6))
    }.filter {it.sprite>0} })
    private val paint=Paint().apply { isFilterBitmap=false; typeface=Typeface.MONOSPACE }
    /** HD art is drawn at or below its native size, so filtering keeps it smooth instead of shimmering. */
    private val hdPaint=Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val neilSprites=NeilSprites(resources)
    private val dst=RectF()
    private val images=mutableMapOf<String,Bitmap>()
    /** A 4x room is ~3.9 MB decoded; keep only the current room and its recent neighbours. */
    private val hdImages=object:LruCache<String,HdImage>(48*1024*1024) {
        override fun sizeOf(key:String,value:HdImage)=value.bitmap.allocationByteCount
    }
    private class HdImage(val bitmap:Bitmap,val scale:Float)
    private val pointers=mutableMapOf<Int,Int>()
    private val keys=mutableSetOf<Int>()
    private var running=false
    private var previous=0L
    private var accumulator=0f
    private var title=true
    private var paused=false
    private var map=false
    private var page=0
    private var dialogue: List<String> = emptyList()
    private var dialoguePage=0
    private var s=1f
    private var ox=0f
    private var oy=0f
    private val cream=Color.rgb(244,231,198)
    private val gold=Color.rgb(244,192,79)
    private val prefs=context.getSharedPreferences("original_game_v1",Context.MODE_PRIVATE)
    init { isFocusableInTouchMode=true; contentDescription="Oh No It's Neil. Original Amiga reconstruction."
        load()
    }
    fun resume() { if(!running) {running=true;previous=0;Choreographer.getInstance().postFrameCallback(this)} }
    fun pause() { running=false;Choreographer.getInstance().removeFrameCallback(this);pointers.clear();keys.clear();previous=0;paused=!title;save() }
    override fun onDetachedFromWindow() { pause();super.onDetachedFromWindow() }
    override fun doFrame(frameTimeNanos: Long) {
        if(!running) return
        if(previous!=0L && !title && !paused && !map && dialogue.isEmpty()) {
            accumulator+=((frameTimeNanos-previous)/1e9f).coerceAtMost(.1f)
            while(accumulator>=1f/120) {
                val held=pointers.values.toSet()+keys
                game.step(1f/120,(if(1 in held)1 else 0)-(if(0 in held)1 else 0),
                    (if(3 in held)1 else 0)-(if(2 in held)1 else 0))
                accumulator-=1f/120
            }
        } else accumulator=0f
        previous=frameTimeNanos;invalidate();Choreographer.getInstance().postFrameCallback(this)
    }
    private fun bitmap(path:String)=images.getOrPut(path) {
        context.assets.open("amiga/"+path).use { BitmapFactory.decodeStream(it) }
    }
    /** Remastered asset with its scale factor, or the original at scale 1 if it was not exported. */
    private fun hd(path:String):HdImage = hdImages.get(path) ?: run {
        val remastered=runCatching {
            context.assets.open("amiga/hd/"+path).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        (if(remastered!=null) HdImage(remastered,HD_SCALE) else HdImage(bitmap(path),1f)).also { hdImages.put(path,it) }
    }
    private fun drawHd(c:Canvas,path:String,x:Float,y:Float) {
        val image=hd(path)
        dst.set(x,y,x+image.bitmap.width/image.scale,y+image.bitmap.height/image.scale)
        c.drawBitmap(image.bitmap,null,dst,if(image.scale>1f) hdPaint else paint)
    }
    private fun drawNeil(c:Canvas) {
        if(game.invulnerable>0f && (game.time*12).toInt()%2==1) return
        val facing=if(game.frameFacesCamera) 1 else game.facing
        // Hitbox spans x+3..x+12 and y..y+32; centre the figure on it with feet on the floor.
        if(neilSprites.drawFrame(c,game.spriteFrame,facing,game.x+7.5f,game.y+32f,NEIL_FRAME_HEIGHT,hdPaint)) return
        drawHd(c,"sprites/%03d.png".format(game.sprite),game.x-8,game.y)
    }
    private fun box(c:Canvas,x:Float,y:Float,w:Float,h:Float,color:Int) {paint.color=color;c.drawRect(x,y,x+w,y+h,paint)}
    private fun text(c:Canvas,t:String,x:Float,y:Float,size:Float=7f,color:Int=cream) {
        paint.color=color;paint.textSize=size;c.drawText(t,x,y,paint)
    }
    override fun onDraw(c:Canvas) {
        super.onDraw(c);c.drawColor(Color.rgb(9,15,24))
        s=min((width-paddingLeft-paddingRight)/360f,(height-paddingTop-paddingBottom)/300f)
        ox=paddingLeft+(width-paddingLeft-paddingRight-360*s)/2
        oy=paddingTop+(height-paddingTop-paddingBottom-300*s)/2
        c.save();c.translate(ox,oy);c.scale(s,s)
        text(c,"OH NO! IT'S NEIL",20f,17f,12f,gold)
        text(c,"AMIGA REMASTERED",20f,29f,6f)
        text(c,if(paused) "RESUME" else "PAUSE",304f,17f,7f)
        c.save();c.translate(20f,36f);c.clipRect(0f,0f,320f,192f)
        drawHd(c,"rooms/room_%03d.png".format(game.room),0f,0f)
        game.objects.filter {it.room==game.room}.forEach {item ->
            drawHd(c,"sprites/%03d.png".format(item.sprite),item.x,item.y)
        }
        drawNeil(c)
        c.restore()
        box(c,20f,216f,320f,12f,Color.rgb(15,22,30))
        text(c,game.current.name,24f,225f,7f,gold)
        text(c,"STARS ${game.stars}   BAG ${game.inventory.size}/3   ROOMS ${game.visited.size}/71",20f,239f,6f)
        text(c,game.message.take(53),20f,249f,6f)
        val labels=listOf("<",">","UP","DOWN","JUMP","USE","MAP")
        labels.forEachIndexed {i,label ->
            box(c,20f+i*46,258f,42f,27f,if(i in pointers.values || i in keys) Color.rgb(72,91,105) else Color.rgb(30,48,64))
            text(c,label,26f+i*46,276f,8f,if(i>=4)gold else cream)
        }
        text(c,"A/D MOVE  W/S CLIMB  SPACE JUMP  E USE  M MAP",20f,296f,6f)
        if(title) {
            box(c,20f,36f,320f,192f,0xEE101C2A.toInt())
            text(c,"OH NO! IT'S NEIL",43f,76f,23f,gold)
            text(c,"THE ORIGINAL AMIGA ADVENTURE",64f,94f,9f)
            text(c,"Gralda has captured Neil's family.",44f,116f,8f)
            text(c,"Escape the tower and explore the kingdom.",30f,130f,8f)
            text(c,"Original rooms and items from Sean Kelly's",36f,151f,7f)
            text(c,"Reality game files, remastered in HD.",46f,162f,7f)
            box(c,72f,179f,216f,27f,gold)
            text(c,"ENTER THE WORLD",113f,197f,11f,Color.BLACK)
            text(c,"Reconstruction preview - puzzle scripts pending",27f,219f,6f)
        } else if(paused) {
            box(c,42f,64f,276f,139f,0xF5101C2A.toInt())
            text(c,"PAUSED",140f,91f,18f,gold)
            text(c,"Tap to resume. Progress saves automatically.",52f,115f,6f)
            text(c,"Inventory: tap an item below to drop it.",52f,132f,6f)
            game.inventory.forEachIndexed {i,id ->
                text(c,game.objects.first {it.id==id}.name,60f,151f+i*14,8f)
            }
        }
        if(map) drawMap(c)
        if(dialogue.isNotEmpty()) {
            box(c,25f,52f,310f,153f,0xFA101C2A.toInt())
            dialogue.drop(dialoguePage*9).take(9).forEachIndexed {i,line->text(c,line,36f,69f+i*13,7f)}
            text(c,"TAP / E TO CONTINUE",98f,194f,8f,gold)
        }
        c.restore()
    }
    private fun drawMap(c:Canvas) {
        box(c,12f,32f,336f,220f,0xFF101C2A.toInt())
        text(c,"ROOM ATLAS",26f,49f,12f,gold)
        text(c,"Tap a room to explore. Arrows change page.",26f,62f,6f)
        used.sorted().drop(page*12).take(12).forEachIndexed {i,id->
            val left=24f+i%4*79;val top=70f+i/4*53
            c.drawBitmap(bitmap("rooms/room_%03d.png".format(id)),null,RectF(left,top,left+73,top+40),paint)
            text(c,"${id+1} "+rooms[id].name.take(13),left,top+48,5f,if(id in game.visited)gold else cream)
        }
        text(c,"< PREVIOUS",24f,243f,8f);text(c,"CLOSE",159f,243f,8f);text(c,"NEXT >",280f,243f,8f)
    }
    private fun use() {
        if(dialogue.isNotEmpty()) {dialoguePage++;if(dialoguePage*9>=dialogue.size) {dialogue=emptyList();dialoguePage=0};return}
        if(game.interact()) {save();return}
        game.message="No loose item nearby. Puzzle interactions are not restored yet."
    }
    private fun action(a:Int) {
        when(a) {4->game.jump();5->use();6->{map=!map;pointers.clear();keys.clear();save()}}
    }
    override fun performClick():Boolean {super.performClick();return true}
    override fun onTouchEvent(e:MotionEvent):Boolean {
        val i=e.actionIndex;val x=(e.getX(i)-ox)/s;val y=(e.getY(i)-oy)/s
        if(e.actionMasked==MotionEvent.ACTION_UP || e.actionMasked==MotionEvent.ACTION_POINTER_UP) {
            pointers.remove(e.getPointerId(i));performClick();return true
        }
        if(e.actionMasked==MotionEvent.ACTION_CANCEL) {pointers.clear();return true}
        if(e.actionMasked!=MotionEvent.ACTION_DOWN && e.actionMasked!=MotionEvent.ACTION_POINTER_DOWN) return true
        when {
            title -> {title=false;paused=false}
            dialogue.isNotEmpty() -> use()
            map -> {
                if(y>=230) { when {x<120->page=(page-1).coerceAtLeast(0);x>240->page=(page+1).coerceAtMost((used.size-1)/12);else->map=false} }
                else if(y in 70f..229f && x in 24f..340f) {
                    val index=page*12+((y-70)/53).toInt()*4+((x-24)/79).toInt()
                    used.sorted().getOrNull(index)?.let {game.enter(it);map=false;save()}
                }
            }
            paused -> {
                val slot=((y-140)/14).toInt()
                if(y in 140f..182f && slot in game.inventory.indices) game.drop(slot)
                else paused=false
                save()
            }
            y<32 && x>280 -> {paused=true;pointers.clear();keys.clear();save()}
            y in 258f..287f && x in 20f..342f -> {
                val a=((x-20)/46).toInt()
                pointers[e.getPointerId(i)]=a;action(a)
            }
        }
        return true
    }
    override fun onKeyDown(code:Int,event:KeyEvent):Boolean {
        if(event.repeatCount>0) return true
        if(title) {if(code==KeyEvent.KEYCODE_ENTER) title=false;return true}
        if(code==KeyEvent.KEYCODE_P || code==KeyEvent.KEYCODE_ESCAPE) {paused=!paused;map=false;keys.clear();save();return true}
        val a=when(code) {
            KeyEvent.KEYCODE_A,KeyEvent.KEYCODE_DPAD_LEFT->0
            KeyEvent.KEYCODE_D,KeyEvent.KEYCODE_DPAD_RIGHT->1
            KeyEvent.KEYCODE_W,KeyEvent.KEYCODE_DPAD_UP->2
            KeyEvent.KEYCODE_S,KeyEvent.KEYCODE_DPAD_DOWN->3
            KeyEvent.KEYCODE_SPACE->4
            KeyEvent.KEYCODE_E,KeyEvent.KEYCODE_ENTER->5
            KeyEvent.KEYCODE_M->6
            else->return super.onKeyDown(code,event)
        }
        if(map && a in 0..1) {page=(page+if(a==0)-1 else 1).coerceIn(0,(used.size-1)/12);return true}
        if(!paused && (!map || a==6)) {keys.add(a);action(a)}
        return true
    }
    override fun onKeyUp(code:Int,event:KeyEvent):Boolean {
        when(code) {
            KeyEvent.KEYCODE_A,KeyEvent.KEYCODE_DPAD_LEFT->keys.remove(0)
            KeyEvent.KEYCODE_D,KeyEvent.KEYCODE_DPAD_RIGHT->keys.remove(1)
            KeyEvent.KEYCODE_W,KeyEvent.KEYCODE_DPAD_UP->keys.remove(2)
            KeyEvent.KEYCODE_S,KeyEvent.KEYCODE_DPAD_DOWN->keys.remove(3)
        };return true
    }
    private fun save() {
        val state=JSONObject().put("room",game.room).put("x",game.x).put("y",game.y)
        val items=org.json.JSONArray()
        game.objects.forEach {items.put(org.json.JSONArray(listOf(it.id,it.room,it.x,it.y)))}
        state.put("items",items).put("bag",org.json.JSONArray(game.inventory)).put("visited",org.json.JSONArray(game.visited.toList()))
        prefs.edit().putString("state",state.toString()).apply()
    }
    private fun load() {
        val raw=prefs.getString("state",null)?:return
        runCatching {
            val state=JSONObject(raw);game.enter(state.getInt("room"),state.getDouble("x").toFloat(),state.getDouble("y").toFloat())
            val items=state.getJSONArray("items")
            for(i in 0 until items.length()) {val a=items.getJSONArray(i);game.objects.firstOrNull {it.id==a.getInt(0)}?.let {
                it.room=a.getInt(1);it.x=a.getDouble(2).toFloat();it.y=a.getDouble(3).toFloat()
            }}
            game.inventory.addAll(ints(state.getJSONArray("bag")).filter {id->game.objects.any {it.id==id}}.take(3))
            game.visited.addAll(ints(state.getJSONArray("visited")).filter {it in used})
        }.onFailure {game.restart()}
    }
    private companion object {
        const val HD_SCALE=4f
        /** Full padded atlas frame height in room pixels; the visible figure is roughly 2.2 tiles tall. */
        const val NEIL_FRAME_HEIGHT=46f
    }
}
