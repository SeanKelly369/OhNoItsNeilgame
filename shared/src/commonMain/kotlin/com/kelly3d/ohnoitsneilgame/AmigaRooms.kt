package com.kelly3d.ohnoitsneilgame

/**
 * Reference-sheet order, NOT world adjacency. Names are transcribed from the
 * supplied contact sheet; clipped labels must be confirmed against original data.
 * Collision geometry and exits deliberately remain unspecified.
 */
object AmigaRooms {
    const val COLUMNS = 5
    const val ROWS = 20
    const val COUNT = COLUMNS * ROWS

    data class Room(val id: Int, val label: String) {
        val column: Int get() = id % COLUMNS
        val row: Int get() = id / COLUMNS
        val assetName: String get() = "amiga/room_${id.toString().padStart(2, '0')}.png"
    }

    private val labels: List<String?> = listOf(
        "It's a Cloud",
        "Above It All",
        "The Tree House",
        "Above It All",
        "Above It All",
        "Above It All",
        "Up in the Sky",
        "Above the Village",
        "High in the Skies",
        "Above the Pub",
        "Above the Harbour",
        "The Main Mast",
        "Dead End",
        "The Tree House",
        "In the Forest",
        "Outside the Tower",
        "In the Forest",
        "The Lumberjack",
        "The Rude Cat",
        "The Giant Spider",
        "The Forest",
        "The Beast",
        "The Small Village",
        "The Well",
        "The White Hart Inn",
        "The Harbour",
        "The Caravel",
        "The Well",
        null,
        null,
        "The Guard House",
        "The Public House",
        "The Lounge",
        "The Basement",
        "The Hermit",
        "Feaha the Witch",
        "Eddie's Home",
        "Snooker Room",
        "Snooker Room",
        "GGG",
        "HHH",
        "G",
        "GG",
        "BJ",
        "Down the Well",
        "The Wicked Witch",
        "Strange Building",
        "Strange Forest",
        "Strange Forest",
        "Strange Forest",
        "The River",
        "Strange Forest",
        "Carbury Castle",
        "Castle Entrance",
        "Carbury Castle",
        "Rocky Land",
        "The Wooden Bridge",
        "Trapped",
        "Frizzy and Charl",
        "The Secret Passage",
        "In the Mines",
        "Dead End",
        "The Beginning",
        "The Underground",
        "Secret Entrance",
        "Passageway",
        "Passageway",
        "Castle Dungeon",
        "Hallway",
        "Hallway",
        "Games Room",
        "Store Room",
        "Store Room",
        "Royal Bedroom",
        "Royal Bedroom",
        "The Toilets",
        "GG",
        null,
        "The Kitchen",
        "Great Hall",
        "Great Hall",
        null,
        "Princess' Bedroom",
        "HH"
    )

    val rooms: List<Room> = List(COUNT) { Room(it, labels.getOrNull(it) ?: "[unnamed]") }

    fun room(id: Int): Room {
        require(id in 0 until COUNT) { "Room ID must be 0..99" }
        return rooms[id]
    }
}
