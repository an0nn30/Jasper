/**
 * The launcher watch test's application: "exit" calls System.exit(3) at once, "throw" throws, "env" prints the
 * launcher's variables the JVM inherited, anything else returns normally.
 */
public final class WatchProbe {
    private WatchProbe() {
    }

    public static void main(String[] arguments) {
        String mode = arguments.length == 0 ? "" : arguments[0];
        if (mode.equals("exit")) {
            System.exit(3);
        }
        if (mode.equals("throw")) {
            throw new IllegalStateException("watch probe");
        }
        if (mode.equals("env")) {
            System.out.println("inherited JASPER_AOT_CACHE_SKIP=" + System.getenv("JASPER_AOT_CACHE_SKIP")
                + " JASPER_VM_OPTIONS_SKIP=" + System.getenv("JASPER_VM_OPTIONS_SKIP")
                + " JASPER_LAUNCH_DIAGNOSE=" + System.getenv("JASPER_LAUNCH_DIAGNOSE"));
        }
    }
}
