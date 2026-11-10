#import <AppKit/AppKit.h>
#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>
#include <jni.h>

// Which application macOS opens a URL scheme's links and a content type's files with, as Launch Services answers, and
// the means to change a content type's default. Any thread and any process may ask, Jasper's bundle or not; nothing here
// touches NSApp. Each function takes the workspace so a test can stand a fake in for it.

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

// The bundle identifier and display name of an application; an application whose bundle names no identifier is named
// by its path.
static NSArray<NSString *> *JasperApplicationPair(NSURL *application) {
    NSBundle *bundle = [NSBundle bundleWithURL:application];
    NSString *identifier = bundle.bundleIdentifier.length > 0 ? bundle.bundleIdentifier : application.path;
    NSString *name = [bundle objectForInfoDictionaryKey:@"CFBundleDisplayName"];
    if (![name isKindOfClass:NSString.class] || name.length == 0) name = [bundle objectForInfoDictionaryKey:@"CFBundleName"];
    if (![name isKindOfClass:NSString.class] || name.length == 0) name = application.lastPathComponent.stringByDeletingPathExtension;
    return @[identifier ?: @"", name ?: identifier ?: @""];
}

// The declared content type with this identifier; nil for an empty or undeclared one.
static UTType *JasperContentType(NSString *uti) {
    return uti.length == 0 ? nil : [UTType typeWithIdentifier:uti];
}

// @[bundle identifier, display name] of the application that opens files of the content type; nil when none does.
static NSArray<NSString *> *JasperContentTypeHandler(NSWorkspace *workspace, NSString *uti) {
    UTType *type = JasperContentType(uti);
    if (type == nil) return nil;
    NSURL *application = nil;
    if (@available(macOS 12.0, *)) {
        application = [workspace URLForApplicationToOpenContentType:type];
    } else {
        NSString *identifier = (__bridge_transfer NSString *)LSCopyDefaultRoleHandlerForContentType(
            (__bridge CFStringRef)uti, kLSRolesAll);
        if (identifier.length == 0) return nil;
        application = [workspace URLForApplicationWithBundleIdentifier:identifier];
        if (application == nil) return @[identifier, identifier];
    }
    return application == nil ? nil : JasperApplicationPair(application);
}

// Makes the installed application with this bundle identifier the default for the content type, waiting at most the
// given seconds for Launch Services. Nil on success; otherwise the system's message, or "timed out".
static NSString *JasperSetContentTypeHandler(NSWorkspace *workspace, NSString *uti, NSString *bundleId, double seconds) {
    UTType *type = JasperContentType(uti);
    if (type == nil) return [NSString stringWithFormat:@"macOS has no content type %@", uti ?: @""];
    if (bundleId.length == 0) return @"no application was named";
    NSURL *application = [workspace URLForApplicationWithBundleIdentifier:bundleId];
    if (application == nil) return [NSString stringWithFormat:@"no application %@ is installed", bundleId];
    if (@available(macOS 12.0, *)) {
        dispatch_semaphore_t done = dispatch_semaphore_create(0);
        // Read only after the semaphore, which orders the handler's write before the read; a late answer writes into
        // storage the block keeps alive and nobody reads.
        __block NSString *failure = nil;
        [workspace setDefaultApplicationAtURL:application toOpenContentType:type completionHandler:^(NSError *error) {
            failure = error == nil ? nil : (error.localizedDescription ?: error.description);
            dispatch_semaphore_signal(done);
        }];
        long waited = dispatch_semaphore_wait(done, dispatch_time(DISPATCH_TIME_NOW, (int64_t) (seconds * NSEC_PER_SEC)));
        return waited == 0 ? failure : @"timed out";
    } else {
        OSStatus status = LSSetDefaultRoleHandlerForContentType((__bridge CFStringRef)uti, kLSRolesAll,
                                                                (__bridge CFStringRef)bundleId);
        return status == noErr ? nil : [NSError errorWithDomain:NSOSStatusErrorDomain code:status userInfo:nil].localizedDescription;
    }
}

// Whether an application with this bundle identifier is installed.
static BOOL JasperHasApplication(NSWorkspace *workspace, NSString *bundleId) {
    return bundleId.length > 0 && [workspace URLForApplicationWithBundleIdentifier:bundleId] != nil;
}

// The declared text type (one conforming to public.text) macOS gives files with this extension, written without its
// dot; nil when the type is dynamic or not text.
static NSString *JasperTextTypeForExtension(NSString *extension) {
    if (extension.length == 0) return nil;
    UTType *type = [UTType typeWithFilenameExtension:extension];
    if (type == nil || type.dynamic || ![type conformsToType:UTTypeText]) return nil;
    return type.identifier;
}

// A Java string as an NSString; nil for a null one, or when the JVM is out of memory (an error is then pending).
static NSString *JasperString(JNIEnv *env, jstring value) {
    if (value == NULL) return nil;
    const char *chars = (*env)->GetStringUTFChars(env, value, NULL);
    if (chars == NULL) return nil;
    NSString *string = [NSString stringWithUTF8String:chars];
    (*env)->ReleaseStringUTFChars(env, value, chars);
    return string;
}

static jstring JasperJavaString(JNIEnv *env, NSString *value) {
    return value == nil ? NULL : (*env)->NewStringUTF(env, value.UTF8String);
}

JNIEXPORT jobjectArray JNICALL Java_dev_jasper_app_platform_MacLaunchServices_contentTypeHandlerOf(JNIEnv *env, jclass type,
                                                                                                  jstring uti) {
    @autoreleasepool {
        NSArray<NSString *> *pair = JasperContentTypeHandler(NSWorkspace.sharedWorkspace, JasperString(env, uti));
        if (pair == nil || (*env)->ExceptionCheck(env)) return NULL;
        jclass stringClass = (*env)->FindClass(env, "java/lang/String");
        if (stringClass == NULL) return NULL;
        jobjectArray result = (*env)->NewObjectArray(env, (jsize) pair.count, stringClass, NULL);
        if (result == NULL) return NULL;
        for (NSUInteger i = 0; i < pair.count; i++) {
            jstring element = JasperJavaString(env, pair[i]);
            if (element == NULL) return NULL;
            (*env)->SetObjectArrayElement(env, result, (jsize) i, element);
            (*env)->DeleteLocalRef(env, element);
        }
        return result;
    }
}

JNIEXPORT jstring JNICALL Java_dev_jasper_app_platform_MacLaunchServices_setContentTypeHandlerOf(JNIEnv *env, jclass type,
                                                                                                jstring uti, jstring bundleId,
                                                                                                jdouble seconds) {
    @autoreleasepool {
        NSString *name = JasperString(env, uti);
        NSString *identifier = JasperString(env, bundleId);
        if ((*env)->ExceptionCheck(env)) return NULL;
        return JasperJavaString(env, JasperSetContentTypeHandler(NSWorkspace.sharedWorkspace, name, identifier, seconds));
    }
}

JNIEXPORT jboolean JNICALL Java_dev_jasper_app_platform_MacLaunchServices_hasApplicationOf(JNIEnv *env, jclass type,
                                                                                          jstring bundleId) {
    @autoreleasepool {
        return JasperHasApplication(NSWorkspace.sharedWorkspace, JasperString(env, bundleId)) ? JNI_TRUE : JNI_FALSE;
    }
}

JNIEXPORT jstring JNICALL Java_dev_jasper_app_platform_MacLaunchServices_textTypeForExtensionOf(JNIEnv *env, jclass type,
                                                                                               jstring extension) {
    @autoreleasepool {
        return JasperJavaString(env, JasperTextTypeForExtension(JasperString(env, extension)));
    }
}
