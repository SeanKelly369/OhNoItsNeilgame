import UIKit

enum GameControl: CaseIterable {
    case left, right, up, down, jump, use, map

    var direction: Int32 {
        switch self {
        case .left: return -1
        case .right: return 1
        default: return 0
        }
    }

    var climb: Int32 {
        switch self {
        case .up: return -1
        case .down: return 1
        default: return 0
        }
    }

    var label: String {
        switch self {
        case .left: return "<"
        case .right: return ">"
        case .up: return "UP"
        case .down: return "DOWN"
        case .jump: return "JUMP"
        case .use: return "USE"
        case .map: return "MAP"
        }
    }
}

/// A press belongs to its finger or key, so releasing Jump cannot release movement.
struct GameControlInput {
    private(set) var held: [AnyHashable: GameControl] = [:]
    private var pressOrder: [AnyHashable] = []

    var direction: Int32 {
        for id in pressOrder.reversed() {
            if let control = held[id], control.direction != 0 { return control.direction }
        }
        return 0
    }

    var climb: Int32 {
        for id in pressOrder.reversed() {
            if let control = held[id], control.climb != 0 { return control.climb }
        }
        return 0
    }

    @discardableResult
    mutating func press(_ control: GameControl, id: AnyHashable) -> Bool {
        guard held[id] != control else { return false }
        held[id] = control
        pressOrder.removeAll { $0 == id }
        pressOrder.append(id)
        return true
    }

    mutating func release(_ id: AnyHashable) {
        held.removeValue(forKey: id)
        pressOrder.removeAll { $0 == id }
    }

    mutating func clear() {
        held.removeAll()
        pressOrder.removeAll()
    }
}

private final class GameControlLabel: UILabel {
    var activate: (() -> Bool)?
    override func accessibilityActivate() -> Bool { activate?() ?? false }
}

/// One UIKit touch surface tracks every finger independently, including during drags.
final class GameControlsView: UIView {
    private enum InputID: Hashable {
        case touch(ObjectIdentifier)
        case key(Int)
        case accessibility(GameControl)
    }

    var onDirectionChange: ((Int32) -> Void)?
    var onClimbChange: ((Int32) -> Void)?
    var onAction: ((GameControl) -> Void)?
    var onPause: (() -> Void)?
    var nearbyDoorLabel: String? { didSet { labels[.use]?.text = nearbyDoorLabel == nil ? "USE" : "ENTER"; setNeedsDisplay() } }
    var controlsEnabled = false {
        didSet {
            guard oldValue != controlsEnabled else { return }
            if controlsEnabled {
                if window != nil { becomeFirstResponder() }
            } else {
                cancelAllInputs()
                // Keep keyboard pause/map shortcuts available while overlays are open.
            }
            updateAppearance()
        }
    }
    var resetVersion = 0 {
        didSet { if oldValue != resetVersion { cancelAllInputs() } }
    }

    private(set) var input = GameControlInput()
    private var labels: [GameControl: GameControlLabel] = [:]
    private(set) var controlFrames: [GameControl: CGRect] = [:]

