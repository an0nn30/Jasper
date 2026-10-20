import Foundation
import ImageIO
import CoreGraphics
func read(_ path: String) -> CGImage {
    CGImageSourceCreateImageAtIndex(CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, nil)!, 0, nil)!
}
func canvas(_ width: Int, _ height: Int) -> CGContext {
    let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8,
        bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    context.interpolationQuality = .none
    context.setShouldAntialias(false)
    return context
}
let args = CommandLine.arguments
precondition(args.count == 4, "Usage: pack-atlas.swift source-atlas original-sparkle-strip output-strip")
let source = read(args[1]), sparkles = read(args[2])
precondition(source.width == 1429 && source.height == 1100, "Reinspect atlas boundaries for a new source")
let width = 84, height = 96, output = canvas(1680, 96)
// Measured transparent gutters of the approved pixel atlas, not guessed uniform cells.
let xs = [0, 275, 555, 825, 1110, 1429], ys = [0, 320, 620, 837, 1100]
var cells: [CGImage] = []
for index in 0..<20 {
    let col = index % 5, row = index / 5
    let cell = source.cropping(to: CGRect(x: xs[col], y: ys[row], width: xs[col+1]-xs[col], height: ys[row+1]-ys[row]))!
    let probe = canvas(cell.width, cell.height)
    probe.draw(cell, in: CGRect(x: 0, y: 0, width: cell.width, height: cell.height))
    let pixels = probe.data!.assumingMemoryBound(to: UInt8.self)
    var minX = cell.width, minY = cell.height, maxX = -1, maxY = -1
    for y in 0..<cell.height { for x in 0..<cell.width {
        if pixels[(y * cell.width + x) * 4 + 3] >= 128 {
            minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
        }
    } }
    precondition(maxX >= minX && maxY >= minY, "Empty atlas cell")
    cells.append(cell.cropping(to: CGRect(x: minX, y: minY, width: maxX-minX+1, height: maxY-minY+1))!)
}
let poses = cells.enumerated().filter { !(14...16).contains($0.offset) }.map { $0.element }
let factor = min(78.0 / Double(poses.map { $0.width }.max()!), 86.0 / Double(poses.map { $0.height }.max()!))
for index in 0..<20 {
    if (14...16).contains(index) {
        let cell = sparkles.cropping(to: CGRect(x: index * 42, y: 0, width: 42, height: 48))!
        output.draw(cell, in: CGRect(x: index * width, y: 0, width: width, height: height))
    } else {
        let cell = cells[index], w = Int((Double(cell.width) * factor).rounded()), h = Int((Double(cell.height) * factor).rounded())
        output.draw(cell, in: CGRect(x: index * width + (width-w)/2, y: index == 5 ? 8 : 4, width: w, height: h))
    }
}
// Sprite pixels are either solid or transparent: discard the generator's translucent edge fringe.
let pixels = output.data!.assumingMemoryBound(to: UInt8.self)
for i in 0..<(1680 * 96) {
    let p = i * 4, alpha = Int(pixels[p+3])
    if alpha < 128 { for c in 0..<4 { pixels[p+c] = 0 } }
    else {
        for c in 0..<3 { pixels[p+c] = UInt8(min(255, (Int(pixels[p+c]) * 255 + alpha/2) / alpha)) }
        pixels[p+3] = 255
    }
}
let dest = CGImageDestinationCreateWithURL(URL(fileURLWithPath: args[3]) as CFURL, "public.png" as CFString, 1, nil)!
CGImageDestinationAddImage(dest, output.makeImage()!, nil)
precondition(CGImageDestinationFinalize(dest))
print("Packed 20 pixel frames at 84x96; shared source scale \(factor)")
