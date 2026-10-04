package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.Map;
import java.util.Objects;

/**
 * A Jasper theme with every value filled in: all tokens and syntax roles, the terminal palette, and the ordered UI
 * keys FlatLaf installs (IntelliJ theme {@code ui} entries) and the workspace glass. Immutable and Swing-free.
 */
public record GeneratedTheme(String name, boolean dark, String author, Map<Token, Color> tokens, Map<SyntaxRole, SyntaxStyle> syntax,
                             TerminalColors terminal, Map<String, Object> uiKeys, Map<String, Object> icons, GlassLevels glass, SemanticPalette semantic) {
    public GeneratedTheme {
        Objects.requireNonNull(name); Objects.requireNonNull(author); Objects.requireNonNull(terminal);
        Objects.requireNonNull(glass); Objects.requireNonNull(semantic);
        tokens = Map.copyOf(tokens);
        syntax = Map.copyOf(syntax);
        uiKeys = ThemeDocument.data(uiKeys);
        icons = ThemeDocument.data(icons);
    }

}
