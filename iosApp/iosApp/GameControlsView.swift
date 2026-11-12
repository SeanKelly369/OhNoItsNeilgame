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
    var controlsEnabled = false {
        didSet {
            guard oldValue != controlsEnabled else { return }
            if controlsEnabled {
                if window != nil { becomeFirstResponder() }
            } else {
                cancelAllInputs()
                resignFirstResponder()
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
    override var canBecomeFirstResponder: Bool { controlsEnabled }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil { cancelAllInputs() }
        else if controlsEnabled { becomeFirstResponder() }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let count = CGFloat(GameControl.allCases.count)
        let spacing: CGFloat = 6
        let totalSpacing = spacing * (count - 1)
        let buttonWidth = max(24, (bounds.width - totalSpacing) / count)
        var frames: [GameControl: CGRect] = [:]
        for (i, control) in GameControl.allCases.enumerated() {
            let x = CGFloat(i) * (buttonWidth + spacing)
            frames[control] = CGRect(x: x, y: 0, width: buttonWidth, height: bounds.height)
        }
        controlFrames = frames
        for (control, frame) in controlFrames { labels[control]?.frame = frame }
    }

    func press(_ control: GameControl, id: AnyHashable) {
        guard controlsEnabled, input.press(control, id: id) else { return }
        publishInput()
        // Trigger on touch-down, while the movement/climb finger remains held.
        if control.direction == 0 && control.climb == 0 { onAction?(control) }
    }

    func beginTouch(id: AnyHashable, at point: CGPoint) {
        guard let control = GameControl.allCases.first(where: { controlFrames[$0]?.contains(point) == true }) else { return }
        press(control, id: id)
    }

    func moveTouch(id: AnyHashable, to point: CGPoint) {
        guard let control = input.held[id], control.direction != 0 else { return }
        let opposite: GameControl = control == .left ? .right : .left
        // Capture thumb drift until release; reverse only deliberately inside the other arrow.
        if controlFrames[opposite]?.insetBy(dx: 6, dy: 0).contains(point) == true {
            press(opposite, id: id)
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
        for (control, label) in labels {
            let held = input.held.values.contains(control)
            label.backgroundColor = held
                ? UIColor(red: 66/255, green: 101/255, blue: 116/255, alpha: 1)
                : UIColor(red: 35/255, green: 56/255, blue: 76/255, alpha: 1)
            label.alpha = controlsEnabled ? 1 : 0.45
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
            guard let key = keyPress.key, let control = control(for: key) else { continue }
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
