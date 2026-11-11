# Neil sprite atlas

Open [sprites/neil_sprite_atlas.png](sprites/neil_sprite_atlas.png) to view the production artwork.
It is a transparent RGBA PNG, 1252 × 2504 pixels: four columns by eight rows of 313 × 313 cells.
The adjacent JSON manifest records each frame's name, pixel rectangle, and visible foot baseline.

Android packages `art/sprites` directly as its assets directory. Xcode copies the same directory into
the iOS application bundle as `sprites`. There are no platform-specific artwork copies to keep in sync.
Both renderers cache the artwork, use nearest-neighbor sampling, and align the feet at the same world position.

## Frame map (zero-based, row-major)

| Indices | Poses | Current use |
| --- | --- | --- |
| 0–3 | Original idle | Retained |
| 4–7 | Original walk | Retained |
| 8–11 | Jump, fall, land, hurt | Gameplay |
| 12–15 | Original interaction | Retained |
| 16 | New right idle | Gameplay |
| 17–22 | Six revised right walking frames | Gameplay |
| 23–25 | Front idle, rear idle, shrug | Ready for future interactions |
| 26 | Point right | Use action |
| 27 | Left idle | Available; gameplay mirrors the right-facing sprites |
| 28–30 | Three rear climbing frames | Ready for a ladder mechanic |
| 31 | Rear rest | Ready for a ladder mechanic |

The shared `NeilSpriteFrames` constants define these indices for game logic. Ladder physics are not implemented.
Ladders and reference backgrounds were removed from the character art so scenery can be drawn independently.

The new poses were prepared from the two supplied references using the built-in imagegen tool.
They are reference-guided redraws, not pixel-identical extractions. The exact prompt is saved in
[NEIL_ADDITIONAL_POSES_PROMPT.txt](NEIL_ADDITIONAL_POSES_PROMPT.txt); its unmodified output is retained
as [source/neil_additional_poses.png](source/neil_additional_poses.png).

The original Base64 PNG remains in `app/src/main/assets/neil_sprite_sheet.txt` as an import source only;
it is no longer loaded or packaged as an Android asset. The earlier block-art
`app/src/main/res/drawable/neil_sprite_atlas.png` is also unused. The active PNG is the one linked above.

## Revised walk cycle

Frames 17–22 now use [source/neil_walk_cycle.png](source/neil_walk_cycle.png), a dedicated
three-column, two-row sheet generated with the built-in imagegen tool. The cycle alternates
contact, passing and raised-knee push-off poses, with arm swing and hair follow-through.
The prompts are retained in [NEIL_WALK_CYCLE_PROMPT.txt](NEIL_WALK_CYCLE_PROMPT.txt).
Open [neil_walk_preview.html](neil_walk_preview.html) in a browser to play, pause or slow
the loop in both directions at the game's normal 10 fps.

The replacement cells are scaled with nearest-neighbor sampling into the existing 313 × 313
slots. All other frames remain pixel-identical. Foot baselines are recalculated for both renderers.

## Re-export

The importer packs the original source sheets losslessly and overlays the revised walk cells using
nearest-neighbor scaling. It generates the frame rectangles and foot baselines. Run from the project root:

```sh
./gradlew --no-configuration-cache -I art/export-neil-atlas.gradle exportNeilAtlas
```

To import a replacement additional-pose sheet, append `-PneilPoses=/absolute/path/to/poses.png`.
This updates the retained pose source, production PNG, and JSON; it does not modify the original 16 frames.
To replace the dedicated walk sheet, append `-PneilWalk=/absolute/path/to/walk.png`.
When importing only a walk sheet, the exporter verifies all 26 other frames are pixel-identical
before writing the production atlas.
