# Oh No! It's Neil — The Mosswood Mix-up

![Oh No! It's Neil — A classic adventure reimagined](art/branding/neil_logo.png)

A self-contained, playable Android and iOS prototype inspired by classic item-puzzle platform adventures.
The world is drawn in code; Neil's sprites and the game branding are bundled artwork.
No downloads, accounts or permissions are required. This prototype has no audio.

The logo appears in the game header, title/pause/completion screens and platform launch screens.
The stacked variant supplies the Android adaptive launcher and iOS app icons.
See [branding assets and generation prompts](art/branding/README.md).

## Play
Run the app configuration in Android Studio. Tap the gold start button.
Phones prefer landscape; the game also fits portrait and resizable windows.

- Hold the arrows to walk; press Jump while holding an arrow to leap.
- Use interacts with the nearby river crossing or cottage.
- Keyboard: A/D or left/right arrows, Space or up to jump, E to use, P to pause, Enter to start.
- Collect eight optional stars. Avoid the two moss monsters.
- Damage returns Neil to a safe bank. Puzzle items stay collected; losing all three hearts refills them.
- Backgrounding pauses play. Rotation preserves the game in a ViewModel. Process termination starts a new game.

## Puzzle walkthrough
1. Climb the three woodland platforms and collect the plank.
2. Stand beside the left bridge post and press Use.
3. Cross the repaired bridge and climb the three ruins platforms for the brass key.
4. Reach the cottage at the far right and press Use.
5. Play Again resets the adventure and collectibles.

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
