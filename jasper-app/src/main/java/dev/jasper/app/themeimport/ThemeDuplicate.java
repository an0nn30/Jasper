package dev.jasper.app.themeimport;

import dev.jasper.app.appearance.GeneratedTheme;
import dev.jasper.app.appearance.Seed;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeDocument;
import dev.jasper.app.appearance.ThemeTomlWriter;
import dev.jasper.app.appearance.Token;
import java.awt.Color;
import java.util.Map;
import java.util.Optional;

/** A user-owned inherited theme template; current values are comments, not frozen overrides. */
public final class ThemeDuplicate {
    private ThemeDuplicate() {}

    /** Writes an escaped name/parent and commented seeds, ANSI colors and effective tokens. */
    public static String text(Theme original, GeneratedTheme effective, String name) {
        name = name.strip();
        if (name.isEmpty() || name.equalsIgnoreCase("match")) throw new IllegalArgumentException("Choose a nonblank theme name other than match");
        if (!original.name().equals(effective.name())) throw new IllegalArgumentException("Preview values do not belong to the original theme");
        var header = new ThemeDocument("duplicate", name, Optional.empty(), Optional.of(original.name()), 0,
            Optional.empty(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        var text = new StringBuilder(ThemeTomlWriter.write(header));
        text.append("\n# [colors]\n");
        for (Seed seed : Seed.values()) {
            Color color = switch (seed) {
                case BACKGROUND -> effective.terminal().background();
                case FOREGROUND -> effective.terminal().foreground();
                case CURSOR -> effective.terminal().cursor();
                case SELECTION -> effective.terminal().selection();
                case ACCENT -> effective.tokens().get(Token.ACCENT);
            };
            comment(text, seed.key(), color);
        }
        for (int offset : new int[] {0, 8}) {
            text.append(offset == 0 ? "\n# [colors.normal]\n" : "\n# [colors.bright]\n");
            for (int i = 0; i < 8; i++) comment(text, ThemeDocument.ANSI_NAMES.get(i), effective.terminal().ansi().get(offset + i));
        }
        text.append("\n# [tokens]\n");
        for (Token token : Token.values()) comment(text, token.key(), effective.tokens().get(token));
        return text.toString();
    }

    private static void comment(StringBuilder text, String key, Color color) {
        String hex = String.format("#%06x", color.getRGB() & 0xffffff);
        if (color.getAlpha() != 255) hex += String.format("%02x", color.getAlpha());
        text.append("# ").append(key).append(" = \"").append(hex).append("\"\n");
    }
}
