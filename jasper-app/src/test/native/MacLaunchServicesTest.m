// Headless: a fake workspace answers which application opens a URL, and stand-in bundles in a temporary folder carry
// the identifiers; the real Launch Services is asked only about a scheme no application opens.
#import "../../main/native/MacLaunchServices.m"
#include <assert.h>

@interface TestWorkspace : NSObject
@property NSURL *application;
@property NSURL *asked;
@end
@implementation TestWorkspace
- (NSURL *)URLForApplicationToOpenURL:(NSURL *)url {
    self.asked = url;
    return self.application;
}
@end

// An application bundle in the folder whose Info.plist names the identifier, or none when it is nil.
static NSURL *TestBundle(NSURL *folder, NSString *name, NSString *identifier) {
    NSURL *bundle = [folder URLByAppendingPathComponent:name isDirectory:YES];
    NSURL *contents = [bundle URLByAppendingPathComponent:@"Contents" isDirectory:YES];
    BOOL made = [NSFileManager.defaultManager createDirectoryAtURL:contents withIntermediateDirectories:YES attributes:nil error:nil];
    assert(made);
    NSMutableDictionary *info = [NSMutableDictionary dictionaryWithObject:name forKey:@"CFBundleName"];
    if (identifier != nil) info[@"CFBundleIdentifier"] = identifier;
    BOOL written = [info writeToURL:[contents URLByAppendingPathComponent:@"Info.plist"] error:nil];
    assert(written);
    return bundle;
}

int main(void) {
    @autoreleasepool {
        NSURL *folder = [NSURL fileURLWithPath:[NSTemporaryDirectory() stringByAppendingPathComponent:
            [@"jasper-launch-services-" stringByAppendingString:NSUUID.UUID.UUIDString]] isDirectory:YES];
        TestWorkspace *workspace = [TestWorkspace new];
        // No application opens the scheme; the question is a whole URL of it.
        assert(JasperDefaultBrowserBundleId((NSWorkspace *)workspace, @"https") == nil);
        assert([workspace.asked.absoluteString isEqualToString:@"https://example.com"]);
        workspace.application = TestBundle(folder, @"Jasper.app", @"dev.jasper.app");
        assert([JasperDefaultBrowserBundleId((NSWorkspace *)workspace, @"https") isEqualToString:@"dev.jasper.app"]);
        workspace.application = TestBundle(folder, @"Other.app", @"com.example.browser");
        assert([JasperDefaultBrowserBundleId((NSWorkspace *)workspace, @"http") isEqualToString:@"com.example.browser"]);
        assert([workspace.asked.absoluteString isEqualToString:@"http://example.com"]);
        // An application whose bundle names no identifier.
        workspace.application = TestBundle(folder, @"Anonymous.app", nil);
        assert(JasperDefaultBrowserBundleId((NSWorkspace *)workspace, @"https") == nil);
        // No scheme: nothing is asked.
        workspace.asked = nil;
        assert(JasperDefaultBrowserBundleId((NSWorkspace *)workspace, @"") == nil);
        assert(JasperDefaultBrowserBundleId((NSWorkspace *)workspace, nil) == nil);
        assert(workspace.asked == nil);
        // The real workspace, from a plain process with no NSApp: read-only, and nothing opens an unknown scheme.
        assert(JasperDefaultBrowserBundleId(NSWorkspace.sharedWorkspace, @"jasper-test-no-such-scheme") == nil);
        assert(NSApp == nil);
        [NSFileManager.defaultManager removeItemAtURL:folder error:nil];
        puts("macOS Launch Services bridge: all headless checks passed");
    }
    return 0;
}
