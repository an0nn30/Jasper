package dev.jasper.app.architecture;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Packaging probe, not a test: verifyPackage runs it headless with a packaged image's runtime, launcher options and
 * class path, and BundledNativesProbeTest runs it against a folder laid out like the image. It loads the native
 * libraries Jasper ships in the image (JNA's dispatch library, FlatLaf's window library) and starts one process through
 * pty4j as Jasper does, which opens libpty and, on macOS, runs the spawn helper. JNA opens pty4j's files with its own
 * dlopen, which -Xlog:library does not see, so the probe prints the files pty4j resolved, one "pty4j name path" line
 * each. The app compiles against neither JNA nor pty4j, hence the reflection. Exits 1 when a step fails.
 */
public final class BundledNativesProbe {
    private BundledNativesProbe() {}

    public static void main(String[] args) throws Exception {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Class.forName("com.sun.jna.Native");
        String flatLaf = windows ? "com.formdev.flatlaf.ui.FlatNativeWindowsLibrary" : "com.formdev.flatlaf.ui.FlatNativeMacLibrary";
        if (!Boolean.TRUE.equals(Class.forName(flatLaf).getMethod("isLoaded").invoke(null))) fail("FlatLaf's native library did not load");
        String[] command = windows
            ? new String[] {System.getenv().getOrDefault("SystemRoot", "C:\\Windows") + "\\System32\\hostname.exe"}
            : new String[] {"/usr/bin/true"};
        Class<?> builderType = Class.forName("com.pty4j.PtyProcessBuilder");
        Object builder = builderType.getConstructor(String[].class).newInstance((Object) command);
        builderType.getMethod("setUseWinConPty", boolean.class).invoke(builder, windows);
        Process process = (Process) builderType.getMethod("start").invoke(builder);
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("The PTY process did not exit");
        }
        if (process.exitValue() != 0) fail("The PTY process exited with " + process.exitValue());
        Method resolve = Class.forName("com.pty4j.util.PtyUtil").getMethod("resolveNativeFile", String.class);
        List<String> files = windows ? List.of("conpty.dll", "OpenConsole.exe", "win-helper.dll") : List.of("libpty.dylib", "pty4j-unix-spawn-helper");
        for (String name : files) System.out.println("pty4j " + name + " " + ((File) resolve.invoke(null, name)).getAbsolutePath());
        System.out.println("bundled natives probe passed");
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
