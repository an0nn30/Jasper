// Headless: a fake application object records the policy calls and a private notification center stands in for
// NSWorkspace's; nothing registers with the window server or changes any real application's presence.
#import "../../main/native/MacDock.m"
#include <assert.h>

@interface TestApplication : NSObject
@property NSApplicationActivationPolicy policy;
@property NSInteger activations;
@end
@implementation TestApplication
- (BOOL)setActivationPolicy:(NSApplicationActivationPolicy)policy {
    self.policy = policy;
    return YES;
}
- (void)activateIgnoringOtherApps:(BOOL)flag {
    self.activations += 1;
}
@end

int main(void) {
    @autoreleasepool {
        TestApplication *app = [TestApplication new];
        app.policy = NSApplicationActivationPolicyProhibited;
        JasperApplyPolicy((NSApplication *)app, NO);
        assert(app.policy == NSApplicationActivationPolicyAccessory);
        assert(app.activations == 0);
        JasperApplyPolicy((NSApplication *)app, YES);
        assert(app.policy == NSApplicationActivationPolicyRegular);
        assert(app.activations == 1);
        __block BOOL ran = NO;
        JasperOnMainThread(^{ ran = YES; });
        assert(ran); // The main thread runs it inline.
        NSNotificationCenter *center = [NSNotificationCenter new];
        assert(Java_dev_jasper_app_platform_MacDock_poweringOffNow(NULL, NULL) == JNI_FALSE);
        JasperWatchPowerOff(center);
        [center postNotificationName:NSWorkspaceDidWakeNotification object:nil];
        assert(Java_dev_jasper_app_platform_MacDock_poweringOffNow(NULL, NULL) == JNI_FALSE);
        [center postNotificationName:NSWorkspaceWillPowerOffNotification object:nil];
        assert(Java_dev_jasper_app_platform_MacDock_poweringOffNow(NULL, NULL) == JNI_TRUE);
        // A plain process has no Jasper bundle: both entry points return before touching NSApp or NSWorkspace.
        Java_dev_jasper_app_platform_MacDock_setRegular(NULL, NULL, JNI_TRUE);
        Java_dev_jasper_app_platform_MacDock_watchPowerOff(NULL, NULL);
        assert(NSApp == nil);
        puts("macOS Dock bridge: all headless checks passed");
    }
    return 0;
}