    override init(frame: CGRect) {
        super.init(frame: frame)
        isMultipleTouchEnabled = true
        isExclusiveTouch = false
        backgroundColor = .clear
        for control in GameControl.allCases {
            let label = GameControlLabel()
            label.text = control.label
            label.textAlignment = .center
            label.font = .monospacedSystemFont(ofSize: control.direction == 0 ? 12 : 24, weight: .bold)
            label.textColor = control == .jump
                ? UIColor(red: 1, green: 199/255, blue: 91/255, alpha: 1)
                : UIColor(red: 249/255, green: 232/255, blue: 190/255, alpha: 1)
            label.layer.cornerRadius = 6
            label.clipsToBounds = true
            label.isUserInteractionEnabled = false
            label.isAccessibilityElement = true
            label.accessibilityTraits = .button
            label.accessibilityLabel = control == .left ? "Move left" : control == .right ? "Move right" : control.label
            if control.direction != 0 { label.accessibilityHint = "Double tap to start or stop moving." }
            label.activate = { [weak self] in
                guard let self, self.controlsEnabled else { return false }
                let id = InputID.accessibility(control)
                if control.direction != 0, self.input.held[id] != nil {
                    self.releaseInput(id)
                } else {
                    self.press(control, id: id)
                    if control.direction == 0 { self.releaseInput(id) }
                }
                return true
            }
            labels[control] = label
            addSubview(label)
        }
        updateAppearance()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override var canBecomeFirstResponder: Bool { true }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        guard controlsEnabled, hitControl(point) != nil else { return nil }
        return self
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil { cancelAllInputs() }
        else { becomeFirstResponder() }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let unit = min(96, max(44, min(bounds.width / 4.2, bounds.height * 0.42)))
        let radius = unit * 0.88
        let cx = 18 + radius, cy = bounds.height - 16 - radius
        let thick = radius * 0.62, length = radius * 0.94
        controlFrames[.left] = CGRect(x: cx - length, y: cy - thick / 2, width: length - thick * 0.25, height: thick)
        controlFrames[.right] = CGRect(x: cx + thick * 0.25, y: cy - thick / 2, width: length - thick * 0.25, height: thick)
        controlFrames[.up] = CGRect(x: cx - thick / 2, y: cy - length, width: thick, height: length - thick * 0.25)
        controlFrames[.down] = CGRect(x: cx - thick / 2, y: cy + thick * 0.25, width: thick, height: length - thick * 0.25)
        let jumpR = unit * 0.44, useR = unit * 0.40, mapR = unit * 0.28
        let jx = bounds.width - 20 - jumpR, jy = bounds.height - 18 - jumpR
        let ux = jx - jumpR - useR - 12, uy = bounds.height - 26 - useR
        let mx = jx - 4, my = jy - jumpR - mapR - 16
        controlFrames[.jump] = CGRect(x: jx - jumpR, y: jy - jumpR, width: jumpR * 2, height: jumpR * 2)
        controlFrames[.use] = CGRect(x: ux - useR, y: uy - useR, width: useR * 2, height: useR * 2)
        controlFrames[.map] = CGRect(x: mx - mapR, y: my - mapR, width: mapR * 2, height: mapR * 2)
        setNeedsDisplay()
        for (control, frame) in controlFrames {
            labels[control]?.frame = frame
            labels[control]?.layer.cornerRadius = control.direction == 0 && control.climb == 0 ? frame.height / 2 : 6
            labels[control]?.layer.borderWidth = 0
            labels[control]?.layer.borderColor = UIColor(red: 76/255, green: 108/255, blue: 121/255, alpha: 0.84).cgColor
        }
    }

    private var padRadius: CGFloat { min(96, max(44, min(bounds.width / 4.2, bounds.height * 0.42))) * 0.88 }
    private var padCenter: CGPoint { CGPoint(x: 18 + padRadius, y: bounds.height - 16 - padRadius) }

    private func hitDpad(_ point: CGPoint, factor: CGFloat, current: GameControl? = nil) -> GameControl? {
        let dx = point.x - padCenter.x, dy = point.y - padCenter.y
        let distance = hypot(dx, dy)
        guard distance <= padRadius * factor else { return nil }
        if distance < padRadius * 0.16 { return current }
        let bias = current == nil ? 0 : padRadius * 0.08
        if abs(dx) > abs(dy) + bias { return dx < 0 ? .left : .right }
        if abs(dy) > abs(dx) + bias { return dy < 0 ? .up : .down }
        if let current { return current }
        return abs(dx) >= abs(dy) ? (dx < 0 ? .left : .right) : (dy < 0 ? .up : .down)
    }
    private func hitControl(_ point: CGPoint) -> GameControl? {
        if let direction = hitDpad(point, factor: 1.22) { return direction }
        return [GameControl.jump, .use, .map].first { control in
            guard let r = controlFrames[control] else { return false }
            return hypot(point.x - r.midX, point.y - r.midY) <= r.width * 0.59
        }
    }

