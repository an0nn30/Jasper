package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JToolTip;

/**
 * Ruled status cells (spec 10.6; the Workstation mocks): on {@code surface.app} under a 1 px {@code border.window}
 * rule, cells divided by 1 px {@code border.divider} rules: the run item, the session (its dot and name) and the
 * folder on the left; contributed items as cells; then the grid size in the data face, "UTF-8" and, while there is a
 * problem, the configuration on {@code warning.ground} or {@code danger.ground} after a painted "▲" to the right edge.
 */
final class RuledCells implements StatusCells {
    private final JLabel session = label("statusSession"), folder = label("statusFolder"), grid = label("statusGrid"), encoding = label("statusEncoding");
    /** Where each cell rule is, in the bar. */
    private final List<Integer> rules = new ArrayList<>();
    private int sessionDotX = -1;

    // A cell's text and tip come from the shell or a plugin, so neither renders HTML.
    private static JLabel label(String name) {
        var label = new JLabel() {
            @Override public JToolTip createToolTip() { var tip = new JToolTip(); tip.putClientProperty("html.disable", true); tip.setComponent(this); return tip; }
        };
        label.setName(name); label.putClientProperty("html.disable", true); return label;
    }
    private static int metric(DesignMetrics.Role role) { return UIScale.scale(FlatLafInstaller.installedKit().metrics().value(role)); }

