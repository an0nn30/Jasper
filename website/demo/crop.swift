import Foundation
import ImageIO
import CoreGraphics
let a=CommandLine.arguments
let source=CGImageSourceCreateWithURL(URL(fileURLWithPath:a[1]) as CFURL,nil)!
let img=CGImageSourceCreateImageAtIndex(source,0,nil)!
let rect=CGRect(x:Double(a[3])!,y:Double(a[4])!,width:Double(a[5])!,height:Double(a[6])!)
let crop=img.cropping(to:rect)!
let out=CGImageDestinationCreateWithURL(URL(fileURLWithPath:a[2]) as CFURL,"public.png" as CFString,1,nil)!
CGImageDestinationAddImage(out,crop,nil)
precondition(CGImageDestinationFinalize(out))
