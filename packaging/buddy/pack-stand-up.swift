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
precondition(args.count == 3, "Usage: pack-stand-up.swift source-atlas output-three-cell-strip")
let source = read(args[1])
precondition(source.width == 2172 && source.height == 724, "Reinspect atlas for changed dimensions")
// Measured alpha >= 128 bounds within three transparent-gutter regions.
let regions = [(0, 724), (724, 1448), (1448, 2172)]
let bounds = [CGRect(x: 253, y: 174, width: 427, height: 503),
              CGRect(x: 896, y: 177, width: 446, height: 495),
              CGRect(x: 1538, y: 106, width: 410, height: 564)]
let output = canvas(252, 96)
let factor = 0.15 // head width matches the existing 50–54 pixel heads
for (index, rect) in bounds.enumerated() {
    let region = regions[index]
    precondition(Int(rect.minX) > region.0 && Int(rect.maxX) < region.1)
    let cell = source.cropping(to: rect)!
    let w = Int((Double(cell.width) * factor).rounded())
    let h = Int((Double(cell.height) * factor).rounded())
    precondition(w < 82 && h < 92)
    // CoreGraphics' origin is bottom-left; all feet end at image row 91.
    output.draw(cell, in: CGRect(x: index * 84 + (84-w)/2, y: 4, width: w, height: h))
}
let pixels = output.data!.assumingMemoryBound(to: UInt8.self)
for i in 0..<(252 * 96) {
    let p = i * 4, alpha = Int(pixels[p+3])
    if alpha < 128 { for c in 0..<4 { pixels[p+c] = 0 } }
    else {
        for c in 0..<3 { pixels[p+c] = UInt8(min(255, (Int(pixels[p+c]) * 255 + alpha/2) / alpha)) }
        pixels[p+3] = 255
    }
}
let dest = CGImageDestinationCreateWithURL(URL(fileURLWithPath: args[2]) as CFURL, "public.png" as CFString, 1, nil)!
CGImageDestinationAddImage(dest, output.makeImage()!, nil)
precondition(CGImageDestinationFinalize(dest))
print("Packed three transition cells; scale 0.15, foot baseline row 91")
