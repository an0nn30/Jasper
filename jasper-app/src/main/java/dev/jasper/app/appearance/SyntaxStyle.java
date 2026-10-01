package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.Objects;

/** How one syntax role is drawn. */
public record SyntaxStyle(Color color, boolean bold, boolean italic) {
    public SyntaxStyle { Objects.requireNonNull(color); }
}
