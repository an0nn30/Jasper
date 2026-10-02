// Headless: checks finding a window by the layer JAWT reports, with stand-in windows, and the effect view's mask,
// appearance and lifetime (added below the content view, refreshed on full-screen changes, removed with its observer).
// Creates no window. Stand-in windows hold real NSViews, so the selectors MacGlass.m sends to id keep
// AppKit's own signatures.
#import "../../main/native/MacGlass.m"
#include <assert.h>

@interface TestWindow : NSObject
@property NSView *contentView;
@property NSWindowStyleMask styleMask;
@end
@implementation TestWindow
@end

static TestWindow *WindowWithLayer(CALayer *layer) {
    TestWindow *window = [TestWindow new];
    NSView *view = [[NSView alloc] initWithFrame:NSZeroRect];
    view.wantsLayer = YES;
    view.layer = layer;
    window.contentView = view;
    return window;
}

int main(void) {
    @autoreleasepool {
        CALayer *content = [CALayer layer];
        CALayer *metal = [CALayer layer];
        [content addSublayer:metal];
        // A custom title bar nests the frame's layer deeper inside the content view's.
        CALayer *wrapper = [CALayer layer];
        CALayer *nested = [CALayer layer];
        [content addSublayer:wrapper];
        [wrapper addSublayer:nested];
        TestWindow *other = WindowWithLayer([CALayer layer]);
        TestWindow *jasper = WindowWithLayer(content);
        NSArray *windows = @[other, jasper];

        assert(JasperWindowForLayer(content, windows) == jasper);
        assert(JasperWindowForLayer(metal, windows) == jasper);
        assert(JasperWindowForLayer(nested, windows) == jasper);
        CALayer *detached = [CALayer layer];
        CALayer *detachedChild = [CALayer layer];
        [detached addSublayer:detachedChild];
        assert(JasperWindowForLayer(detachedChild, windows) == nil);
        assert(JasperWindowForLayer([CALayer layer], windows) == nil);
        assert(JasperWindowForLayer(nil, windows) == nil);
        assert(JasperWindowForLayer(metal, @[]) == nil);

        // The effect view's mask keeps the window's corners and stretches only between them.
        NSImage *mask = JasperCornerMask(16);
        assert(mask.size.width == 33 && mask.size.height == 33);
        assert(mask.capInsets.top == 16 && mask.capInsets.left == 16 && mask.capInsets.bottom == 16 && mask.capInsets.right == 16);
        assert(mask.resizingMode == NSImageResizingModeStretch);
        // Full screen reports a radius of 0: square corners, no mask.
        assert(JasperMaskForRadius(0) == nil);
        assert(JasperMaskForRadius(-1) == nil);
        assert(JasperMaskForRadius(16).size.width == 33);

        // The material's look follows the workspace theme, not the system appearance.
        assert([JasperEffectAppearance(JNI_TRUE) isEqualToString:NSAppearanceNameAqua]);
        assert([JasperEffectAppearance(JNI_FALSE) isEqualToString:NSAppearanceNameDarkAqua]);

        // The effect view goes below the content view, inside its superview, and follows full screen.
        TestWindow *stand = [TestWindow new];
        NSView *frameView = [[NSView alloc] initWithFrame:NSMakeRect(0, 0, 200, 100)];
        NSView *contentView = [[NSView alloc] initWithFrame:frameView.bounds];
        [frameView addSubview:contentView];
        stand.contentView = contentView;
        NSWindow *window = (NSWindow *) (id) stand;
        JasperApplyEffect(window, 13, JNI_FALSE);
        assert(frameView.subviews.count == 2);
        NSVisualEffectView *effect = (NSVisualEffectView *) frameView.subviews.firstObject;
        assert([effect.identifier isEqualToString:JasperEffectIdentifier]);
        assert(frameView.subviews.lastObject == contentView);
        assert(effect.material == NSVisualEffectMaterialHUDWindow);
        assert([effect.appearance.name isEqualToString:NSAppearanceNameDarkAqua]);
        assert(effect.maskImage != nil);
        JasperCornerObserver *observer = objc_getAssociatedObject(effect, JasperObserverKey);
        assert(observer != nil && observer.effect == effect && observer.window == window);

        // Applying again reuses the view and switches material and appearance.
        JasperApplyEffect(window, 21, JNI_TRUE);
        assert(frameView.subviews.count == 2 && frameView.subviews.firstObject == effect);
        assert(effect.material == NSVisualEffectMaterialUnderWindowBackground);
        assert([effect.appearance.name isEqualToString:NSAppearanceNameAqua]);
        assert(objc_getAssociatedObject(effect, JasperObserverKey) == observer);

        NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
        stand.styleMask = NSWindowStyleMaskFullScreen;
        [center postNotificationName:NSWindowDidEnterFullScreenNotification object:stand];
        assert(effect.maskImage == nil);
        stand.styleMask = 0;
        [center postNotificationName:NSWindowDidExitFullScreenNotification object:stand];
        assert(effect.maskImage != nil);
        effect.maskImage = nil;
        [center postNotificationName:NSWindowDidChangeScreenNotification object:stand];
        assert(effect.maskImage != nil);

        // Removing the view drops its observer: later notifications no longer touch it.
        JasperApplyEffect(window, -1, JNI_FALSE);
        assert(frameView.subviews.count == 1 && frameView.subviews.firstObject == contentView);
        assert(effect.superview == nil);
        assert(objc_getAssociatedObject(effect, JasperObserverKey) == nil);
        effect.maskImage = nil;
        stand.styleMask = 0;
        [center postNotificationName:NSWindowDidExitFullScreenNotification object:stand];
        assert(effect.maskImage == nil);
    }
    return 0;
}
