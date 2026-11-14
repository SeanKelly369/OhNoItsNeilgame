import SwiftUI
import Shared

struct ContentView: View {
    @StateObject private var controller = GameController()
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color(red: 9/255, green: 15/255, blue: 24/255)
                    .ignoresSafeArea()

                Canvas { ctx, size in
                    controller.draw(context: &ctx, size: size)
                }
                .allowsHitTesting(false)
                .onReceive(Timer.publish(every: 1.0 / 60.0, on: .main, in: .common).autoconnect()) { date in
                    controller.tick(date: date)
                }

                ControlOverlay(controller: controller)
            }
        }
        .statusBar(hidden: true)
        .onChange(of: scenePhase) { phase in
            if phase != .active { controller.pause() }
        }
        .onDisappear { controller.pause() }
    }
}

final class GameController: ObservableObject {
    let game: OriginalGame
    let usedRooms: Set<Int>
    private let sprites = NeilSprites()
    private var imageCache: [String: UIImage] = [:]
    private var imageScales: [String: CGFloat] = [:]
    private let lumberjackPosition = CGPoint(x: 240, y: 160)
    private(set) lazy var harry = HarryAnimator(position: lumberjackPosition)
    private var harryDialogueIndex = 0

    @Published private(set) var isTitle = true
    @Published private(set) var isPaused = false
    @Published private(set) var isMap = false
    @Published private(set) var mapPage = 0
    @Published private(set) var controlResetVersion = 0

    private(set) var direction: Int32 = 0
    private(set) var climb: Int32 = 0
    private var accumulator: Float = 0
    private var lastDate: Date?

    var controlsEnabled: Bool { !isTitle && !isPaused && !isMap }

    init() {
        var loadedRooms: [OriginalGame.Room] = []
        var loadedItems: [OriginalGame.Item] = []
        var used: Set<Int> = []

        if let worldURL = Bundle.main.url(forResource: "world", withExtension: "json", subdirectory: "sprites/amiga"),
           let jsonString = try? String(contentsOf: worldURL, encoding: .utf8) {
            let pair = OriginalWorldData.shared.parse(json: jsonString)
            loadedRooms = pair.first as? [OriginalGame.Room] ?? []
            loadedItems = pair.second as? [OriginalGame.Item] ?? []
            for r in loadedRooms where r.occupied {
                used.insert(Int(r.id))
            }
        }

        self.game = OriginalGame(
            rooms: loadedRooms,
            objects: loadedItems,
            doors: OriginalWorldData.shared.defaultDoors(rooms: loadedRooms)
        )
        self.usedRooms = used
        loadSave()
    }

    func setDirection(_ dir: Int32) { self.direction = dir }
    func setClimb(_ cl: Int32) { self.climb = cl }

    func activate(_ control: GameControl) {
        if isTitle && (control == .jump || control == .use) { startAdventure(); return }
        if control == .map && isMap {
            isMap = false
            resetControls()
            return
        }
        guard controlsEnabled else { return }
        switch control {
        case .up:
            if !game.onLadder(), game.enterNearbyDoor() { save() }
        case .jump:
            game.jump()
        case .use:
            use()
        case .map:
            isMap = !isMap
            direction = 0
            climb = 0
            save()
        default:
            break
        }
    }

    func togglePause() {
        guard !isTitle else { return }
        isPaused = !isPaused
        isMap = false
        resetControls()
        save()
    }

    func startAdventure() {
        isTitle = false
        isPaused = false
        isMap = false
        resetControls()
    }

    func pause() {
        if !isTitle { isPaused = true }
        resetControls()
        save()
    }

    private func resetControls() {
        direction = 0
        climb = 0
        lastDate = nil
        accumulator = 0
        controlResetVersion += 1
    }

    func use() {
        if game.room == 17 && abs(CGFloat(game.x + 7.5) - lumberjackPosition.x) < 40 &&
            abs(CGFloat(game.y + 32) - lumberjackPosition.y) < 38 {
            let lines = [
                "Hello! I'm Harry. I work as a lumberjack.",
                "Lovely day for a walk in the forest, isn't it?",
                "I know these woods like the back of my hand.",
                "Take care out there, Neil!"
            ]
            game.message = lines[harryDialogueIndex % lines.count]
            harryDialogueIndex += 1
            harry.speak(at: game.time, neilX: CGFloat(game.x + 7.5))
            objectWillChange.send()
            return
        }
        if game.interact() {
            save()
            return
        }
        game.message = "Walk to an item or doorway and press ENTER."
    }

    func dropItem(at slot: Int) {
        if slot >= 0 && slot < game.inventory.count {
            game.drop(index: Int32(slot))
            save()
        }
    }

    func warpToRoom(_ roomId: Int) {
        game.enter(id: Int32(roomId), px: 152, py: 64)
        isMap = false
        resetControls()
        save()
    }

    func setMapPage(_ page: Int) {
        let maxPage = max(0, (usedRooms.count - 1) / 12)
        mapPage = min(max(0, page), maxPage)
    }

    func tick(date: Date) {
        let now = date
        guard let last = lastDate else {
            lastDate = now
            return
        }
        let dt = Float(min(now.timeIntervalSince(last), 0.1))
        lastDate = now

        if controlsEnabled {
            // OriginalGame is shared Kotlin state, so notify SwiftUI when a frame advances.
            objectWillChange.send()
            accumulator += max(0, dt)
            let stepTime: Float = 1.0 / 120.0
            while accumulator >= stepTime {
                game.step(dt: stepTime, direction: direction, climb: climb)
                updateHarry()
                accumulator -= stepTime
            }
        } else {
            accumulator = 0
        }
    }

    private func updateHarry() {
        harry.update(
            time: game.time,
            neilX: CGFloat(game.x + 7.5),
            neilY: CGFloat(game.y + 32),
            inRoom: game.room == 17
        )
    }

