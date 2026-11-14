package com.kelly3d.ohnoitsneilgame

/**
 * Parsed world representation of Sean Kelly's recovered Amiga files.
 * Provides a pure Kotlin multiplatform loader from raw JSON string,
 * suitable for both Android and iOS without platform JSON dependencies.
 */
object OriginalWorldData {
    fun parse(json: String): Pair<List<OriginalGame.Room>, List<OriginalGame.Item>> {
        val usedMatch = Regex("\"used\":\\[([0-9,]*)\\]").find(json)
        val usedSet = usedMatch?.groupValues?.get(1)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.map { it.toInt() }
            ?.toSet() ?: emptySet()

        val roomNamesMatch = Regex("\"name\":\"(.*?)\"").findAll(json).toList()
        val collisionsMatch = Regex("\"collision\":\\[([0-9,]*)\\]").findAll(json).toList()

        val rooms = List(100) { id ->
            val name = roomNamesMatch.getOrNull(id)?.groupValues?.get(1) ?: "Room $id"
            val collisionList = collisionsMatch.getOrNull(id)?.groupValues?.get(1)
                ?.split(',')
                ?.filter { it.isNotBlank() }
                ?.map { it.toInt() } ?: List(240) { 0 }
            OriginalGame.Room(id, name, collisionList, id in usedSet)
        }

        val objectNames = Regex("\"objectNames\":\\[(.*?)\\]").find(json)?.groupValues?.get(1)?.let { raw ->
            raw.split(',').map { it.trim().removeSurrounding("\"") }
        } ?: emptyList()

        val rawObjects = Regex("\"objects\":\\[(.*?)\\]\\s*,\\s*\"bobs\"").find(json)?.groupValues?.get(1)
        val items = mutableListOf<OriginalGame.Item>()
        if (rawObjects != null) {
            val recordMatches = Regex("\\[([0-9,-]+)\\]").findAll(rawObjects)
            var index = 0
            for (match in recordMatches) {
                val nums = match.groupValues[1].split(',').map { it.trim().toInt() }
                if (nums.size >= 7) {
                    val sprite = nums[0]
                    val rawRoom = nums[1]
                    val room = if (rawRoom > 0) rawRoom - 1 else -2
                    val x = nums[2].toFloat()
                    val y = nums[3].toFloat()
                    val type = nums[6]
                    val name = objectNames.getOrNull(index)?.ifBlank { "OBJECT" } ?: "OBJECT"
                    if (sprite > 0) {
                        items.add(OriginalGame.Item(index, sprite, name, room, x, y, type))
                    }
                }
                index++
            }
        }

        return rooms to items
    }

    /**
     * Bidirectional doorway and portal connections derived from ZONES.DAT and room geometry
     * so Neil can enter and exit enclosed buildings, tower rooms, the well, and castle rooms.
     */
    fun defaultDoors(rooms: List<OriginalGame.Room>): List<OriginalGame.Door> {
        fun link(
            aRoom: Int, ax1: Int, ay1: Int, ax2: Int, ay2: Int, aSpawnX: Float, aSpawnY: Float,
            bRoom: Int, bx1: Int, by1: Int, bx2: Int, by2: Int, bSpawnX: Float, bSpawnY: Float
        ): List<OriginalGame.Door> {
            val aName = rooms.getOrNull(aRoom)?.name?.takeIf { it.isNotBlank() } ?: "Room ${aRoom + 1}"
            val bName = rooms.getOrNull(bRoom)?.name?.takeIf { it.isNotBlank() } ?: "Room ${bRoom + 1}"
            return listOf(
                OriginalGame.Door(aRoom, ax1, ay1, ax2, ay2, bRoom, bSpawnX, bSpawnY, bName),
                OriginalGame.Door(bRoom, bx1, by1, bx2, by2, aRoom, aSpawnX, aSpawnY, aName)
            )
        }

        val all = buildList {
            // Tower: The Beginning <-> Outside the Tower (ground door and upper ladder/ledge)
            addAll(link(62, 224, 128, 256, 175, 232f, 144f, 15, 208, 112, 255, 175, 224f, 128f))
            addAll(link(62, 149, 7, 185, 48, 160f, 16f, 15, 55, 35, 91, 86, 65f, 48f))
            // Forest & Village houses
            addAll(link(16, 199, 77, 257, 127, 220f, 128f, 36, 239, 108, 287, 176, 255f, 144f))
            addAll(link(20, 160, 112, 208, 175, 176f, 128f, 35, 144, 100, 191, 175, 160f, 144f))
            addAll(link(22, 285, 103, 317, 158, 293f, 144f, 30, 176, 118, 207, 176, 184f, 144f))
            addAll(link(23, 120, 112, 165, 143, 135f, 112f, 44, 159, 3, 271, 17, 208f, 16f))
            addAll(link(46, 32, 75, 79, 159, 48f, 128f, 34, 176, 97, 209, 175, 184f, 144f))
            // White Hart Inn / Public House / Lounge / Basement / Snooker Room / Toilets
            addAll(link(24, 46, 103, 80, 176, 56f, 144f, 31, 48, 105, 80, 176, 56f, 144f))
            addAll(link(31, 191, 99, 224, 175, 200f, 144f, 33, 15, 16, 47, 79, 24f, 48f))
            addAll(link(32, 209, 102, 255, 175, 224f, 144f, 37, 62, 99, 111, 159, 80f, 128f))
            addAll(link(31, 271, 96, 318, 176, 286f, 144f, 75, 271, 80, 318, 159, 286f, 128f))
            addAll(link(38, 252, 80, 303, 159, 270f, 128f, 75, 15, 74, 57, 158, 28f, 128f))
            // Carbury Castle
            addAll(link(53, 165, 106, 201, 175, 176f, 144f, 68, 109, 100, 164, 175, 128f, 144f))
            addAll(link(53, 117, 110, 155, 176, 128f, 144f, 67, 205, 82, 251, 144, 220f, 112f))
            addAll(link(68, 43, 101, 97, 174, 62f, 144f, 70, 18, 107, 69, 175, 36f, 144f))
            addAll(link(68, 209, 104, 255, 175, 224f, 144f, 79, 32, 89, 87, 175, 52f, 144f))
            addAll(link(80, 249, 112, 297, 175, 265f, 144f, 69, 131, 104, 177, 175, 146f, 144f))
            addAll(link(69, 32, 96, 83, 175, 50f, 144f, 73, 144, 108, 191, 159, 160f, 128f))
            addAll(link(69, 249, 106, 295, 176, 264f, 144f, 71, 47, 84, 101, 159, 66f, 128f))
        }
        return all.filter {
            rooms.getOrNull(it.fromRoom)?.occupied == true &&
                rooms.getOrNull(it.toRoom)?.occupied == true
        }
    }
}
