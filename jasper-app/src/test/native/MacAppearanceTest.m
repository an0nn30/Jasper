#define JASPER_APPEARANCE_TEST 1
#import "../../main/native/MacAppearance.m"
#include <assert.h>
@interface AppearanceFixture : NSObject
@property(strong) NSAppearance *effectiveAppearance;
@end
@implementation AppearanceFixture
@end
int main(void) {
    @autoreleasepool {
        AppearanceFixture *fixture = [AppearanceFixture new];
        fixture.effectiveAppearance = [NSAppearance appearanceNamed:NSAppearanceNameAqua];
        __block NSInteger value = -2, count = 0;
        JasperAppearanceObservation *observer = [[JasperAppearanceObservation alloc] initWithTarget:fixture changed:^(NSInteger appearance) { value = appearance; count++; }];
        assert(value == 0 && count == 1);
        fixture.effectiveAppearance = [NSAppearance appearanceNamed:NSAppearanceNameDarkAqua];
        assert(value == 1 && count == 2);
        fixture.effectiveAppearance = [NSAppearance appearanceNamed:NSAppearanceNameAccessibilityHighContrastDarkAqua];
        assert(value == 1 && count == 3);
        fixture.effectiveAppearance = nil;
        assert(value == -1 && count == 4);
        [observer invalidate]; [observer invalidate];
        fixture.effectiveAppearance = [NSAppearance appearanceNamed:NSAppearanceNameAqua];
        assert(count == 4);
        observer = nil;
    }
    return 0;
}
