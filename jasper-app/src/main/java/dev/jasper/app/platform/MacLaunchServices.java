package dev.jasper.app.platform;

/**
 * Which application macOS opens a URL scheme's links and a content type's files with, as Launch Services answers
 * through the bridge {@code libjasper-macos.dylib} ({@code MacLaunchServices.m}), and the means to change a content
 * type's default. Any thread; {@link #setContentTypeHandler} waits for Launch Services, so not the Event Dispatch Thread.
 */
final class MacLaunchServices {
    /** How long {@link #setContentTypeHandler} waits for Launch Services, in seconds. */
    static final double SET_WAIT_SECONDS = 5;

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

    /**
     * The application that opens files of a content type.
     *
     * @param uti a content type, such as {@code public.plain-text}
     * @return its bundle identifier (its path when its bundle names none) and display name; null outside a packaged
     *     macOS app, for an undeclared type, or when no application opens it
     * @throws LinkageError when the bridge is present but cannot load
     */
    static String[] contentTypeHandler(String uti) {
        return MacNative.load() ? contentTypeHandlerOf(uti) : null;
    }

    /**
     * Makes an installed application the default for a content type, waiting at most {@link #SET_WAIT_SECONDS}.
     *
     * @return null when it is done; otherwise the system's message, "timed out", or why it was not asked
     * @throws LinkageError when the bridge is present but cannot load
     */
    static String setContentTypeHandler(String uti, String bundleId) {
        return MacNative.load() ? setContentTypeHandlerOf(uti, bundleId, SET_WAIT_SECONDS) : "the macOS bridge is not loaded";
    }

    /**
     * Whether an application with this bundle identifier is installed; false outside a packaged macOS app.
     *
     * @throws LinkageError when the bridge is present but cannot load
     */
    static boolean hasApplication(String bundleId) {
        return MacNative.load() && hasApplicationOf(bundleId);
    }

    /**
     * The declared text type macOS gives files with an extension.
     *
     * @param extension the extension without its dot, such as {@code swift}
     * @return the type's identifier; null outside a packaged macOS app, or when the type is dynamic or not text
     * @throws LinkageError when the bridge is present but cannot load
     */
    static String textTypeForExtension(String extension) {
        return MacNative.load() ? textTypeForExtensionOf(extension) : null;
    }

    /** The bridge's answer for {@link #defaultHandler}; the library must already be loaded. */
    static native String defaultBrowserBundleId(String scheme);

    /** The bridge's answer for {@link #contentTypeHandler}; the library must already be loaded. */
    static native String[] contentTypeHandlerOf(String uti);

    /** The bridge's answer for {@link #setContentTypeHandler}; the library must already be loaded. */
    static native String setContentTypeHandlerOf(String uti, String bundleId, double seconds);

    /** The bridge's answer for {@link #hasApplication}; the library must already be loaded. */
    static native boolean hasApplicationOf(String bundleId);

    /** The bridge's answer for {@link #textTypeForExtension}; the library must already be loaded. */
    static native String textTypeForExtensionOf(String extension);
}
