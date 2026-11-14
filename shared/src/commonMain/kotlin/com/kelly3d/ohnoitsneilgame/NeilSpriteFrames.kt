package com.kelly3d.ohnoitsneilgame

/** Indices into art/sprites/neil_sprite_atlas.json, shared by both renderers. */
object NeilSpriteFrames {
    const val COUNT = 44
    const val JUMP = 8
    const val FALL = 9
    const val LAND = 10
    const val HURT = 11
    const val IDLE_RIGHT = 16
    // Appended so the original 32 pose indices remain stable.
    const val WALK_START = 32
    const val WALK_COUNT = 12
    const val WALK_FPS = 20f
    const val IDLE_FRONT = 23
    const val IDLE_BACK = 24
    const val SHRUG = 25
    const val POINT_RIGHT = 26
    const val IDLE_LEFT = 27
    // Prepared artwork; ladder physics are not implemented yet.
    const val CLIMB_LEFT = 28
    const val CLIMB_RIGHT = 29
    const val CLIMB_ALTERNATE = 30
    const val CLIMB_REST = 31
}
