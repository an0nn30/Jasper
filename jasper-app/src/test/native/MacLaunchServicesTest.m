// Headless: a fake workspace answers which application opens a URL or a content type and records each default it is
// asked to set, and stand-in bundles in a temporary folder carry the identifiers. The real Launch Services is only read:
// about a scheme and a content type no application opens, and for the file types of extensions. No default changes.
//
// Usage: mac-launchservices-tests <file-types.tsv>
#import "../../main/native/MacLaunchServices.m"
#include <assert.h>
#include <string.h>

// How the fake answers a set call: at once with no error, at once with an error, or never.
typedef NS_ENUM(NSInteger, TestCompletion) { TestCompletionSuccess, TestCompletionError, TestCompletionNever };

@interface TestWorkspace : NSObject
@property NSURL *application;
@property NSURL *asked;
@property UTType *askedType;
@property NSDictionary<NSString *, NSURL *> *installed;
@property TestCompletion completion;
@property NSURL *setApplication;
@property UTType *setType;
@property(copy) void (^pending)(NSError *);
@end
@implementation TestWorkspace
- (NSURL *)URLForApplicationToOpenURL:(NSURL *)url {
    self.asked = url;
    return self.application;
}
- (NSURL *)URLForApplicationToOpenContentType:(UTType *)contentType {
    self.askedType = contentType;
    return self.application;
}
- (NSURL *)URLForApplicationWithBundleIdentifier:(NSString *)bundleIdentifier {
    return self.installed[bundleIdentifier];
}
- (void)setDefaultApplicationAtURL:(NSURL *)applicationURL toOpenContentType:(UTType *)contentType
                 completionHandler:(void (^)(NSError *))completionHandler {
    self.setApplication = applicationURL;
    self.setType = contentType;
    if (self.completion == TestCompletionSuccess) {
        // From another queue, as Launch Services answers.
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_DEFAULT, 0), ^{ completionHandler(nil); });
    } else if (self.completion == TestCompletionError) {
        completionHandler([NSError errorWithDomain:@"TestDomain" code:7
                                          userInfo:@{NSLocalizedDescriptionKey: @"The test refused."}]);
    } else {
        self.pending = completionHandler;
    }
}
@end

// A JNI environment that knows only what the set call uses: GetStringUTFChars fails with an exception pending, as when
// the JVM is out of memory, and NewStringUTF records what it was given.
static jboolean TestPending;
static char TestMade[256];
static const char *JNICALL TestGetChars(JNIEnv *env, jstring value, jboolean *copy) {
    TestPending = JNI_TRUE;
    return NULL;
}
static void JNICALL TestReleaseChars(JNIEnv *env, jstring value, const char *chars) { }
static jboolean JNICALL TestExceptionCheck(JNIEnv *env) { return TestPending; }
static void JNICALL TestExceptionClear(JNIEnv *env) { TestPending = JNI_FALSE; }
static jstring JNICALL TestNewString(JNIEnv *env, const char *chars) {
    if (TestPending) return NULL;
    strlcpy(TestMade, chars, sizeof TestMade);
    return (jstring) TestMade;
}

// An application bundle in the folder whose Info.plist names the identifier, or none when it is nil.
static NSURL *TestBundle(NSURL *folder, NSString *name, NSString *identifier) {
    NSURL *bundle = [folder URLByAppendingPathComponent:name isDirectory:YES];
    NSURL *contents = [bundle URLByAppendingPathComponent:@"Contents" isDirectory:YES];
    BOOL made = [NSFileManager.defaultManager createDirectoryAtURL:contents withIntermediateDirectories:YES attributes:nil error:nil];
    assert(made);
    NSMutableDictionary *info = [NSMutableDictionary dictionaryWithObject:name.stringByDeletingPathExtension forKey:@"CFBundleName"];
    if (identifier != nil) info[@"CFBundleIdentifier"] = identifier;
    BOOL written = [info writeToURL:[contents URLByAppendingPathComponent:@"Info.plist"] error:nil];
    assert(written);
    return bundle;
}

static NSArray<NSString *> *TestSplit(NSString *column) {
    NSMutableArray<NSString *> *parts = [NSMutableArray array];
    for (NSString *part in [column componentsSeparatedByString:@","]) {
        NSString *trimmed = [part stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceCharacterSet];
        if (trimmed.length > 0) [parts addObject:trimmed];
    }
    return parts;
}

