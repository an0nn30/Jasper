// Blurs the desktop behind a translucent Jasper window, in one of two ways. setBlur uses the private CoreGraphics call
// iTerm2, Alacritty and WezTerm use: an adjustable radius, but it blurs the window's whole rectangle, so the square
// corners outside the window's rounded shape show blurred backdrop. setEffect places a public NSVisualEffectView behind
// the content instead: Apple's fixed materials, masked to the window's rounded corners. The private symbols and
// JAWT_GetAWT are looked up at run time, so a missing symbol only disables blur.
#import <AppKit/AppKit.h>
#import <QuartzCore/QuartzCore.h>
#include <dlfcn.h>
#include <jni.h>
#include <jawt.h>
#include <jawt_md.h>

typedef int32_t JasperConnection;
typedef JasperConnection (*JasperDefaultConnection)(void);
typedef int32_t (*JasperSetBlurRadius)(JasperConnection, int32_t, int);
typedef jboolean (JNICALL *JasperGetAWT)(JNIEnv *, JAWT *);

/** The radius CoreGraphics receives; Java already limits it to 40. */
static int JasperClampRadius(jint radius) { return radius < 0 ? 0 : radius > 64 ? 64 : (int) radius; }

/**
 * The window whose content view hosts {@code layer}: the view's own layer or any layer beneath it, however deep (a
 * custom title bar nests the frame's layer). The nearest ancestor that is a content view's layer wins.
 */
static id JasperWindowForLayer(CALayer *layer, NSArray *windows) {
    for (CALayer *ancestor = layer; ancestor != nil; ancestor = [ancestor superlayer]) {
        for (id window in windows) {
            CALayer *content = [[window contentView] layer];
            if (content != nil && content == ancestor) return window;
        }
    }
    return nil;
}

/** The AWT window's Metal layer, through JAWT; nil when JAWT is unavailable or the window has no surface. */
static CALayer *JasperLayer(JNIEnv *env, jobject window) {
    JasperGetAWT getAWT = (JasperGetAWT) dlsym(RTLD_DEFAULT, "JAWT_GetAWT");
    if (getAWT == NULL) return nil;
    JAWT awt;
    awt.version = JAWT_VERSION_9;
    if (!getAWT(env, &awt)) return nil;
    JAWT_DrawingSurface *surface = awt.GetDrawingSurface(env, window);
    if (surface == NULL) return nil;
    CALayer *layer = nil;
    jint lock = surface->Lock(surface);
    if (!(lock & JAWT_LOCK_ERROR)) {
        JAWT_DrawingSurfaceInfo *info = surface->GetDrawingSurfaceInfo(surface);
        if (info != NULL) {
            id<JAWT_SurfaceLayers> layers = (__bridge id<JAWT_SurfaceLayers>) info->platformInfo;
            layer = [layers windowLayer];
            surface->FreeDrawingSurfaceInfo(info);
        }
        surface->Unlock(surface);
    }
    awt.FreeDrawingSurface(surface);
    return layer;
}

JNIEXPORT jboolean JNICALL Java_dev_jasper_app_platform_MacGlass_setBlur(JNIEnv *env, jclass type, jobject window, jint radius) {
    JasperDefaultConnection connection = (JasperDefaultConnection) dlsym(RTLD_DEFAULT, "CGSDefaultConnectionForThread");
    JasperSetBlurRadius setBlurRadius = (JasperSetBlurRadius) dlsym(RTLD_DEFAULT, "CGSSetWindowBackgroundBlurRadius");
    if (connection == NULL || setBlurRadius == NULL) return JNI_FALSE;
    CALayer *layer = JasperLayer(env, window);
    if (layer == nil) return JNI_FALSE;
    int clamped = JasperClampRadius(radius);
    // AppKit belongs to the main thread. Never wait for it here: it may be waiting for the EDT.
    dispatch_async(dispatch_get_main_queue(), ^{
        NSWindow *target = JasperWindowForLayer(layer, [NSApp windows]);
        if (target != nil) setBlurRadius(connection(), (int32_t) [target windowNumber], clamped);
    });
    return JNI_TRUE;
}

/** The identifier of the effect view setEffect adds behind the content view. */
static NSString *const JasperEffectIdentifier = @"dev.jasper.glass.effect";

/** A stretchable rounded-rectangle mask for a window whose corners have {@code radius} points. */
static NSImage *JasperCornerMask(CGFloat radius) {
    CGFloat edge = 2 * radius + 1;
    NSImage *mask = [NSImage imageWithSize:NSMakeSize(edge, edge) flipped:NO drawingHandler:^BOOL(NSRect rect) {
        [[NSColor blackColor] setFill];
        [[NSBezierPath bezierPathWithRoundedRect:rect xRadius:radius yRadius:radius] fill];
        return YES;
    }];
    mask.capInsets = NSEdgeInsetsMake(radius, radius, radius, radius);
    mask.resizingMode = NSImageResizingModeStretch;
    return mask;
}

/** The window's corner radius: AppKit's own value when it reports one, otherwise the classic 10 points; 0 in full screen. */
static CGFloat JasperWindowCornerRadius(NSWindow *window) {
    if ((window.styleMask & NSWindowStyleMaskFullScreen) != 0) return 0;
    SEL selector = NSSelectorFromString(@"_cornerRadius");
    if (![window respondsToSelector:selector]) return 10;
    CGFloat radius = ((CGFloat (*)(id, SEL)) [window methodForSelector:selector])(window, selector);
    return radius >= 0 && radius <= 64 ? radius : 10;
}

/** Shows (material >= 0) or removes (material < 0) the effect view behind {@code window}'s content view. */
static void JasperApplyEffect(NSWindow *window, NSInteger material) {
    NSView *content = window.contentView, *frame = content.superview;
    if (frame == nil) return;
    NSVisualEffectView *effect = nil;
    for (NSView *view in frame.subviews)
        if ([view.identifier isEqualToString:JasperEffectIdentifier]) effect = (NSVisualEffectView *) view;
    if (material < 0) { [effect removeFromSuperview]; return; }
    if (effect == nil) {
        effect = [[NSVisualEffectView alloc] initWithFrame:frame.bounds];
        effect.identifier = JasperEffectIdentifier;
        effect.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
        effect.blendingMode = NSVisualEffectBlendingModeBehindWindow;
        effect.state = NSVisualEffectStateActive;
        [frame addSubview:effect positioned:NSWindowBelow relativeTo:content];
    }
    effect.material = (NSVisualEffectMaterial) material;
    CGFloat radius = JasperWindowCornerRadius(window);
    effect.maskImage = radius > 0 ? JasperCornerMask(radius) : nil;
}

JNIEXPORT jboolean JNICALL Java_dev_jasper_app_platform_MacGlass_setEffect(JNIEnv *env, jclass type, jobject window, jint material) {
    CALayer *layer = JasperLayer(env, window);
    if (layer == nil) return JNI_FALSE;
    NSInteger value = material;
    dispatch_async(dispatch_get_main_queue(), ^{
        NSWindow *target = JasperWindowForLayer(layer, [NSApp windows]);
        if (target != nil) JasperApplyEffect(target, value);
    });
    return JNI_TRUE;
}
