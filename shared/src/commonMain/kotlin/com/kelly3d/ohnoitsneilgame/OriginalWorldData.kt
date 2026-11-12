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
}
