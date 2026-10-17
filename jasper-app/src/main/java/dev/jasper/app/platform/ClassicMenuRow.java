package dev.jasper.app.platform;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.font.FontRenderContext;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JMenuBar;

/**
 * The classic menu row (Enterprise presentation spec 10.1 and 10.2), the mocks' row: 24 px of titles on
 * {@code surface.app} over a 1 px {@code border.divider} rule; a hovered closed title on {@code surface.control.hover},
 * an open one in the accent, square. The titles stand side by side at their text's fractional width, as the mocks set
 * them, so whole-pixel text widths do not add up along the row. Terminal windows and the auxiliary frames of a classic
 * look share it. EDT only.
 */
public final class ClassicMenuRow {
    private ClassicMenuRow() { }

    /**
     * Styles {@code bar} under the installed theme and kit; call it again after a theme change.
     *
     * @param bar the menu row
     */
    public static void style(JMenuBar bar) {
        var theme = FlatLafInstaller.installedTheme();
        var metrics = FlatLafInstaller.installedKit().metrics();
        var palette = theme.semantic();
        // The item margins make the row exactly MENU_BAR_HEIGHT for the menu face's line, in logical pixels.
        int line = Math.round(bar.getFontMetrics(ThemeZone.font(Typography.Role.BODY)).getHeight() / UIScale.getUserScaleFactor());
        int height = metrics.value(DesignMetrics.Role.MENU_BAR_HEIGHT), padding = metrics.value(DesignMetrics.Role.MENU_BAR_ITEM_PADDING);
        int top = Math.max(0, (height - line) / 2), bottom = Math.max(0, height - line - top);
        bar.putClientProperty("FlatLaf.style", Map.of("itemMargins", new Insets(top, padding, bottom, padding),
            "selectionInsets", new Insets(0, 0, 0, 0), "selectionArc", 0,
            "hoverBackground", palette.color(SemanticColor.SURFACE_CONTROL_HOVER),
            "selectionBackground", palette.color(SemanticColor.ACCENT), "selectionForeground", palette.color(SemanticColor.TEXT_ONACCENT)));
        int side = UIScale.scale(metrics.value(DesignMetrics.Role.MENU_BAR_SIDE)), rule = UIScale.scale(metrics.value(DesignMetrics.Role.HAIRLINE));
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, rule, 0, DerivedColor.BORDER_DIVIDER.color(theme)),
            BorderFactory.createEmptyBorder(0, side, 0, side)));
        if (!(bar.getLayout() instanceof Titles)) bar.setLayout(new Titles());
        bar.setBackground(palette.color(SemanticColor.SURFACE_APP));
        bar.setOpaque(true);
        bar.revalidate(); bar.repaint();
    }

    /**
     * The titles left to right, each its preferred width with its text measured at fractional advances (as the kit's labels
     * measure), every edge rounded from the running total: whole-pixel text widths would drift the later titles by up to
     * a pixel each (IBM Plex's "Session" is 41.8 px, not 43). The row is as tall as its tallest title.
     */
    static final class Titles implements LayoutManager {
        private static final FontRenderContext FRACTIONAL = new FontRenderContext(null, true, true);

        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }

        @Override public Dimension preferredLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            double width = 0; int height = 0;
            for (Component child : parent.getComponents()) if (child.isVisible()) {
                width += width(child);
                height = Math.max(height, child.getPreferredSize().height);
            }
            return new Dimension(insets.left + (int) Math.round(width) + insets.right, insets.top + height + insets.bottom);
        }

        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            double run = 0;
            for (Component child : parent.getComponents()) if (child.isVisible()) {
                int start = insets.left + (int) Math.round(run);
                run += width(child);
                child.setBounds(start, insets.top, insets.left + (int) Math.round(run) - start, height);
            }
        }

        /** {@code child}'s preferred width with its whole-pixel text width replaced by the fractional one. */
        static double width(Component child) {
            int preferred = child.getPreferredSize().width;
            if (!(child instanceof AbstractButton title) || title.getText() == null || title.getText().isEmpty() || title.getFont() == null)
                return preferred;
            String text = title.getText();
            return preferred - title.getFontMetrics(title.getFont()).stringWidth(text) + title.getFont().getStringBounds(text, FRACTIONAL).getWidth();
        }
    }
}
