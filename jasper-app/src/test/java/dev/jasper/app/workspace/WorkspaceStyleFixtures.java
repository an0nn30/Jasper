package dev.jasper.app.workspace;

import dev.jasper.app.appearance.*;
import dev.jasper.ui.theme.*;
import dev.jasper.ui.themeio.*;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import javax.swing.*;
import javax.swing.text.JTextComponent;

/** Real controls and theme values used by the workspace style contract tests. */
final class WorkspaceStyleFixtures {
    private WorkspaceStyleFixtures() {}

    static ResolvedTheme independent() {
        GeneratedTheme chrome = ThemeManager.generated(Theme.JASPER_LIGHT);
        GeneratedTheme workspace = ThemeManager.generated(Theme.DARK);
        return new ResolvedTheme(chrome, workspace);
    }

    static ResolvedTheme recolored(ResolvedTheme original, Token token, Color color) {
        GeneratedTheme old = original.workspace();
        var tokens = new EnumMap<Token, Color>(Token.class);
        tokens.putAll(old.tokens());
        tokens.put(token, color);
        var changed = new GeneratedTheme(old.name(), old.dark(), old.author(), tokens, old.syntax(),
            old.terminal(), old.uiKeys(), old.icons(), old.glass(), old.semantic());
        return new ResolvedTheme(original.chrome(), changed);
    }

    static void applyRecipe(JComponent component, WorkspaceStyles.Recipe recipe) {
        component.putClientProperty("FlatLaf.style", recipe.style());
        recipe.direct().forEach((key, value) -> {
            switch (key) {
                case "background" -> component.setBackground(value);
                case "foreground" -> component.setForeground(value);
                case "selectionBackground" -> {
                    if (component instanceof JList<?> list) list.setSelectionBackground(value);
                    else if (component instanceof JTable table) table.setSelectionBackground(value);
                    else if (component instanceof JTextComponent text) text.setSelectionColor(value);
                    else throw new IllegalArgumentException(key + " on " + component.getClass());
                }
                case "selectionForeground" -> {
                    if (component instanceof JList<?> list) list.setSelectionForeground(value);
                    else if (component instanceof JTable table) table.setSelectionForeground(value);
                    else if (component instanceof JTextComponent text) text.setSelectedTextColor(value);
                    else throw new IllegalArgumentException(key + " on " + component.getClass());
                }
                case "caretColor" -> {
                    if (component instanceof JTextComponent text) text.setCaretColor(value);
                    else throw new IllegalArgumentException(key + " on " + component.getClass());
                }
                case "disabledTextColor" -> {
                    if (component instanceof JTextComponent text) text.setDisabledTextColor(value);
                    else throw new IllegalArgumentException(key + " on " + component.getClass());
                }
                default -> throw new IllegalArgumentException(key);
            }
        });
    }

    static BufferedImage paint(JComponent component, int width, int height) {
        component.setSize(width, height);
        component.doLayout();
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { component.paint(graphics); }
        finally { graphics.dispose(); }
        return image;
    }
}
