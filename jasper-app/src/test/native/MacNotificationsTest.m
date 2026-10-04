// Headless: a fake center exercises production content, identifiers, withdrawal and the click
// callback without requesting permission, registering an application, delivering a banner, or
// creating a window.
#import "../../main/native/MacNotifications.m"
#include <assert.h>

@interface TestNotificationCenter : NSObject
@property NSError *deliveryError;
@property UNAuthorizationOptions options;
@property NSMutableArray<UNNotificationRequest *> *requests;
@property NSMutableArray<NSString *> *withdrawnPending;
@property NSMutableArray<NSString *> *withdrawnDelivered;
@property NSUInteger clearedPending;
@property NSUInteger clearedDelivered;
@property (copy) void (^authorization)(BOOL, NSError *);
@end
@implementation TestNotificationCenter
- (instancetype)init {
    self = [super init];
    if (self) {
        _requests = [NSMutableArray new];
        _withdrawnPending = [NSMutableArray new];
        _withdrawnDelivered = [NSMutableArray new];
    }
    return self;
}
- (void)requestAuthorizationWithOptions:(UNAuthorizationOptions)options
                     completionHandler:(void (^)(BOOL, NSError *))completion {
    self.options = options;
    self.authorization = completion;
}
- (void)addNotificationRequest:(UNNotificationRequest *)request
        withCompletionHandler:(void (^)(NSError *))completion {
    [self.requests addObject:request];
    completion(self.deliveryError);
}
- (void)removePendingNotificationRequestsWithIdentifiers:(NSArray<NSString *> *)identifiers {
    [self.withdrawnPending addObjectsFromArray:identifiers];
}
- (void)removeDeliveredNotificationsWithIdentifiers:(NSArray<NSString *> *)identifiers {
    [self.withdrawnDelivered addObjectsFromArray:identifiers];
}
- (void)removeAllPendingNotificationRequests { self.clearedPending++; }
- (void)removeAllDeliveredNotifications { self.clearedDelivered++; }
@end

// A click as macOS describes it: which action, on which notification.
@interface TestNotification : NSObject
@property UNNotificationRequest *request;
@end
@implementation TestNotification
@end
@interface TestResponse : NSObject
@property NSString *actionIdentifier;
@property TestNotification *notification;
@end
@implementation TestResponse
@end

static NSMutableArray<NSString *> *clicks;
static void RecordClick(NSString *identifier) { [clicks addObject:identifier]; }

static TestResponse *Response(NSString *action, NSString *identifier) {
    TestNotification *notification = [TestNotification new];
    notification.request = [UNNotificationRequest requestWithIdentifier:identifier
        content:[UNMutableNotificationContent new] trigger:nil];
    TestResponse *response = [TestResponse new];
    response.actionIdentifier = action;
    response.notification = notification;
    return response;
}

static const jchar javaCharacters[] = {'b', 0, 0xD83D, 0xDE80};
static BOOL released;
static const jchar *GetCharacters(JNIEnv *env, jstring string, jboolean *copy) {
    return javaCharacters;
}
static jsize GetLength(JNIEnv *env, jstring string) { return 4; }
static void ReleaseCharacters(JNIEnv *env, jstring string, const jchar *characters) {
    assert(characters == javaCharacters);
    released = YES;
}

