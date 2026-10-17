package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * Ruled status cells (spec 10.6; the Workstation mocks): on {@code surface.app} under a 1 px {@code border.window}
 * rule, cells divided by 1 px {@code border.divider} rules: the run item, the session (its dot and name) and the
 * folder on the left; contributed items as cells; then the grid size in the data face, "UTF-8" and, while there is a
 * problem, the configuration on {@code warning.ground} or {@code danger.ground} with "▲" to the right edge.
 */
final class RuledCells implements StatusCells {
    private final JLabel session = label("statusSession"), folder = label("statusFolder"), grid = label("statusGrid"), encoding = label("statusEncoding");
    /** Where each cell rule is, in the bar. */
    private final List<Integer> rules = new ArrayList<>();
    private int sessionDotX = -1;

    private static JLabel label(String name) { var label = new JLabel(); label.setName(name); label.putClientProperty("html.disable", true); return label; }
    private static int metric(DesignMetrics.Role role) { return UIScale.scale(FlatLafInstaller.installedKit().metrics().value(role)); }

    @Override public void install(WindowStatusBar bar) {
        encoding.setText("UTF-8");
        bar.add(session); bar.add(folder); bar.add(bar.leftItems); bar.add(bar.rightItems); bar.add(bar.contentItems);
        bar.add(grid); bar.add(encoding); bar.add(bar.configButton());
    }

    @Override public int itemGap() { return 2 * metric(DesignMetrics.Role.STATUS_CELL_PADDING) + metric(DesignMetrics.Role.HAIRLINE); }

    @Override public void refresh(WindowStatusBar bar) {
        var theme = ThemeZone.theme(null);
        var palette = theme.semantic();
        Color text = SurfaceContrast.on(palette, SemanticColor.TEXT_STRONG_SECONDARY, SemanticColor.SURFACE_APP, SurfaceContrast.TEXT);
        session.setText(bar.session());
        folder.setText(bar.folder());
        grid.setText(bar.dimensions());
        for (JLabel label : List.of(session, folder, encoding)) { label.setFont(ThemeZone.font(Typography.Role.STATUS_TEXT)); label.setForeground(text); }
        grid.setFont(ThemeZone.font(Typography.Role.MONO)); grid.setForeground(text);
        for (var row : new JComponent[] {bar.leftItems, bar.rightItems, bar.contentItems})
            for (Component child : row.getComponents()) if (child instanceof JButton item) { item.setForeground(text); item.setFont(ThemeZone.font(Typography.Role.STATUS_TEXT)); }
        var config = bar.configButton();
        boolean problem = bar.configurationProblem() != null;
        config.setVisible(problem);
        if (problem) {
            config.setText("▲ " + bar.configurationText());
            DerivedColor ground = bar.configurationProblem() == SemanticColor.STATUS_DANGER ? DerivedColor.DANGER_GROUND : DerivedColor.WARNING_GROUND;
            config.setForeground(SurfaceContrast.painted(theme, new PaintPair(bar.configurationProblem(), ground, SurfaceContrast.Guard.TEXT)));
            config.setFont(ThemeZone.font(Typography.Role.STATUS_TEXT));
        }
        bar.revalidate(); bar.repaint();
    }

