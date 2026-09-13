package dev.moray.app;

import dev.moray.terminal.Palette;

/** Built-in palettes and their corresponding chrome variants. */
enum BuiltinTheme {
    DARK("moray-dark-purple", "Dark purple", Palette.morayDarkPurple()),
    CLASSIC_DARK("moray-dark", "Classic dark", Palette.morayDark()),
    LIGHT("moray-light", "Light", Palette.morayLight());

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