    // MARK: - Asset Resolution with HD fallback
    func image(named path: String) -> UIImage? {
        if let cached = imageCache[path] { return cached }
        // Try 4x HD folder first
        let hdSub = "sprites/amiga/hd/" + (path as NSString).deletingLastPathComponent
        let hdName = (path as NSString).lastPathComponent
        let baseName = (hdName as NSString).deletingPathExtension
        let ext = (hdName as NSString).pathExtension.isEmpty ? "png" : (hdName as NSString).pathExtension

        if let url = Bundle.main.url(forResource: baseName, withExtension: ext, subdirectory: hdSub),
           let img = UIImage(contentsOfFile: url.path) {
            imageCache[path] = img
            return img
        }
        // Fallback to standard resolution
        let stdSub = "sprites/amiga/" + (path as NSString).deletingLastPathComponent
        if let url = Bundle.main.url(forResource: baseName, withExtension: ext, subdirectory: stdSub),
           let img = UIImage(contentsOfFile: url.path) {
            imageCache[path] = img
            return img
        }
        // Check asset catalog
        if let img = UIImage(named: baseName) {
            imageCache[path] = img
            return img
        }
        return nil
    }

    // MARK: - Persistence
    private func save() {
        UserDefaults.standard.set(game.saveState(), forKey: "original_game_snapshot")
        let state: [String: Any] = [
            "room": game.room,
            "x": game.x,
            "y": game.y
        ]
        UserDefaults.standard.set(state, forKey: "original_game_state_ios")
    }

    private func loadSave() {
        if let raw = UserDefaults.standard.string(forKey: "original_game_snapshot"), game.restoreState(raw: raw) { return }
        guard let state = UserDefaults.standard.dictionary(forKey: "original_game_state_ios"),
              let room = state["room"] as? Int32,
              let x = state["x"] as? Float,
              let y = state["y"] as? Float else { return }
        game.enter(id: room, px: x, py: y)
    }

    // MARK: - Rendering
    func draw(context ctx: inout GraphicsContext, size: CGSize) {
        let targetWidth: CGFloat = 360
        let targetHeight: CGFloat = 300
        let scale = min(size.width / targetWidth, size.height / targetHeight)
        let offsetX = (size.width - targetWidth * scale) / 2
        let offsetY = (size.height - targetHeight * scale) / 2

        ctx.translateBy(x: offsetX, y: offsetY)
        ctx.scaleBy(x: scale, y: scale)

        // Header Title
        drawText(&ctx, text: "OH NO! IT'S NEIL", x: 20, y: 17, size: 12, color: .gold)
        drawText(&ctx, text: "AMIGA REMASTERED", x: 20, y: 29, size: 6, color: .cream)
        drawText(&ctx, text: isPaused ? "RESUME" : "PAUSE", x: 304, y: 17, size: 7, color: .cream)

        // 320x192 Game Viewport at (20, 36)
        var gameCtx = ctx
        gameCtx.translateBy(x: 20, y: 36)
        gameCtx.clip(to: Path(CGRect(x: 0, y: 0, width: 320, height: 192)))

        // Room Background
        let roomPath = String(format: "rooms/room_%03d.png", game.room)
        if let roomImg = image(named: roomPath) {
            gameCtx.draw(Image(uiImage: roomImg), in: CGRect(x: 0, y: 0, width: 320, height: 192))
        }

        let activeDoor = controlsEnabled ? game.nearbyDoor() : nil
        let pulse = 0.55 + 0.45 * sin(Double(game.time) * 5)
        for door in game.doors where door.fromRoom == game.room {
            let active = door == activeDoor
            let cx = CGFloat(door.x1 + door.x2) / 2
            let markerY = max(8, CGFloat(door.y1) - 6)
            if active {
                gameCtx.stroke(Path(CGRect(x: CGFloat(door.x1), y: CGFloat(door.y1),
                    width: CGFloat(door.x2 - door.x1), height: CGFloat(door.y2 - door.y1))),
                    with: .color(.teal.opacity((180 + 75 * pulse) / 255)), lineWidth: 1.5)
            }
            var marker = Path()
            marker.move(to: CGPoint(x: cx, y: markerY - 4))
            marker.addLine(to: CGPoint(x: cx - 4, y: markerY + 2))
            marker.addLine(to: CGPoint(x: cx + 4, y: markerY + 2))
            marker.closeSubpath()
            gameCtx.fill(marker, with: .color(active ? .teal : .gold.opacity((120 + 80 * pulse) / 255)))
        }

        // Room Items
        for item in game.objects where item.room == game.room {
            let itemPath = String(format: "sprites/%03d.png", item.sprite)
            if let itemImg = image(named: itemPath) {
                let assetScale = imageScales[itemPath] ?? 1
                let w = itemImg.size.width / assetScale
                let h = itemImg.size.height / assetScale
                gameCtx.draw(Image(uiImage: itemImg), in: CGRect(x: CGFloat(item.x), y: CGFloat(item.y), width: w, height: h))
            }
        }

        // Harry stands on the forest floor in the original lumberjack room.
        if game.room == 17 {
            harry.draw(in: gameCtx, time: game.time)
        }

        // Neil Character (Feet on floor at y+32, center x+7.5)
        let invulnerableFlash = game.invulnerable > 0 && Int(game.time * 12) % 2 == 1
        if !invulnerableFlash {
            let facing = game.frameFacesCamera ? 1 : game.facing
            let drawn = sprites.draw(
                gameCtx,
                frame: Int(game.spriteFrame),
                facing: facing,
                x: CGFloat(game.x + 7.5),
                y: CGFloat(game.y + 32),
                height: 46,
                walkTime: game.walkTime
            )
            if !drawn {
                let neilPath = String(format: "sprites/%03d.png", game.sprite)
                if let fallback = image(named: neilPath) {
                    gameCtx.draw(Image(uiImage: fallback), in: CGRect(x: CGFloat(game.x - 8), y: CGFloat(game.y), width: 32, height: 32))
                }
            }
        }

        if let door = activeDoor {
            let banner = Path(CGRect(x: 52, y: 162, width: 216, height: 16))
            gameCtx.fill(banner, with: .color(Color(red: 14/255, green: 24/255, blue: 40/255).opacity(0.875)))
            gameCtx.stroke(banner, with: .color(.teal), lineWidth: 1)
            drawText(&gameCtx, text: String(("ENTER: " + door.label).prefix(29)), x: 60, y: 173, size: 6.5, color: .teal)
        }

        // Status & Inventory strip
        ctx.fill(Path(CGRect(x: 20, y: 216, width: 320, height: 12)), with: .color(Color(red: 15/255, green: 22/255, blue: 30/255)))
        drawText(&ctx, text: game.current.name, x: 24, y: 225, size: 7, color: .gold)
        let status = "STARS \(game.stars)   BAG \(game.inventory.count)/3   ROOMS \(game.visited.count)/71"
        drawText(&ctx, text: status, x: 20, y: 239, size: 6, color: .cream)
        drawText(&ctx, text: String(game.message.prefix(53)), x: 20, y: 250, size: 6, color: .cream)

        // Overlay screens
        if isTitle {
            drawTitleOverlay(&ctx)
        } else if isPaused {
            drawPauseOverlay(&ctx)
        } else if isMap {
            drawMapOverlay(&ctx)
        }
    }

