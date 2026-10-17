package dev.jasper.app.platform;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Insets;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JMenuBar;

/**
 * The classic menu row (Enterprise presentation spec 10.1 and 10.2), the mocks' row: 24 px of titles on
 * {@code surface.app} over a 1 px {@code border.divider} rule; a hovered closed title on {@code surface.control.hover},
 * an open one in the accent, square. Terminal windows and the auxiliary frames of a classic look share it. EDT only.
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
        bar.setBackground(palette.color(SemanticColor.SURFACE_APP));
        bar.setOpaque(true);
        bar.revalidate(); bar.repaint();
    }
}
