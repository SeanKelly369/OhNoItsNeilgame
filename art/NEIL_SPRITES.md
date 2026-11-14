# Neil sprite atlas

Open [sprites/neil_sprite_atlas.png](sprites/neil_sprite_atlas.png) to view the production artwork.
It is a transparent RGBA PNG, 1252 × 3443 pixels: four columns by eleven rows of 313 × 313 cells.
The adjacent JSON manifest records each frame's name, pixel rectangle, and visible foot baseline.

Android packages `art/sprites` directly as its assets directory. Xcode copies the same directory into
the iOS application bundle as `sprites`. Both renderers cache the artwork, use nearest-neighbor
sampling, and align the feet at the same world position.

## Frame map (zero-based, row-major)

| Indices | Poses | Current use |
| --- | --- | --- |
| 0–3 | Original idle | Retained |
| 4–7 | Original walk | Retained |
| 8–11 | Jump, fall, land, hurt | Gameplay |
| 12–15 | Original interaction | Retained |
| 16 | Right idle | Gameplay |
| 17–22 | Previous six-pose walk | Retained |
| 23–25 | Front idle, rear idle, shrug | Available |
| 26 | Point right | Use action |
| 27 | Left idle | Available; gameplay mirrors right-facing sprites |
| 28–30 | Rear climbing | Ladder animation |
| 31 | Rear rest | Resting on a ladder |
| 32–43 | Twelve-pose right walk | Gameplay at 20 fps |

The shared `NeilSpriteFrames` constants define these indices for game logic. The original
32 frames and their metadata are preserved when the twelve-pose walk is imported.

## Twelve-pose walk cycle

[source/neil_walk_cycle_12.png](source/neil_walk_cycle_12.png) is a four-column, three-row
sheet generated with the built-in imagegen tool using the existing character and walk sheets
as references. The exact prompt is in [NEIL_WALK_CYCLE_12_PROMPT.txt](NEIL_WALK_CYCLE_12_PROMPT.txt).
The twelve distinct poses add weight shifts, foot lifts and passing positions between contacts.

Both games use twelve frames at 20 fps, keeping the previous 0.6-second full stride duration.
The iOS renderer uses the same twelve atlas frames. The importer uses one scale for the entire
sheet, aligns the purple-hat centre horizontally, aligns the support-foot baseline, and applies
the dark hair palette followed by consistent blue highlights. It checks that all twelve lower-body images differ and that the original
32 frames are pixel-identical.

Open [neil_walk_preview.html](neil_walk_preview.html) in a browser to play, pause, slow down,
or step through the cycle in both directions. No server is required.

## Earlier artwork and consistent hair highlights

The earlier six walking poses remain in [source/neil_walk_cycle.png](source/neil_walk_cycle.png);
their prompt is [NEIL_WALK_CYCLE_PROMPT.txt](NEIL_WALK_CYCLE_PROMPT.txt).
The other added poses come from [source/neil_additional_poses.png](source/neil_additional_poses.png),
with prompt [NEIL_ADDITIONAL_POSES_PROMPT.txt](NEIL_ADDITIONAL_POSES_PROMPT.txt).
The original Base64 PNG in `app/src/main/assets/neil_sprite_sheet.txt` is an import source only.

[RemoveNeilHairHighlights.java](RemoveNeilHairHighlights.java) caps bright blue hair colours and
replaces pale glints enclosed by hair. It preserves every alpha value and all pixels outside its
hair mask. Dark strand shadows remain visible. It supports four-column sheets of square cells,
including the original 32-frame sheet and the new twelve-frame sheet.

[AddNeilHairHighlights.java](AddNeilHairHighlights.java) then adds a restrained sheen using
the same palette in every frame: dark blue `#0c2ab4`, soft blue `#1748c3`, and lighter blue
`#2b65d8`. It highlights 20% of the eligible hair-body pixels, with the lightest shade limited
to 5%. Highlight placement follows the interiors of the existing strands and a consistent
upper-left light bias. Dark grooves, outlines, transparency, and frame geometry are preserved.
The pass verifies its PNG output and that applying it again produces identical pixels.

The complete 44-frame dark atlas is retained at
[source/neil_before_consistent_highlights.png](source/neil_before_consistent_highlights.png).
To regenerate the highlighted production atlas directly from this backup:

```sh
java art/AddNeilHairHighlights.java art/source/neil_before_consistent_highlights.png art/sprites/neil_sprite_atlas.png
```

The pre-edit 32-frame atlas, including local artwork changes, is retained at
[source/neil_before_highlight_removal.png](source/neil_before_highlight_removal.png).
Restoring that backup directly would omit the new walking frames; re-import the walk afterward.

## Re-export

To update only the twelve walking poses while preserving the current first 32 frames:

```sh
./gradlew --no-configuration-cache -I art/export-neil-atlas.gradle exportNeilSmoothWalk
```

To rebuild everything from the retained source sheets:

```sh
./gradlew --no-configuration-cache -I art/export-neil-atlas.gradle exportNeilAtlas
```

The full export packs the original sheets, normalizes the hair and adds the consistent sheen,
then appends the new cycle with the same two colour passes.
To import replacement additional poses, append `-PneilPoses=/absolute/path/to/poses.png`.
To replace the retained six-pose source, append `-PneilWalk=/absolute/path/to/walk.png`.
The latter checks that the other 26 original frames are unchanged before appending the twelve-pose cycle.
To replace the active twelve-pose source, update `art/source/neil_walk_cycle_12.png` with a transparent
four-column, three-row sheet and run `exportNeilSmoothWalk`.
