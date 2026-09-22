import AppKit
import ImageIO
import UniformTypeIdentifiers

// Native raster preparation for the generated, opaque presentation images.
// Flood only low-chroma pixels connected to the outer canvas. The blue/teal
// tile encloses the white turtle, so its white facets remain untouched.
let args = CommandLine.arguments
guard args.count == 3,
      let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: args[1]) as CFURL, nil),
      let input = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
    fatalError("Usage: render-icons SOURCE.png OUTPUT_DIRECTORY")
}
let width = input.width, height = input.height
let space = CGColorSpace(name: CGColorSpace.sRGB)!
let info = CGBitmapInfo.byteOrder32Big.rawValue | CGImageAlphaInfo.premultipliedLast.rawValue
var rgba = [UInt8](repeating: 0, count: width * height * 4)
rgba.withUnsafeMutableBytes { bytes in
    let ctx = CGContext(data: bytes.baseAddress, width: width, height: height, bitsPerComponent: 8,
                        bytesPerRow: width * 4, space: space, bitmapInfo: info)!
    ctx.draw(input, in: CGRect(x: 0, y: 0, width: width, height: height))
}
func neutral(_ index: Int) -> Bool {
    let i = index * 4
    return Int(max(rgba[i], rgba[i+1], rgba[i+2])) - Int(min(rgba[i], rgba[i+1], rgba[i+2])) < 28
}
var outside = [Bool](repeating: false, count: width * height)
var queue = [Int]()
func seed(_ i: Int) {
    if !outside[i] && neutral(i) { outside[i] = true; queue.append(i) }
}
for x in 0..<width { seed(x); seed((height-1)*width+x) }
for y in 0..<height { seed(y*width); seed(y*width+width-1) }
var cursor = 0
while cursor < queue.count {
    let p = queue[cursor]; cursor += 1
    let x = p % width, y = p / width
    if x > 0 { seed(p-1) }; if x+1 < width { seed(p+1) }
    if y > 0 { seed(p-width) }; if y+1 < height { seed(p+width) }
}
// Keep the main connected foreground, discarding stray color pixels outside it.
var keep = [Bool](repeating: false, count: width * height)
queue = [width * (height/2) + width/2]; keep[queue[0]] = true; cursor = 0
while cursor < queue.count {
    let p = queue[cursor]; cursor += 1
    let x = p % width, y = p / width
    var next = [Int]()
    if x > 0 { next.append(p-1) }; if x+1 < width { next.append(p+1) }
    if y > 0 { next.append(p-width) }; if y+1 < height { next.append(p+width) }
    for n in next where !outside[n] && !keep[n] { keep[n] = true; queue.append(n) }
}
var minX = width, maxX = 0, minY = height, maxY = 0
for p in 0..<(width*height) {
    if keep[p] {
        minX = min(minX,p%width); maxX = max(maxX,p%width)
        minY = min(minY,p/width); maxY = max(maxY,p/width)
    } else {
        for c in 0..<4 { rgba[p*4+c] = 0 }
    }
}
precondition(maxX-minX > width/2 && maxY-minY > height/2, "Tile extraction failed")
let data = Data(rgba) as CFData
let provider = CGDataProvider(data: data)!
let full = CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32,
                   bytesPerRow: width*4, space: space, bitmapInfo: CGBitmapInfo(rawValue: info),
                   provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)!
let tile = full.cropping(to: CGRect(x: minX, y: minY, width: maxX-minX+1, height: maxY-minY+1))!
let output = URL(fileURLWithPath: args[2], isDirectory: true)
let fm = FileManager.default
let sizes = [16,20,24,30,32,36,40,48,60,64,72,80,96,128,256,512,1024]
for (platform, coverage) in [("macos",0.796875),("windows",0.9375),("linux",0.90)] {
    let dir = output.appendingPathComponent(platform)
    try fm.createDirectory(at: dir, withIntermediateDirectories: true)
    for size in sizes {
        let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8,
                            bytesPerRow: size*4, space: space, bitmapInfo: info)!
        ctx.interpolationQuality = .high
        let side = Double(size)*coverage
        let factor = side/Double(max(tile.width,tile.height))
        let w = Double(tile.width)*factor, h = Double(tile.height)*factor
        ctx.draw(tile, in: CGRect(x:(Double(size)-w)/2, y:(Double(size)-h)/2, width:w, height:h))
        let url = dir.appendingPathComponent("icon-\(size).png")
        let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
        CGImageDestinationAddImage(destination,ctx.makeImage()!,nil)
        precondition(CGImageDestinationFinalize(destination))
    }
}
print("Extracted tile \(tile.width)x\(tile.height); wrote \(sizes.count * 3) PNGs.")