    override func draw(_ rect: CGRect) {
        guard controlsEnabled else { return }
        func color(_ r: CGFloat, _ g: CGFloat, _ b: CGFloat, _ a: CGFloat = 1) -> UIColor {
            UIColor(red: r/255, green: g/255, blue: b/255, alpha: a)
        }
        let ink = color(12,20,32), idle = color(34,54,74), pressed = color(74,107,130)
        let cream = color(244,231,198), gold = color(244,192,79), teal = color(103,221,177)
        let rim = color(76,108,121,215/255)
        func circle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ fill: UIColor) {
            fill.setFill(); UIBezierPath(ovalIn: CGRect(x: x-r, y: y-r, width: 2*r, height: 2*r)).fill()
        }
        func ring(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ stroke: UIColor, _ width: CGFloat) {
            stroke.setStroke()
            let path = UIBezierPath(ovalIn: CGRect(x: x-r, y: y-r, width: 2*r, height: 2*r))
            path.lineWidth = width; path.stroke()
        }
        func rounded(_ rect: CGRect, _ radius: CGFloat, _ fill: UIColor) {
            fill.setFill(); UIBezierPath(roundedRect: rect, cornerRadius: radius).fill()
        }
        let cx = padCenter.x, cy = padCenter.y, radius = padRadius
        circle(cx, cy, radius * 1.06, color(18,31,47,210/255))
        ring(cx, cy, radius * 1.06, rim, 1.5)
        let thick = radius * 0.62, length = radius * 0.94
        let horizontal = CGRect(x: cx-length, y: cy-thick/2, width: length*2, height: thick)
        let vertical = CGRect(x: cx-thick/2, y: cy-length, width: thick, height: length*2)
        for bar in [horizontal, vertical] { rounded(bar.offsetBy(dx: 0, dy: 2.5), 8, ink) }
        for bar in [horizontal, vertical] { rounded(bar, 8, idle) }
        for control in [GameControl.left, .right, .up, .down] {
            guard let r = controlFrames[control] else { continue }
            let held = input.held.values.contains(control)
            if held { rounded(r, 6, pressed) }
            let x = r.midX, y = r.midY, s = min(r.width, r.height) * 0.24
            let points: [CGPoint]
            switch control {
            case .left: points = [CGPoint(x:x-s,y:y),CGPoint(x:x+s*0.65,y:y-s),CGPoint(x:x+s*0.65,y:y+s)]
            case .right: points = [CGPoint(x:x+s,y:y),CGPoint(x:x-s*0.65,y:y-s),CGPoint(x:x-s*0.65,y:y+s)]
            case .up: points = [CGPoint(x:x,y:y-s),CGPoint(x:x-s,y:y+s*0.65),CGPoint(x:x+s,y:y+s*0.65)]
            default: points = [CGPoint(x:x,y:y+s),CGPoint(x:x-s,y:y-s*0.65),CGPoint(x:x+s,y:y-s*0.65)]
            }
            let path = UIBezierPath(); path.move(to: points[0])
            path.addLine(to: points[1]); path.addLine(to: points[2]); path.close()
            (held ? gold : cream).setFill(); path.fill()
        }
        circle(cx, cy, radius * 0.17, color(12,20,32,110/255))
        for control in [GameControl.jump, .use, .map] {
            guard let r = controlFrames[control] else { continue }
            let held = input.held.values.contains(control)
            let highlighted = control == .use && nearbyDoorLabel != nil
            let accent = control == .jump ? gold : highlighted ? teal : cream
            let offset: CGFloat = held ? 2 : 0
            let radius = r.width / 2
            circle(r.midX, r.midY + 3, radius, ink)
            circle(r.midX, r.midY + offset, radius, held ? pressed : highlighted ? color(26,72,74) : idle)
            ring(r.midX, r.midY + offset, radius - 1, held || highlighted || control == .jump ? accent : rim, highlighted ? 2.2 : 1.5)
            let label = control == .map ? "ROOMS" : highlighted ? "ENTER" : control.label
            let font = UIFont.monospacedSystemFont(ofSize: radius * (control == .map ? 0.44 : 0.40), weight: .bold)
            let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: held && control != .jump ? gold : accent]
            let size = (label as NSString).size(withAttributes: attributes)
            (label as NSString).draw(at: CGPoint(x: r.midX-size.width/2, y: r.midY+offset-size.height/2), withAttributes: attributes)
        }
    }

    func press(_ control: GameControl, id: AnyHashable) {
        guard controlsEnabled, input.press(control, id: id) else { return }
        publishInput()
        // Trigger on touch-down, while the movement/climb finger remains held.
        if control.direction == 0 && (control.climb == 0 || control == .up) { onAction?(control) }
    }

    func beginTouch(id: AnyHashable, at point: CGPoint) {
        guard let control = hitControl(point) else { return }
        press(control, id: id)
    }

    func moveTouch(id: AnyHashable, to point: CGPoint) {
        guard let control = input.held[id], control.direction != 0 || control.climb != 0 else { return }
        if let next = hitDpad(point, factor: 1.65, current: control), next != control {
            press(next, id: id)
        }
    }

    func releaseInput(_ id: AnyHashable) {
        input.release(id)
        publishInput()
    }

    func cancelAllInputs() {
        input.clear()
        publishInput()
    }

    private func publishInput() {
        onDirectionChange?(input.direction)
        onClimbChange?(input.climb)
        updateAppearance()
    }

    private func updateAppearance() {
        setNeedsDisplay()
        for (control, label) in labels {
            let held = input.held.values.contains(control)
            label.backgroundColor = .clear
            label.textColor = .clear
            label.alpha = controlsEnabled ? 1 : 0
            label.accessibilityValue = held ? "Held" : nil
            label.accessibilityTraits = controlsEnabled ? .button : [.button, .notEnabled]
        }
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches.sorted(by: { $0.timestamp < $1.timestamp }) {
            beginTouch(id: InputID.touch(ObjectIdentifier(touch)), at: touch.location(in: self))
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches.sorted(by: { $0.timestamp < $1.timestamp }) {
            moveTouch(id: InputID.touch(ObjectIdentifier(touch)), to: touch.location(in: self))
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches { releaseInput(InputID.touch(ObjectIdentifier(touch))) }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches { releaseInput(InputID.touch(ObjectIdentifier(touch))) }
    }

    private func control(for key: UIKey) -> GameControl? {
        switch key.keyCode {
        case .keyboardA, .keyboardLeftArrow: return .left
        case .keyboardD, .keyboardRightArrow: return .right
        case .keyboardW, .keyboardUpArrow: return .up
        case .keyboardS, .keyboardDownArrow: return .down
        case .keyboardSpacebar: return .jump
        case .keyboardE, .keyboardReturnOrEnter: return .use
        case .keyboardM: return .map
        default: return nil
        }
    }

    override func pressesBegan(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
        var unhandled = presses
        for keyPress in presses {
            guard let key = keyPress.key else { continue }
            if key.keyCode == .keyboardP || key.keyCode == .keyboardEscape {
                onPause?(); unhandled.remove(keyPress); continue
            }
            if key.keyCode == .keyboardM && !controlsEnabled {
                onAction?(.map); unhandled.remove(keyPress); continue
            }
            guard let control = control(for: key) else { continue }
            press(control, id: InputID.key(key.keyCode.rawValue))
            unhandled.remove(keyPress)
        }
        if !unhandled.isEmpty { super.pressesBegan(unhandled, with: event) }
    }

    override func pressesEnded(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
        releaseKeys(presses)
        super.pressesEnded(presses, with: event)
    }

    override func pressesCancelled(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
        releaseKeys(presses)
        super.pressesCancelled(presses, with: event)
    }

    private func releaseKeys(_ presses: Set<UIPress>) {
        for keyPress in presses {
            if let key = keyPress.key { releaseInput(InputID.key(key.keyCode.rawValue)) }
        }
    }
}