// No extension with a macOS column may resolve to a declared type that is not text: claiming it would take over other
// files. Any other difference from the catalogue (another text type, or no declared type where the catalogue names a
// system type) is only noted, since macOS versions declare different types and run time uses the system's answer.
static int TestCatalogue(NSString *path) {
    NSError *error = nil;
    NSString *text = [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:&error];
    if (text == nil) {
        fprintf(stderr, "cannot read the catalogue %s: %s\n", path.UTF8String, error.localizedDescription.UTF8String);
        return 1;
    }
    NSMutableSet<NSString *> *imports = [NSMutableSet set];
    NSMutableArray<NSArray<NSString *> *> *rows = [NSMutableArray array];
    for (NSString *line in [text componentsSeparatedByCharactersInSet:NSCharacterSet.newlineCharacterSet]) {
        if ([line stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceCharacterSet].length == 0 || [line hasPrefix:@"#"]) continue;
        NSArray<NSString *> *columns = [line componentsSeparatedByString:@"\t"];
        if ([columns[0] isEqualToString:@"import"]) {
            [imports addObject:columns[1]];
        } else {
            [rows addObject:columns];
        }
    }
    int mismatches = 0;
    int checked = 0;
    for (NSArray<NSString *> *columns in rows) {
        NSString *extension = [columns[0] substringFromIndex:1];
        NSArray<NSString *> *types = TestSplit(columns[1]);
        if (types.count == 0) continue;
        UTType *resolved = [UTType typeWithFilenameExtension:extension];
        NSString *identifier = resolved.identifier ?: @"(none)";
        BOOL text = resolved != nil && !resolved.dynamic && [resolved conformsToType:UTTypeText];
        NSString *listed = [types componentsJoinedByString:@","];
        checked++;
        if (resolved != nil && !resolved.dynamic && !text) {
            // The safety property: claiming would take over files that are not text.
            printf("MISMATCH: .%s resolves to %s, which is not text; the catalogue says %s\n",
                   extension.UTF8String, identifier.UTF8String, listed.UTF8String);
            mismatches++;
        } else if (resolved == nil || resolved.dynamic) {
            if (![[NSSet setWithArray:types] isSubsetOfSet:imports]) {
                printf("note: .%s has no declared type here (%s); the catalogue says %s\n",
                       extension.UTF8String, identifier.UTF8String, listed.UTF8String);
            }
        } else if (types.count != 1 || ![types[0] isEqualToString:identifier]) {
            printf("note: .%s resolves to the text type %s; the catalogue says %s\n",
                   extension.UTF8String, identifier.UTF8String, listed.UTF8String);
        }
    }
    if (checked == 0) {
        fprintf(stderr, "the catalogue %s names no macOS types\n", path.UTF8String);
        return 1;
    }
    return mismatches;
}

