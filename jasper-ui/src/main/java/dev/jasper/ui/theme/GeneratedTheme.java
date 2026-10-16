package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.Map;
import java.util.Objects;

/**
 * A theme variant with every value filled in: the semantic palette, the matching terminal palette, the editor scheme and
 * its syntax styles, the workspace glass and the derived roles the variant authors itself. Immutable and Swing-free.
 * FlatLaf's UI keys are derived from it when it is installed ({@link UiColorKeys#flatLaf(GeneratedTheme, UiKeySet)}).
 *
 * @param derivedOverrides the {@linkplain DerivedColor#overridable() overridable} derived roles the variant lists, opaque
 */
public record GeneratedTheme(String name, boolean dark, String author, SemanticPalette semantic, TerminalColors terminal,
                             EditorColors editor, Map<SyntaxRole, SyntaxStyle> syntax, GlassLevels glass,
                             Map<DerivedColor, Color> derivedOverrides) {
    public GeneratedTheme {
        Objects.requireNonNull(name); Objects.requireNonNull(author); Objects.requireNonNull(semantic);
        Objects.requireNonNull(terminal); Objects.requireNonNull(editor); Objects.requireNonNull(glass);
        syntax = Map.copyOf(syntax);
        derivedOverrides = Map.copyOf(derivedOverrides);
        derivedOverrides.forEach((role, color) -> {
            if (!role.overridable()) throw new IllegalArgumentException("Not an override key: " + role.key());
            if (color.getAlpha() != 255) throw new IllegalArgumentException("Translucent override: " + role.key());
        });
    }
}
