# Neil Map Kit — first version

A desktop tile editor backed by the game's Kotlin Multiplatform module.
Run from the project root:

```sh
./gradlew :shared:runMapEditor
```

A demo level and four simple tiles are included in code, so no artwork download is needed.
This is a map-authoring foundation. The existing game still uses its original hardcoded
platforms; loading these maps into gameplay is a separate next step.

## Open the reconstructed forest

Click **Forest map** in the toolbar, or launch it directly:

```sh
./gradlew :shared:runMapEditor --args="--open art/sprites/maps/forest/forest.neilmap"
```

The editable 120 × 24 map uses 16-pixel tiles, with three 40-cell compositions:
carved-tree forest, open clearing, and tree house. See
[the rendered overview](../../art/sprites/maps/forest/forest_preview.png).
Switch off Grid and Collision overlay to inspect the artwork.

These are approximate compositions transcribed from the three chat references, using
code-drawn replacement tiles. They are not a pixel-exact Amiga reconstruction.
The original images are still unavailable as local assets. All three rooms share
a ground baseline at row 19; horizontal order follows the order the images were supplied,
not confirmed original room exits. Trees and the house are non-solid scenery.
The doorway is visual only. Ground is solid; the starting cell is (2, 17).

The map and its atlas are saved in `art/sprites/maps/forest/`, which both apps package.
They are editable in the kit; gameplay still requires integration with the map runtime.
Loading **Forest map** reads the saved map, so subsequent edits are preserved.
The preview is an initial export and does not automatically update when you edit the map.

The Kotlin layout source is `ForestReconstruction.kt`; `ForestAssets.kt` builds the
replacement palette and initial preview. Export a fresh copy into a new directory with:

```sh
./gradlew :shared:runMapEditor --args="--export-forest /tmp/neil-forest-copy"
```

Export refuses to overwrite existing forest output files.

## Create a map

1. Use **Tileset…** to select a local image and enter its tile size, such as 16 × 16.
   Tiles must form a uniform grid without spacing or margins. This starts an empty map
   because tile IDs from another atlas would be ambiguous.
2. Use **New** to choose dimensions (up to 256 × 256 cells), keeping the selected tileset.
3. Select Background, Terrain, or Foreground. Pick a palette tile and drag on the map.
   Right-drag erases the active layer. **Erase** also works with the left button.
4. Select **Collision** and paint Empty, Solid, One-way, Ladder, or Hazard cells.
   Collision is separate from art. Right-drag clears collision.
5. Select **Spawn** and click a cell. The white S marks the spawn cell.
6. **Undo/Redo** operates per drag gesture, with up to 30 undo snapshots.
7. **Validate**, then **Save…**. Saving writes a UTF-8 .neilmap file and its PNG tileset
   next to it (or in the relative subdirectory recorded by the map). Replacement of
   existing files requires confirmation. Spawn cells inside solids or hazards are rejected.

New, Open, Tileset, and closing the window prompt before discarding edits.
The zoom selector and scrollbars support larger maps. Collision overlays and grid can be hidden.
Atlas selection currently uses one tile at a time; multi-tile brushes, flood fill, entity placement,
room links, gameplay preview, and automatic tile rules are future extensions.

The supplied forest/tree-house pictures are complete scenes, not pre-indexed tile maps.
They can be sliced on a grid, but that does not infer reusable tiles or collisions.
Use the original Amiga tileset when its local file is available.

## Shared format and runtime API

`shared/src/commonMain/kotlin/com/kelly3d/ohnoitsneilgame/maps/TileMap.kt` contains:

- TileMap: grid dimensions, atlas geometry, three tile layers, collision, and spawn.
- TileMapCodec: strict versioned encode/decode, usable on Android and iOS.
- sourceX/sourceY: source rectangle origin in the atlas, for native image rendering.
- overlapsSolid: half-open pixel rectangle collision query. Outside the map is empty.
- validationErrors: authoring checks, currently spawn vs. solid/hazard.

Coordinates use a top-left origin. A spawn is a cell, not a player-foot position.
Each layer uses row-major tile IDs; -1 means empty and tile 0 is valid.
Render in Background, Terrain, Foreground order, placing actors before Foreground.
Render nearest-neighbor at integer scales where possible.

`.neilmap` layout:

1. `NEILMAP 1`
2. Six integers: map width, map height, tile width, tile height, atlas columns, atlas rows.
3. Relative tileset path, using letters, numbers, underscores, hyphens, dots, and slashes.
4. Spawn column and row.
5. BACKGROUND, TERRAIN, FOREGROUND: each heading followed by map-height rows of map-width IDs.
6. COLLISION: map-height rows, with codes 0 empty, 1 solid, 2 one-way, 3 ladder, 4 hazard.

The parser rejects unsupported versions, invalid dimensions, invalid IDs, malformed rows,
truncated data, unsafe asset paths, and trailing data. Collision tags other than Solid are
metadata: the future game controller must implement directional platforms, ladders, and hazards.

Save production assets under `art/sprites/maps/` to package them through the existing
Android assets and iOS sprites bundle. Native clients will need to read the map text,
decode it with TileMapCodec, and load the atlas relative to the map's asset directory.

## Verification

```sh
./gradlew :shared:jvmTest :app:assembleDebug
./gradlew :shared:runMapEditor --args="--smoke-test /tmp/neil-map-kit.png"
```

The second command renders the demo without opening a window, checks the codec round trip
and collision query, and writes a preview PNG.
