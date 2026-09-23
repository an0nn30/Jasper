package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.terminal.config.Palette;

/** App presentation presets, each with its terminal palette. */
public enum BuiltinTheme {
    DARK("dark", "Dark", Palette.jasperDark()),
    LIGHT("light", "Light", Palette.jasperLight()),
    RETRO("retro", "Retro", RetroPalette.create());

    private final String id;
    private final String label;
    private final Palette palette;

    BuiltinTheme(String id, String label, Palette palette) {
        this.id = id; this.label = label; this.palette = palette;
    }

    static BuiltinTheme of(Appearance appearance) { return appearance == Appearance.LIGHT ? LIGHT : DARK; }

    String id() { return id; }
    String label() { return label; }
    public Palette palette() { return palette; }
    public Appearance appearance() { return this == DARK ? Appearance.DARK : Appearance.LIGHT; }
}
