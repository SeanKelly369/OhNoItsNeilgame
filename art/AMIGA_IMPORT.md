# Amiga recreation: artwork pipeline

## Current state

The existing game already uses Kotlin Multiplatform for its simulation and native
Android Canvas / iOS SwiftUI renderers. The original room contact sheet and tileset
shown in chat are not yet available as files in this workspace.

The new **AMIGA ROOMS** title-screen action opens a reference browser on each platform.
Android also supports R, arrow keys, and Escape. Both browsers use the shared
100-room catalogue and the same PNG assets. Until imported, they show an explicit
missing-art message. The existing playable demo is unchanged.

This is an import/reference foundation, not a completed recreation. Room labels are
transcribed from the supplied preview; clipped names are provisional. Sheet order
does not establish room exits, collision, ladders, NPC behavior, or puzzle rules.

## Import original files

Use the highest-resolution originals available. The room contact sheet must contain
exactly five columns and twenty rows, with no outer margins. The label strip must be
at the bottom of every cell. Supply its height in source pixels. Tile width and height
must describe the actual tile grid, not dimensions guessed from a scaled preview.

Run from the project root, replacing each placeholder:

```sh
./gradlew --no-configuration-cache -I art/import-amiga.gradle importAmigaArtwork \
  -PamigaRooms=/absolute/path/to/rooms.png \
  -PamigaTiles=/absolute/path/to/tiles.png \
  -PamigaLabelHeight=LABEL_PIXELS \
  -PamigaTileWidth=TILE_WIDTH \
  -PamigaTileHeight=TILE_HEIGHT
```

The task validates both inputs, crops 100 room images without filtering or scaling,
and writes the tileset and a source-coordinate manifest under `art/sprites/amiga`.
It keeps the blue sky pixels intact: blue is scenery, not a universal transparency key.
Rerunning replaces these generated outputs. Original input files remain untouched.
Android already packages `art/sprites`; the iOS project already bundles it as `sprites`.
Rebuild the apps after importing.

For a resized contact sheet, integer partitioning assigns every column and row to a
crop; boundary rounding can still include edge pixels from neighboring labels.
Prefer a native-resolution contact sheet and verify representative crops visually.

## Reconstruction work still needed

1. Confirm room names and map directional exits/door destinations from original game data or playthroughs.
2. Author per-room solid geometry, one-way surfaces, ladders, hazards, and spawn points in commonMain.
3. Reconstruct tile indices from original map data where available. Room thumbnails alone cannot recover those indices reliably.
4. Add the original inventory, interactions, enemies, progression, and save state to the shared simulation.
5. Render shared room state in both native clients, with matching collision and gameplay tests.

The browser deliberately does not infer walkable ground from pixel colors or treat
the room contact sheet as a connected world.
