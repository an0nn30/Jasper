#import <Foundation/Foundation.h>
#import <UserNotifications/UserNotifications.h>
#include <jni.h>

// Keep the delegate alive: UNUserNotificationCenter holds it weakly. Jasper's Java notice
// policy already decides whether a hidden pane merits a banner, even while the app is active.
@interface JasperNotificationDelegate : NSObject <UNUserNotificationCenterDelegate>
@end
@implementation JasperNotificationDelegate
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
      willPresentNotification:(UNNotification *)notification
        withCompletionHandler:(void (^)(UNNotificationPresentationOptions))completion {
    completion(UNNotificationPresentationOptionBanner | UNNotificationPresentationOptionList);
}
@end

static void JasperPost(UNUserNotificationCenter *center, NSString *title, NSString *detail) {
    UNMutableNotificationContent *content = [UNMutableNotificationContent new];
    content.title = title;
    content.body = detail;
    UNNotificationRequest *request = [UNNotificationRequest
        requestWithIdentifier:NSUUID.UUID.UUIDString content:content trigger:nil];
    [center requestAuthorizationWithOptions:UNAuthorizationOptionAlert
        completionHandler:^(BOOL granted, NSError *error) {
            if (error) NSLog(@"Jasper notification authorization failed: %@", error);
            if (!granted || error) return;
            [center addNotificationRequest:request withCompletionHandler:^(NSError *deliveryError) {
                if (deliveryError) NSLog(@"Jasper notification delivery failed: %@", deliveryError);
            }];
        }];
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

JNIEXPORT void JNICALL Java_dev_jasper_app_platform_MacNotifications_post(
    JNIEnv *env, jclass type, jstring title, jstring detail) {
    @autoreleasepool {
        @try {
            // currentNotificationCenter raises an Objective-C exception outside an app bundle.
            // Never impersonate an installed app when run by Gradle, an IDE, or a test JVM.
            if (![NSBundle.mainBundle.bundleIdentifier isEqualToString:@"dev.jasper.app"]) return;
            NSString *nativeTitle = JasperString(env, title);
            if ((*env)->ExceptionCheck(env)) return;
            NSString *nativeDetail = JasperString(env, detail);
            if ((*env)->ExceptionCheck(env)) return;
            UNUserNotificationCenter *center = UNUserNotificationCenter.currentNotificationCenter;
            static JasperNotificationDelegate *delegate;
            static dispatch_once_t once;
            dispatch_once(&once, ^{ delegate = [JasperNotificationDelegate new]; });
            center.delegate = delegate;
            JasperPost(center, nativeTitle, nativeDetail);
        } @catch (NSException *exception) {
            jclass failure = (*env)->FindClass(env, "java/lang/IllegalStateException");
            if (failure) (*env)->ThrowNew(env, failure, "macOS could not submit a Jasper notification");
        }
    }
}
