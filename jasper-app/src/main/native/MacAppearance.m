#import <AppKit/AppKit.h>

// This observer is deliberately on application appearance, not a themed Jasper window.
@interface JasperAppearanceObservation : NSObject
- (instancetype)initWithTarget:(NSObject *)target changed:(void (^)(NSInteger))changed;
- (void)invalidate;
@end
@implementation JasperAppearanceObservation {
    NSObject *_target;
    void (^_changed)(NSInteger);
    BOOL _watching;
}
- (instancetype)initWithTarget:(NSObject *)target changed:(void (^)(NSInteger))changed {
    self = [super init];
    if (self) {
        _target = target;
        _changed = [changed copy];
        [target addObserver:self forKeyPath:@"effectiveAppearance" options:NSKeyValueObservingOptionNew context:(__bridge void *)self];
        _watching = YES;
        [self publish];
    }
    return self;
}
- (void)publish {
    NSAppearance *appearance = [_target valueForKey:@"effectiveAppearance"];
    NSString *match = [appearance bestMatchFromAppearancesWithNames:@[NSAppearanceNameAqua, NSAppearanceNameDarkAqua]];
    _changed(match == nil ? -1 : [match isEqualToString:NSAppearanceNameDarkAqua] ? 1 : 0);
}
- (void)observeValueForKeyPath:(NSString *)path ofObject:(id)object change:(NSDictionary *)change context:(void *)context {
    if (context == (__bridge void *)self && _watching) [self publish];
    else [super observeValueForKeyPath:path ofObject:object change:change context:context];
}
- (void)invalidate {
    if (_watching) { _watching = NO; [_target removeObserver:self forKeyPath:@"effectiveAppearance" context:(__bridge void *)self]; }
    _changed = nil; _target = nil;
}
- (void)dealloc { [self invalidate]; }
@end

#ifndef JASPER_APPEARANCE_TEST
#include <jni.h>
@interface JasperAppearanceMainBlock : NSObject
@property(copy) void (^block)(void);
- (void)run;
@end
@implementation JasperAppearanceMainBlock
- (void)run { self.block(); }
@end
// JBR may wait for EDT in AWTRunLoopMode. Never wait for EDT from this main-thread block.
static void JasperAppearanceOnMain(void (^block)(void)) {
    if (NSThread.isMainThread) { block(); return; }
    JasperAppearanceMainBlock *target = [JasperAppearanceMainBlock new]; target.block = block;
    [target performSelectorOnMainThread:@selector(run) withObject:nil waitUntilDone:YES
        modes:@[NSDefaultRunLoopMode, NSModalPanelRunLoopMode, NSEventTrackingRunLoopMode, @"AWTRunLoopMode"]];
}
@interface JasperAppearanceEntry : NSObject
@property(strong) JasperAppearanceObservation *observer;
@property(assign) jobject receiver;
@end
@implementation JasperAppearanceEntry
@end
static NSMutableDictionary<NSNumber *, JasperAppearanceEntry *> *JasperAppearanceEntries;
static jlong JasperAppearanceNext = 0;
JNIEXPORT jlong JNICALL Java_dev_jasper_app_platform_MacAppearance_watch(JNIEnv *env, jclass type, jobject receiver) {
    @autoreleasepool {
        JavaVM *vm = NULL; (*env)->GetJavaVM(env, &vm);
        jclass receiverClass = (*env)->GetObjectClass(env, receiver);
        jmethodID accept = (*env)->GetMethodID(env, receiverClass, "accept", "(I)V");
        (*env)->DeleteLocalRef(env, receiverClass);
        if (accept == NULL) return 0;
        jobject retained = (*env)->NewGlobalRef(env, receiver);
        if (retained == NULL) return 0;
        __block jlong handle = 0;
        JasperAppearanceOnMain(^{
            if (NSApp == nil) return;
            @try {
                JasperAppearanceEntry *entry = [JasperAppearanceEntry new]; entry.receiver = retained;
                entry.observer = [[JasperAppearanceObservation alloc] initWithTarget:NSApp changed:^(NSInteger preference) {
                    JNIEnv *callback = NULL; BOOL attached = NO;
                    if ((*vm)->GetEnv(vm, (void **)&callback, JNI_VERSION_1_8) != JNI_OK) {
                        if ((*vm)->AttachCurrentThreadAsDaemon(vm, (void **)&callback, NULL) != JNI_OK) return;
                        attached = YES;
                    }
                    (*callback)->CallVoidMethod(callback, retained, accept, (jint)preference);
                    // A failing Java callback cannot leave an exception on AppKit's thread.
                    if ((*callback)->ExceptionCheck(callback)) { (*callback)->ExceptionDescribe(callback); (*callback)->ExceptionClear(callback); }
                    if (attached) (*vm)->DetachCurrentThread(vm);
                }];
                if (JasperAppearanceEntries == nil) JasperAppearanceEntries = [NSMutableDictionary new];
                handle = ++JasperAppearanceNext;
                JasperAppearanceEntries[@(handle)] = entry;
            } @catch (NSException *exception) { NSLog(@"Jasper appearance observer: %@", exception.reason); }
        });
        if (handle == 0) (*env)->DeleteGlobalRef(env, retained);
        return handle;
    }
}
JNIEXPORT void JNICALL Java_dev_jasper_app_platform_MacAppearance_unwatch(JNIEnv *env, jclass type, jlong handle) {
    @autoreleasepool {
        __block jobject receiver = NULL;
        JasperAppearanceOnMain(^{
            JasperAppearanceEntry *entry = JasperAppearanceEntries[@(handle)];
            [entry.observer invalidate]; receiver = entry.receiver;
            [JasperAppearanceEntries removeObjectForKey:@(handle)];
        });
        if (receiver != NULL) (*env)->DeleteGlobalRef(env, receiver);
    }
}
#endif
