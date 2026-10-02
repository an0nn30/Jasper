// Headless: checks the radius bounds and finding a window by the layer JAWT reports, with stand-in windows.
// Creates no window and never calls the private blur function. Stand-in windows hold real NSViews, so the
// selectors MacGlass.m sends to id keep AppKit's own signatures.
#import "../../main/native/MacGlass.m"
#include <assert.h>

@interface TestWindow : NSObject
@property NSView *contentView;
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
        assert(JasperClampRadius(-5) == 0);
        assert(JasperClampRadius(20) == 20);
        assert(JasperClampRadius(500) == 64);

        CALayer *content = [CALayer layer];
        CALayer *metal = [CALayer layer];
        [content addSublayer:metal];
        TestWindow *other = WindowWithLayer([CALayer layer]);
        TestWindow *jasper = WindowWithLayer(content);
        NSArray *windows = @[other, jasper];

        assert(JasperWindowForLayer(content, windows) == jasper);
        assert(JasperWindowForLayer(metal, windows) == jasper);
        assert(JasperWindowForLayer([CALayer layer], windows) == nil);
        assert(JasperWindowForLayer(nil, windows) == nil);
        assert(JasperWindowForLayer(metal, @[]) == nil);
    }
    return 0;
}
