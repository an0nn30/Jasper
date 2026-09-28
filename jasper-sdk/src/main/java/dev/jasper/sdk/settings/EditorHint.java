package dev.jasper.sdk.settings;

/** Which control Settings uses for a setting, where the kind alone does not say. */
public sealed interface EditorHint {
    /**
     * A font family picker.
     *
     * @param monospaced whether only monospaced families are offered
     */
    record Font(boolean monospaced) implements EditorHint { }

    /** A folder chooser beside a text field. */
    record Directory() implements EditorHint { }

    /** A file chooser beside a text field. */
    record File() implements EditorHint { }

    /**
     * A font family picker.
     *
     * @param monospaced whether only monospaced families are offered
     * @return the hint
     */
    static EditorHint font(boolean monospaced) { return new Font(monospaced); }

    /**
     * A folder chooser.
     *
     * @return the hint
     */
    static EditorHint directory() { return new Directory(); }

    /**
     * A file chooser.
     *
     * @return the hint
     */
    static EditorHint file() { return new File(); }
}
