package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A Jasper theme with every value filled in: all tokens and syntax roles, the terminal palette, and the ordered UI
 * keys FlatLaf installs (IntelliJ theme {@code ui} entries) and the workspace glass. Immutable and Swing-free.
 */
public record GeneratedTheme(String name, boolean dark, String author, Map<Token, Color> tokens, Map<SyntaxRole, SyntaxStyle> syntax,
                             Palette terminal, Map<String, Object> uiKeys, Map<String, Object> icons, GlassLevels glass) {
    public GeneratedTheme {
        Objects.requireNonNull(name); Objects.requireNonNull(author); Objects.requireNonNull(terminal);
        Objects.requireNonNull(glass);
        tokens = Map.copyOf(tokens);
        syntax = Map.copyOf(syntax);
        uiKeys = Collections.unmodifiableMap(new LinkedHashMap<>(uiKeys));
        icons = Collections.unmodifiableMap(new LinkedHashMap<>(icons));
    }

    /** An opaque theme, for callers that predate workspace glass. */
    public GeneratedTheme(String name, boolean dark, String author, Map<Token, Color> tokens, Map<SyntaxRole, SyntaxStyle> syntax,
                          Palette terminal, Map<String, Object> uiKeys, Map<String, Object> icons) {
        this(name, dark, author, tokens, syntax, terminal, uiKeys, icons, GlassLevels.OPAQUE);
    }
}