    private func drawTitleOverlay(_ ctx: inout GraphicsContext) {
        ctx.fill(Path(CGRect(x: 20, y: 36, width: 320, height: 192)), with: .color(Color(red: 16/255, green: 28/255, blue: 42/255).opacity(0.93)))
        drawText(&ctx, text: "OH NO! IT'S NEIL", x: 43, y: 76, size: 23, color: .gold)
        drawText(&ctx, text: "THE ORIGINAL AMIGA ADVENTURE", x: 64, y: 94, size: 9, color: .cream)
        drawText(&ctx, text: "Gralda has captured Neil's family.", x: 44, y: 116, size: 8, color: .cream)
        drawText(&ctx, text: "Escape the tower and explore the kingdom.", x: 30, y: 130, size: 8, color: .cream)
        drawText(&ctx, text: "Original rooms and items from Sean Kelly's", x: 36, y: 151, size: 7, color: .cream)
        drawText(&ctx, text: "Reality game files, remastered in HD.", x: 46, y: 162, size: 7, color: .cream)
        ctx.fill(Path(CGRect(x: 72, y: 179, width: 216, height: 27)), with: .color(.gold))
        drawText(&ctx, text: "ENTER THE WORLD", x: 113, y: 197, size: 11, color: .black)
    }

    private func drawPauseOverlay(_ ctx: inout GraphicsContext) {
        ctx.fill(Path(CGRect(x: 42, y: 64, width: 276, height: 139)), with: .color(Color(red: 16/255, green: 28/255, blue: 42/255).opacity(0.96)))
        drawText(&ctx, text: "PAUSED", x: 140, y: 91, size: 18, color: .gold)
        drawText(&ctx, text: "Tap to resume. Progress saves automatically.", x: 52, y: 115, size: 6, color: .cream)
        drawText(&ctx, text: "Inventory: tap an item below to drop it.", x: 52, y: 132, size: 6, color: .cream)
        for (i, id) in game.inventory.enumerated() {
            let intId = (id as? NSNumber)?.int32Value ?? (id as? Int32) ?? 0
            if let it = game.objects.first(where: { $0.id == intId }) {
                drawText(&ctx, text: it.name, x: 60, y: CGFloat(151 + i * 14), size: 8, color: .cream)
            }
        }
    }

    private func drawMapOverlay(_ ctx: inout GraphicsContext) {
        ctx.fill(Path(CGRect(x: 12, y: 32, width: 336, height: 220)), with: .color(Color(red: 16/255, green: 28/255, blue: 42/255)))
        drawText(&ctx, text: "ROOM ATLAS", x: 26, y: 49, size: 12, color: .gold)
        drawText(&ctx, text: "Tap a room to enter. Arrows change page.", x: 26, y: 62, size: 6, color: .cream)

        let sorted = usedRooms.sorted()
        let pageRooms = Array(sorted.dropFirst(mapPage * 12).prefix(12))

        for (i, id) in pageRooms.enumerated() {
            let left = CGFloat(24 + (i % 4) * 79)
            let top = CGFloat(70 + (i / 4) * 53)
            let path = String(format: "rooms/room_%03d.png", id)
            if let img = image(named: path) {
                ctx.draw(Image(uiImage: img), in: CGRect(x: left, y: top, width: 73, height: 40))
            }
            let isVisited = game.visited.contains(KotlinInt(integerLiteral: id))
            let name = game.rooms.first(where: { $0.id == Int32(id) })?.name ?? "Room \(id)"
            drawText(&ctx, text: "\(id + 1) " + String(name.prefix(13)), x: left, y: top + 48, size: 5, color: isVisited ? .gold : .cream)
        }
        drawText(&ctx, text: "< PREVIOUS", x: 24, y: 243, size: 8, color: .cream)
        drawText(&ctx, text: "CLOSE", x: 159, y: 243, size: 8, color: .cream)
        drawText(&ctx, text: "NEXT >", x: 280, y: 243, size: 8, color: .cream)
    }

    private func drawText(_ ctx: inout GraphicsContext, text: String, x: CGFloat, y: CGFloat, size: CGFloat, color: Color) {
        ctx.draw(
            Text(text).font(.system(size: size, weight: .bold, design: .monospaced)).foregroundColor(color),
            at: CGPoint(x: x, y: y - UIFont.monospacedSystemFont(ofSize: size, weight: .bold).ascender),
            anchor: .topLeading
        )
    }
}

