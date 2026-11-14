import XCTest
import UIKit
import SwiftUI
@testable import iosApp

@MainActor
final class GameControlsTests: XCTestCase {
    func testSharedSpritePNGAndManifestAreBundledAndAllFramesDecode() {
        let sprites = NeilSprites()
        XCTAssertEqual(sprites.frameCount, 32)
        XCTAssertEqual(sprites.walkFrameCount, 6)
        // Verify the full-resolution 313x313 production atlas is loaded.
        let idle = sprites.cgImage(forFrame: 16)
        XCTAssertEqual(idle?.width, 313)
        XCTAssertEqual(idle?.height, 313)
    }

    func testWalkCycleLoopsAcrossAllSixBiomechanicalPhases() {
        let sprites = NeilSprites()
        XCTAssertEqual(sprites.walkFrameCount, 6)
        for tick in 0..<18 {
            let time = Float(Double(tick) * NeilSprites.walkDuration / 6.0 + 0.001)
            XCTAssertEqual(sprites.walkFrameIndex(frame: 17 + tick % 6, walkTime: time), tick % 6)
        }
        for frame in [8, 9, 10, 11, 16, 26, 28, 29, 30, 31] {
            XCTAssertNil(sprites.walkFrameIndex(frame: frame, walkTime: 0.35))
        }
        for phase in 0..<6 {
            XCTAssertEqual(sprites.walkFrameIndex(frame: 17 + phase, walkTime: 0), phase)
        }
    }

    func testWalkCyclePreservesConsistentProportionsAndAnimatesEveryPhase() throws {
        let sprites = NeilSprites()
        for facing: Int32 in [-1, 1] {
            var renderedFrames: [Data] = []
            for index in 0..<6 {
                let time = Float(Double(index) * NeilSprites.walkDuration / 6.0 + 0.001)
                let renderer = ImageRenderer(content: Canvas { context, _ in
                    sprites.draw(context, frame: 17 + index, facing: facing, x: 72, y: 140,
                                 height: 128, walkTime: time)
                }.frame(width: 144, height: 160))
                renderer.scale = 1
                let image = try XCTUnwrap(renderer.cgImage)
                let lowerBody = try XCTUnwrap(image.cropping(to: CGRect(x: 0, y: 72, width: 144, height: 88)))
                let lowerData = try XCTUnwrap(lowerBody.dataProvider?.data) as Data
                renderedFrames.append(lowerData)
            }
            XCTAssertEqual(Set(renderedFrames).count, 6, "All 6 walk phases must be distinct for facing \(facing)")
        }
    }

    private func makeGame(width: CGFloat = 480) -> (GameController, GameControlsView) {
        let controller = GameController()
        controller.startAdventure()
        controller.tick(date: Date(timeIntervalSince1970: 0))
        let view = GameControlsView(frame: CGRect(x: 0, y: 0, width: width, height: 320))
        view.onDirectionChange = { [weak controller] in controller?.setDirection($0) }
        view.onClimbChange = { [weak controller] in controller?.setClimb($0) }
        view.onAction = { [weak controller] in controller?.activate($0) }
        view.controlsEnabled = true
        view.setNeedsLayout()
        view.layoutIfNeeded()
        return (controller, view)
    }

    private func center(_ action: GameControl, in view: GameControlsView) -> CGPoint {
        let frame = view.controlFrames[action]!
        return CGPoint(x: frame.midX, y: frame.midY)
    }

    func testJumpWhileMovingBothWaysAndReleasingJumpKeepsMovement() async {
        for action in [GameControl.left, .right] {
            let (controller, view) = makeGame()
            XCTAssertTrue(view.isMultipleTouchEnabled)
            let start = controller.game.x
            view.beginTouch(id: 7, at: center(action, in: view))
            view.beginTouch(id: 12, at: center(.jump, in: view))
            controller.tick(date: Date(timeIntervalSince1970: 0.1))
            XCTAssertGreaterThan((controller.game.x - start) * Float(action.direction), 0)
            XCTAssertEqual(controller.game.facing, action.direction)
            view.releaseInput(12)
            let before = controller.game.x
            controller.tick(date: Date(timeIntervalSince1970: 0.2))
            XCTAssertGreaterThan((controller.game.x - before) * Float(action.direction), 0)
        }
    }

