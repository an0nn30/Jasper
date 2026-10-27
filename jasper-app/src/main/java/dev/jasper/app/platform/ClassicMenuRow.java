package dev.jasper.app.platform;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JMenuBar;
import javax.swing.plaf.UIResource;

/**
 * The classic menu row (Enterprise presentation spec 10.1 and 10.2), the mocks' row: 24 px of titles on
 * {@code surface.app} over a 1 px {@code border.divider} rule; a hovered closed title on {@code surface.control.hover},
 * an open one in the accent, square. The titles stand side by side at their {@link #titleWidth}, as the mocks set them,
 * so whole-pixel text widths do not add up along the row. A classic look's window
 * shows it. EDT only.
 */
public final class ClassicMenuRow {
    /** An open title on the accent (spec 16.1.1), declared for the window chrome's pairs ({@code WindowPairs}). */
    public static final PaintPair OPEN_TITLE = new PaintPair(SemanticColor.TEXT_ONACCENT, SemanticColor.ACCENT, SurfaceContrast.Guard.TEXT);
    /** The client property {@link #style} sets on the bars it styles ({@link #styled}). */
    private static final String STYLED = "Jasper.classicMenuRow";
    private static final FontRenderContext FRACTIONAL = new FontRenderContext(null, true, true);
    private static final Map<TextAttribute, Object> KERNED = Map.of(TextAttribute.KERNING, TextAttribute.KERNING_ON);

    private ClassicMenuRow() { }

    /**
     * Styles {@code bar} under the installed theme and kit; call it again after a theme change. A bar that lays out its
     * own titles (a window's folding bar) keeps its layout and places them at their {@link #titleWidth} while
     * {@link #styled} says so; a plain bar's look-and-feel layout gives way to the row's.
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
            "selectionBackground", palette.color(SemanticColor.ACCENT), "selectionForeground", SurfaceContrast.painted(theme, OPEN_TITLE)));
        int side = UIScale.scale(metrics.value(DesignMetrics.Role.MENU_BAR_SIDE)), rule = UIScale.scale(metrics.value(DesignMetrics.Role.HAIRLINE));
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, rule, 0, DerivedColor.BORDER_DIVIDER.color(theme)),
            BorderFactory.createEmptyBorder(0, side, 0, side)));
        bar.putClientProperty(STYLED, Boolean.TRUE);
        if (bar.getLayout() == null || bar.getLayout() instanceof UIResource) bar.setLayout(new Titles());
        bar.setBackground(palette.color(SemanticColor.SURFACE_APP));
        bar.setOpaque(true);
        bar.revalidate(); bar.repaint();
    }

    /**
     * Whether {@link #style} styled {@code bar}, so its titles stand at their {@link #titleWidth}.
     *
     * @param bar a menu bar
     * @return whether it is a classic row
     */
    public static boolean styled(JComponent bar) { return Boolean.TRUE.equals(bar.getClientProperty(STYLED)); }

    /**
     * {@code title}'s width in a classic row: its preferred width with its whole-pixel text width replaced by the text's
     * kerned fractional advance, as the mocks' browser sets the titles (in IBM Plex "Session" is 41.7 px, not 43, and
     * "Transfer" 44.5 with its "Tr" kerned; FlatLaf draws a title unkerned, that fraction of a pixel into its right
     * padding); any other component's preferred width. A row sets each title's edges from the running total of these,
     * rounded, so the titles do not drift along the row.
     *
     * @param title a title, or any other component of the row
     * @return its width in the row's pixels
     */
    public static double titleWidth(Component title) {
        int preferred = title.getPreferredSize().width;
        if (!(title instanceof AbstractButton button) || button.getText() == null || button.getText().isEmpty() || button.getFont() == null)
            return preferred;
        String text = button.getText();
        return preferred - button.getFontMetrics(button.getFont()).stringWidth(text)
            + button.getFont().deriveFont(KERNED).getStringBounds(text, FRACTIONAL).getWidth();
    }

    /**
     * A plain bar's titles left to right at their {@link #titleWidth}, every edge rounded from the running total; the row
     * is as tall as its tallest title.
     */
    static final class Titles implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }

        @Override public Dimension preferredLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            double width = 0; int height = 0;
            for (Component child : parent.getComponents()) if (child.isVisible()) {
                width += titleWidth(child);
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
                run += titleWidth(child);
                child.setBounds(start, insets.top, insets.left + (int) Math.round(run) - start, height);
            }
        }
    }
}
