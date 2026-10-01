package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A Jasper theme with every value filled in: all tokens and syntax roles, the terminal palette, and the ordered UI
 * keys FlatLaf installs (IntelliJ theme {@code ui} entries). Immutable and Swing-free.
 */
public record GeneratedTheme(String name, boolean dark, Map<Token, Color> tokens, Map<SyntaxRole, SyntaxStyle> syntax,
                             Palette terminal, Map<String, Object> uiKeys, Map<String, Object> icons) {
    public GeneratedTheme {
        Objects.requireNonNull(name); Objects.requireNonNull(terminal);
        tokens = Map.copyOf(tokens);
        syntax = Map.copyOf(syntax);
        uiKeys = Collections.unmodifiableMap(new LinkedHashMap<>(uiKeys));
        icons = Collections.unmodifiableMap(new LinkedHashMap<>(icons));
    }
}
