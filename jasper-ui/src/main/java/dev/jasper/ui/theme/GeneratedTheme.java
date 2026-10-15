package dev.jasper.ui.theme;

import java.util.Map;
import java.util.Objects;

/**
 * A theme variant with every value filled in: the semantic palette, the matching terminal palette, the editor scheme and
 * its syntax styles and the workspace glass. Immutable and Swing-free. FlatLaf's UI keys are derived from it when it is
 * installed ({@link UiColorKeys#flatLaf(GeneratedTheme, UiKeySet)}).
 */
public record GeneratedTheme(String name, boolean dark, String author, SemanticPalette semantic, TerminalColors terminal,
                             EditorColors editor, Map<SyntaxRole, SyntaxStyle> syntax, GlassLevels glass) {
    public GeneratedTheme {
        Objects.requireNonNull(name); Objects.requireNonNull(author); Objects.requireNonNull(semantic);
        Objects.requireNonNull(terminal); Objects.requireNonNull(editor); Objects.requireNonNull(glass);
        syntax = Map.copyOf(syntax);
    }
}
