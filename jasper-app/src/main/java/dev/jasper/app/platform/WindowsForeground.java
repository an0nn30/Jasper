package dev.jasper.app.platform;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Windows' foreground lock, passed on at a handoff. Windows lets the process the user just launched bring a window to
 * the front, but not a resident process that has been idle in the background: its new window would open behind the
 * current one and only flash on the taskbar. The launcher therefore lends its right to the resident while it asks for
 * a window. Uses the JDK's own foreign function interface, so a handed-off launch loads no native library of Jasper's.
 */
public final class WindowsForeground {
    private static final System.Logger LOG = System.getLogger(WindowsForeground.class.getName());

    private WindowsForeground() { }

    /**
     * Lets process {@code pid} take the foreground once (user32 {@code AllowSetForegroundWindow}). Does nothing outside
     * Windows or for an unknown id ({@code pid <= 0}). Never throws: at worst the window opens behind.
     */
    public static void allow(String osName, long pid) {
        if (!applies(osName, pid)) return;
        try {
            if (!grant(pid)) {
                // Expected when this launch holds no foreground right of its own, for example one started from a script.
                LOG.log(System.Logger.Level.DEBUG, "Windows declined to pass the foreground right to process " + pid);
            }
        } catch (Throwable unavailable) {
            LOG.log(System.Logger.Level.DEBUG, "Could not pass the foreground right to process " + pid, unavailable);
        }
    }

    private static boolean applies(String osName, long pid) {
        return osName != null && osName.startsWith("Windows") && pid > 0 && pid <= 0xFFFF_FFFFL;
    }

    /** The call itself, Windows only: whether Windows granted it. Throws when user32 or the function is missing. */
    static boolean grant(long pid) throws Throwable {
        return (int) allowSetForegroundWindow().invokeExact((int) pid) != 0;
    }

    @SuppressWarnings("restricted") // The packaged launcher explicitly enables native access.
    private static MethodHandle allowSetForegroundWindow() {
        // BOOL AllowSetForegroundWindow(DWORD dwProcessId): both 32-bit; the DWORD's bits pass unchanged through an int.
        SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
        return Linker.nativeLinker().downcallHandle(user32.find("AllowSetForegroundWindow").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    }
}
