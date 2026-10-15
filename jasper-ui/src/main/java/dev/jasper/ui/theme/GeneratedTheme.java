package dev.jasper.ui.theme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A Slate Cobalt variant with every value filled in: the semantic palette, the matching terminal palette, the editor
 * scheme and its syntax styles, FlatLaf's ordered UI keys and the workspace glass. Immutable and Swing-free.
 */
public record GeneratedTheme(String name, boolean dark, String author, SemanticPalette semantic, TerminalColors terminal,
                             EditorColors editor, Map<SyntaxRole, SyntaxStyle> syntax, Map<String, Object> uiKeys, GlassLevels glass) {
    public GeneratedTheme {
        Objects.requireNonNull(name); Objects.requireNonNull(author); Objects.requireNonNull(semantic);
        Objects.requireNonNull(terminal); Objects.requireNonNull(editor); Objects.requireNonNull(glass);
        syntax = Map.copyOf(syntax);
        uiKeys = Collections.unmodifiableMap(new LinkedHashMap<>(uiKeys));
    }
}
