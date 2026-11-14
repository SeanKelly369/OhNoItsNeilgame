// Run from the repository root: swift tools/export-parity-art.swift
// Exports the iOS renderer's exact frames for Android; never maintains a second animation warp.
import Foundation

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
func read(_ path: String) throws -> String {
    try String(contentsOf: root.appendingPathComponent(path), encoding: .utf8)
}
var neil = try read("iosApp/iosApp/NeilSprites.swift")
neil = neil.replacingOccurrences(of: "private ", with: "")

let content = try read("iosApp/iosApp/ContentView.swift")
let start = content.range(of: "struct LumberjackFrame {")!.lowerBound
var harry = String(content[start...]).replacingOccurrences(of: "private ", with: "")
harry = harry.replacingOccurrences(of: "UIImage(data: data)?.cgImage",
    with: "CGImageSourceCreateWithData(data as CFData, nil).flatMap({ CGImageSourceCreateImageAtIndex($0, 0, nil) })")
for name in ["neil", "harry"] {
    var source = name == "neil" ? neil : harry
    source = source.replacingOccurrences(of: "bundle.url(forResource:", with: "assetURL(forResource:")
    if name == "neil" { neil = source } else { harry = source }
}
let prelude = """
import SwiftUI
import ImageIO
import UniformTypeIdentifiers
func assetURL(forResource name: String, withExtension ext: String, subdirectory: String? = nil) -> URL? {
    let base = ext == "txt" ? "iosApp/iosApp" : "art/sprites"
    return URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
        .appendingPathComponent(base).appendingPathComponent(name + "." + ext)
}
"""
let export = #"""
let output = URL(fileURLWithPath: FileManager.default.currentDirectoryPath).appendingPathComponent("art/sprites/parity")
try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
var manifest: [String: [[String: Any]]] = [:]
func emit(_ image: CGImage, group: String, index: Int, width: CGFloat, height: CGFloat, anchor: CGFloat, baseline: CGFloat) {
    let name = "\(group)_\(index).png"
    let destination = CGImageDestinationCreateWithURL(output.appendingPathComponent(name) as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(destination, image, nil)
    precondition(CGImageDestinationFinalize(destination))
    manifest[group, default: []].append(["file": name, "width": width, "height": height, "anchor": anchor, "baseline": baseline])
}
let neilSprites = NeilSprites()
precondition(neilSprites.frames.count >= 24 && neilSprites.walkFrames.count == NeilSprites.walkPoseCount)
for (i, frame) in neilSprites.frames.enumerated() {
    emit(frame.cgImage, group: "neil", index: i, width: frame.width, height: frame.height, anchor: frame.width / 2, baseline: frame.baseline)
}
for (i, frame) in neilSprites.walkFrames.enumerated() {
    emit(frame.cgImage, group: "walk", index: i, width: frame.width, height: frame.height, anchor: frame.width / 2, baseline: frame.baseline)
}
let animator = HarryAnimator(position: CGPoint(x: 240, y: 160))
precondition(animator.frames.count == 10)
for (i, frame) in animator.frames.enumerated() {
    emit(frame.cgImage, group: "harry", index: i, width: frame.width, height: frame.height, anchor: frame.anchorX, baseline: frame.baselineY)
}
try JSONSerialization.data(withJSONObject: manifest, options: [.prettyPrinted, .sortedKeys]).write(to: output.appendingPathComponent("frames.json"))
print("Exported shared character frames.")
"""#
let temporary = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".swift")
try (prelude + "\n" + neil + "\n" + harry + "\n" + export).write(to: temporary, atomically: true, encoding: .utf8)
defer { try? FileManager.default.removeItem(at: temporary) }
let process = Process()
process.executableURL = URL(fileURLWithPath: "/usr/bin/swift")
process.arguments = [temporary.path]
try process.run()
process.waitUntilExit()
exit(process.terminationStatus)
