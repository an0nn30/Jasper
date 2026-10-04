#import <Foundation/Foundation.h>
#import <UserNotifications/UserNotifications.h>
#include <jni.h>
#include <stdlib.h>

// The JVM and the Java method a click calls, cached when the library loads.
static JavaVM *JasperVM;
static jclass JasperBridge;
static jmethodID JasperClicked;

// Hands a clicked notification's identifier to Java, on whatever thread macOS calls the delegate.
static void JasperDeliverClick(NSString *identifier) {
    if (!JasperVM || !JasperBridge || !JasperClicked || !identifier) return;
    JNIEnv *env = NULL;
    jint state = (*JasperVM)->GetEnv(JasperVM, (void **)&env, JNI_VERSION_1_8);
    if (state == JNI_EDETACHED) {
        // A thread macOS owns: attached as a daemon, it never holds up the JVM's exit.
        if ((*JasperVM)->AttachCurrentThreadAsDaemon(JasperVM, (void **)&env, NULL) != JNI_OK) return;
    } else if (state != JNI_OK) {
        return;
    }
    NSUInteger length = identifier.length;
    unichar *characters = malloc(sizeof(unichar) * (length == 0 ? 1 : length));
    if (!characters) return;
    [identifier getCharacters:characters range:NSMakeRange(0, length)];
    jstring value = (*env)->NewString(env, characters, (jsize)length);
    free(characters);
    if (value) {
        (*env)->CallStaticVoidMethod(env, JasperBridge, JasperClicked, value);
        (*env)->DeleteLocalRef(env, value);
    }
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
}

// Where a click goes; the headless test points it at a recorder.
static void (*JasperClickSink)(NSString *) = JasperDeliverClick;

// Keep the delegate alive: UNUserNotificationCenter holds it weakly. Jasper's Java delivery rule already decides
// which notifications reach the operating system, so a banner is presented even while the app is active.
@interface JasperNotificationDelegate : NSObject <UNUserNotificationCenterDelegate>
@end
@implementation JasperNotificationDelegate
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
      willPresentNotification:(UNNotification *)notification
        withCompletionHandler:(void (^)(UNNotificationPresentationOptions))completion {
    completion(UNNotificationPresentationOptionBanner | UNNotificationPresentationOptionList);
}
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
didReceiveNotificationResponse:(UNNotificationResponse *)response
         withCompletionHandler:(void (^)(void))completion {
    // Only a click on the banner itself: dismissing it is not asking to see it.
    if ([response.actionIdentifier isEqualToString:UNNotificationDefaultActionIdentifier])
        JasperClickSink(response.notification.request.identifier);
    completion();
}
@end

static void JasperPost(UNUserNotificationCenter *center, NSString *identifier, NSString *title, NSString *detail) {
    UNMutableNotificationContent *content = [UNMutableNotificationContent new];
    content.title = title;
    content.body = detail;
    // The notification's id: posting it again replaces its banner, and withdrawing it removes the banner.
    UNNotificationRequest *request = [UNNotificationRequest
        requestWithIdentifier:identifier content:content trigger:nil];
    [center requestAuthorizationWithOptions:UNAuthorizationOptionAlert
        completionHandler:^(BOOL granted, NSError *error) {
            if (error) NSLog(@"Jasper notification authorization failed: %@", error);
            if (!granted || error) return;
            [center addNotificationRequest:request withCompletionHandler:^(NSError *deliveryError) {
                if (deliveryError) NSLog(@"Jasper notification delivery failed: %@", deliveryError);
            }];
        }];
}

static void JasperWithdraw(UNUserNotificationCenter *center, NSString *identifier) {
    NSArray<NSString *> *identifiers = @[identifier];
    [center removePendingNotificationRequestsWithIdentifiers:identifiers];
    [center removeDeliveredNotificationsWithIdentifiers:identifiers];
}

static void JasperWithdrawAll(UNUserNotificationCenter *center) {
    [center removeAllPendingNotificationRequests];
    [center removeAllDeliveredNotifications];
}

