# Oh No! It's Neil — Amiga Remastered

![Oh No! It's Neil — A classic adventure reimagined](art/branding/neil_logo.png)

A self-contained Android and iOS adventure built on Sean Kelly's original Amiga game files (`MAIN_FILES`), combined with the modern, high-resolution Neil character sprite atlas.

The original 71 occupied rooms, collision layers (`GAME_MAP.VAL`), items (`OBJECTS.DAT`), and object names have been remastered into 4x high-resolution tiles and rendered smoothly alongside the modern animated Neil.

## Features & Upgrades
- **Modern Neil Character**: The high-res animated Neil sprite atlas (`neil_sprite_atlas.png`) powers the player animations — idle, walk cycle, jumps, falls, landing squish, and ladder climbing poses (`CLIMB_LEFT`, `CLIMB_RIGHT`, `CLIMB_REST`).
- **High-Res Amiga World**: All 71 room backgrounds and 260 item sprites are upscaled to 4x (1280×768 room canvases) via edge-directed xBR smoothing (`:shared:exportAmigaHd`), eliminating pixel chunkiness while preserving original geometry and color contrast.
- **Accurate Physics & Ladders**: Tile collision (solid blocks, one-way ledges, ladders) directly reflects original Amiga map data. Players can climb ladders, pause mid-rung, jump off, and navigate across room boundaries.
- **Room Atlas & Map Browser**: Tap the `MAP` button (or press `M`) at any time to inspect visited and unvisited rooms across the kingdom and quickly warp to explored locations.
- **Persistent State**: Visited rooms, inventory, and player position are saved across sessions via `SharedPreferences`.

## Controls
- **Touch**: On-screen buttons for `<` (left), `>` (right), `UP` (climb up), `DOWN` (climb down), `JUMP`, `USE` (interact/pickup/drop), `MAP` (world atlas).
- **Keyboard**:
  - `A` / `D` or Left / Right: Walk
  - `W` / `S` or Up / Down: Climb ladders
  - `Space`: Jump
  - `E` or `Enter`: Interact / Pick up items
  - `M`: Room Atlas
  - `P` or `Esc`: Pause / Inventory

## Building and Exporting Art
- Build and run Android: `./gradlew :app:assembleDebug` or Run in Android Studio
- Re-run the 4x HD tile upscaler: `./gradlew :shared:exportAmigaHd`
- Run shared unit tests: `./gradlew :shared:jvmTest`

## Code Architecture (Kotlin Multiplatform)
- `:shared` (Multiplatform Library):
  - `shared/src/commonMain/kotlin/`: pure Kotlin domain models, physics, and game state (`NeilGame.kt`, `NeilGameLoop.kt`).
  - `shared/src/commonTest/kotlin/`: target-agnostic unit tests (`NeilGameTest.kt`, `NeilGameLoopTest.kt`, `NeilAnimationTest.kt`).
  - Configured for Android, JVM, and iOS targets (`iosX64`, `iosArm64`, `iosSimulatorArm64`).
- `:app` (Android Application):
  - `app/src/main/java/`: Android-specific SurfaceView renderer, Choreographer loop, input handling, and activity lifecycle (`NeilGameView.kt`, `NeilSprites.kt`, `MainActivity.kt`).
  - `app/src/androidTest/java/`: Android instrumented tests (`NeilControlsTest.kt`).
- `iosApp` (iOS Native Application):
  - `iosApp/iosApp/`: SwiftUI interface (`ContentView.swift`, `iosAppApp.swift`) connecting to the shared `NeilGame` engine and `NeilGameLoop`.
  - `iosApp/iosApp.xcodeproj`: Xcode project referencing the compiled `Shared.framework`.

## Validation
Run `:shared:allTests`, `:app:assembleDebug`, and `:app:connectedDebugAndroidTest`.
For iOS, run `:shared:linkDebugFrameworkIosSimulatorArm64` or open `iosApp/iosApp.xcodeproj` in Xcode to build and run on simulator/device.
The game uses a 120 Hz physics timestep independently of display refresh rate, with interpolated rendering.
Walking speed is 168 world pixels/second. Jump timing keeps both item routes reachable and the bridge necessary.
NeilGameLoopTest checks steady movement at 60/90/120/144 Hz, irregular frame intervals, reversal and respawn.
Run `:app:connectedDebugAndroidTest` for touch-control regression tests on a connected emulator or device.
This is a demo foundation: no persistent save, audio, or individually accessible virtual controls yet.