int main(int argc, const char *argv[]) {
    @autoreleasepool {
        if (argc != 2) {
            fprintf(stderr, "usage: %s <file-types.tsv>\n", argv[0]);
            return 2;
        }
        NSURL *folder = [NSURL fileURLWithPath:[NSTemporaryDirectory() stringByAppendingPathComponent:
            [@"jasper-launch-services-" stringByAppendingString:NSUUID.UUID.UUIDString]] isDirectory:YES];
        TestWorkspace *workspace = [TestWorkspace new];
        NSWorkspace *fake = (NSWorkspace *)workspace;
        // No application opens the scheme; the question is a whole URL of it.
        assert(JasperDefaultBrowserBundleId(fake, @"https") == nil);
        assert([workspace.asked.absoluteString isEqualToString:@"https://example.com"]);
        workspace.application = TestBundle(folder, @"Jasper.app", @"dev.jasper.app");
        assert([JasperDefaultBrowserBundleId(fake, @"https") isEqualToString:@"dev.jasper.app"]);
        NSURL *other = TestBundle(folder, @"Other.app", @"com.example.browser");
        workspace.application = other;
        assert([JasperDefaultBrowserBundleId(fake, @"http") isEqualToString:@"com.example.browser"]);
        assert([workspace.asked.absoluteString isEqualToString:@"http://example.com"]);
        // An application whose bundle names no identifier.
        NSURL *anonymous = TestBundle(folder, @"Anonymous.app", nil);
        workspace.application = anonymous;
        assert(JasperDefaultBrowserBundleId(fake, @"https") == nil);
        // No scheme: nothing is asked.
        workspace.asked = nil;
        assert(JasperDefaultBrowserBundleId(fake, @"") == nil);
        assert(JasperDefaultBrowserBundleId(fake, nil) == nil);
        assert(workspace.asked == nil);

        // Which application opens a content type: its identifier and display name, asked about that type.
        workspace.application = other;
        NSArray<NSString *> *pair = JasperContentTypeHandler(fake, @"public.plain-text");
        assert([pair isEqualToArray:(@[@"com.example.browser", @"Other"])]);
        assert([workspace.askedType.identifier isEqualToString:@"public.plain-text"]);
        // A bundle with no identifier is named by its path.
        workspace.application = anonymous;
        pair = JasperContentTypeHandler(fake, @"public.plain-text");
        assert([pair isEqualToArray:(@[anonymous.path, @"Anonymous"])]);
        // No application opens it; an empty or unknown type is not asked.
        workspace.application = nil;
        assert(JasperContentTypeHandler(fake, @"public.plain-text") == nil);
        workspace.askedType = nil;
        assert(JasperContentTypeHandler(fake, @"") == nil);
        assert(JasperContentTypeHandler(fake, nil) == nil);
        assert(JasperContentTypeHandler(fake, @"dev.jasper.test-no-such-type") == nil);
        assert(workspace.askedType == nil);

        // Installed applications, by identifier.
        workspace.installed = @{@"dev.jasper.app": TestBundle(folder, @"Jasper.app", @"dev.jasper.app"), @"com.example.browser": other};
        assert(JasperHasApplication(fake, @"dev.jasper.app"));
        assert(!JasperHasApplication(fake, @"com.example.gone"));
        assert(!JasperHasApplication(fake, @""));
        assert(!JasperHasApplication(fake, nil));

        // Setting a default: the application's URL and the content type reach the workspace; success is nil.
        workspace.completion = TestCompletionSuccess;
        assert(JasperSetContentTypeHandler(fake, @"public.plain-text", @"com.example.browser", 5) == nil);
        assert([workspace.setApplication isEqual:other]);
        assert([workspace.setType.identifier isEqualToString:@"public.plain-text"]);
        // Failure is the error's description.
        workspace.completion = TestCompletionError;
        assert([JasperSetContentTypeHandler(fake, @"public.json", @"dev.jasper.app", 5) isEqualToString:@"The test refused."]);
        assert([workspace.setType.identifier isEqualToString:@"public.json"]);
        // No answer within the wait.
        workspace.completion = TestCompletionNever;
        NSDate *start = NSDate.date;
        assert([JasperSetContentTypeHandler(fake, @"public.plain-text", @"dev.jasper.app", 0.2) isEqualToString:@"timed out"]);
        NSTimeInterval waited = -start.timeIntervalSinceNow;
        assert(waited >= 0.15 && waited < 3);
        // A late answer after the wait is harmless.
        workspace.pending(nil);
        workspace.pending = nil;
        // An application that is not installed, or a type that does not exist, is never set.
        workspace.setType = nil;
        workspace.completion = TestCompletionSuccess;
        NSString *gone = JasperSetContentTypeHandler(fake, @"public.plain-text", @"com.example.gone", 5);
        assert(gone != nil && [gone containsString:@"com.example.gone"]);
        NSString *unknown = JasperSetContentTypeHandler(fake, @"dev.jasper.test-no-such-type", @"dev.jasper.app", 5);
        assert(unknown != nil && [unknown containsString:@"dev.jasper.test-no-such-type"]);
        assert(JasperSetContentTypeHandler(fake, @"", @"dev.jasper.app", 5) != nil);
        assert(JasperSetContentTypeHandler(fake, nil, nil, 5) != nil);
        assert(workspace.setType == nil);

        // A Java exception pending while the arguments are read is a failure, never the null that means done; the
        // workspace is never asked.
        struct JNINativeInterface_ functions = {0};
        functions.GetStringUTFChars = TestGetChars;
        functions.ReleaseStringUTFChars = TestReleaseChars;
        functions.ExceptionCheck = TestExceptionCheck;
        functions.ExceptionClear = TestExceptionClear;
        functions.NewStringUTF = TestNewString;
        const struct JNINativeInterface_ *table = &functions;
        JNIEnv *env = (JNIEnv *) &table;
        jstring answer = Java_dev_jasper_app_platform_MacLaunchServices_setContentTypeHandlerOf(
            env, NULL, (jstring) "uti", (jstring) "bundle", 0.1);
        assert(answer != NULL);
        assert(strstr(TestMade, "could not be read") != NULL);
        assert(!TestPending);

        // The real type database, read only: a system text type, and nothing for an extension no type claims.
        assert([JasperTextTypeForExtension(@"swift") isEqualToString:@"public.swift-source"]);
        assert(JasperTextTypeForExtension(@"jasper-no-such-ext") == nil);
        assert(JasperTextTypeForExtension(@"") == nil);
        assert(JasperTextTypeForExtension(nil) == nil);
        // A system type that is not text.
        assert(JasperTextTypeForExtension(@"png") == nil);

        // The real workspace, from a plain process with no NSApp: read-only, and nothing opens an unknown scheme or type.
        assert(JasperDefaultBrowserBundleId(NSWorkspace.sharedWorkspace, @"jasper-test-no-such-scheme") == nil);
        assert(JasperContentTypeHandler(NSWorkspace.sharedWorkspace, @"dev.jasper.test-no-such-type") == nil);
        assert(NSApp == nil);
        [NSFileManager.defaultManager removeItemAtURL:folder error:nil];

        int mismatches = TestCatalogue([NSString stringWithUTF8String:argv[1]]);
        if (mismatches != 0) {
            fprintf(stderr, "macOS Launch Services bridge: the catalogue disagrees with the type database (%d)\n", mismatches);
            return 1;
        }
        puts("macOS Launch Services bridge: all headless checks passed");
    }
    return 0;
}