    func testJumpFirstThenMoveAndReleaseMovementOnly() async {
        for action in [GameControl.left, .right] {
            let (controller, view) = makeGame()
            view.beginTouch(id: 12, at: center(.jump, in: view))
            view.beginTouch(id: 7, at: center(action, in: view))
            controller.tick(date: Date(timeIntervalSince1970: 0.1))
            XCTAssertEqual(controller.direction, action.direction)
            view.releaseInput(7)
            let stoppedX = controller.game.x
            controller.tick(date: Date(timeIntervalSince1970: 0.2))
            XCTAssertEqual(controller.game.x, stoppedX)
        }
    }

    func testLatestDirectionWinsAndOlderDirectionReturns() async {
        let (controller, view) = makeGame()
        view.beginTouch(id: 7, at: center(.left, in: view))
        view.beginTouch(id: 12, at: center(.right, in: view))
        XCTAssertEqual(controller.direction, 1)
        controller.tick(date: Date(timeIntervalSince1970: 0.1))
        let before = controller.game.x
        view.releaseInput(12)
        controller.tick(date: Date(timeIntervalSince1970: 0.2))
        XCTAssertLessThan(controller.game.x, before)
        XCTAssertEqual(controller.game.facing, -1)
    }

    func testClimbUpChangesClimbInput() async {
        let (controller, view) = makeGame()
        view.beginTouch(id: 5, at: center(.up, in: view))
        XCTAssertEqual(controller.climb, -1)
        view.releaseInput(5)
        XCTAssertEqual(controller.climb, 0)
        view.beginTouch(id: 6, at: center(.down, in: view))
        XCTAssertEqual(controller.climb, 1)
        view.releaseInput(6)
        XCTAssertEqual(controller.climb, 0)
    }

    func testJumpFiresOnPressAndEachFingerOnlyFiresOnce() async {
        let (_, view) = makeGame()
        var actions: [GameControl] = []
        view.onAction = { actions.append($0) }
        view.beginTouch(id: 7, at: center(.left, in: view))
        view.beginTouch(id: 12, at: center(.jump, in: view))
        XCTAssertEqual(actions, [.jump], "Jump must not wait for finger release")
        view.moveTouch(id: 12, to: center(.jump, in: view))
        view.releaseInput(12)
        XCTAssertEqual(actions, [.jump])
        XCTAssertEqual(view.input.direction, -1)
        view.beginTouch(id: 13, at: center(.jump, in: view))
        XCTAssertEqual(actions, [.jump, .jump])
    }

    func testSuspendingClearsInputs() async {
        let (controller, view) = makeGame()
        view.beginTouch(id: 7, at: center(.left, in: view))
        controller.tick(date: Date(timeIntervalSince1970: 0.1))
        let stoppedX = controller.game.x
        controller.pause()
        view.resetVersion = controller.controlResetVersion
        view.controlsEnabled = controller.controlsEnabled
        XCTAssertTrue(view.input.held.isEmpty)

        controller.startAdventure()
        view.resetVersion = controller.controlResetVersion
        view.controlsEnabled = controller.controlsEnabled
        controller.tick(date: Date(timeIntervalSince1970: 20))
        controller.tick(date: Date(timeIntervalSince1970: 20.1))
        XCTAssertEqual(controller.direction, 0)
        XCTAssertEqual(controller.game.x, stoppedX, "No stale movement on resume")
    }

    func testFacingPersistsAfterMovementStops() async {
        let (controller, view) = makeGame()
        view.beginTouch(id: 7, at: center(.left, in: view))
        controller.tick(date: Date(timeIntervalSince1970: 0.1))
        view.releaseInput(7)
        controller.tick(date: Date(timeIntervalSince1970: 0.2))
        XCTAssertEqual(controller.direction, 0)
        XCTAssertEqual(controller.game.facing, -1)
    }

    func testDpadDragChangesBetweenWalkingAndClimbing() {
        let (controller, view) = makeGame()
        view.beginTouch(id: 1, at: center(.left, in: view))
        view.moveTouch(id: 1, to: center(.up, in: view))
        XCTAssertEqual(controller.direction, 0)
        XCTAssertEqual(controller.climb, -1)
        view.moveTouch(id: 1, to: center(.right, in: view))
        XCTAssertEqual(controller.direction, 1)
        XCTAssertEqual(controller.climb, 0)
    }

    func testControlsPassViewportTapsThroughAndHideOnPause() {
        let (_, view) = makeGame()
        XCTAssertNil(view.hitTest(CGPoint(x: 240, y: 40), with: nil))
        XCTAssertTrue(view.hitTest(center(.jump, in: view), with: nil) === view)
        view.controlsEnabled = false
        XCTAssertNil(view.hitTest(center(.jump, in: view), with: nil))
    }

