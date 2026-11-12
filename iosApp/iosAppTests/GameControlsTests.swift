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
        controller.startAdventure()
        controller.tick(date: Date(timeIntervalSince1970: 0))
        let view = GameControlsView(frame: CGRect(x: 0, y: 0, width: width, height: 48))
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
}
