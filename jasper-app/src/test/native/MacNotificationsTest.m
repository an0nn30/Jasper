// Headless: a fake center exercises production content, identifiers, withdrawal and the click
// callback without requesting permission, registering an application, delivering a banner, or
// creating a window; a fake JVM exercises binding the callback and handing a click to Java.
#import "../../main/native/MacNotifications.m"
#include <assert.h>
#include <stdarg.h>
#include <string.h>

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

// A JVM and a JNIEnv just large enough for bind() and a click: they record what the bridge asks of them.
static JavaVM fakeVm;
static JNIEnv fakeEnv;
static BOOL methodExists;
static int resolved, cleared, attached, deleted;
static jclass clickedClass;
static jmethodID clickedMethod;
static NSString *clickedWith;
static jclass const bridgeClass = (jclass)(uintptr_t)0x10;
static jobject const bridgeGlobal = (jobject)(uintptr_t)0x20;
static jmethodID const clickedId = (jmethodID)(uintptr_t)0x30;
static jstring const javaIdentifier = (jstring)(uintptr_t)0x40;

static jint JNICALL FakeGetJavaVM(JNIEnv *env, JavaVM **vm) {
    *vm = &fakeVm;
    return JNI_OK;
}
static jmethodID JNICALL FakeGetStaticMethodID(JNIEnv *env, jclass type, const char *name, const char *signature) {
    resolved++;
    assert(type == bridgeClass);
    assert(strcmp(name, "clicked") == 0 && strcmp(signature, "(Ljava/lang/String;)V") == 0);
    return methodExists ? clickedId : NULL; // A missing method leaves NoSuchMethodError pending.
}
static jobject JNICALL FakeNewGlobalRef(JNIEnv *env, jobject object) {
    assert(object == bridgeClass);
    return bridgeGlobal;
}
static void JNICALL FakeExceptionClear(JNIEnv *env) { cleared++; }
static jboolean JNICALL FakeExceptionCheck(JNIEnv *env) { return JNI_FALSE; }
static jstring JNICALL FakeNewString(JNIEnv *env, const jchar *characters, jsize length) {
    clickedWith = [NSString stringWithCharacters:characters length:(NSUInteger)length];
    return javaIdentifier;
}
static void JNICALL FakeCallStaticVoidMethod(JNIEnv *env, jclass type, jmethodID method, ...) {
    va_list arguments;
    va_start(arguments, method);
    assert(va_arg(arguments, jstring) == javaIdentifier);
    va_end(arguments);
    clickedClass = type;
    clickedMethod = method;
}
static void JNICALL FakeDeleteLocalRef(JNIEnv *env, jobject object) {
    assert(object == javaIdentifier);
    deleted++;
}
static jint JNICALL FakeGetEnv(JavaVM *vm, void **env, jint version) {
    assert(vm == &fakeVm && version == JNI_VERSION_1_8);
    return JNI_EDETACHED; // The delegate runs on a thread macOS owns.
}
static jint JNICALL FakeAttachAsDaemon(JavaVM *vm, void **env, void *arguments) {
    attached++;
    *env = &fakeEnv;
    return JNI_OK;
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

        struct JNINativeInterface_ jni = {0};
        jni.GetJavaVM = FakeGetJavaVM;
        jni.GetStaticMethodID = FakeGetStaticMethodID;
        jni.NewGlobalRef = FakeNewGlobalRef;
        jni.ExceptionClear = FakeExceptionClear;
        jni.ExceptionCheck = FakeExceptionCheck;
        jni.NewString = FakeNewString;
        jni.CallStaticVoidMethod = FakeCallStaticVoidMethod;
        jni.DeleteLocalRef = FakeDeleteLocalRef;
        fakeEnv = &jni;
        struct JNIInvokeInterface_ invocation = {0};
        invocation.GetEnv = FakeGetEnv;
        invocation.AttachCurrentThreadAsDaemon = FakeAttachAsDaemon;
        fakeVm = &invocation;

        // A Java class without clicked(String): nothing binds, the pending error is cleared, and a click is dropped.
        methodExists = NO;
        assert(Java_dev_jasper_app_platform_MacNotifications_bind(&fakeEnv, bridgeClass) == JNI_FALSE);
        assert(cleared == 1);
        assert(!JasperVM && !JasperBridge && !JasperClicked);
        JasperDeliverClick(identifier);
        assert(attached == 0);

        // The real class binds once; a click then reaches clicked(String) with the identifier, on a daemon thread.
        methodExists = YES;
        assert(Java_dev_jasper_app_platform_MacNotifications_bind(&fakeEnv, bridgeClass) == JNI_TRUE);
        assert(JasperVM == &fakeVm && JasperBridge == bridgeGlobal && JasperClicked == clickedId);
        assert(Java_dev_jasper_app_platform_MacNotifications_bind(&fakeEnv, bridgeClass) == JNI_TRUE);
        assert(resolved == 2); // Bound already: the second call resolves nothing.
        JasperDeliverClick(identifier);
        assert(attached == 1);
        assert(clickedClass == bridgeGlobal && clickedMethod == clickedId);
        assert([clickedWith isEqualToString:identifier]);
        assert(deleted == 1);
        JasperVM = NULL;
        JasperBridge = NULL;
        JasperClicked = NULL;

        // A plain headless process must return before touching JNI or notification services.
        Java_dev_jasper_app_platform_MacNotifications_post(NULL, NULL, NULL, NULL, NULL);
        Java_dev_jasper_app_platform_MacNotifications_remove(NULL, NULL, NULL);
        Java_dev_jasper_app_platform_MacNotifications_removeAll(NULL, NULL);
        puts("macOS notification bridge: all headless checks passed");
    }
    return 0;
}
