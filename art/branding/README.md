# Neil branding

These assets were derived from the user's supplied concept sheet using the built-in imagegen tool.
They are generated extractions/adaptations, not a lossless crop of the original attachment.

- `neil_logo.png`: wide transparent title artwork, with the original tagline.
- `neil_icon_source.png`: stacked opaque launcher variant; the tagline is omitted at icon sizes.
- Android packages the wide logo and a 1024px icon in `drawable-nodpi`.
- iOS packages the same logo and an opaque RGB 1024px icon in `Assets.xcassets`.
- UI renderers fit the logo without clipping or changing its aspect ratio.
- Android's adaptive icon includes additional mask padding. Its round and regular manifest entries use the same adaptive asset.
- Android 12+ uses the adaptive icon on its system splash; older Android and iOS show the wide logo.

The copies are self-contained and require no generation service or runtime network access.

## Re-export

From the project root, after replacing the retained sources:

```sh
./gradlew --no-configuration-cache -I art/import-branding.gradle importNeilBranding \
  -PlogoSource="$PWD/art/branding/neil_logo.png" \
  -PiconSource="$PWD/art/branding/neil_icon_source.png"
```

The importer copies the artwork and normalizes the platform icon to opaque 1024px RGB.
No character or gameplay assets are modified.

## Prompts (built-in imagegen)

Wide logo:
"Use case: background-extraction. Asset type: existing game logo for Android and iOS game screens. Edit target: the user's attached Oh No! It's Neil concept sheet. Isolate ONLY the logo in the upper-left banner: the red 'OH NO!', orange/yellow 'IT’S NEIL', the small metallic dot above the I, black pixel outlines and shadows, and white subtitle 'A CLASSIC ADVENTURE REIMAGINED'. Remove the blue-haired character, the entire rest of the sheet, all scenery, other text and navy background. Preserve the original logo lettering, word arrangement, colors, pixel-art texture, proportions, outlines and subtitle exactly; do not redesign or add any new elements. Tight wide crop with just a small transparent margin around the whole logo, actual transparent alpha background. The complete logo and subtitle must be uncut."

Launcher:
"Use case: compositing. Asset type: square mobile app icon variant of the provided game logo. Rearrange the exact provided pixel-art lettering into a compact stacked arrangement: red OH NO! across the top, small golden IT’S centered in the middle, large yellow-orange NEIL across the bottom. Keep original letter shapes, chunky black outlines and shadows, pixel art texture, orange yellow gradient, and metallic dot above the I in NEIL. Omit the small subtitle for app-icon readability. Square 1024x1024 opaque image, flat dark navy #0E1828 background edge to edge, no rounded corner mask, no border, no other graphics or characters. Logo fills the central 82% of the square, centered, with generous clear padding on every side. Exact text OH NO! IT’S NEIL."
