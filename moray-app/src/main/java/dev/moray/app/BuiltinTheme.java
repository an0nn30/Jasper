package dev.moray.app;

import dev.moray.terminal.Palette;

/** The session-only built-in choices for both chrome and terminal rendering. */
enum BuiltinTheme {
    DARK("moray-dark", "Dark", Palette.morayDark()),
    LIGHT("moray-light", "Light", Palette.morayLight());

    private final String id;
    private final String label;
    private final Palette palette;

    BuiltinTheme(String id, String label, Palette palette) {
        this.id = id; this.label = label; this.palette = palette;
    }

    String id() { return id; }
    String label() { return label; }
    Palette palette() { return palette; }
}
