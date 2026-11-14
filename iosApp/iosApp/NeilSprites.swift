import SwiftUI
import ImageIO

/// Loads the shared Neil sprite atlas and caches each animation frame.
final class NeilSprites {
    private struct Manifest: Decodable {
        let columns: Int
        let rows: Int
        let frames: [FrameSpec]
    }

    private struct FrameSpec: Decodable {
        let x: Int
        let y: Int
        let width: Int
        let height: Int
        let baseline: Int
    }

    private struct Frame {
        let cgImage: CGImage
        let image: Image
        let width: CGFloat
        let height: CGFloat
        let baseline: CGFloat
    }

    private let frames: [Frame]
    private let walkFrames: [Frame]
    var frameCount: Int { frames.count }
    var walkFrameCount: Int { walkFrames.count }

    // Six natural biomechanical walk phases complete a full two-step stride in 0.6 seconds (10 fps).
    static let walkDuration = 0.6
    static let walkPoseCount = 6

    func walkFrameIndex(frame: Int, walkTime: Float) -> Int? {
        guard walkFrames.count == Self.walkPoseCount, (17...22).contains(frame) else { return nil }
        if walkTime <= 0 {
            return frame - 17
        }
        let phase = (Double(walkTime) / Self.walkDuration).truncatingRemainder(dividingBy: 1)
        return min(Self.walkPoseCount - 1, Int(phase * Double(Self.walkPoseCount)))
    }

    func cgImage(forFrame index: Int) -> CGImage? {
        guard frames.indices.contains(index) else { return nil }
        return frames[index].cgImage
    }

    init(bundle: Bundle = .main) {
        let atlas: CGImage? = {
            if let pngURL = bundle.url(forResource: "neil_sprite_atlas", withExtension: "png", subdirectory: "sprites"),
               let source = CGImageSourceCreateWithURL(pngURL as CFURL, nil),
               let image = CGImageSourceCreateImageAtIndex(source, 0, nil) {
                return image
            }
            if let txtURL = bundle.url(forResource: "neil-dark-atlas", withExtension: "txt"),
               let encoded = try? String(contentsOf: txtURL, encoding: .utf8),
               let imageData = Data(base64Encoded: encoded, options: .ignoreUnknownCharacters),
               let source = CGImageSourceCreateWithData(imageData as CFData, nil) {
                return CGImageSourceCreateImageAtIndex(source, 0, nil)
            }
            return nil
        }()

        guard let atlas,
              let metadataURL = bundle.url(forResource: "neil_sprite_atlas", withExtension: "json", subdirectory: "sprites"),
              let data = try? Data(contentsOf: metadataURL),
              let manifest = try? JSONDecoder().decode(Manifest.self, from: data),
              manifest.frames.count == manifest.columns * manifest.rows else {
            frames = []
            walkFrames = []
            return
        }
        var decoded: [Frame] = []
        for (index, spec) in manifest.frames.enumerated() {
            let column = index % manifest.columns
            let row = index / manifest.columns
            let left = column * atlas.width / manifest.columns
            let right = (column + 1) * atlas.width / manifest.columns
            let top = row * atlas.height / manifest.rows
            let bottom = (row + 1) * atlas.height / manifest.rows
            guard spec.width > 0, spec.height > 0,
                  (1...spec.height).contains(spec.baseline),
                  let crop = atlas.cropping(to: CGRect(x: left, y: top, width: right - left, height: bottom - top)) else {
                frames = []
                walkFrames = []
                return
            }
            decoded.append(Frame(
                cgImage: crop,
                image: Image(decorative: crop, scale: 1, orientation: .up).interpolation(.none),
                width: CGFloat(spec.width),
                height: CGFloat(spec.height),
                baseline: CGFloat(spec.baseline)
            ))
        }
        frames = decoded
        walkFrames = decoded.count >= 23 ? Array(decoded[17...22]) : []
    }

    @discardableResult
    func draw(_ context: GraphicsContext, frame index: Int, facing: Int32, x: CGFloat, y: CGFloat, height: CGFloat = 38, walkTime: Float = 0) -> Bool {
        guard frames.indices.contains(index) else { return false }
        let walkIndex = walkFrameIndex(frame: index, walkTime: walkTime)
        let frame = walkIndex.map { walkFrames[$0] } ?? frames[index]
        let scale = height / frame.height
        let width = frame.width * scale
        var ctx = context
        ctx.translateBy(x: x, y: y)
        ctx.scaleBy(x: facing < 0 ? -1 : 1, y: 1)
        let rect = CGRect(x: -width / 2, y: -frame.baseline * scale, width: width, height: height)
        ctx.draw(frame.image, in: rect)
        return true
    }
}