int main(void) {
    @autoreleasepool {
        struct JNINativeInterface_ functions = {0};
        functions.GetStringChars = GetCharacters;
        functions.GetStringLength = GetLength;
        functions.ReleaseStringChars = ReleaseCharacters;
        JNIEnv env = &functions;
        NSString *converted = JasperString(&env, (jstring)(uintptr_t)1);
        assert([converted isEqualToString:[NSString stringWithCharacters:javaCharacters length:4]]);
        assert(released);
        assert([JasperString(&env, NULL) isEqualToString:@""]);

        TestNotificationCenter *center = [TestNotificationCenter new];
        unichar characters[] = {'b', 'u', 'i', 'l', 'd', '\n', '"', 0, 0xD83D, 0xDE80};
        NSString *title = [NSString stringWithCharacters:characters length:10];
        NSString *body = @"Finished in 1m; $() \\";
        NSString *identifier = @"0f8fad5b-d9cb-469f-a165-70867728950e";
        JasperPost((UNUserNotificationCenter *)center, identifier, title, body);
        assert(center.requests.count == 0); // Delivery waits for authorization.
        assert(center.options == UNAuthorizationOptionAlert);
        center.authorization(YES, nil);
        assert(center.requests.count == 1);
        UNNotificationRequest *first = center.requests.firstObject;
        // The notification's id, so posting it again replaces its banner and withdrawing it removes it.
        assert([first.identifier isEqualToString:identifier]);
        assert([first.content.title isEqualToString:title]);
        assert([first.content.body isEqualToString:body]);
        assert(first.trigger == nil);
        assert(first.content.sound == nil);
        JasperPost((UNUserNotificationCenter *)center, identifier, title, body);
        center.authorization(YES, nil);
        assert(center.requests.count == 2);
        assert([center.requests.lastObject.identifier isEqualToString:identifier]);
        JasperPost((UNUserNotificationCenter *)center, @"denied", title, body);
        center.authorization(NO, nil);
        assert(center.requests.count == 2);
        NSError *error = [NSError errorWithDomain:@"JasperTest" code:1 userInfo:nil];
        JasperPost((UNUserNotificationCenter *)center, @"failed", title, body);
        center.authorization(YES, error);
        assert(center.requests.count == 2);
        center.deliveryError = error;
        JasperPost((UNUserNotificationCenter *)center, @"undelivered", title, body);
        center.authorization(YES, nil); // Delivery errors remain nonfatal.
        assert(center.requests.count == 3);

        JasperWithdraw((UNUserNotificationCenter *)center, identifier);
        assert([center.withdrawnPending isEqualToArray:@[identifier]]);
        assert([center.withdrawnDelivered isEqualToArray:@[identifier]]);
        JasperWithdrawAll((UNUserNotificationCenter *)center);
        assert(center.clearedPending == 1 && center.clearedDelivered == 1);

        __block BOOL presented = NO;
        JasperNotificationDelegate *delegate = [JasperNotificationDelegate new];
        [delegate userNotificationCenter:(UNUserNotificationCenter *)center
            willPresentNotification:(UNNotification *)[NSObject new]
            withCompletionHandler:^(UNNotificationPresentationOptions options) {
                assert(options == (UNNotificationPresentationOptionBanner | UNNotificationPresentationOptionList));
                presented = YES;
            }];
        assert(presented);

        clicks = [NSMutableArray new];
        JasperClickSink = RecordClick;
        __block int completed = 0;
        [delegate userNotificationCenter:(UNUserNotificationCenter *)center
            didReceiveNotificationResponse:(UNNotificationResponse *)Response(UNNotificationDefaultActionIdentifier, identifier)
            withCompletionHandler:^{ completed++; }];
        assert([clicks isEqualToArray:@[identifier]]);
        [delegate userNotificationCenter:(UNUserNotificationCenter *)center
            didReceiveNotificationResponse:(UNNotificationResponse *)Response(UNNotificationDismissActionIdentifier, identifier)
            withCompletionHandler:^{ completed++; }];
        assert(clicks.count == 1); // Dismissing a banner is not asking to see it.
        assert(completed == 2);    // macOS waits for the handler either way.

        // With no JVM loaded the bridge drops a click instead of calling into nothing.
        JasperDeliverClick(identifier);

        // A plain headless process must return before touching JNI or notification services.
        Java_dev_jasper_app_platform_MacNotifications_post(NULL, NULL, NULL, NULL, NULL);
        Java_dev_jasper_app_platform_MacNotifications_remove(NULL, NULL, NULL);
        Java_dev_jasper_app_platform_MacNotifications_removeAll(NULL, NULL);
        puts("macOS notification bridge: all headless checks passed");
    }
    return 0;
}