struct ControlOverlay: View {
    @ObservedObject var controller: GameController

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                Color.clear
                    .contentShape(Rectangle())
                    .onTapGesture { location in
                        handleViewportTap(location: location, size: proxy.size)
                    }
                GameControls(controller: controller)
            }
        }
    }

    private func handleViewportTap(location: CGPoint, size: CGSize) {
        let targetWidth: CGFloat = 360
        let targetHeight: CGFloat = 300
        let scale = min(size.width / targetWidth, size.height / targetHeight)
        let offsetX = (size.width - targetWidth * scale) / 2
        let offsetY = (size.height - targetHeight * scale) / 2

        let x = (location.x - offsetX) / scale
        let y = (location.y - offsetY) / scale

        if controller.isTitle {
            controller.startAdventure()
            return
        }
        if controller.isPaused {
            let slot = Int((y - 140) / 14)
            if y >= 140 && y <= 182 && slot >= 0 && slot < controller.game.inventory.count {
                controller.dropItem(at: slot)
            } else {
                controller.togglePause()
            }
            return
        }
        if controller.isMap {
            if y >= 230 {
                if x < 120 { controller.setMapPage(controller.mapPage - 1) }
                else if x > 240 { controller.setMapPage(controller.mapPage + 1) }
                else { controller.activate(.map) }
            } else if y >= 70 && y <= 229 && x >= 24 && x <= 340 {
                let col = Int((x - 24) / 79)
                let row = Int((y - 70) / 53)
                let index = controller.mapPage * 12 + row * 4 + col
                let sorted = controller.usedRooms.sorted()
                if index < sorted.count {
                    controller.warpToRoom(sorted[index])
                }
            }
            return
        }
        if y < 32 && x > 280 { controller.togglePause() }
        else if x >= 72 && x <= 288 && y >= 196 && y <= 216 {
            controller.activate(.up)
        }
    }
}

struct GameControls: UIViewRepresentable {
    @ObservedObject var controller: GameController

    func makeUIView(context: Context) -> GameControlsView {
        let view = GameControlsView()
        view.onDirectionChange = { [weak controller] in controller?.setDirection($0) }
        view.onClimbChange = { [weak controller] in controller?.setClimb($0) }
        view.onAction = { [weak controller] in controller?.activate($0) }
        view.onPause = { [weak controller] in controller?.togglePause() }
        return view
    }

    func updateUIView(_ view: GameControlsView, context: Context) {
        view.resetVersion = controller.controlResetVersion
        view.controlsEnabled = controller.controlsEnabled
        view.nearbyDoorLabel = controller.game.nearbyDoor()?.label
    }

    static func dismantleUIView(_ view: GameControlsView, coordinator: ()) {
        view.cancelAllInputs()
        view.resignFirstResponder()
    }
}

extension Color {
    static let gold = Color(red: 255/255, green: 199/255, blue: 91/255)
    static let teal = Color(red: 103/255, green: 221/255, blue: 177/255)
    static let cream = Color(red: 249/255, green: 232/255, blue: 190/255)
}

struct LumberjackFrame {
    let image: Image
    let cgImage: CGImage
    let width: CGFloat
    let height: CGFloat
    let anchorX: CGFloat
    let baselineY: CGFloat
}

/// Animates Harry in the lumberjack room using foot-anchored frames, multi-step gestures,
/// reactive greetings, blinking, and dedicated turnaround frames at a fixed size.
final class HarryAnimator {
    enum Pose {
        static let idle = 0
        static let blink = 1
        static let waveHigh = 2
        static let gestureForward = 3
        static let happyTalk = 4
        static let beardScratch = 5
        static let waveMid = 6
        static let handsClasped = 7
        static let turnQuarter = 8
        static let turnFront = 9
    }

    struct Step {
        let frame: Int
        let duration: Float
    }

    struct Sequence {
        let kind: Kind
        let steps: [Step]
        var elapsed: Float = 0

        enum Kind { case greeting, talking, idleAction }

        var totalDuration: Float { steps.reduce(0) { $0 + $1.duration } }

        func frame(at t: Float) -> Int {
            var acc: Float = 0
            for step in steps {
                acc += step.duration
                if t < acc { return step.frame }
            }
            return steps.last?.frame ?? Pose.idle
        }
    }

    static let defaultWidth: CGFloat = 69.4
    static let defaultHeight: CGFloat = 78.2

    let position: CGPoint
    let frames: [LumberjackFrame]
    private(set) var facesLeft = true
    private(set) var activeSequence: Sequence?
    private(set) var turnTimer: Float = 0

    private let turnDuration: Float = 0.195
    private var turnFromLeft = true
    private var lastTime: Float?
    private var wasNeilNearby = false
    private var nextMoveAt: Float = 2.0
    private var nextBlinkAt: Float = 2.4
    private var blinkUntil: Float = -1
    private var lastGreetAt: Float = -100

    init(position: CGPoint, bundle: Bundle = .main) {
        self.position = position
        self.frames = Self.loadFrames(bundle: bundle)
    }

    private static func loadFrames(bundle: Bundle) -> [LumberjackFrame] {
        guard let url = bundle.url(forResource: "lumberjack-atlas", withExtension: "txt"),
              let encoded = try? String(contentsOf: url, encoding: .utf8),
              let data = Data(base64Encoded: encoded, options: .ignoreUnknownCharacters),
              let atlas = UIImage(data: data)?.cgImage else { return [] }

        var crops: [CGImage] = []
        var loaded: [LumberjackFrame] = []
        for index in 0..<8 {
            let column = index % 4
            let row = index / 4
            let left = column * atlas.width / 4
            let right = (column + 1) * atlas.width / 4
            let top = row * atlas.height / 2
            let bottom = (row + 1) * atlas.height / 2
            guard let crop = atlas.cropping(to: CGRect(x: left, y: top, width: right - left, height: bottom - top)) else {
                continue
            }
            crops.append(crop)
            loaded.append(makeFrame(from: crop))
        }

        if let neutralCrop = crops.first {
            if let quarterCrop = synthesizeTurnCrop(from: neutralCrop, turnAmount: 0.5) {
                loaded.append(makeFrame(from: quarterCrop))
            }
            if let frontCrop = synthesizeTurnCrop(from: neutralCrop, turnAmount: 0.0) {
                loaded.append(makeFrame(from: frontCrop))
            }
        }
        return loaded
    }

    private static func makeFrame(from cgImage: CGImage) -> LumberjackFrame {
        let anchor = measureBootAnchor(in: cgImage)
        return LumberjackFrame(
            image: Image(decorative: cgImage, scale: 1).interpolation(.high),
            cgImage: cgImage,
            width: CGFloat(cgImage.width),
            height: CGFloat(cgImage.height),
            anchorX: anchor.x,
            baselineY: anchor.y
        )
    }

