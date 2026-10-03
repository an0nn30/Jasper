#import <AppKit/AppKit.h>
#include <jni.h>
#include <stdatomic.h>
#include <stdbool.h>

// The Dock and Cmd+Tab presence of a resident Jasper, and whether macOS is logging out or shutting down. AppKit owns
// NSApp on its main thread; Java calls from the EDT and waits, so the window it shows next meets the policy applied.

@interface JasperMainThreadBlock : NSObject
- (instancetype)initWithBlock:(void (^)(void))block;
- (void)run;
@end

@implementation JasperMainThreadBlock {
    void (^_block)(void);
}
- (instancetype)initWithBlock:(void (^)(void))block {
    self = [super init];
    if (self) _block = [block copy];
    return self;
}
- (void)run {
    _block();
}
@end

// AWT's own run loop mode as well as the standard ones, so the call also runs while AppKit waits on the EDT.
static void JasperOnMainThread(void (^block)(void)) {
    if (NSThread.isMainThread) {
        block();
        return;
    }
    JasperMainThreadBlock *target = [[JasperMainThreadBlock alloc] initWithBlock:block];
    [target performSelectorOnMainThread:@selector(run) withObject:nil waitUntilDone:YES
        modes:@[NSDefaultRunLoopMode, NSModalPanelRunLoopMode, NSEventTrackingRunLoopMode, @"AWTRunLoopMode"]];
}

// Foreground: the regular policy (Dock icon, Cmd+Tab, Jasper's menu bar), and active. Background: accessory, hidden.
static void JasperApplyPolicy(NSApplication *app, BOOL foreground) {
    [app setActivationPolicy:foreground ? NSApplicationActivationPolicyRegular : NSApplicationActivationPolicyAccessory];
    if (foreground) [app activateIgnoringOtherApps:YES];
}

static atomic_bool JasperPoweringOff;
static id JasperPowerOffObserver;

// Posted before macOS asks every application to quit for a log-out, restart or shutdown. Returns whether the observer is
// installed, now or by an earlier call: a failed registration must never read as a watch that will never fire.
static BOOL JasperWatchPowerOff(NSNotificationCenter *center) {
    @synchronized(NSNotificationCenter.class) {
        if (JasperPowerOffObserver != nil) return YES;
        JasperPowerOffObserver = [center addObserverForName:NSWorkspaceWillPowerOffNotification object:nil queue:nil
            usingBlock:^(NSNotification *notification) { atomic_store(&JasperPoweringOff, true); }];
        return JasperPowerOffObserver != nil;
    }
}

// Gradle, an IDE or a test JVM has no Jasper bundle: never change another application's presence.
static BOOL JasperIsJasper(void) {
    return [NSBundle.mainBundle.bundleIdentifier isEqualToString:@"dev.jasper.app"];
}

JNIEXPORT void JNICALL Java_dev_jasper_app_platform_MacDock_setRegular(JNIEnv *env, jclass type, jboolean regular) {
    @autoreleasepool {
        if (!JasperIsJasper()) return;
        BOOL foreground = regular == JNI_TRUE;
        JasperOnMainThread(^{ JasperApplyPolicy(NSApp, foreground); });
    }
}

// True when the power-off observer is installed (now or earlier); false outside Jasper's bundle or when registering fails.
JNIEXPORT jboolean JNICALL Java_dev_jasper_app_platform_MacDock_watchPowerOff(JNIEnv *env, jclass type) {
    @autoreleasepool {
        if (!JasperIsJasper()) return JNI_FALSE;
        return JasperWatchPowerOff(NSWorkspace.sharedWorkspace.notificationCenter) ? JNI_TRUE : JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL Java_dev_jasper_app_platform_MacDock_poweringOffNow(JNIEnv *env, jclass type) {
    return atomic_load(&JasperPoweringOff) ? JNI_TRUE : JNI_FALSE;
}