    func testHarryFramesDecodeAndHaveAlignedBootAnchors() {
        let animator = HarryAnimator(position: CGPoint(x: 240, y: 160))
        XCTAssertEqual(animator.frames.count, 10)
        for frame in animator.frames {
            XCTAssertGreaterThan(frame.width, 0)
            XCTAssertGreaterThan(frame.height, 0)
            XCTAssertEqual(frame.baselineY / frame.height, 0.95, accuracy: 0.02)
        }
        // Leftmost atlas columns have feet further right inside their cell than rightmost columns.
        XCTAssertGreaterThan(animator.frames[0].anchorX, animator.frames[3].anchorX + 30)
        XCTAssertEqual(animator.frames[0].anchorX, animator.frames[4].anchorX, accuracy: 2.0)
        XCTAssertEqual(animator.frames[2].anchorX, animator.frames[6].anchorX, accuracy: 2.0)
        // Turnaround frames preserve Frame 0's exact size and boot ground pivot.
        for turnIndex in [HarryAnimator.Pose.turnQuarter, HarryAnimator.Pose.turnFront] {
            XCTAssertEqual(animator.frames[turnIndex].width, animator.frames[0].width)
            XCTAssertEqual(animator.frames[turnIndex].height, animator.frames[0].height)
            XCTAssertEqual(animator.frames[turnIndex].anchorX, animator.frames[0].anchorX)
            XCTAssertEqual(animator.frames[turnIndex].baselineY, animator.frames[0].baselineY)
        }
    }

    func testHarryEyeSizeRemainsConsistentDuringTurnaround() {
        let animator = HarryAnimator(position: CGPoint(x: 240, y: 160))

        func measureEyeSclera(in image: CGImage) -> CGSize {
            let w = image.width
            let h = image.height
            var bytes = [UInt8](repeating: 0, count: w * h * 4)
            let ctx = CGContext(
                data: &bytes,
                width: w,
                height: h,
                bitsPerComponent: 8,
                bytesPerRow: w * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            )!
            ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))