    /// Synthesizes front (turnAmount = 0) and quarter-turn (turnAmount = 0.5) frames from Frame 0
    /// while keeping outer silhouette height, width, and boot ground alignment identical.
    private static func synthesizeTurnCrop(from baseImage: CGImage, turnAmount: Double) -> CGImage? {
        let w = baseImage.width
        let h = baseImage.height
        var srcPx = [UInt8](repeating: 0, count: w * h * 4)
        guard let srcCtx = CGContext(
            data: &srcPx,
            width: w,
            height: h,
            bitsPerComponent: 8,
            bytesPerRow: w * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return nil }
        srcCtx.draw(baseImage, in: CGRect(x: 0, y: 0, width: w, height: h))

        var out = srcPx

        @inline(__always)
        func clamp01(_ v: Double) -> Double { max(0, min(1, v)) }

        @inline(__always)
        func smoothstep(_ e0: Double, _ e1: Double, _ x: Double) -> Double {
            let t = clamp01((x - e0) / (e1 - e0))
            return t * t * (3 - 2 * t)
        }

        @inline(__always)
        func sample(_ x: Double, _ y: Double) -> (r: Double, g: Double, b: Double, a: Double) {
            if x < 0 || x > Double(w - 1) || y < 0 || y > Double(h - 1) { return (0, 0, 0, 0) }
            let x0 = Int(floor(x)), y0 = Int(floor(y))
            let x1 = min(w - 1, x0 + 1), y1 = min(h - 1, y0 + 1)
            let fx = x - Double(x0), fy = y - Double(y0)
            let i00 = (y0 * w + x0) * 4, i10 = (y0 * w + x1) * 4
            let i01 = (y1 * w + x0) * 4, i11 = (y1 * w + x1) * 4
            let w00 = (1 - fx) * (1 - fy), w10 = fx * (1 - fy), w01 = (1 - fx) * fy, w11 = fx * fy
            return (
                Double(srcPx[i00]) * w00 + Double(srcPx[i10]) * w10 + Double(srcPx[i01]) * w01 + Double(srcPx[i11]) * w11,
                Double(srcPx[i00 + 1]) * w00 + Double(srcPx[i10 + 1]) * w10 + Double(srcPx[i01 + 1]) * w01 + Double(srcPx[i11 + 1]) * w11,
                Double(srcPx[i00 + 2]) * w00 + Double(srcPx[i10 + 2]) * w10 + Double(srcPx[i01 + 2]) * w01 + Double(srcPx[i11 + 2]) * w11,
                Double(srcPx[i00 + 3]) * w00 + Double(srcPx[i10 + 3]) * w10 + Double(srcPx[i01 + 3]) * w01 + Double(srcPx[i11 + 3]) * w11
            )
        }

        @inline(__always)
        func setPixel(_ x: Int, _ y: Int, _ r: Double, _ g: Double, _ b: Double, _ a: Double) {
            let i = (y * w + x) * 4
            out[i] = UInt8(max(0, min(255, Int(round(r)))))
            out[i + 1] = UInt8(max(0, min(255, Int(round(g)))))
            out[i + 2] = UInt8(max(0, min(255, Int(round(b)))))
            out[i + 3] = UInt8(max(0, min(255, Int(round(a)))))
        }

        @inline(__always)
        func blendOver(_ x: Int, _ y: Int, _ sr: Double, _ sg: Double, _ sb: Double, _ sa: Double) {
            let i = (y * w + x) * 4
            let inv = 1.0 - sa / 255.0
            setPixel(
                x, y,
                sr + Double(out[i]) * inv,
                sg + Double(out[i + 1]) * inv,
                sb + Double(out[i + 2]) * inv,
                sa + Double(out[i + 3]) * inv
            )
        }

        // 1. Re-center chin, collar, suspenders, and belt pouch horizontally without moving outer arms.
        let bodyShift = (1.0 - turnAmount) * 11.0
        for y in 104...min(312, h - 1) {
            for x in 140...min(340, w - 1) {
                let armFade = smoothstep(145, 178, Double(x)) * (1.0 - smoothstep(302, 335, Double(x)))
                let yFade = smoothstep(104, 120, Double(y)) * (1.0 - smoothstep(302, 312, Double(y)))
                let c = sample(Double(x) + bodyShift * armFade * yFade, Double(y))
                setPixel(x, y, c.r, c.g, c.b, c.a)
            }
        }

        // 2. Rebuild head skin canvas & silhouette symmetrically for front, or quarter-turned.
        var minX = [Int](repeating: w, count: h)
        var maxX = [Int](repeating: -1, count: h)
        for y in 14...min(115, h - 1) {
            for x in 150...min(320, w - 1) where srcPx[(y * w + x) * 4 + 3] > 30 {
                minX[y] = min(minX[y], x)
                maxX[y] = max(maxX[y], x)
            }
        }

        let cx = 243.0
        for y in 14...min(112, h - 1) {
            let fMin = Double(minX[y]), fMax = Double(maxX[y])
            guard fMin < fMax else { continue }
            let rDist = max(1.0, fMax - cx)
            let showRightEar = turnAmount < 0.25
            let fLeftDist = cx - fMin
            let targetRightDist = showRightEar ? fLeftDist : rDist
            let headFade = 1.0 - smoothstep(103.0, 112.0, Double(y))

            for x in 155...min(325, w - 1) {
                let dx = Double(x) - cx
                var c = (r: 0.0, g: 0.0, b: 0.0, a: 0.0)
                var uNorm = 1.0

                if dx < 0 {
                    let u = (-dx) / max(1.0, fLeftDist)
                    uNorm = u
                    if u <= 1.04 {
                        c = sample(cx + dx, Double(y))
                    }
                } else {
                    let u = dx / max(1.0, targetRightDist)
                    uNorm = u
                    if u <= 1.04 {
                        if showRightEar && y >= 56 && y <= 94 && dx > rDist {
                            c = sample(cx - dx, Double(y))
                        } else {
                            let mirrorX = cx - u * min(fLeftDist, rDist)
                            let cleanSkin = sample(mirrorX, Double(y))
                            let outlineZone = smoothstep(0.76, 0.90, u)
                            let rightEdge = sample(fMax - (1.0 - u) * rDist, Double(y))
                            c = (
                                cleanSkin.r * (1 - outlineZone) + rightEdge.r * outlineZone,
                                cleanSkin.g * (1 - outlineZone) + rightEdge.g * outlineZone,
                                cleanSkin.b * (1 - outlineZone) + rightEdge.b * outlineZone,
                                cleanSkin.a * (1 - outlineZone) + rightEdge.a * outlineZone
                            )
                        }
                    }
                }

                let interiorMask = 1.0 - smoothstep(0.68, 0.80, uNorm)
                let faceMaskY = smoothstep(23.0, 29.0, Double(y)) * (1.0 - smoothstep(99.0, 106.0, Double(y)))
                let clearMask = interiorMask * faceMaskY
                if clearMask > 0.001 && c.a > 20 {
                    let cleanY = (y >= 78 && y <= 92) ? 75.0 : Double(y)
                    let skin = sample(216.0 + (dx / 45.0) * 6.0, cleanY)
                    let alphaRatio = c.a / max(1.0, skin.a)
                    c = (
                        c.r * (1 - clearMask) + skin.r * alphaRatio * clearMask,
                        c.g * (1 - clearMask) + skin.g * alphaRatio * clearMask,
                        c.b * (1 - clearMask) + skin.b * alphaRatio * clearMask,
                        c.a
                    )
                }

                if headFade < 0.999 {
                    let i = (y * w + x) * 4
                    c = (
                        Double(out[i]) * (1 - headFade) + c.r * headFade,
                        Double(out[i + 1]) * (1 - headFade) + c.g * headFade,
                        Double(out[i + 2]) * (1 - headFade) + c.b * headFade,
                        Double(out[i + 3]) * (1 - headFade) + c.a * headFade
                    )
                }
                setPixel(x, y, c.r, c.g, c.b, c.a)
            }
        }

        // 3. Keep a single eye and eyebrow as the face turns from front to 3/4 right.
        func drawEye(centerX: Double, centerY: Double, halfW: Double, halfH: Double, gazeOffset: Double) {
            for y in max(0, Int(floor(centerY - halfH - 2)))...min(h - 1, Int(ceil(centerY + halfH + 2))) {
                for x in max(0, Int(floor(centerX - halfW - 2)))...min(w - 1, Int(ceil(centerX + halfW + 2))) {
                    guard out[(y * w + x) * 4 + 3] > 20 else { continue }
                    let nx = (Double(x) - centerX) / halfW
                    let ny = (Double(y) - centerY) / halfH
                    let dist = sqrt(nx * nx + ny * ny)
                    let edgeAlpha = 1.0 - smoothstep(0.95, 1.15, dist)
                    if edgeAlpha <= 0.01 { continue }

                    let borderMask = smoothstep(0.83, 0.91, dist)
                    let pDx = (nx - gazeOffset) / 0.31
                    let pDy = (ny - 0.06) / 0.38
                    let pupilMask = 1.0 - smoothstep(0.85, 1.05, sqrt(pDx * pDx + pDy * pDy))
                    let clDx = (nx - (gazeOffset - 0.11)) / 0.10
                    let clDy = (ny - (-0.08)) / 0.11
                    let catchMask = 1.0 - smoothstep(0.7, 1.0, sqrt(clDx * clDx + clDy * clDy))

                    var r = 238.0, g = 239.0, b = 240.0
                    r = r * (1 - pupilMask) + 20 * pupilMask
                    g = g * (1 - pupilMask) + 11 * pupilMask
                    b = b * (1 - pupilMask) + 7 * pupilMask
                    r = r * (1 - catchMask * 0.85) + 255 * catchMask * 0.85
                    g = g * (1 - catchMask * 0.85) + 255 * catchMask * 0.85
                    b = b * (1 - catchMask * 0.85) + 255 * catchMask * 0.85
                    r = r * (1 - borderMask) + 18 * borderMask
                    g = g * (1 - borderMask) + 10 * borderMask
                    b = b * (1 - borderMask) + 6 * borderMask
                    blendOver(x, y, r * edgeAlpha, g * edgeAlpha, b * edgeAlpha, 255 * edgeAlpha)
                }
            }
        }

        func drawEyebrow(centerX: Double, centerY: Double, halfW: Double, halfH: Double, tilt: Double) {
            for y in max(0, Int(floor(centerY - halfH - 4)))...min(h - 1, Int(ceil(centerY + halfH + 4))) {
                for x in max(0, Int(floor(centerX - halfW - 2)))...min(w - 1, Int(ceil(centerX + halfW + 2))) {
                    guard out[(y * w + x) * 4 + 3] > 20 else { continue }
                    let nx = (Double(x) - centerX) / halfW
                    let archY = centerY - 2.0 * (1.0 - nx * nx) + tilt * nx
                    let ny = (Double(y) - archY) / halfH
                    let d = sqrt(nx * nx * 0.85 + ny * ny)
                    let a = 1.0 - smoothstep(0.85, 1.15, d)
                    if a <= 0.01 { continue }
                    let outline = smoothstep(0.55, 0.85, d)
                    let r = 82 * (1 - outline) + 22 * outline
                    let g = 42 * (1 - outline) + 11 * outline
                    let b = 22 * (1 - outline) + 6 * outline
                    blendOver(x, y, r * a, g * a, b * a, 255 * a)
                }
            }
        }

        func drawNose(centerX: Double, centerY: Double, halfW: Double, halfH: Double, biasX: Double) {
            for y in max(0, Int(floor(centerY - halfH - 3)))...min(h - 1, Int(ceil(centerY + halfH + 3))) {
                for x in max(0, Int(floor(centerX - halfW - 3)))...min(w - 1, Int(ceil(centerX + halfW + 3))) {
                    let nx = (Double(x) - centerX) / halfW
                    let ny = (Double(y) - centerY) / halfH
                    let d = sqrt(nx * nx + ny * ny)
                    let shadeA = (1.0 - smoothstep(0.68, 0.98, d)) * smoothstep(-0.35, 0.35, ny) * 0.55
                    let outlineBand = 1.0 - smoothstep(0.75, 1.0, abs(d - 0.90) * 3.8)
                    let lowerCup = smoothstep(0.05, 0.45, ny + 0.15 * biasX * nx)
                    let lineA = outlineBand * lowerCup
                    if shadeA > 0.01 {
                        blendOver(x, y, 205 * shadeA, 112 * shadeA, 72 * shadeA, 255 * shadeA)
                    }
                    if lineA > 0.01 {
                        blendOver(x, y, 42 * lineA, 20 * lineA, 12 * lineA, 255 * lineA)
                    }
                }
            }
        }

        func drawSmile(centerX: Double, baseY: Double, halfW: Double, curveDepth: Double, biasX: Double) {
            for y in max(0, Int(floor(baseY - 4)))...min(h - 1, Int(ceil(baseY + curveDepth + 4))) {
                for x in max(0, Int(floor(centerX - halfW - 3)))...min(w - 1, Int(ceil(centerX + halfW + 3))) {
                    let nx = (Double(x) - centerX) / halfW
                    if abs(nx) > 1.05 { continue }
                    let curveY = baseY + curveDepth * (1.0 - nx * nx) + biasX * 1.2 * nx
                    let dy = abs(Double(y) - curveY)
                    let taper = 1.0 - smoothstep(0.85, 1.05, abs(nx))
                    let a = (1.0 - smoothstep(1.1, 2.2, dy)) * taper
                    if a > 0.01 {
                        blendOver(x, y, 28 * a, 14 * a, 8 * a, 255 * a)
                    }
                }
            }
        }

        // Keep Harry's eye size constant and matched to the base sprite across all turn frames.
        let eyeCenterX = cx + turnAmount * (256.0 - cx)
        let eyeHalfW = 21.1
        let eyeHalfH = 15.8
        let gaze = 0.25 * turnAmount

        drawEye(centerX: eyeCenterX, centerY: 56.0, halfW: eyeHalfW, halfH: eyeHalfH, gazeOffset: gaze)
        drawEyebrow(centerX: eyeCenterX, centerY: 33.0, halfW: eyeHalfW + 2.0, halfH: 4.0, tilt: -0.8 * turnAmount)
        drawNose(centerX: cx + turnAmount * 22.0, centerY: 79.0, halfW: 13.0 + turnAmount * 3.0, halfH: 10.5, biasX: turnAmount)
        drawSmile(centerX: cx + turnAmount * 17.0, baseY: 96.0, halfW: 17.0 - turnAmount * 1.5, curveDepth: 4.5, biasX: turnAmount)

        guard let outCtx = CGContext(
            data: &out,
            width: w,
            height: h,
            bitsPerComponent: 8,
            bytesPerRow: w * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return nil }
        return outCtx.makeImage()
    }