    /** The configuration cell's "▲", {@code CHROME_GLYPH} square, in the cell's text colour. */
    private static final Icon WARNING = new Icon() {
        @Override public int getIconWidth() { return metric(DesignMetrics.Role.CHROME_GLYPH); }
        @Override public int getIconHeight() { return metric(DesignMetrics.Role.CHROME_GLYPH); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            var g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(component.getForeground());
                double size = getIconWidth();
                var triangle = new Path2D.Double();
                triangle.moveTo(x, y + size); triangle.lineTo(x + size / 2, y); triangle.lineTo(x + size, y + size); triangle.closePath();
                g.fill(triangle);
            } finally { g.dispose(); }
        }
    };

    @Override public void install(WindowStatusBar bar) {
        encoding.setText("UTF-8");
        bar.add(session); bar.add(folder); bar.add(bar.leftItems); bar.add(bar.rightItems); bar.add(bar.contentItems);
        bar.add(grid); bar.add(encoding); bar.add(bar.configButton());
    }

    @Override public int itemGap() { return 2 * metric(DesignMetrics.Role.STATUS_CELL_PADDING) + metric(DesignMetrics.Role.HAIRLINE); }

    @Override public void refresh(WindowStatusBar bar) {
        var theme = ThemeZone.theme(null);
        Color text = SurfaceContrast.painted(theme, WindowPairs.CELL_TEXT);
        session.setText(bar.session());
        session.setToolTipText(bar.showingContent() ? null : bar.integrationToolTip());
        folder.setText(bar.folder());
        folder.setToolTipText(bar.folder().isEmpty() ? null : bar.showingContent() ? bar.folder() : bar.directory());
        grid.setText(bar.dimensions());
        for (JLabel label : List.of(session, folder, encoding)) { label.setFont(ThemeZone.font(Typography.Role.STATUS_TEXT)); label.setForeground(text); }
        grid.setFont(ThemeZone.font(Typography.Role.MONO)); grid.setForeground(text);
        for (var row : new JComponent[] {bar.leftItems, bar.rightItems, bar.contentItems})
            for (Component child : row.getComponents()) if (child instanceof JButton item) { item.setForeground(text); item.setFont(ThemeZone.font(Typography.Role.STATUS_TEXT)); }
        var config = bar.configButton();
        boolean problem = bar.configurationProblem() != null;
        config.setVisible(problem);
        if (problem) {
            config.setText(bar.configurationText());
            config.setForeground(SurfaceContrast.painted(theme, WindowPairs.configuration(bar.configurationProblem())));
            config.setFont(ThemeZone.font(Typography.Role.STATUS_TEXT));
            // "▲ " before the text: the triangle painted, as the run item paints its "▶" (IBM Plex has no such glyph).
            config.setIcon(WARNING); config.setDisabledIcon(WARNING);
            config.setIconTextGap(config.getFontMetrics(config.getFont()).charWidth(' '));
        }
        bar.revalidate(); bar.repaint();
    }

    @Override public void layout(WindowStatusBar bar) {
        rules.clear();
        int width = bar.getWidth(), top = metric(DesignMetrics.Role.HAIRLINE), height = Math.max(0, bar.getHeight() - top);
        int pad = metric(DesignMetrics.Role.STATUS_CELL_PADDING), line = metric(DesignMetrics.Role.HAIRLINE);
        int x = 0;
        if (bar.runItem() != null) {
            int run = Math.min(width, bar.runItem().getPreferredSize().width);
            bar.runItem().setBounds(0, 0, run, bar.getHeight());
            x = run;
            if (x < width) { rules.add(x); x += line; }
        }
        // The right cells first, from the right edge, so the left cells take what is left; a cell with no room collapses.
        int right = width;
        var config = bar.configButton();
        for (JComponent cell : List.of(config, encoding, grid, bar.contentItems, bar.rightItems)) {
            int start = Math.max(x, right - cell.getPreferredSize().width - 2 * pad), inner = right - start - 2 * pad;
            if (empty(bar, cell) || inner <= 0) { cell.setBounds(0, 0, 0, 0); continue; }
            cell.setBounds(start + pad, top, inner, height);
            right = start - line; rules.add(right);
        }
        int dot = metric(DesignMetrics.Role.STATUS_DOT), dotGap = metric(DesignMetrics.Role.STATUS_CELL_DOT_GAP);
        // A content tab's title has no session dot.
        int lead = bar.showingContent() ? 0 : dot + dotGap;
        int sessionContent = Math.min(lead + session.getPreferredSize().width, right - x - 2 * pad);
        if (!session.getText().isEmpty() && sessionContent > 0) {
            sessionDotX = lead > 0 && sessionContent >= dot ? x + pad : -1;
            session.setBounds(x + pad + lead, top, Math.max(0, sessionContent - lead), height);
            x += sessionContent + 2 * pad;
            if (x < right) { rules.add(x); x += line; }
        } else { sessionDotX = -1; session.setBounds(0, 0, 0, 0); }
        for (JComponent cell : List.of(folder, bar.leftItems)) {
            int content = Math.min(cell.getPreferredSize().width, right - x - 2 * pad);
            if (empty(bar, cell) || content <= 0) { cell.setBounds(0, 0, 0, 0); continue; }
            cell.setBounds(x + pad, top, content, height);
            x += content + 2 * pad;
            if (x < right) { rules.add(x); x += line; }
        }
    }

    /** Whether {@code cell} has nothing to show; the session's "UTF-8" is not a content tab's encoding. */
    private boolean empty(WindowStatusBar bar, JComponent cell) {
        if (cell == bar.configButton()) return !cell.isVisible();
        if (cell == encoding && bar.showingContent()) return true;
        return cell instanceof JLabel label ? label.getText().isEmpty() : cell.getComponentCount() == 0;
    }

    @Override public void paint(WindowStatusBar bar, Graphics graphics) {
        var theme = ThemeZone.theme(null);
        var palette = theme.semantic();
        int line = metric(DesignMetrics.Role.HAIRLINE), height = bar.getHeight();
        graphics.setColor(palette.color(SemanticColor.SURFACE_APP));
        graphics.fillRect(0, 0, bar.getWidth(), height);
        var config = bar.configButton();
        if (config.isVisible() && config.getWidth() > 0 && bar.configurationProblem() != null) {
            DerivedColor ground = bar.configurationProblem() == SemanticColor.STATUS_DANGER ? DerivedColor.DANGER_GROUND : DerivedColor.WARNING_GROUND;
            int pad = metric(DesignMetrics.Role.STATUS_CELL_PADDING);
            graphics.setColor(ground.color(theme));
            graphics.fillRect(config.getX() - pad, line, bar.getWidth() - config.getX() + pad, height - line);
        }
        graphics.setColor(SurfaceContrast.painted(theme, WindowPairs.RULE));
        graphics.fillRect(0, 0, bar.getWidth(), line);
        graphics.setColor(DerivedColor.BORDER_DIVIDER.color(theme));
        for (int x : rules) graphics.fillRect(x, line, line, height - line);
        // The rule between two left cells is drawn above; in the divider's colour, each contributed item is a cell, ruled
        // halfway between it and the item before it (in the strut between two status items, between two content items).
        for (var row : new JComponent[] {bar.leftItems, bar.rightItems, bar.contentItems}) {
            if (row.getWidth() == 0) continue;
            int previousEnd = -1;
            for (Component child : row.getComponents()) {
                if (child instanceof Box.Filler || !child.isVisible()) continue;
                if (previousEnd >= 0) graphics.fillRect(row.getX() + (previousEnd + child.getX() - line) / 2, line, line, height - line);
                previousEnd = child.getX() + child.getWidth();
            }
        }
        if (sessionDotX >= 0 && !bar.showingContent()) {
            int dot = metric(DesignMetrics.Role.STATUS_DOT);
            graphics.setColor(SurfaceContrast.painted(theme, WindowPairs.sessionDot(bar.running())));
            // On a copy: the bar's own graphics go on to paint its border and children.
            var g = (java.awt.Graphics2D) graphics.create();
            try {
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.fillOval(sessionDotX, line + (height - line - dot) / 2, dot, dot);
            } finally { g.dispose(); }
        }
    }
}
