#import <AppKit/AppKit.h>
#include <jni.h>

// Which application macOS opens a URL scheme's links with, as Launch Services answers. Read-only, so any thread and any
// process may ask, Jasper's bundle or not; nothing here touches NSApp.

// The bundle identifier of the application that opens <scheme>://example.com; nil when none does, or when that
// application's bundle names no identifier.
static NSString *JasperDefaultBrowserBundleId(NSWorkspace *workspace, NSString *scheme) {
    if (scheme.length == 0) return nil;
    NSURL *probe = [NSURL URLWithString:[scheme stringByAppendingString:@"://example.com"]];
    if (probe == nil) return nil;
    NSURL *application = [workspace URLForApplicationToOpenURL:probe];
    if (application == nil) return nil;
    return [NSBundle bundleWithURL:application].bundleIdentifier;
}

JNIEXPORT jstring JNICALL Java_dev_jasper_app_platform_MacLaunchServices_defaultBrowserBundleId(JNIEnv *env, jclass type,
                                                                                                jstring scheme) {
    @autoreleasepool {
        if (scheme == NULL) return NULL;
        const char *chars = (*env)->GetStringUTFChars(env, scheme, NULL);
        if (chars == NULL) return NULL; // An OutOfMemoryError is pending.
        NSString *name = [NSString stringWithUTF8String:chars];
        (*env)->ReleaseStringUTFChars(env, scheme, chars);
        NSString *identifier = JasperDefaultBrowserBundleId(NSWorkspace.sharedWorkspace, name);
        return identifier == nil ? NULL : (*env)->NewStringUTF(env, identifier.UTF8String);
    }
}
