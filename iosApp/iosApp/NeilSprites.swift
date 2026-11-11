import SwiftUI
import ImageIO

/// Loads the same PNG and pixel bounds as Android; crops are cached once.
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
        let image: Image
        let width: CGFloat
        let height: CGFloat
        let baseline: CGFloat
    }

    private let frames: [Frame]
    var frameCount: Int { frames.count }

    init(bundle: Bundle = .main) {
        guard let imageURL = bundle.url(forResource: "neil_sprite_atlas", withExtension: "png", subdirectory: "sprites"),
              let metadataURL = bundle.url(forResource: "neil_sprite_atlas", withExtension: "json", subdirectory: "sprites"),
              let source = CGImageSourceCreateWithURL(imageURL as CFURL, nil),
              let atlas = CGImageSourceCreateImageAtIndex(source, 0, nil),
              let data = try? Data(contentsOf: metadataURL),
              let manifest = try? JSONDecoder().decode(Manifest.self, from: data),
              manifest.frames.count == manifest.columns * manifest.rows else {
            frames = []
            return
        }
        var decoded: [Frame] = []
        for spec in manifest.frames {
            guard spec.x >= 0, spec.y >= 0, spec.width > 0, spec.height > 0,
                  spec.x + spec.width <= atlas.width, spec.y + spec.height <= atlas.height,
                  (1...spec.height).contains(spec.baseline),
                  let crop = atlas.cropping(to: CGRect(x: spec.x, y: spec.y, width: spec.width, height: spec.height)) else {
                frames = []
                return
            }
            decoded.append(Frame(
                image: Image(decorative: crop, scale: 1, orientation: .up).interpolation(.none),
                width: CGFloat(spec.width), height: CGFloat(spec.height), baseline: CGFloat(spec.baseline)
            ))
        }
        frames = decoded
    }

    @discardableResult
    func draw(_ context: GraphicsContext, frame index: Int, facing: Int32, x: CGFloat, y: CGFloat) -> Bool {
        guard frames.indices.contains(index) else { return false }
        let frame = frames[index]
        let height: CGFloat = 38
        let scale = height / frame.height
        let width = frame.width * scale
        var ctx = context
        ctx.translateBy(x: x + 10, y: y + 24)
        ctx.scaleBy(x: facing < 0 ? -1 : 1, y: 1)
        ctx.draw(frame.image, in: CGRect(x: -width / 2, y: -frame.baseline * scale, width: width, height: height))
        return true
    }
}
