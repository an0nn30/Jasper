// Render a compiled Icon Composer catalog through macOS without launching an app.
import AppKit

let arguments = CommandLine.arguments
let icon = NSWorkspace.shared.icon(forFile: arguments[1])
let output = URL(fileURLWithPath: arguments[2], isDirectory: true)
for argument in arguments.dropFirst(3) {
    let size = Int(argument)!
    let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: size,
        pixelsHigh: size, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true,
        isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    // Icon Services supports up to 512 points. Request 1024px as 512pt @2x;
    // a 1024-point request can silently draw only a shadow.
    let points = min(size, 512)
    bitmap.size = NSSize(width: points, height: points)
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
    icon.draw(in: NSRect(x: 0, y: 0, width: points, height: points),
        from: .zero, operation: .copy, fraction: 1)
    NSGraphicsContext.restoreGraphicsState()
    var opaque = 0, bright = 0
    for y in 0..<size {
        for x in 0..<size {
            let color = bitmap.colorAt(x: x, y: y)!.usingColorSpace(.deviceRGB)!
            if color.alphaComponent >= 0.9 {
                opaque += 1
                if min(color.redComponent, color.greenComponent, color.blueComponent) > 0.6 {
                    bright += 1
                }
            }
        }
    }
    precondition(opaque > size * size / 3 && bright > size * size / 100,
        "Native \(size)px icon is missing its face or prompt")
    try bitmap.representation(using: .png, properties: [:])!
        .write(to: output.appendingPathComponent("icon-\(size).png"))
}
