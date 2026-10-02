// Blurs the desktop behind a translucent Jasper window. Terminal.app, iTerm2, Alacritty and WezTerm use the same
// private CoreGraphics call. It and JAWT_GetAWT are looked up at run time, so a missing symbol only disables blur.
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
