import SwiftUI
import Shared

struct ContentView: View {
    @StateObject private var controller: GameController
    @Environment(\.scenePhase) private var scenePhase
    @State private var showingAmigaRooms = false

    init(controller: GameController = GameController()) {
        _controller = StateObject(wrappedValue: controller)
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color(red: 14/255, green: 24/255, blue: 40/255)
                    .ignoresSafeArea()

                TimelineView(.animation) { context in
                    // Capture a value that changes with the simulation. Capturing only
                    // the controller lets SwiftUI reuse a stale canvas indefinitely.
                    let frame = controller.frame
                    Canvas { ctx, size in
                        controller.draw(context: &ctx, size: size, frame: frame)
                    }
                    .allowsHitTesting(false)
                    .onChange(of: context.date) { date in
                        controller.tick(date: date)
                    }
                }

                ControlOverlay(controller: controller)
                if controller.game.phase == .title {
                    VStack {
                        HStack {
                            Spacer()
                            Button("AMIGA ROOMS") { showingAmigaRooms = true }
                                .font(.system(size: 12, weight: .bold, design: .monospaced))
                                .padding().background(Color.black.opacity(0.8))
                        }
                        Spacer()
                    }
                }
            }
        }
        .statusBar(hidden: true)
        .sheet(isPresented: $showingAmigaRooms) { AmigaRoomReferenceView() }
        .onChange(of: scenePhase) { phase in
            if phase != .active { controller.suspend() }
        }
        .onDisappear { controller.suspend() }
    }
}

/// Immutable drawing input: Kotlin's mutable game properties aren't observable by SwiftUI.
struct GameFrame {
    let x: Float
    let y: Float
    let spriteFrame: Int
    let facing: Int32
    let hearts: Int
    let starCount: Int
    let bridge: Bool
    let plank: Bool
    let key: Bool
    let objective: String

    init(game: NeilGame, loop: NeilGameLoop) {
        x = loop.renderX
        y = loop.renderY
        spriteFrame = Int(game.spriteFrame)
        facing = game.facing
        hearts = Int(game.hearts)
        starCount = game.collected.count
        bridge = game.bridge
        plank = game.plank
        key = game.key
        objective = game.phase == .title
            ? "A LITTLE ADVENTURE. A BIG OH NO." : game.objective.uppercased()
    }
}

final class GameController: ObservableObject {
    let game: NeilGame
    let loop: NeilGameLoop
    private let sprites = NeilSprites()
    @Published private(set) var frame: GameFrame

    init() {
        let game = NeilGame()
        let loop = NeilGameLoop(game: game)
        self.game = game
        self.loop = loop
        frame = GameFrame(game: game, loop: loop)
    }

    @Published private(set) var isPlaying = false
    @Published private(set) var isPaused = false
    @Published private(set) var controlResetVersion = 0
    private(set) var direction: Int32 = 0
    var controlsEnabled: Bool { isPlaying && !isPaused }

    private var lastDate: Date?

    func setDirection(_ direction: Int32) {
        self.direction = controlsEnabled ? direction : 0
    }

    func activate(_ control: GameControl) {
        guard controlsEnabled else { return }
        switch control {
        case .jump: game.jump()
        case .use:
            game.interact()
            isPlaying = game.phase == .playing
            if !isPlaying { resetControls() }
        default: break
        }
        publishFrame()
    }

    func startOrResume() {
        if !isPaused { game.start() }
        isPlaying = game.phase == .playing
        isPaused = false
        resetControls()
    }

    func suspend() {
        if game.phase == .playing { isPaused = true }
        resetControls()
    }

    private func resetControls() {
        direction = 0
        lastDate = nil
        loop.reset()
        controlResetVersion += 1
        publishFrame()
    }

    private func publishFrame() {
        frame = GameFrame(game: game, loop: loop)
    }

