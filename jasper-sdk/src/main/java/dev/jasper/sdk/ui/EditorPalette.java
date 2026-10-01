package dev.jasper.sdk.ui;

import java.awt.Color;
import java.util.Objects;
import javax.swing.UIManager;

/** Host-owned editor surface colors; syntax token classification remains the plugin's responsibility.
 * @param background document background
 * @param foreground plain text
 * @param caret caret color
 * @param selection selected text background
 * @param currentLine current line background
 * @param gutter line-number gutter background
 * @param lineNumbers line-number foreground
 * @since 0.8.5
 */
public record EditorPalette(Color background, Color foreground, Color caret, Color selection,
                            Color currentLine, Color gutter, Color lineNumbers) {
    /** Validates that every semantic color is present. */
    public EditorPalette {
        Objects.requireNonNull(background); Objects.requireNonNull(foreground); Objects.requireNonNull(caret);
        Objects.requireNonNull(selection); Objects.requireNonNull(currentLine); Objects.requireNonNull(gutter);
        Objects.requireNonNull(lineNumbers);
    }

    /** Reads the host's semantic defaults, with standard Swing fallbacks for custom hosts.
     * @return current editor colors
     */
    public static EditorPalette current() {
        Color background = color("Jasper.editorBackground", "TextArea.background");
        Color foreground = color("Jasper.editorForeground", "TextArea.foreground");
        Color line = UIManager.getColor("Jasper.workspace.editorCurrentLine");
        if (line == null) line = UIManager.getColor("Jasper.editorCurrentLine");
        if (line == null) line = new Color((background.getRed() * 94 + foreground.getRed() * 6) / 100,
            (background.getGreen() * 94 + foreground.getGreen() * 6) / 100,
            (background.getBlue() * 94 + foreground.getBlue() * 6) / 100);
        return new EditorPalette(background, foreground, color("Jasper.editorCaret", "TextArea.caretForeground"),
            color("Jasper.editorSelection", "TextArea.selectionBackground"), new Color(line.getRGB(), true),
            color("Jasper.editorGutter", "TextArea.background"), color("Jasper.editorLineNumbers", "Label.disabledForeground"));
    }

    private static Color color(String semantic, String fallback) {
        Color value = UIManager.getColor(semantic.replace("Jasper.", "Jasper.workspace."));
        if (value == null) value = UIManager.getColor(semantic);
        if (value == null) value = Objects.requireNonNull(UIManager.getColor(fallback), fallback);
        return new Color(value.getRGB(), true);
    }
}
