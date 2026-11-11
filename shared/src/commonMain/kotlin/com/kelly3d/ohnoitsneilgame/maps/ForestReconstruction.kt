package com.kelly3d.ohnoitsneilgame.maps

/**
 * Editable reconstruction of the three supplied forest compositions.
 * Horizontal order and a common ground baseline are authoring choices, not recovered Amiga data.
 * IDs address the replacement forest_tiles.png atlas, never the original Amiga tileset.
 */
object ForestReconstruction {
    const val ROOM_WIDTH = 40
    const val HEIGHT = 24
    const val GROUND = 19
    val roomNames = listOf("The carved-tree forest", "The clearing", "The tree house")

    fun create(): TileMap {
        val map = TileMap(ROOM_WIDTH * 3, HEIGHT, 16, 16, 8, 4, "forest_tiles.png")
        map.setSpawn(2, GROUND - 2)
        fun tile(x: Int, y: Int, id: Int, layer: MapLayer = MapLayer.BACKGROUND) {
            if (map.contains(x, y)) map.paint(layer, x, y, id)
        }
        fun trunk(left: Int, right: Int, top: Int, base: Int = GROUND - 1) {
            for (y in top..base) {
                val flare = if (y >= base - 1) 1 else 0
                for (x in left - flare..right + flare) {
                    tile(x, y, when (x) {
                        left - flare -> 5
                        right + flare -> 6
                        else -> if ((x + y) % 5 == 0) 28 else 4
                    })
                }
            }
        }
        fun branch(x: Int, y: Int, length: Int, direction: Int) {
            for (i in 0 until length) tile(x + i * direction, y - i, if (direction < 0) 13 else 14)
        }
        fun canopy(cx: Int, cy: Int, rx: Int, ry: Int, bush: Boolean = false) {
            for (y in cy - ry..cy + ry) for (x in cx - rx..cx + rx) {
                val dx = (x - cx).toFloat() / rx
                val dy = (y - cy).toFloat() / ry
                if (dx * dx + dy * dy <= 1f) {
                    val boundary = dx * dx + dy * dy > 0.65f
                    val id = if (boundary) {
                        if (y <= cy) { if (x < cx) 9 else 10 } else { if (x < cx) 11 else 12 }
                    } else if (bush || (x + y) % 4 == 0) 15 else 8
                    tile(x, y, id)
                }
            }
        }
        fun bush(cx: Int, radius: Int) = canopy(cx, GROUND - 1, radius, 3, true)
        fun window(x: Int, y: Int) {
            tile(x, y, 20); tile(x + 1, y, 21)
            tile(x, y + 1, 22); tile(x + 1, y + 1, 23)
        }
        // Rear shrubs, with a clear walking silhouette above the common baseline.
        for (cx in listOf(1, 12, 32, 39, 54, 59, 72, 77, 82, 116, 120)) bush(cx, 4)

        // Scene 1: a carved stump, broad ancient tree and small round-canopy tree.
        trunk(-2, 0, 4)
        canopy(-1, 3, 3, 4)
        trunk(6, 8, 11)
        branch(6, 11, 3, -1); branch(8, 11, 3, 1)
        tile(6, 13, 24); tile(7, 13, 25); tile(8, 13, 24)
        tile(6, 14, 28); tile(7, 14, 29); tile(8, 14, 28)
        trunk(21, 25, 3)
        branch(21, 9, 6, -1); branch(25, 9, 6, 1)
        canopy(23, 2, 9, 5)
        canopy(15, 6, 4, 3)
        canopy(29, 4, 4, 4)
        branch(21, 14, 5, -1); branch(25, 13, 4, 1)
        canopy(23, 10, 6, 2)
        trunk(35, 36, 9)
        canopy(35, 7, 3, 3)
        branch(35, 13, 2, -1); canopy(33, 12, 1, 1)

        // Scene 2: a cropped tree at the left and an open shrub-lined clearing.
        trunk(40, 43, 0)
        branch(43, 8, 5, 1)
        canopy(42, 2, 7, 5)
        canopy(42, 8, 5, 2)

        // Scene 3: thin tree and oversized tree-house trunk with blue leaded windows.
        trunk(86, 87, 6)
        canopy(86, 4, 3, 3)
        branch(86, 9, 2, -1); canopy(84, 8, 1, 1)
        trunk(98, 108, 0)
        branch(108, 7, 6, 1); canopy(113, 2, 2, 2)
        branch(98, 13, 4, -1); canopy(94, 10, 2, 2)
        window(103, 3)
        window(98, 2)
        window(100, 14)
        // Door is visual decoration until room exits / interior maps are authored.
        tile(105, 15, 18); tile(106, 15, 19)
        for (y in 16 until GROUND) { tile(105, y, 16); tile(106, y, 17) }

        for (x in 0 until map.width) {
            for (y in GROUND until HEIGHT) {
                map.paint(MapLayer.TERRAIN, x, y, if (y == GROUND) 0 else if ((x + y) % 3 == 0) 2 else 1)
                map.markCollision(x, y, TileCollision.SOLID)
            }
            if (x % 3 == 0) tile(x, GROUND - 1, 26, MapLayer.FOREGROUND)
        }
        return map
    }
}