    func tick(date: Date) {
        let now = date
        guard let last = lastDate else {
            lastDate = now
            return
        }
        let dt = min(now.timeIntervalSince(last), 0.1)
        lastDate = now

        if controlsEnabled {
            loop.advance(seconds: dt, direction: direction)
            publishFrame()
        }
    }

    func draw(context ctx: inout GraphicsContext, size: CGSize, frame: GameFrame) {
        let targetWidth: CGFloat = 480
        let targetHeight: CGFloat = 360
        let scale = min(size.width / targetWidth, size.height / targetHeight)
        let offsetX = (size.width - targetWidth * scale) / 2
        let offsetY = (size.height - targetHeight * scale) / 2

        let originalContext = ctx
        ctx.translateBy(x: offsetX, y: offsetY)
        ctx.scaleBy(x: scale, y: scale)

        // Background box
        let backgroundRect = CGRect(x: 0, y: 0, width: targetWidth, height: targetHeight)
        ctx.fill(Path(backgroundRect), with: .color(Color(red: 14/255, green: 24/255, blue: 40/255)))

        // Use the same aspect-fit artwork as the Android header.
        let logo = ctx.resolve(Image("NeilLogo"))
        let logoScale = min(198 / logo.size.width, 38 / logo.size.height)
        let logoSize = CGSize(width: logo.size.width * logoScale, height: logo.size.height * logoScale)
        ctx.draw(logo, in: CGRect(x: 12 + (198 - logoSize.width) / 2,
                                 y: 1 + (38 - logoSize.height) / 2,
                                 width: logoSize.width, height: logoSize.height))

        let heartsText = "LIFE " + String(repeating: "♥", count: frame.hearts)
        drawText(&ctx, text: heartsText, x: 222, y: 17, size: 10, color: Color(red: 255/255, green: 131/255, blue: 144/255))

        let starsText = "STARS \(frame.starCount)/8"
        drawText(&ctx, text: starsText, x: 335, y: 17, size: 9, color: .gold)

        let bagText = frame.bridge ? "BRIDGE: OK" : (frame.plank ? "BAG: PLANK" : "BAG: EMPTY")
        drawText(&ctx, text: bagText, x: 222, y: 31, size: 7, color: .cream)

        let keyText = frame.key ? "KEY: YES" : "KEY: --"
        drawText(&ctx, text: keyText, x: 335, y: 31, size: 7, color: .cream)

        // World Clip Area
        let headerContext = ctx
        let clipPath = Path(CGRect(x: 0, y: 40, width: 480, height: 230))
        ctx.clip(to: clipPath)

        let cameraX = CGFloat(max(0, min(960, frame.x - 220)))

        ctx.translateBy(x: 0, y: 40)
        ctx.fill(Path(CGRect(x: 0, y: 0, width: 480, height: 230)), with: .color(Color(red: 23/255, green: 43/255, blue: 70/255)))

        // Stars background
        for i in 0..<36 {
            let sx = CGFloat(((i * 73 - Int(cameraX * 0.12)) % 500 + 500) % 500)
            ctx.fill(Path(CGRect(x: sx, y: CGFloat((i * 31) % 96), width: 1, height: 1)), with: .color(Color(red: 110/255, green: 145/255, blue: 163/255)))
        }

        // World Objects & Platforms
        ctx.translateBy(x: -cameraX, y: 0)

        // Water
        ctx.fill(Path(CGRect(x: 650, y: 220, width: 140, height: 30)), with: .color(Color(red: 61/255, green: 138/255, blue: 160/255)))

        // Bridge
        if frame.bridge {
            for i in 0..<14 {
                ctx.fill(Path(CGRect(x: 650 + i * 10, y: 216, width: 9, height: 8)), with: .color(Color(red: 199/255, green: 152/255, blue: 98/255)))
            }
        }

        // Platforms
        for plat in game.platforms {
            let h: CGFloat = plat.y == 222 ? 35 : 12
            ctx.fill(Path(CGRect(x: CGFloat(plat.x), y: CGFloat(plat.y), width: CGFloat(plat.width), height: h)), with: .color(Color(red: 102/255, green: 86/255, blue: 78/255)))
            ctx.fill(Path(CGRect(x: CGFloat(plat.x), y: CGFloat(plat.y), width: CGFloat(plat.width), height: 4)), with: .color(Color(red: 136/255, green: 190/255, blue: 119/255)))
        }

        // Neil Character Drawing
        drawNeil(&ctx, frame: frame)

        ctx = headerContext

        // Objective Bar
        drawText(&ctx, text: frame.objective, x: 12, y: 283, size: 8, color: .teal)

        ctx = originalContext
    }

