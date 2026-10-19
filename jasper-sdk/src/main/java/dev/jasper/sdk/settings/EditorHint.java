package dev.jasper.sdk.settings;

/** Which control Settings uses for a setting, where the kind alone does not say. */
public sealed interface EditorHint {
    /**
     * A font family picker.
     *
     * @param monospaced whether only monospaced families are offered
     */
    record Font(boolean monospaced) implements EditorHint { }

    /** A folder path. Settings offers no folder chooser for it: the setting's kind decides the control. */
    record Directory() implements EditorHint { }

    /** A file chooser beside a text field. */
    record File() implements EditorHint { }

    /**
     * A data value, such as a host, a port range, a path or a command: a text setting's field is drawn in the data face.
     * Other kinds ignore it.
     *
     * @since 0.18.0
     */
    record Technical() implements EditorHint { }

    /**
     * A font family picker.
     *
     * @param monospaced whether only monospaced families are offered
     * @return the hint
     */
    static EditorHint font(boolean monospaced) { return new Font(monospaced); }

    /**
     * A folder path; see {@link Directory}.
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

    /**
     * A data value's text field.
     *
     * @return the hint
     * @since 0.18.0
     */
    static EditorHint technical() { return new Technical(); }
}
