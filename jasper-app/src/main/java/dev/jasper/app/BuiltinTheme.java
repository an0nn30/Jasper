package dev.jasper.app;

import dev.jasper.terminal.config.Palette;

/** The two modern variants: each pairs a FlatLaf chrome with its terminal palette. */
enum BuiltinTheme {
    DARK("dark", "Dark", Palette.jasperDark()),
    LIGHT("light", "Light", Palette.jasperLight());

    private final String id;
    private final String label;
    private final Palette palette;

    BuiltinTheme(String id, String label, Palette palette) {
        this.id = id; this.label = label; this.palette = palette;
    }

    static BuiltinTheme of(Appearance appearance) { return appearance == Appearance.LIGHT ? LIGHT : DARK; }

    String id() { return id; }
    String label() { return label; }
    Palette palette() { return palette; }
    Appearance appearance() { return this == LIGHT ? Appearance.LIGHT : Appearance.DARK; }
}