    private func drawNeil(_ ctx: inout GraphicsContext, frame: GameFrame) {
        let x = CGFloat(frame.x)
        let y = CGFloat(frame.y)
        if sprites.draw(ctx, frame: frame.spriteFrame, facing: frame.facing, x: x, y: y) { return }
        let isFacingLeft = frame.facing < 0

        let hairColor = Color(red: 29/255, green: 95/255, blue: 224/255)
        let hairShadow = Color(red: 16/255, green: 59/255, blue: 148/255)
        let hatColor = Color(red: 38/255, green: 30/255, blue: 71/255)
        let hatBand = Color(red: 240/255, green: 132/255, blue: 24/255)
        let coatColor = Color(red: 232/255, green: 130/255, blue: 30/255)
        let satchelColor = Color(red: 89/255, green: 54/255, blue: 25/255)
        let skinColor = Color(red: 250/255, green: 196/255, blue: 150/255)
        let pantsColor = Color(red: 59/255, green: 34/255, blue: 19/255)
        let shoeColor = Color(red: 247/255, green: 224/255, blue: 181/255)
        let eyeColor = Color(red: 18/255, green: 26/255, blue: 40/255)

        // Hair cascading down back
        let hairX = isFacingLeft ? x + 12 : x - 2
        ctx.fill(Path(CGRect(x: hairX, y: y + 6, width: 10, height: 16)), with: .color(hairColor))
        ctx.fill(Path(CGRect(x: isFacingLeft ? x + 14 : x - 4, y: y + 10, width: 6, height: 10)), with: .color(hairShadow))

        // Pants & Shoes
        ctx.fill(Path(CGRect(x: x + 4, y: y + 17, width: 4, height: 6)), with: .color(pantsColor))
        ctx.fill(Path(CGRect(x: x + 12, y: y + 17, width: 4, height: 6)), with: .color(pantsColor))
        ctx.fill(Path(CGRect(x: x + 2, y: y + 22, width: 6, height: 3)), with: .color(shoeColor))
        ctx.fill(Path(CGRect(x: x + 12, y: y + 22, width: 6, height: 3)), with: .color(shoeColor))

        // Ochre Tunic Coat
        ctx.fill(Path(CGRect(x: x + 3, y: y + 10, width: 14, height: 9)), with: .color(coatColor))
        // Satchel
        ctx.fill(Path(CGRect(x: x + 5, y: y + 10, width: 10, height: 2)), with: .color(satchelColor))
        ctx.fill(Path(CGRect(x: isFacingLeft ? x + 14 : x + 2, y: y + 14, width: 4, height: 4)), with: .color(satchelColor))

        // Peach face and nose
        ctx.fill(Path(CGRect(x: x + 5, y: y + 4, width: 10, height: 7)), with: .color(skinColor))
        let noseX = isFacingLeft ? x + 1 : x + 14
        ctx.fill(Path(CGRect(x: noseX, y: y + 6, width: 5, height: 3)), with: .color(skinColor))
        let eyeX = isFacingLeft ? x + 7 : x + 11
        ctx.fill(Path(CGRect(x: eyeX, y: y + 6, width: 2, height: 2)), with: .color(eyeColor))

        // Floppy hat with orange band
        ctx.fill(Path(CGRect(x: x + 4, y: y, width: 12, height: 4)), with: .color(hatColor))
        ctx.fill(Path(CGRect(x: x + 3, y: y + 4, width: 14, height: 2)), with: .color(hatBand))
        ctx.fill(Path(CGRect(x: x + 1, y: y + 5, width: 18, height: 2)), with: .color(hatColor))
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
        VStack {
            Spacer()

            if !controller.controlsEnabled {
                VStack(spacing: 8) {
                    Image("NeilLogo")
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: 352, maxHeight: controller.isPaused || controller.game.phase == .won ? 48 : 80)
                        .accessibilityLabel("Oh No! It's Neil. A classic adventure reimagined.")

                    Text(controller.isPaused ? "TAKE A BREATHER" : (controller.game.phase == .won ? "NEIL MADE IT HOME!" : "THE MOSSWOOD MIX-UP"))
                        .font(.system(size: 16, weight: .bold, design: .monospaced))
                        .foregroundColor(.gold)
                        .minimumScaleFactor(0.75)
                        .lineLimit(1)

                    Text(controller.game.phase == .won ? "Kettle on. Feet up. Crisis averted." : "A lost key. A broken bridge. A very long day.")
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundColor(.teal)

                    Button(action: controller.startOrResume) {
                        Text(controller.isPaused ? "RESUME" : (controller.game.phase == .won ? "PLAY AGAIN" : "START ADVENTURE"))
                            .font(.system(size: 14, weight: .bold, design: .monospaced))
                            .padding(.horizontal, 24)
                            .padding(.vertical, 12)
                            .background(Color.gold)
                            .foregroundColor(Color(red: 14/255, green: 24/255, blue: 40/255))
                            .cornerRadius(8)
                    }
                }
                .padding(16)
                .background(Color(red: 14/255, green: 24/255, blue: 40/255).opacity(0.95))
                .cornerRadius(12)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.gold, lineWidth: 2))
                .padding(.horizontal, 32)
                .padding(.vertical, 8)
            }

            GameControls(controller: controller)
                .frame(height: 48)
                .padding(.horizontal, 24)
                .padding(.bottom, 24)
        }
    }
}

