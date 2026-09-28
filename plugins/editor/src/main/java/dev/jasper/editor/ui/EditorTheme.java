package dev.jasper.editor.ui;

import dev.jasper.sdk.Variant;
import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.RTextScrollPane;

/**
 * Editor colours (spec 3.4): token colours from RSyntaxTextArea's {@code idea.xml} or {@code dark.xml}, and every
 * surface from the host's semantic editor palette.
 */
public final class EditorTheme {
    private final Theme light = load("idea.xml");
    private final Theme dark = load("dark.xml");

    /**
     * @param area the text area
     * @param scroll its scroll pane, whose gutter is coloured too
     * @param variant Jasper's light or dark look
     */
    public void apply(RSyntaxTextArea area, RTextScrollPane scroll, Variant variant) {
        apply(area, scroll, variant, dev.jasper.sdk.ui.EditorPalette.current());
    }

    /** Applies the active host's semantic palette to every document surface. */
    public void apply(RSyntaxTextArea area, RTextScrollPane scroll, Variant variant, dev.jasper.sdk.ui.EditorPalette palette) {
        Font font = area.getFont();
        (variant == Variant.DARK ? dark : light).apply(area);
        area.setFont(font);
        area.setBackground(palette.background());
        area.setForeground(palette.foreground());
        area.setCaretColor(palette.caret());
        area.setSelectionColor(palette.selection());
        area.setCurrentLineHighlightColor(palette.currentLine());
        scroll.setBackground(palette.background());
        scroll.getViewport().setBackground(palette.background());
        Gutter gutter = scroll.getGutter();
        gutter.setBackground(palette.gutter());
        gutter.setBorderColor(palette.gutter());
        gutter.setFoldBackground(palette.gutter());
        gutter.setLineNumberColor(palette.lineNumbers());
    }

    /**
     * @param base the starting colour
     * @param toward the colour to move toward
     * @param amount 0 keeps {@code base}, 1 gives {@code toward}
     * @return the blend
     */
    public static Color blend(Color base, Color toward, double amount) {
        return new Color((int) Math.round(base.getRed() + (toward.getRed() - base.getRed()) * amount),
            (int) Math.round(base.getGreen() + (toward.getGreen() - base.getGreen()) * amount),
            (int) Math.round(base.getBlue() + (toward.getBlue() - base.getBlue()) * amount));
    }

    private static Theme load(String name) {
        try (InputStream in = RSyntaxTextArea.class.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/" + name)) {
            if (in == null) throw new IOException("RSyntaxTextArea has no theme " + name);
            return Theme.load(in);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
