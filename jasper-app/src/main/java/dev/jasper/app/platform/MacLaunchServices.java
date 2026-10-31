package dev.jasper.app.platform;

/**
 * Which application macOS opens a URL scheme's links with, as Launch Services answers through the bridge
 * {@code libjasper-macos.dylib} ({@code MacLaunchServices.m}). Read-only; any thread.
 */
final class MacLaunchServices {
    private MacLaunchServices() { }

    /**
     * The bundle identifier of the application that opens {@code <scheme>://example.com}, once the bridge has loaded.
     *
     * @param scheme a URL scheme, such as {@code https}
     * @return null outside a packaged macOS app (only its bundle carries the bridge), when no application opens the
     *     scheme, or when that application names no identifier
     * @throws LinkageError when the bridge is present but cannot load
     */
    static String defaultHandler(String scheme) {
        return MacNative.load() ? defaultBrowserBundleId(scheme) : null;
    }

    /** The bridge's answer for {@link #defaultHandler}; the library must already be loaded. */
    static native String defaultBrowserBundleId(String scheme);
}
