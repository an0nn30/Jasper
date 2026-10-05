package dev.jasper.ui.theme;

/** Runtime platform selection without Swing or vendor state. */
public enum RuntimePlatform {
    MAC("os.mac"),
    WINDOWS("os.windows"),
    LINUX("os.linux"),
    OTHER("os.default");
    private final String key;

    RuntimePlatform(String key) {
        this.key = key;
    }

    /** Authored UI branch key. */
    public String key() {
        return key;
    }

    /** Detect the current runtime; explicit generation overloads remain deterministic. */
    public static RuntimePlatform current() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return os.startsWith("mac")
                ? MAC
                : os.startsWith("windows") ? WINDOWS : os.startsWith("linux") ? LINUX : OTHER;
    }
}
