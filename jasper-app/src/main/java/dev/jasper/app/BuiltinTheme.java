package dev.jasper.app;

import dev.jasper.terminal.Palette;

/** Built-in palettes and their corresponding chrome variants. */
enum BuiltinTheme {
    DARK("jasper-dark-purple", "Dark purple", Palette.jasperDarkPurple()),
    CLASSIC_DARK("jasper-dark", "Classic dark", Palette.jasperDark()),
    LIGHT("jasper-light", "Light", Palette.jasperLight());

    private final String id;
    private final String label;
    private final Palette palette;

    BuiltinTheme(String id, String label, Palette palette) {
        this.id = id; this.label = label; this.palette = palette;
    }

    static BuiltinTheme fromId(String id) {
        for (BuiltinTheme theme : values()) if (theme.id.equals(id)) return theme;
        return null;
    }

    String id() { return id; }
    String label() { return label; }
    Palette palette() { return palette; }
}