struct GameControls: UIViewRepresentable {
    @ObservedObject var controller: GameController

    func makeUIView(context: Context) -> GameControlsView {
        let view = GameControlsView()
        view.onDirectionChange = { [weak controller] in controller?.setDirection($0) }
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

/// Shares the KMP catalogue and the imported room PNGs with Android.
private struct AmigaRoomReferenceView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var roomID: Int32 = 15
    @State private var artwork: UIImage?

    private var roomLabel: String { AmigaRooms.shared.room(id: roomID).label }

    private func loadArtwork() {
        guard let url = Bundle.main.url(
            forResource: String(format: "room_%02d", roomID),
            withExtension: "png", subdirectory: "sprites/amiga"
        ) else { artwork = nil; return }
        artwork = UIImage(contentsOfFile: url.path)
    }

    var body: some View {
        VStack(spacing: 20) {
            Text("AMIGA ROOM REFERENCE").font(.headline)
            Text(String(format: "%02d", roomID) + "  " + roomLabel)
            Spacer()
            if let artwork {
                Image(uiImage: artwork).resizable().interpolation(.none).scaledToFit()
            } else {
                Text("Original room artwork has not been imported.\nSee art/AMIGA_IMPORT.md.")
                    .multilineTextAlignment(.center)
            }
            Spacer()
            Text("Reference order; room exits are not yet mapped.").font(.caption)
            HStack {
                Button("Previous") { roomID = (roomID + 99) % 100 }
                Spacer()
                Button("Back") { dismiss() }
                Spacer()
                Button("Next") { roomID = (roomID + 1) % 100 }
            }
        }
        .padding()
        .onAppear(perform: loadArtwork)
        .onChange(of: roomID) { _ in loadArtwork() }
    }
}

extension Color {
    static let gold = Color(red: 255/255, green: 199/255, blue: 91/255)
    static let teal = Color(red: 103/255, green: 221/255, blue: 177/255)
    static let cream = Color(red: 249/255, green: 232/255, blue: 190/255)
}
