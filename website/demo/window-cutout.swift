import Foundation
import ImageIO
import CoreGraphics
let a=CommandLine.arguments
let source=CGImageSourceCreateWithURL(URL(fileURLWithPath:a[1]) as CFURL,nil)!
let input=CGImageSourceCreateImageAtIndex(source,0,nil)!
let inset=Int(a[3])!, radius=Double(a[4])!
let image=input.cropping(to:CGRect(x:inset,y:inset,width:input.width-2*inset,height:input.height-2*inset))!
let w=image.width,h=image.height
let ctx=CGContext(data:nil,width:w,height:h,bitsPerComponent:8,bytesPerRow:w*4,space:CGColorSpaceCreateDeviceRGB(),bitmapInfo:CGImageAlphaInfo.premultipliedLast.rawValue)!
let rect=CGRect(x:0,y:0,width:w,height:h)
ctx.addPath(CGPath(roundedRect:rect,cornerWidth:radius,cornerHeight:radius,transform:nil));ctx.clip();ctx.draw(image,in:rect)
let out=CGImageDestinationCreateWithURL(URL(fileURLWithPath:a[2]) as CFURL,"public.png" as CFString,1,nil)!
CGImageDestinationAddImage(out,ctx.makeImage()!,nil);precondition(CGImageDestinationFinalize(out))
