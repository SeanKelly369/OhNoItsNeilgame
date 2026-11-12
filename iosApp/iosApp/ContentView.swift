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

        self.game = OriginalGame(rooms: loadedRooms, objects: loadedItems)
        self.usedRooms = used
        loadSave()
    }

    func setDirection(_ dir: Int32) { self.direction = dir }
    func setClimb(_ cl: Int32) { self.climb = cl }

    func activate(_ control: GameControl) {
        if control == .map && isMap {
            isMap = false
            resetControls()
            return
        }
        guard controlsEnabled else { return }
        switch control {
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
        isPaused = !isPaused
        isMap = false
        direction = 0
        climb = 0
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
        if game.interact() {
            save()
            return
        }
        game.message = "No loose item nearby. Puzzle interactions are not restored yet."
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
                accumulator -= stepTime
            }
        } else {
            accumulator = 0
        }
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
        let state: [String: Any] = [
            "room": game.room,
            "x": game.x,
            "y": game.y
        ]
        UserDefaults.standard.set(state, forKey: "original_game_state_ios")
    }

    private func loadSave() {
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

        // Room Items
        for item in game.objects where item.room == game.room {
            let itemPath = String(format: "sprites/%03d.png", item.sprite)
            if let itemImg = image(named: itemPath) {
                let w = itemImg.size.width / (itemImg.size.width > 32 ? 4.0 : 1.0)
                let h = itemImg.size.height / (itemImg.size.height > 32 ? 4.0 : 1.0)
                gameCtx.draw(Image(uiImage: itemImg), in: CGRect(x: CGFloat(item.x), y: CGFloat(item.y), width: w, height: h))
            }
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
                height: 46
            )
            if !drawn {
                let neilPath = String(format: "sprites/%03d.png", game.sprite)
                if let fallback = image(named: neilPath) {
                    gameCtx.draw(Image(uiImage: fallback), in: CGRect(x: CGFloat(game.x - 8), y: CGFloat(game.y), width: 32, height: 32))
                }
            }
        }

        // Status & Inventory strip
        ctx.fill(Path(CGRect(x: 20, y: 216, width: 320, height: 12)), with: .color(Color(red: 15/255, green: 22/255, blue: 30/255)))
        drawText(&ctx, text: game.current.name, x: 24, y: 225, size: 7, color: .gold)
        let status = "STARS \(game.stars)   BAG \(game.inventory.count)/3   ROOMS \(game.visited.count)/71"
        drawText(&ctx, text: status, x: 20, y: 239, size: 6, color: .cream)
        drawText(&ctx, text: String(game.message.prefix(53)), x: 20, y: 249, size: 6, color: .cream)

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
        drawText(&ctx, text: "Inventory: tap below to drop an item.", x: 52, y: 132, size: 6, color: .cream)
        for (i, id) in game.inventory.enumerated() {
            let intId = (id as? NSNumber)?.int32Value ?? (id as? Int32) ?? 0
            if let it = game.objects.first(where: { $0.id == intId }) {
                drawText(&ctx, text: "• \(it.name)", x: 60, y: CGFloat(151 + i * 14), size: 8, color: .gold)
            }
        }
    }

    private func drawMapOverlay(_ ctx: inout GraphicsContext) {
        ctx.fill(Path(CGRect(x: 12, y: 32, width: 336, height: 220)), with: .color(Color(red: 16/255, green: 28/255, blue: 42/255)))
        drawText(&ctx, text: "ROOM ATLAS", x: 26, y: 49, size: 12, color: .gold)
        drawText(&ctx, text: "Tap a room to explore. Arrows change page.", x: 26, y: 62, size: 6, color: .cream)

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
        drawText(&ctx, text: "CLOSE", x: 159, y: 243, size: 8, color: .gold)
        drawText(&ctx, text: "NEXT >", x: 280, y: 243, size: 8, color: .cream)
    }

    private func drawText(_ ctx: inout GraphicsContext, text: String, x: CGFloat, y: CGFloat, size: CGFloat, color: Color) {
        ctx.draw(
            Text(text).font(.system(size: size, weight: .bold, design: .monospaced)).foregroundColor(color),
            at: CGPoint(x: x, y: y),
            anchor: .topLeading
        )
    }
}

struct ControlOverlay: View {
    @ObservedObject var controller: GameController

    var body: some View {
        GeometryReader { proxy in
            VStack(spacing: 0) {
                // Top tap bar (for Pause / Resume button)
                HStack {
                    Spacer()
                    Button(action: controller.togglePause) {
                        Text(controller.isPaused ? "RESUME" : "PAUSE")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundColor(.cream)
                            .padding(8)
                    }
                    .padding(.trailing, 16)
                }
                .frame(height: 36)

                // Middle area handles title tap, pause unpause, or room atlas tap
                Color.clear
                    .contentShape(Rectangle())
                    .onTapGesture { location in
                        handleViewportTap(location: location, size: proxy.size)
                    }

                // Bottom control buttons
                GameControls(controller: controller)
                    .frame(height: 48)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 12)
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
        // The tap gesture is attached below the 36-point header.
        let y = (location.y + 36 - offsetY) / scale

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
    }
}

struct GameControls: UIViewRepresentable {
    @ObservedObject var controller: GameController

    func makeUIView(context: Context) -> GameControlsView {
        let view = GameControlsView()
        view.onDirectionChange = { [weak controller] in controller?.setDirection($0) }
        view.onClimbChange = { [weak controller] in controller?.setClimb($0) }
        view.onAction = { [weak controller] in controller?.activate($0) }
        return view
    }

    func updateUIView(_ view: GameControlsView, context: Context) {
        view.resetVersion = controller.controlResetVersion
        view.controlsEnabled = controller.controlsEnabled
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
