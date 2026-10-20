package dev.jasper.ui.swing;

/** App-only value receiver; recording state changes presentation without changing button semantics. */
public enum ButtonValuePresentation {
    /** Descriptor's ordinary button treatment. */
    DEFAULT,
    /** Left-aligned input-like value receiver, currently stopped. */
    RECORDER,
    /** Value receiver listening for a new key combination. */
    RECORDER_ARMED
}