    @Override public void layout(WindowStatusBar bar) {
        rules.clear();
        int width = bar.getWidth(), top = metric(DesignMetrics.Role.HAIRLINE), height = Math.max(0, bar.getHeight() - top);
        int pad = metric(DesignMetrics.Role.STATUS_CELL_PADDING), line = metric(DesignMetrics.Role.HAIRLINE);
        int x = 0;
        if (bar.runItem() != null) {
            int run = bar.runItem().getPreferredSize().width;
            bar.runItem().setBounds(0, 0, run, bar.getHeight());
            x = run; rules.add(x); x += line;
        }
        // The right cells first, from the right edge, so the left cells take what is left.
        int right = width;
        var config = bar.configButton();
        if (config.isVisible()) {
            int cell = config.getPreferredSize().width + 2 * pad;
            config.setBounds(Math.max(x, right - cell) + pad, top, cell - 2 * pad, height);
            right -= cell; rules.add(right - line); right -= line;
        } else config.setBounds(0, 0, 0, 0);
        for (JComponent cell : List.of(encoding, grid, bar.contentItems, bar.rightItems)) {
            boolean empty = cell.getComponentCount() == 0 && !(cell instanceof JLabel) || cell instanceof JLabel label && label.getText().isEmpty();
            if (empty) { cell.setBounds(0, 0, 0, 0); continue; }
            int content = cell.getPreferredSize().width, span = content + 2 * pad;
            int start = Math.max(x, right - span);
            cell.setBounds(start + pad, top, Math.max(0, right - start - 2 * pad), height);
            right = start; rules.add(right - line); right -= line;
        }
        int dot = metric(DesignMetrics.Role.STATUS_DOT), dotGap = metric(DesignMetrics.Role.STATUS_CELL_DOT_GAP);
        if (!session.getText().isEmpty()) {
            sessionDotX = x + pad;
            int content = dot + dotGap + session.getPreferredSize().width;
            session.setBounds(x + pad + dot + dotGap, top, Math.max(0, Math.min(content, right - x - 2 * pad) - dot - dotGap), height);
            x += content + 2 * pad; rules.add(x); x += line;
        } else { sessionDotX = -1; session.setBounds(0, 0, 0, 0); }
        for (JComponent cell : List.of(folder, bar.leftItems)) {
            boolean empty = cell instanceof JLabel label ? label.getText().isEmpty() : cell.getComponentCount() == 0;
            if (empty || x >= right) { cell.setBounds(0, 0, 0, 0); continue; }
            int content = Math.min(cell.getPreferredSize().width, Math.max(0, right - x - 2 * pad));
            cell.setBounds(x + pad, top, content, height);
            x += content + 2 * pad; rules.add(x); x += line;
        }
    }

    @Override public void paint(WindowStatusBar bar, Graphics graphics) {
        var theme = ThemeZone.theme(null);
        var palette = theme.semantic();
        int line = metric(DesignMetrics.Role.HAIRLINE), height = bar.getHeight();
        graphics.setColor(palette.color(SemanticColor.SURFACE_APP));
        graphics.fillRect(0, 0, bar.getWidth(), height);
        var config = bar.configButton();
        if (config.isVisible() && bar.configurationProblem() != null) {
            DerivedColor ground = bar.configurationProblem() == SemanticColor.STATUS_DANGER ? DerivedColor.DANGER_GROUND : DerivedColor.WARNING_GROUND;
            int pad = metric(DesignMetrics.Role.STATUS_CELL_PADDING);
            graphics.setColor(ground.color(theme));
            graphics.fillRect(config.getX() - pad, line, bar.getWidth() - config.getX() + pad, height - line);
        }
        graphics.setColor(SurfaceContrast.painted(theme, new PaintPair(SemanticColor.BORDER_WINDOW, SemanticColor.SURFACE_APP, SurfaceContrast.Guard.STRUCTURAL)));
        graphics.fillRect(0, 0, bar.getWidth(), line);
        graphics.setColor(DerivedColor.BORDER_DIVIDER.color(theme));
        for (int x : rules) graphics.fillRect(x, line, line, height - line);
        // The rule between two left cells is drawn above; in the divider's colour, the cell rules of the contributed rows are their gaps.
        int gap = itemGap();
        for (var row : new JComponent[] {bar.leftItems, bar.rightItems, bar.contentItems}) {
            if (row.getWidth() == 0) continue;
            int previousEnd = -1;
            for (Component child : row.getComponents()) {
                if (!(child instanceof JButton) || !child.isVisible()) continue;
                if (previousEnd >= 0) graphics.fillRect(row.getX() + previousEnd + (gap - line) / 2, line, line, height - line);
                previousEnd = child.getX() + child.getWidth();
            }
        }
        if (sessionDotX >= 0 && !bar.showingContent()) {
            int dot = metric(DesignMetrics.Role.STATUS_DOT);
            graphics.setColor(SurfaceContrast.on(palette, bar.running() ? SemanticColor.STATUS_SUCCESS : SemanticColor.STATUS_IDLE, SemanticColor.SURFACE_APP, SurfaceContrast.INDICATOR));
            ((java.awt.Graphics2D) graphics).setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.fillOval(sessionDotX, line + (height - line - dot) / 2, dot, dot);
        }
    }
}
