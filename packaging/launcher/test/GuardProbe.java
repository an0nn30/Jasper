/** The guard test's application: "exit" calls System.exit(3), "throw" throws, anything else returns normally. */
public final class GuardProbe {
    private GuardProbe() {
    }

    public static void main(String[] arguments) {
        String mode = arguments.length == 0 ? "" : arguments[0];
        if (mode.equals("exit")) {
            System.exit(3);
        }
        if (mode.equals("throw")) {
            throw new IllegalStateException("guard probe");
        }
    }
}