static NSString *JasperString(JNIEnv *env, jstring value) {
    if (!value) return @"";
    const jchar *characters = (*env)->GetStringChars(env, value, NULL);
    if (!characters) return nil; // The JVM has already raised OutOfMemoryError.
    NSString *result = [[NSString alloc] initWithCharacters:characters
        length:(NSUInteger)(*env)->GetStringLength(env, value)];
    (*env)->ReleaseStringChars(env, value, characters);
    return result;
}

// Jasper's notification center, with the delegate that routes clicks; nil outside Jasper.app.
static UNUserNotificationCenter *JasperCenter(void) {
    // currentNotificationCenter raises an Objective-C exception outside an app bundle.
    // Never impersonate an installed app when run by Gradle, an IDE, or a test JVM.
    if (![NSBundle.mainBundle.bundleIdentifier isEqualToString:@"dev.jasper.app"]) return nil;
    UNUserNotificationCenter *center = UNUserNotificationCenter.currentNotificationCenter;
    static JasperNotificationDelegate *delegate;
    static dispatch_once_t once;
    dispatch_once(&once, ^{ delegate = [JasperNotificationDelegate new]; });
    center.delegate = delegate;
    return center;
}

static void JasperThrow(JNIEnv *env, const char *message) {
    jclass failure = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (failure) (*env)->ThrowNew(env, failure, message);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JasperVM = vm;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_8) != JNI_OK) return JNI_VERSION_1_8;
    jclass bridge = (*env)->FindClass(env, "dev/jasper/app/platform/MacNotifications");
    if (!bridge) {
        (*env)->ExceptionClear(env);
        return JNI_VERSION_1_8;
    }
    JasperBridge = (*env)->NewGlobalRef(env, bridge);
    JasperClicked = (*env)->GetStaticMethodID(env, bridge, "clicked", "(Ljava/lang/String;)V");
    if (!JasperClicked) (*env)->ExceptionClear(env);
    (*env)->DeleteLocalRef(env, bridge);
    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL Java_dev_jasper_app_platform_MacNotifications_post(
    JNIEnv *env, jclass type, jstring identifier, jstring title, jstring detail) {
    @autoreleasepool {
        @try {
            UNUserNotificationCenter *center = JasperCenter();
            if (!center) return;
            NSString *nativeIdentifier = JasperString(env, identifier);
            if ((*env)->ExceptionCheck(env)) return;
            NSString *nativeTitle = JasperString(env, title);
            if ((*env)->ExceptionCheck(env)) return;
            NSString *nativeDetail = JasperString(env, detail);
            if ((*env)->ExceptionCheck(env)) return;
            JasperPost(center, nativeIdentifier, nativeTitle, nativeDetail);
        } @catch (NSException *exception) {
            JasperThrow(env, "macOS could not submit a Jasper notification");
        }
    }
}

JNIEXPORT void JNICALL Java_dev_jasper_app_platform_MacNotifications_remove(
    JNIEnv *env, jclass type, jstring identifier) {
    @autoreleasepool {
        @try {
            UNUserNotificationCenter *center = JasperCenter();
            if (!center) return;
            NSString *nativeIdentifier = JasperString(env, identifier);
            if ((*env)->ExceptionCheck(env)) return;
            JasperWithdraw(center, nativeIdentifier);
        } @catch (NSException *exception) {
            JasperThrow(env, "macOS could not withdraw a Jasper notification");
        }
    }
}

JNIEXPORT void JNICALL Java_dev_jasper_app_platform_MacNotifications_removeAll(JNIEnv *env, jclass type) {
    @autoreleasepool {
        @try {
            UNUserNotificationCenter *center = JasperCenter();
            if (!center) return;
            JasperWithdrawAll(center);
        } @catch (NSException *exception) {
            JasperThrow(env, "macOS could not withdraw Jasper's notifications");
        }
    }
}
