// Blurs the desktop behind a translucent Jasper window with a public NSVisualEffectView placed behind the content view:
// one of Apple's fixed-strength materials, in the appearance the workspace theme asks for, masked to the window's
// rounded corners (square in full screen; the mask follows full-screen and screen changes). Only public AppKit is
// used. JAWT_GetAWT is looked up at run time, so a missing symbol only disables blur.
#import <AppKit/AppKit.h>
#import <QuartzCore/QuartzCore.h>
#import <objc/runtime.h>
#include <dlfcn.h>
#include <jni.h>
#include <jawt.h>
#include <jawt_md.h>

typedef jboolean (JNICALL *JasperGetAWT)(JNIEnv *, JAWT *);

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

/** The identifier of the effect view setEffect adds behind the content view. */
static NSString *const JasperEffectIdentifier = @"dev.jasper.glass.effect";
/** The key under which the effect view keeps its notification observer tokens. */
static const void *JasperObserversKey = &JasperObserversKey;

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

/** The effect view's mask for a window whose corners have {@code radius} points; nil (square corners) for none. */
static NSImage *JasperMaskForRadius(CGFloat radius) { return radius > 0 ? JasperCornerMask(radius) : nil; }

/** The appearance the effect view's material is drawn in: the workspace theme's, not the system's. */
static NSAppearanceName JasperEffectAppearance(jboolean light) { return light ? NSAppearanceNameAqua : NSAppearanceNameDarkAqua; }

/** Masks {@code effect} to {@code window}'s current corners. */
static void JasperRefreshMask(NSVisualEffectView *effect, NSWindow *window) {
    effect.maskImage = JasperMaskForRadius(JasperWindowCornerRadius(window));
}

/**
 * Recomputes the effect view's mask whenever its window enters or leaves full screen or changes screen. Both are
 * captured weakly; the tokens stay on the effect view until JasperApplyEffect removes it.
 */
static void JasperObserveCorners(NSVisualEffectView *effect, NSWindow *window) {
    __weak NSVisualEffectView *weakEffect = effect;
    __weak NSWindow *weakWindow = window;
    NSMutableArray *tokens = [NSMutableArray array];
    NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
    for (NSNotificationName name in @[NSWindowDidEnterFullScreenNotification, NSWindowDidExitFullScreenNotification,
                                      NSWindowDidChangeScreenNotification]) {
        [tokens addObject:[center addObserverForName:name object:window queue:[NSOperationQueue mainQueue]
                                          usingBlock:^(NSNotification *note) {
            NSVisualEffectView *strongEffect = weakEffect;
            NSWindow *strongWindow = weakWindow;
            if (strongEffect != nil && strongWindow != nil) JasperRefreshMask(strongEffect, strongWindow);
        }]];
    }
    objc_setAssociatedObject(effect, JasperObserversKey, tokens, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

/** Removes the effect view and the observers JasperObserveCorners registered for it. */
static void JasperRemoveEffect(NSVisualEffectView *effect) {
    if (effect == nil) return;
    NSArray *tokens = objc_getAssociatedObject(effect, JasperObserversKey);
    for (id token in tokens) [[NSNotificationCenter defaultCenter] removeObserver:token];
    objc_setAssociatedObject(effect, JasperObserversKey, nil, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    [effect removeFromSuperview];
}

/**
 * Shows (material >= 0) or removes (material < 0) the effect view behind {@code window}'s content view, drawn in the
 * light or the dark appearance.
 */
static void JasperApplyEffect(NSWindow *window, NSInteger material, jboolean light) {
    NSView *content = window.contentView, *frame = content.superview;
    if (frame == nil) return;
    NSVisualEffectView *effect = nil;
    for (NSView *view in frame.subviews)
        if ([view.identifier isEqualToString:JasperEffectIdentifier]) effect = (NSVisualEffectView *) view;
    if (material < 0) { JasperRemoveEffect(effect); return; }
    if (effect == nil) {
        effect = [[NSVisualEffectView alloc] initWithFrame:frame.bounds];
        effect.identifier = JasperEffectIdentifier;
        effect.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
        effect.blendingMode = NSVisualEffectBlendingModeBehindWindow;
        effect.state = NSVisualEffectStateActive;
        [frame addSubview:effect positioned:NSWindowBelow relativeTo:content];
        JasperObserveCorners(effect, window);
    }
    effect.material = (NSVisualEffectMaterial) material;
    effect.appearance = [NSAppearance appearanceNamed:JasperEffectAppearance(light)];
    JasperRefreshMask(effect, window);
}

JNIEXPORT jboolean JNICALL Java_dev_jasper_app_platform_MacGlass_setEffect(JNIEnv *env, jclass type, jobject window,
                                                                          jint material, jboolean light) {
    CALayer *layer = JasperLayer(env, window);
    if (layer == nil) return JNI_FALSE;
    NSInteger value = material;
    jboolean lightAppearance = light;
    // AppKit belongs to the main thread. Never wait for it here: it may be waiting for the EDT.
    dispatch_async(dispatch_get_main_queue(), ^{
        NSWindow *target = JasperWindowForLayer(layer, [NSApp windows]);
        if (target != nil) JasperApplyEffect(target, value, lightAppearance);
    });
    return JNI_TRUE;
}
