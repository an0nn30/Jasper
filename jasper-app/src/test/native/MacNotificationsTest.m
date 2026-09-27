// Headless: a fake center exercises production content and callbacks without requesting
// permission, registering an application, delivering a banner, or creating a window.
#import "../../main/native/MacNotifications.m"
#include <assert.h>

@interface TestNotificationCenter : NSObject
@property NSError *deliveryError;
@property UNAuthorizationOptions options;
@property NSMutableArray<UNNotificationRequest *> *requests;
@property (copy) void (^authorization)(BOOL, NSError *);
@end
@implementation TestNotificationCenter
- (instancetype)init {
    self = [super init];
    if (self) _requests = [NSMutableArray new];
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
@end

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
        JasperPost((UNUserNotificationCenter *)center, title, body);
        assert(center.requests.count == 0); // Delivery waits for authorization.
        assert(center.options == UNAuthorizationOptionAlert);
        center.authorization(YES, nil);
        assert(center.requests.count == 1);
        UNNotificationRequest *first = center.requests.firstObject;
        assert([first.content.title isEqualToString:title]);
        assert([first.content.body isEqualToString:body]);
        assert(first.trigger == nil);
        assert(first.content.sound == nil);
        JasperPost((UNUserNotificationCenter *)center, title, body);
        center.authorization(YES, nil);
        assert(center.requests.count == 2);
        assert(![first.identifier isEqualToString:center.requests.lastObject.identifier]);
        JasperPost((UNUserNotificationCenter *)center, title, body);
        center.authorization(NO, nil);
        assert(center.requests.count == 2);
        NSError *error = [NSError errorWithDomain:@"JasperTest" code:1 userInfo:nil];
        JasperPost((UNUserNotificationCenter *)center, title, body);
        center.authorization(YES, error);
        assert(center.requests.count == 2);
        center.deliveryError = error;
        JasperPost((UNUserNotificationCenter *)center, title, body);
        center.authorization(YES, nil); // Delivery errors remain nonfatal.
        assert(center.requests.count == 3);
        __block BOOL presented = NO;
        [[JasperNotificationDelegate new] userNotificationCenter:(UNUserNotificationCenter *)center
            willPresentNotification:(UNNotification *)[NSObject new]
            withCompletionHandler:^(UNNotificationPresentationOptions options) {
                assert(options == (UNNotificationPresentationOptionBanner | UNNotificationPresentationOptionList));
                presented = YES;
            }];
        assert(presented);
        // A plain headless process must return before touching JNI or notification services.
        Java_dev_jasper_app_platform_MacNotifications_post(NULL, NULL, NULL, NULL);
        puts("macOS notification bridge: all headless checks passed");
    }
    return 0;
}
