package dev.jasper.ui.theme;

import java.awt.Color;

/**
 * A colour a face paints with: a {@link SemanticColor} from the variant's palette or a {@link DerivedColor} computed
 * from it. Pairs a face declares ({@link PaintPair}) name roles of either kind.
 */
public sealed interface ColorRole permits SemanticColor, DerivedColor {
    /** The case-sensitive key theme files and frozen tables name the role by. */
    String key();

    /** The role's colour in {@code theme}: its palette's, or its {@linkplain DerivedPalette derived palette}'s. */
    default Color color(GeneratedTheme theme) {
        return switch (this) {
            case SemanticColor role -> theme.semantic().color(role);
            case DerivedColor role -> DerivedPalette.of(theme).color(role);
        };
    }
}