    /// Finds the boot center X and bottom baseline Y so all atlas and turn frames share one ground pivot.
    private static func measureBootAnchor(in image: CGImage) -> CGPoint {
        let w = image.width
        let h = image.height
        var bytes = [UInt8](repeating: 0, count: w * h * 4)
        guard let ctx = CGContext(
            data: &bytes,
            width: w,
            height: h,
            bitsPerComponent: 8,
            bytesPerRow: w * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else {
            return CGPoint(x: CGFloat(w) / 2, y: CGFloat(h))
        }
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))

        var maxY = h - 1
        var foundBottom = false
        for y in stride(from: h - 1, through: 0, by: -1) {
            let rowOffset = y * w * 4
            for x in 0..<w where bytes[rowOffset + x * 4 + 3] > 40 {
                maxY = y
                foundBottom = true
                break
            }
            if foundBottom { break }
        }
        guard foundBottom else {
            return CGPoint(x: CGFloat(w) / 2, y: CGFloat(h))
        }

        var minFootX = w
        var maxFootX = 0
        for y in max(0, maxY - 14)...maxY {
            let rowOffset = y * w * 4
            for x in 0..<w where bytes[rowOffset + x * 4 + 3] > 50 {
                minFootX = min(minFootX, x)
                maxFootX = max(maxFootX, x)
            }
        }
        let anchorX = minFootX <= maxFootX ? CGFloat(minFootX + maxFootX + 1) / 2.0 : CGFloat(w) / 2.0
        return CGPoint(x: anchorX, y: CGFloat(maxY + 1))
    }

    private func beginTurn(to targetLeft: Bool) {
        guard targetLeft != facesLeft else { return }
        if turnTimer > 0 {
            turnFromLeft = facesLeft
            facesLeft = targetLeft
            turnTimer = max(0.01, turnDuration - turnTimer)
        } else {
            turnFromLeft = facesLeft
            facesLeft = targetLeft
            turnTimer = turnDuration
        }
    }

    func update(time: Float, neilX: CGFloat, neilY: CGFloat, inRoom: Bool) {
        guard inRoom else {
            wasNeilNearby = false
            lastTime = nil
            return
        }

        if lastTime == nil || time < (lastTime ?? 0) {
            lastTime = time
            activeSequence = nil
            blinkUntil = -1
            turnTimer = 0
            nextMoveAt = time + 1.8
            nextBlinkAt = time + 2.2
            lastGreetAt = -100
        }

        let dt = min(max(0, time - (lastTime ?? time)), 0.1)
        lastTime = time
        turnTimer = max(0, turnTimer - dt)

        let offset = neilX - position.x
        let neilIsNearby = abs(offset) < 88 && abs(offset) > 6 && abs(neilY - position.y) < 38
        let nearEnoughToGreet = abs(offset) < 88 && abs(neilY - position.y) < 38

        if neilIsNearby && turnTimer == 0 {
            let targetLeft = offset < 0
            if targetLeft != facesLeft {
                beginTurn(to: targetLeft)
            }
        }

        // Wait for the turnaround frames to finish before advancing gesture sequences.
        if turnTimer == 0, var sequence = activeSequence {
            sequence.elapsed += dt
            if sequence.elapsed >= sequence.totalDuration {
                activeSequence = nil
                nextMoveAt = time + Float.random(in: 3.0...5.8)
            } else {
                activeSequence = sequence
            }
        }

        // Greet Neil with a smooth wave when he first walks up.
        if nearEnoughToGreet && !wasNeilNearby && activeSequence == nil && time - lastGreetAt > 6.0 {
            activeSequence = Sequence(kind: .greeting, steps: [
                Step(frame: Pose.gestureForward, duration: 0.09),
                Step(frame: Pose.waveMid, duration: 0.10),
                Step(frame: Pose.waveHigh, duration: 0.16),
                Step(frame: Pose.waveMid, duration: 0.12),
                Step(frame: Pose.waveHigh, duration: 0.16),
                Step(frame: Pose.waveMid, duration: 0.11),
                Step(frame: Pose.gestureForward, duration: 0.10)
            ])
            lastGreetAt = time
            nextMoveAt = time + Float.random(in: 3.5...6.0)
        }
        wasNeilNearby = nearEnoughToGreet

        if activeSequence == nil && turnTimer == 0 {
            if time >= nextBlinkAt {
                blinkUntil = time + 0.13
                let doubleBlink = Float.random(in: 0...1) < 0.25
                nextBlinkAt = time + (doubleBlink ? 0.27 : Float.random(in: 3.0...5.8))
            }

            if time >= nextMoveAt {
                if !nearEnoughToGreet && Bool.random() {
                    beginTurn(to: !facesLeft)
                }
                activeSequence = randomIdleSequence(neilIsNearby: nearEnoughToGreet)
                nextMoveAt = time + Float.random(in: 3.8...6.8)
            }
        }
    }

    func speak(at time: Float, neilX: CGFloat) {
        let targetLeft = neilX < position.x
        if targetLeft != facesLeft {
            beginTurn(to: targetLeft)
        }
        activeSequence = Sequence(kind: .talking, steps: [
            Step(frame: Pose.waveMid, duration: 0.09),
            Step(frame: Pose.waveHigh, duration: 0.22),
            Step(frame: Pose.waveMid, duration: 0.12),
            Step(frame: Pose.happyTalk, duration: 0.26),
            Step(frame: Pose.gestureForward, duration: 0.26),
            Step(frame: Pose.happyTalk, duration: 0.24),
            Step(frame: Pose.waveMid, duration: 0.18),
            Step(frame: Pose.gestureForward, duration: 0.24),
            Step(frame: Pose.happyTalk, duration: 0.22),
            Step(frame: Pose.idle, duration: 0.12)
        ])
        lastGreetAt = time
        nextMoveAt = time + Float.random(in: 4.0...7.0)
    }

    func currentRenderPose(at time: Float) -> (frame: Int, facesLeft: Bool) {
        if turnTimer > 0, frames.indices.contains(Pose.turnFront) {
            let progress = 1.0 - (turnTimer / turnDuration)
            if progress < 1.0 / 3.0 {
                return (Pose.turnQuarter, turnFromLeft)
            } else if progress < 2.0 / 3.0 {
                return (Pose.turnFront, false)
            } else {
                return (Pose.turnQuarter, !turnFromLeft)
            }
        }
        if let sequence = activeSequence {
            return (sequence.frame(at: sequence.elapsed), facesLeft)
        }
        if time < blinkUntil {
            return (Pose.blink, facesLeft)
        }
        return (Pose.idle, facesLeft)
    }

    func currentFrameIndex(at time: Float) -> Int {
        currentRenderPose(at: time).frame
    }

    private func randomIdleSequence(neilIsNearby: Bool) -> Sequence {
        if neilIsNearby {
            switch Int.random(in: 0..<4) {
            case 0:
                return Sequence(kind: .idleAction, steps: [
                    Step(frame: Pose.gestureForward, duration: 0.11),
                    Step(frame: Pose.waveMid, duration: 0.18),
                    Step(frame: Pose.gestureForward, duration: 0.12)
                ])
            case 1:
                return Sequence(kind: .idleAction, steps: [
                    Step(frame: Pose.happyTalk, duration: 0.35),
                    Step(frame: Pose.handsClasped, duration: 0.65),
                    Step(frame: Pose.happyTalk, duration: 0.25)
                ])
            case 2:
                return Sequence(kind: .idleAction, steps: [
                    Step(frame: Pose.gestureForward, duration: 0.10),
                    Step(frame: Pose.beardScratch, duration: 1.05),
                    Step(frame: Pose.gestureForward, duration: 0.10)
                ])
            default:
                return Sequence(kind: .idleAction, steps: [
                    Step(frame: Pose.handsClasped, duration: 1.35)
                ])
            }
        } else {
            switch Int.random(in: 0..<4) {
            case 0:
                return Sequence(kind: .idleAction, steps: [
                    Step(frame: Pose.beardScratch, duration: 1.2)
                ])
            case 1:
                return Sequence(kind: .idleAction, steps: [
                    Step(frame: Pose.handsClasped, duration: 1.45)
                ])
            case 2:
                return Sequence(kind: .idleAction, steps: [
                    Step(frame: Pose.happyTalk, duration: 0.95)
                ])
            default:
                return Sequence(kind: .idleAction, steps: [
                    Step(frame: Pose.gestureForward, duration: 0.9)
                ])
            }
        }
    }

    func draw(in context: GraphicsContext, time: Float, width: CGFloat = defaultWidth, height: CGFloat = defaultHeight) {
        let renderPose = currentRenderPose(at: time)
        guard frames.indices.contains(renderPose.frame), let refFrame = frames.first else { return }
        let frame = frames[renderPose.frame]

        // Soft contact shadow grounds Harry's boots onto the forest floor.
        var shadowContext = context
        shadowContext.translateBy(x: position.x, y: position.y)
        shadowContext.fill(
            Path(ellipseIn: CGRect(x: -17, y: -2.5, width: 34, height: 5)),
            with: .color(Color.black.opacity(0.28))
        )

        // Use one constant pixel-to-point scale across all poses so height and width stay uniform.
        let unitX = width / refFrame.width
        let unitY = height / refFrame.height
        let rect = CGRect(
            x: -frame.anchorX * unitX,
            y: -frame.baselineY * unitY,
            width: frame.width * unitX,
            height: frame.height * unitY
        )

        var harryContext = context
        harryContext.translateBy(x: position.x, y: position.y)
        harryContext.scaleBy(x: renderPose.facesLeft ? -1 : 1, y: 1)
        harryContext.draw(frame.image, in: rect)
    }
}

