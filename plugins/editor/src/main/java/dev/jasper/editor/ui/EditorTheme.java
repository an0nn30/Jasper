package dev.jasper.editor.ui;

import dev.jasper.sdk.Variant;
import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import javax.swing.UIManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.RTextScrollPane;

/**
 * Editor colours (spec 3.4): token colours from RSyntaxTextArea's {@code idea.xml} or {@code dark.xml}, and every
 * surface from the running Jasper theme's {@code UIManager} colours.
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
        Font font = area.getFont();
        (variant == Variant.DARK ? dark : light).apply(area);
        area.setFont(font);
        Color background = color("TextArea.background", area.getBackground());
        Color foreground = color("TextArea.foreground", area.getForeground());
        area.setBackground(background);
        area.setForeground(foreground);
        area.setCaretColor(color("TextArea.caretForeground", foreground));
        area.setSelectionColor(color("TextArea.selectionBackground", area.getSelectionColor()));
        area.setCurrentLineHighlightColor(blend(background, foreground, 0.06));
        Gutter gutter = scroll.getGutter();
        Color panel = color("Panel.background", background);
        gutter.setBackground(panel);
        gutter.setBorderColor(panel);
        gutter.setFoldBackground(panel);
        gutter.setLineNumberColor(color("Label.disabledForeground", foreground));
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

    /** A plain copy, so a later {@code updateUI} does not replace it with the look and feel's default. */
    private static Color color(String key, Color fallback) {
        Color value = UIManager.getColor(key);
        return value == null ? fallback : new Color(value.getRGB(), true);
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
