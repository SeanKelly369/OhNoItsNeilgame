import XCTest
import UIKit
import SwiftUI
@testable import iosApp

@MainActor
final class GameControlsTests: XCTestCase {
    func testSharedSpritePNGAndManifestAreBundledAndAllFramesDecode() {
        XCTAssertEqual(NeilSprites().frameCount, 32)
    }

    private func makeGame(width: CGFloat = 480) -> (GameController, GameControlsView) {
        let controller = GameController()
        controller.startOrResume()
        controller.tick(date: Date(timeIntervalSince1970: 0))
        let view = GameControlsView(frame: CGRect(x: 0, y: 0, width: width, height: 48))
        view.onDirectionChange = { [weak controller] in controller?.setDirection($0) }
        view.onAction = { [weak controller] in controller?.activate($0) }
        view.controlsEnabled = true
        view.setNeedsLayout()
        view.layoutIfNeeded()
        return (controller, view)
    }

    func testHeldMovementAdvancesAndRedrawsWithoutOtherUIChanges() async throws {
        let controller = GameController()
        controller.startOrResume()
        let host = UIHostingController(rootView: ContentView(controller: controller).ignoresSafeArea())
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.first as? UIWindowScene)
        let window = UIWindow(windowScene: scene)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer {
            controller.suspend()
            window.isHidden = true
            window.rootViewController = nil
        }

        func snapshot() -> Data? {
            let renderer = UIGraphicsImageRenderer(bounds: host.view.bounds)
            return renderer.image { _ in
                host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
            }.pngData()
        }

        // Exercise the real TimelineView/Canvas, not just manual calls to tick().
        try await Task.sleep(nanoseconds: 300_000_000)
        let before = try XCTUnwrap(snapshot())
        let startX = controller.game.x
        controller.setDirection(1)
        try await Task.sleep(nanoseconds: 300_000_000)
        controller.setDirection(0)
        let after = try XCTUnwrap(snapshot())

        XCTAssertGreaterThan(controller.game.x - startX, 10, "The live frame clock must keep advancing while held")
        XCTAssertNotEqual(before, after, "Physics changes must invalidate the visible canvas")
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
            XCTAssertLessThan(controller.game.y, 198)
            XCTAssertGreaterThan((controller.game.x - start) * Float(action.direction), 10)
            XCTAssertEqual(controller.game.facing, action.direction)
            view.releaseInput(12)
            let before = controller.game.x
            controller.tick(date: Date(timeIntervalSince1970: 0.2))
            XCTAssertGreaterThan((controller.game.x - before) * Float(action.direction), 10)
        }
    }

    func testJumpFirstThenMoveAndReleaseMovementOnly() async {
        for action in [GameControl.left, .right] {
            let (controller, view) = makeGame()
            view.beginTouch(id: 12, at: center(.jump, in: view))
            view.beginTouch(id: 7, at: center(action, in: view))
            controller.tick(date: Date(timeIntervalSince1970: 0.1))
            XCTAssertEqual(controller.direction, action.direction)
            XCTAssertLessThan(controller.game.y, 198)
            view.releaseInput(7)
            let stoppedX = controller.game.x
            let airborneY = controller.game.y
            controller.tick(date: Date(timeIntervalSince1970: 0.2))
            XCTAssertEqual(controller.game.x, stoppedX)
            XCTAssertLessThan(controller.game.y, airborneY)
        }
    }

    func testLatestDirectionWinsAndOlderDirectionReturnsDuringJump() async {
        let (controller, view) = makeGame()
        view.beginTouch(id: 7, at: center(.left, in: view))
        view.beginTouch(id: 12, at: center(.right, in: view))
        view.beginTouch(id: 20, at: center(.jump, in: view))
        XCTAssertEqual(controller.direction, 1)
        controller.tick(date: Date(timeIntervalSince1970: 0.1))
        let before = controller.game.x
        view.releaseInput(12)
        controller.tick(date: Date(timeIntervalSince1970: 0.2))
        XCTAssertLessThan(controller.game.x, before)
        XCTAssertEqual(controller.game.facing, -1)
        XCTAssertLessThan(controller.game.y, 198)
    }

    func testThumbDriftIsCapturedAndSlidingAcrossReverses() async {
        let (controller, view) = makeGame(width: 272)
        view.beginTouch(id: 7, at: center(.right, in: view))
        view.moveTouch(id: 7, to: CGPoint(x: -20, y: 80))
        XCTAssertEqual(controller.direction, 1)
        let left = view.controlFrames[.left]!
        view.moveTouch(id: 7, to: CGPoint(x: left.maxX - 1, y: left.midY))
        XCTAssertEqual(controller.direction, 1, "Do not flicker at the shared edge")
        view.moveTouch(id: 7, to: center(.left, in: view))
        XCTAssertEqual(controller.direction, -1)
        view.releaseInput(7)
        XCTAssertEqual(controller.direction, 0)
    }

    func testKeyRepeatsAndJumpDoNotOverrideNewerDirection() async {
        let (controller, view) = makeGame()
        view.press(.right, id: "D")
        view.beginTouch(id: 7, at: center(.left, in: view))
        view.press(.right, id: "D")
        view.press(.jump, id: "Space")
        XCTAssertEqual(controller.direction, -1)
        controller.tick(date: Date(timeIntervalSince1970: 0.1))
        let risingVelocity = controller.game.vy
        view.press(.jump, id: "Space")
        XCTAssertEqual(controller.game.vy, risingVelocity)
        view.releaseInput(7)
        XCTAssertEqual(controller.direction, 1)
        view.releaseInput("Space")
        XCTAssertEqual(controller.direction, 1)
        view.releaseInput("D")
        XCTAssertEqual(controller.direction, 0)
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

    func testSuspendingClearsInputsAndResumeRequiresFreshPress() async {
        let (controller, view) = makeGame()
        view.beginTouch(id: 7, at: center(.left, in: view))
        view.press(.right, id: "D")
        controller.tick(date: Date(timeIntervalSince1970: 0.1))
        let stoppedX = controller.game.x
        controller.suspend()
        view.resetVersion = controller.controlResetVersion
        view.controlsEnabled = controller.controlsEnabled
        XCTAssertTrue(view.input.held.isEmpty)
        view.beginTouch(id: 12, at: center(.jump, in: view))
        controller.tick(date: Date(timeIntervalSince1970: 10))
        XCTAssertEqual(controller.game.x, stoppedX)
        XCTAssertTrue(controller.game.grounded)

        controller.startOrResume()
        view.resetVersion = controller.controlResetVersion
        view.controlsEnabled = controller.controlsEnabled
        view.moveTouch(id: 7, to: center(.left, in: view))
        controller.tick(date: Date(timeIntervalSince1970: 20))
        controller.tick(date: Date(timeIntervalSince1970: 20.1))
        XCTAssertEqual(controller.direction, 0)
        XCTAssertEqual(controller.game.x, stoppedX, "No catch-up or stale movement on resume")
    }

    func testCancellationAndViewRemovalReleaseAllMovement() async {
        let (controller, view) = makeGame()
        view.beginTouch(id: 7, at: center(.left, in: view))
        view.press(.right, id: "D")
        view.cancelAllInputs()
        XCTAssertEqual(controller.direction, 0)
        view.beginTouch(id: 12, at: center(.right, in: view))
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 480, height: 360))
        window.addSubview(view)
        view.removeFromSuperview()
        XCTAssertEqual(controller.direction, 0)
        XCTAssertTrue(view.input.held.isEmpty)
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
}
