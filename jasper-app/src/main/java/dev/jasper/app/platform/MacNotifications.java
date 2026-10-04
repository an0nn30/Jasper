package dev.jasper.app.platform;

/** In-process UserNotifications bridge in {@code libjasper-macos.dylib}; macOS derives identity and icon from the main bundle. */
final class MacNotifications {
    private MacNotifications() { }

    static void send(String title, String detail) {
        if (!MacNative.load()) return;
        post(title, detail);
    }

    // NSStrings are built from Java UTF-16, not JNI's modified UTF-8; emoji and NUL stay data.
    private static native void post(String title, String detail);
}