            var minX = w, maxX = -1, minY = h, maxY = -1
            for y in 36...75 {
                for x in 190...290 {
                    let i = (y * w + x) * 4
                    let r = Int(bytes[i])
                    let g = Int(bytes[i + 1])
                    let b = Int(bytes[i + 2])
                    let a = Int(bytes[i + 3])
                    if a > 200 && r > 190 && g > 190 && b > 190 {
                        minX = min(minX, x)
                        maxX = max(maxX, x)
                        minY = min(minY, y)
                        maxY = max(maxY, y)
                    }
                }
            }
            return CGSize(width: maxX - minX + 1, height: maxY - minY + 1)
        }

        let idleEye = measureEyeSclera(in: animator.frames[HarryAnimator.Pose.idle].cgImage)
        let quarterEye = measureEyeSclera(in: animator.frames[HarryAnimator.Pose.turnQuarter].cgImage)
        let frontEye = measureEyeSclera(in: animator.frames[HarryAnimator.Pose.turnFront].cgImage)

        XCTAssertEqual(quarterEye.width, idleEye.width, accuracy: 2.0)
        XCTAssertEqual(quarterEye.height, idleEye.height, accuracy: 2.0)
        XCTAssertEqual(frontEye.width, idleEye.width, accuracy: 2.0)
        XCTAssertEqual(frontEye.height, idleEye.height, accuracy: 2.0)
        XCTAssertEqual(quarterEye.width, frontEye.width, accuracy: 1.0)
        XCTAssertEqual(quarterEye.height, frontEye.height, accuracy: 1.0)
    }

    func testHarryGreetsAndFacesNeilOnApproach() {
        let animator = HarryAnimator(position: CGPoint(x: 240, y: 160))
        // Start far to the left in the lumberjack room.
        animator.update(time: 0.1, neilX: 40, neilY: 160, inRoom: true)
        XCTAssertNil(animator.activeSequence)

        // Walk near Harry from the right side -> steps through turnaround frames before waving.
        animator.update(time: 0.2, neilX: 290, neilY: 160, inRoom: true)
        XCTAssertEqual(animator.activeSequence?.kind, .greeting)
        XCTAssertFalse(animator.facesLeft)
        XCTAssertGreaterThan(animator.turnTimer, 0)
        var pose = animator.currentRenderPose(at: 0.2)
        XCTAssertEqual(pose.frame, HarryAnimator.Pose.turnQuarter)
        XCTAssertTrue(pose.facesLeft)

        animator.update(time: 0.28, neilX: 290, neilY: 160, inRoom: true)
        pose = animator.currentRenderPose(at: 0.28)
        XCTAssertEqual(pose.frame, HarryAnimator.Pose.turnFront)

        animator.update(time: 0.35, neilX: 290, neilY: 160, inRoom: true)
        pose = animator.currentRenderPose(at: 0.35)
        XCTAssertEqual(pose.frame, HarryAnimator.Pose.turnQuarter)
        XCTAssertFalse(pose.facesLeft)

        // After turnaround completes, Harry faces right and waves at Neil.
        animator.update(time: 0.42, neilX: 290, neilY: 160, inRoom: true)
        pose = animator.currentRenderPose(at: 0.42)
        XCTAssertEqual(pose.frame, HarryAnimator.Pose.gestureForward)
        XCTAssertFalse(pose.facesLeft)

        animator.update(time: 0.55, neilX: 290, neilY: 160, inRoom: true)
        XCTAssertEqual(animator.currentFrameIndex(at: 0.55), HarryAnimator.Pose.waveMid)

        animator.update(time: 0.65, neilX: 290, neilY: 160, inRoom: true)
        XCTAssertEqual(animator.currentFrameIndex(at: 0.65), HarryAnimator.Pose.waveHigh)
    }

    func testHarryTurnaroundRightToLeftStepsThroughFrontAndQuarterFrames() {
        let animator = HarryAnimator(position: CGPoint(x: 240, y: 160))
        animator.update(time: 0.1, neilX: 40, neilY: 160, inRoom: true)
        // Turn Harry to face right first.
        animator.speak(at: 0.2, neilX: 290)
        var t: Float = 0.25
        while t <= 0.45 {
            animator.update(time: t, neilX: 290, neilY: 160, inRoom: true)
            t += 0.05
        }
        XCTAssertFalse(animator.facesLeft)
        XCTAssertEqual(animator.turnTimer, 0)

        // Now talk from the left -> plays 1/4 Right -> Front -> 1/4 Left -> Talking facing left.
        animator.speak(at: 0.50, neilX: 200)
        XCTAssertTrue(animator.facesLeft)

        var pose = animator.currentRenderPose(at: 0.50)
        XCTAssertEqual(pose.frame, HarryAnimator.Pose.turnQuarter)
        XCTAssertFalse(pose.facesLeft)

        animator.update(time: 0.58, neilX: 200, neilY: 160, inRoom: true)
        pose = animator.currentRenderPose(at: 0.58)
        XCTAssertEqual(pose.frame, HarryAnimator.Pose.turnFront)

        animator.update(time: 0.65, neilX: 200, neilY: 160, inRoom: true)
        pose = animator.currentRenderPose(at: 0.65)
        XCTAssertEqual(pose.frame, HarryAnimator.Pose.turnQuarter)
        XCTAssertTrue(pose.facesLeft)

        animator.update(time: 0.72, neilX: 200, neilY: 160, inRoom: true)
        pose = animator.currentRenderPose(at: 0.72)
        XCTAssertEqual(pose.frame, HarryAnimator.Pose.waveMid)
        XCTAssertTrue(pose.facesLeft)
    }

    func testHarryTalkingAnimatesThroughMultiplePoses() {
        let animator = HarryAnimator(position: CGPoint(x: 240, y: 160))
        animator.update(time: 1.0, neilX: 210, neilY: 160, inRoom: true)
        animator.speak(at: 1.0, neilX: 210)
        XCTAssertEqual(animator.activeSequence?.kind, .talking)
        XCTAssertTrue(animator.facesLeft)

        var seenFrames: Set<Int> = []
        var t: Float = 1.0
        while t < 3.0 {
            animator.update(time: t, neilX: 210, neilY: 160, inRoom: true)
            seenFrames.insert(animator.currentFrameIndex(at: t))
            t += 0.05
        }
        XCTAssertGreaterThanOrEqual(seenFrames.count, 4)
    }

    func testHarryBlinksDuringIdle() {
        let animator = HarryAnimator(position: CGPoint(x: 240, y: 160))
        var sawBlink = false
        var t: Float = 0.0
        while t < 6.0 {
            animator.update(time: t, neilX: 20, neilY: 160, inRoom: true)
            if animator.currentFrameIndex(at: t) == HarryAnimator.Pose.blink {
                sawBlink = true
                break
            }
            t += 0.05
        }
        XCTAssertTrue(sawBlink)
    }
}
