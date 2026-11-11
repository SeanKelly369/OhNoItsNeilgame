package com.kelly3d.ohnoitsneilgame.maps

import kotlin.math.ceil
import kotlin.math.floor

enum class MapLayer { BACKGROUND, TERRAIN, FOREGROUND }
enum class TileCollision { EMPTY, SOLID, ONE_WAY, LADDER, HAZARD }

/** Grid coordinates are independent of pixels. Atlas IDs are zero-based, row-major; -1 is empty. */
class TileMap(
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val atlasColumns: Int,
    val atlasRows: Int,
    val tileset: String
) {
    init {
        require(width in 1..256 && height in 1..256) { "Map size must be 1..256 cells per axis" }
        require(tileWidth in 1..256 && tileHeight in 1..256) { "Tile size must be 1..256 pixels" }
        require(atlasColumns in 1..256 && atlasRows in 1..256) { "Atlas grid must be 1..256 per axis" }
        require(tileset.matches(Regex("[A-Za-z0-9_./-]+")) &&
            !tileset.startsWith("/") && tileset.split('/').none { it == ".." || it.isEmpty() }) {
            "Tileset must be a relative asset path without spaces or parent traversal"
        }
    }
    private val layers = Array(MapLayer.entries.size) { IntArray(width * height) { -1 } }
    private val collision = IntArray(width * height)
    var spawnX: Int = 1.coerceAtMost(width - 1)
        private set
    var spawnY: Int = 1.coerceAtMost(height - 1)
        private set

    fun contains(x: Int, y: Int) = x in 0 until width && y in 0 until height
    private fun index(x: Int, y: Int): Int {
        require(contains(x, y)) { "Cell ($x, $y) is outside the map" }
        return y * width + x
    }
    fun tile(layer: MapLayer, x: Int, y: Int) = layers[layer.ordinal][index(x, y)]
    fun paint(layer: MapLayer, x: Int, y: Int, tile: Int) {
        require(tile in -1 until atlasColumns * atlasRows) { "Tile ID outside atlas" }
        layers[layer.ordinal][index(x, y)] = tile
    }
    fun collision(x: Int, y: Int) = TileCollision.entries[collision[index(x, y)]]
    fun markCollision(x: Int, y: Int, kind: TileCollision) { collision[index(x, y)] = kind.ordinal }
    fun setSpawn(x: Int, y: Int) { index(x, y); spawnX = x; spawnY = y }

    /** Source rectangles for native renderers, without any platform image dependency. */
    fun sourceX(tile: Int): Int {
        require(tile in 0 until atlasColumns * atlasRows)
        return tile % atlasColumns * tileWidth
    }
    fun sourceY(tile: Int): Int {
        require(tile in 0 until atlasColumns * atlasRows)
        return tile / atlasColumns * tileHeight
    }

    /** Half-open pixel rectangle. Out-of-map space is empty; callers choose boundary behavior. */
    fun overlapsSolid(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        require(listOf(left, top, right, bottom).all { it.isFinite() })
        if (right <= left || bottom <= top) return false
        val x0 = floor(left / tileWidth).toInt().coerceIn(0, width)
        val y0 = floor(top / tileHeight).toInt().coerceIn(0, height)
        val x1 = ceil(right / tileWidth).toInt().coerceIn(0, width) - 1
        val y1 = ceil(bottom / tileHeight).toInt().coerceIn(0, height) - 1
        for (y in y0..y1) for (x in x0..x1) {
            if (collision(x, y) == TileCollision.SOLID) return true
        }
        return false
    }

    /** Authoring errors that should be corrected before a level is played. */
    fun validationErrors(): List<String> = buildList {
        if (collision(spawnX, spawnY) in listOf(TileCollision.SOLID, TileCollision.HAZARD)) {
            add("Spawn must not be inside a solid or hazardous cell")
        }
    }
}

/** Versioned UTF-8 format shared by the editor and native clients; no platform JSON dependency. */
object TileMapCodec {
    fun encode(map: TileMap): String = buildString {
        appendLine("NEILMAP 1")
        appendLine("${map.width} ${map.height} ${map.tileWidth} ${map.tileHeight} ${map.atlasColumns} ${map.atlasRows}")
        appendLine(map.tileset)
        appendLine("${map.spawnX} ${map.spawnY}")
        for (layer in MapLayer.entries) {
            appendLine(layer.name)
            for (y in 0 until map.height) {
                appendLine((0 until map.width).joinToString(" ") { map.tile(layer, it, y).toString() })
            }
        }
        appendLine("COLLISION")
        for (y in 0 until map.height) {
            appendLine((0 until map.width).joinToString(" ") { map.collision(it, y).ordinal.toString() })
        }
    }

    fun decode(text: String): TileMap {
        require(text.length <= 4_000_000) { "Map file is too large" }
        val lines = text.trimEnd().lines()
        var cursor = 0
        fun line(): String {
            require(cursor < lines.size) { "Map is truncated at line ${cursor + 1}" }
            return lines[cursor++].trim()
        }
        fun numbers(count: Int): List<Int> {
            val values = line().split(Regex("\\s+")).map {
                it.toIntOrNull() ?: throw IllegalArgumentException("Invalid number on line $cursor")
            }
            require(values.size == count) { "Expected $count values on line $cursor" }
            return values
        }
        require(line() == "NEILMAP 1") { "Unsupported map format/version" }
        val size = numbers(6)
        val map = TileMap(size[0], size[1], size[2], size[3], size[4], size[5], line())
        val spawn = numbers(2)
        map.setSpawn(spawn[0], spawn[1])
        for (layer in MapLayer.entries) {
            require(line() == layer.name) { "Expected layer ${layer.name}" }
            for (y in 0 until map.height) numbers(map.width).forEachIndexed { x, id -> map.paint(layer, x, y, id) }
        }
        require(line() == "COLLISION") { "Expected collision layer" }
        for (y in 0 until map.height) numbers(map.width).forEachIndexed { x, id ->
            require(id in TileCollision.entries.indices) { "Unknown collision type $id" }
            map.markCollision(x, y, TileCollision.entries[id])
        }
        require(cursor == lines.size) { "Unexpected trailing map data" }
        return map
    }
}
