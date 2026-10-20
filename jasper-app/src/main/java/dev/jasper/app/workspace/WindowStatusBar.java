package dev.jasper.app.workspace;

import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.NativeStyles;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.contributions.StatusEntry;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.swing.*;

/**
 * Bounded status segments: long shell/path metadata never displaces dimensions/defaults. The look's {@link StatusCells}
 * lays the model out (spec 10.6): today's slash segments or ruled cells, each led by the run item when the look has one.
 */
final class WindowStatusBar extends JPanel {
    final PathBreadcrumbs breadcrumbs = new PathBreadcrumbs();
    final Segment left = new Segment(breadcrumbs);
    private final JButton configButton = new JButton("Built-in defaults");
    final Segment right = new Segment(configButton);
    private final java.util.Map<String, JComponent> itemViews = new java.util.HashMap<>();
    final Box contentItems = Box.createHorizontalBox();
    private java.util.List<dev.jasper.app.contributions.ContentStatus> contentStatus = java.util.List.of();
    private boolean showingContent;
    final Box leftItems = Box.createHorizontalBox();
    final Box rightItems = Box.createHorizontalBox();
    Runnable onConfigurationDetails = () -> {};
    private String configText = "Built-in defaults";
    private SemanticColor configColor = SemanticColor.STATUS_SUCCESS;
    private boolean configProblem;
    private String shell = "", directory = "", dimensions = "", session = "", folder = "";
    private boolean running;
    private boolean integration;
    private String text = "Built-in defaults";
    /** Height in logical pixels; the bar is taller only when the status font needs more. */
    private int barHeight = ConfigSnapshot.DEFAULT_STATUS_BAR_HEIGHT;
    private final StatusCells cells;
    private final RunItem run;

    WindowStatusBar() { this(new SlashSegments(), null); }

    /** A bar laid out by {@code cells}, led by {@code run} when it is not null. */
    WindowStatusBar(StatusCells cells, RunItem run) {
        super(null);
        this.cells = java.util.Objects.requireNonNull(cells);
        this.run = run;
        configButton.setBorder(BorderFactory.createEmptyBorder());
        configButton.setContentAreaFilled(false); configButton.setOpaque(false);
        configButton.setEnabled(false); configButton.putClientProperty("html.disable", true);
        configButton.addActionListener(event -> onConfigurationDetails.run());
        if (run != null) add(run);
        cells.install(this);
        refreshTheme();
        getAccessibleContext().setAccessibleName("Terminal status");
        dev.jasper.app.platform.AppIcons.chromeReceiver(this);
    }

    StatusCells cells() { return cells; }
    RunItem runItem() { return run; }
    boolean showingContent() { return showingContent; }
    boolean running() { return running; }
    String session() { return session; }
    String folder() { return folder; }
    /** The pane's full directory, as the slash layout's tip gives it. */
    String directory() { return directory; }
    /** Whether the shell has marked a prompt, as a tip; null without a shell. */
    String integrationToolTip() { return shell.isEmpty() ? null : integration ? "Shell integration active" : "Shell integration not detected"; }
    String dimensions() { return dimensions; }
    String configurationText() { return configText; }
    /** The status role of a configuration problem, or null when there is none. */
    SemanticColor configurationProblem() { return configProblem ? configColor : null; }

    /** Sets the height in logical pixels; the caller validates the range. */
    void setBarHeight(int height) {
        if (barHeight == height) return;
        barHeight = height;
        revalidate(); repaint();
    }

    void setMetadata(dev.jasper.app.terminals.PaneSnapshot pane, String dimensions, boolean running) {
        var location = PathBreadcrumbs.Location.from(pane, java.nio.file.Path.of(System.getProperty("user.home")));
        this.running = running; this.shell = pane == null ? "" : pane.shell();
        this.directory = location.path(); this.dimensions = dimensions;
        this.integration = pane != null && pane.shellIntegration();
        this.session = WindowTitle.sessionCell(pane);
        this.folder = WindowTitle.directory(pane, java.nio.file.Path.of(System.getProperty("user.home")));
        breadcrumbs.setLocation(location);
        // The remote breadcrumb already names the provider's host.
        left.setParts(pane != null && pane.providerId().isPresent() ? "" : shellLabel(), directory);
        left.setToolTipText(directory);
        left.setFirstToolTip(integrationToolTip());
        updateText();
    }

    void setContent(dev.jasper.app.contributions.ContentEntry entry) {
        showingContent = entry != null;
        var items = entry == null ? java.util.List.<dev.jasper.app.contributions.ContentStatus>of() : entry.status();
        if (!items.equals(contentStatus)) {
            contentStatus = items; contentItems.removeAll();
            for (var item : items) {
                var button = new JButton(item.text()); button.putClientProperty("html.disable", true);
                button.setToolTipText(item.tooltip()); button.setBorder(BorderFactory.createEmptyBorder(0, NativeStyles.installed().metric(DesignMetrics.Role.STATUS_ITEM_GAP), 0, NativeStyles.installed().metric(DesignMetrics.Role.STATUS_ITEM_GAP)));
                button.setContentAreaFilled(false); button.setFocusable(false); button.setFont(statusFont());
                button.setForeground(muted()); if (item.run() != null) button.addActionListener(event -> item.run().run());
                contentItems.add(button);
            }
        }
        if (entry != null) {
            var crumbs = new ArrayList<PathBreadcrumbs.Crumb>();
            if (!entry.host().isEmpty()) crumbs.add(new PathBreadcrumbs.Crumb(entry.host(), entry.host(), "SERVER"));
            String path = entry.location();
            if (!entry.host().isEmpty() && path.indexOf(":/") >= 0) path = path.substring(path.indexOf(":/") + 1);
            // A location supplied by a plugin stays text, including on remote hosts.
            String[] parts = path.replace('\\', '/').split("/");
            String built = path.startsWith("/") ? "/" : "";
            for (String part : parts) if (!part.isEmpty()) {
                built += (built.isEmpty() || built.endsWith("/") ? "" : "/") + part;
                crumbs.add(new PathBreadcrumbs.Crumb(part, built, crumbs.isEmpty() ? "FOLDER" : null));
            }
            breadcrumbs.setLocation(new PathBreadcrumbs.Location(crumbs, path));
            left.setParts("", path); dimensions = ""; folder = path; session = entry.title(); updateText();
        }
        cells.refresh(this);
        revalidate(); repaint();
    }

    void setConfiguration(ConfigService.State state) {
        boolean error = state.diagnostics().stream().anyMatch(d -> d.severity() == ConfigDiagnostic.Severity.ERROR);
        boolean warning = !state.diagnostics().isEmpty();
        configText = error ? "Config error" : warning ? "Config warnings" : state.present() ? "Config loaded" : "Built-in defaults";
        if (warning) state.diagnostics().stream().filter(d -> d.line() > 0).findFirst()
            .ifPresent(d -> configText += " (line " + d.line() + ")");
        configColor = error ? SemanticColor.STATUS_DANGER : warning ? SemanticColor.STATUS_WARNING : SemanticColor.STATUS_SUCCESS;
        configProblem = warning;
        configButton.setToolTipText(state.file().toString());
        configButton.setEnabled(true);
        updateText(); refreshTheme();
    }

    /** The shell name with its integration dot: filled once the shell has marked a prompt. */
    private String shellLabel() {
        return shell.isEmpty() ? "" : shell + (integration ? " \u25cf" : " \u25cb");
    }

    private void updateText() {
        right.setParts(dimensions, configText);
        configButton.getAccessibleContext().setAccessibleName(configText);
        text = shell.isEmpty() ? configText
            : shellLabel() + "  |  " + directory + "  |  " + dimensions + "  |  " + configText;
        // A screen reader would otherwise announce the dot's Unicode name; say what it means instead.
        String spoken = shell.isEmpty() ? ""
            : shell + (integration ? ", shell integration active" : ", shell integration not detected");
        getAccessibleContext().setAccessibleDescription(shell.isEmpty() ? text
            : spoken + "  |  " + directory + "  |  " + dimensions + "  |  " + configText);
        cells.refresh(this);
        revalidate(); repaint();
    }
    /** Replaces the contributed items. An item with a live action is a button; any other is inert text. */
    void setContributed(List<StatusEntry> entries, Function<String, Action> actions) {
        var leftViews = new ArrayList<JComponent>();
        var rightViews = new ArrayList<JComponent>();
        var liveIds = new java.util.HashSet<String>();
        for (StatusEntry entry : entries) {
            if (!entry.visible()) continue;
            liveIds.add(entry.id());
            JComponent view = itemViews.get(entry.id());
            if (entry.progress() != null) {
                if (!(view instanceof StatusProgressView)) view = new StatusProgressView();
                ((StatusProgressView) view).update(entry.progress(), actions);
            } else {
                if (!(view instanceof JButton)) {
                    var item = new JButton();
                    item.setBorder(BorderFactory.createEmptyBorder()); item.setContentAreaFilled(false); item.setOpaque(false);
                    item.setFocusable(false); item.putClientProperty("html.disable", true);
                    item.setIconTextGap(NativeStyles.installed().metric(DesignMetrics.Role.STATUS_ITEM_GAP));
                    view = item;
                }
                var item = (JButton) view;
                item.setText(entry.text()); item.setIcon(entry.icon()); item.setToolTipText(entry.tooltip());
                item.getAccessibleContext().setAccessibleName(entry.text().isEmpty() && entry.tooltip() != null ? entry.tooltip() : entry.text());
                for (var listener : item.getActionListeners()) item.removeActionListener(listener);
                Action action = entry.actionId() == null ? null : actions.apply(entry.actionId());
                item.setEnabled(action == null || action.isEnabled());
                item.setRolloverEnabled(action != null);
                if (action != null) item.addActionListener(event -> { if (action.isEnabled()) action.actionPerformed(event); });
            }
            itemViews.put(entry.id(), view);
            (entry.left() ? leftViews : rightViews).add(view);
        }
        itemViews.keySet().retainAll(liveIds);
        replaceStructure(leftItems, leftViews, cells.itemGap());
        replaceStructure(rightItems, rightViews, cells.itemGap());
        refreshTheme();
        revalidate(); repaint();
    }

    private static void replaceStructure(Box row, List<JComponent> views, int gap) {
        var current = java.util.Arrays.stream(row.getComponents()).filter(c -> !(c instanceof Box.Filler)).toList();
        if (current.equals(views)) return;
        row.removeAll();
        for (var view : views) {
            if (row.getComponentCount() > 0) row.add(Box.createHorizontalStrut(gap));
            row.add(view);
        }
    }

    List<JButton> contributedItems(boolean leftSide) {
        List<JButton> items = new ArrayList<>();
        for (Component child : (leftSide ? leftItems : rightItems).getComponents()) if (child instanceof JButton button) items.add(button);
        return items;
    }

    JButton configButton() { return configButton; }
    String getText() { return text; }
    static java.awt.Font statusFont() {
        return ThemeZone.font(Typography.Role.STATUS_TEXT);
    }

    /** A status color as painted on the status surface: held to the text floor there, shared role untouched. */
    private static Color onStatus(SemanticColor role) {
        return dev.jasper.ui.theme.SurfaceContrast.on(ThemeZone.theme(null).semantic(), role, SemanticColor.SURFACE_STATUS,
            dev.jasper.ui.theme.SurfaceContrast.TEXT);
    }
    Color muted() { return ThemeZone.color(null, SemanticColor.TEXT_SECONDARY); }
    private static Color surface() {
        return ThemeZone.color(null, SemanticColor.SURFACE_STATUS);
    }
    void refreshTheme() {
        setBackground(surface());
        left.refreshTheme(); right.refreshTheme(); breadcrumbs.refreshTheme();
        for (Box row : new Box[]{leftItems, rightItems, contentItems})
            for (Component child : row.getComponents()) if (child instanceof JButton item) {
                item.setForeground(muted());
                item.setFont(statusFont());
            } else if (child instanceof StatusProgressView progress) progress.refreshTheme();
        configButton.setForeground(onStatus(configColor));
        if (run != null) { run.setFont(statusFont()); run.repaint(); }
        cells.refresh(this);
    }

    final class Segment extends JPanel {
        private final JLabel first = new JLabel(), slash = new JLabel("/", SwingConstants.CENTER);
        private final JComponent last;
        Segment(JComponent last) {
            super(null); setOpaque(false); this.last = last;
            first.putClientProperty("html.disable", true); last.putClientProperty("html.disable", true);
            add(first); add(slash); add(last);
        }
        void setParts(String first, String last) {
            this.first.setText(first);
            if (this.last instanceof JLabel label) label.setText(last);
            else if (this.last instanceof JButton button && !(cells instanceof RuledCells)) button.setText(last);
            slash.setVisible(!first.isEmpty());
        }
        void setFirstToolTip(String tip) { first.setToolTipText(tip); }
        void refreshTheme() {
            for (JComponent label : new JComponent[]{first, slash, last}) {
                if (label == last && cells instanceof RuledCells) continue;
                label.setForeground(label == slash ? ThemeZone.color(null, SemanticColor.BORDER_SUBTLE) : muted());
                label.setFont(statusFont());
            }
        }
        @Override public Dimension getPreferredSize() {
            return new Dimension(first.getPreferredSize().width + (slash.isVisible() ? NativeStyles.installed().metric(DesignMetrics.Role.STATUS_SEPARATOR) : 0)
                + last.getPreferredSize().width, Math.max(first.getPreferredSize().height, last.getPreferredSize().height));
        }
        @Override public void doLayout() {
            int firstWidth = Math.min(last instanceof PathBreadcrumbs ? getWidth() / 3 : getWidth(), first.getPreferredSize().width);
            int separatorWidth = Math.min(Math.max(0, getWidth() - firstWidth), slash.isVisible() ? NativeStyles.installed().metric(DesignMetrics.Role.STATUS_SEPARATOR) : 0);
            first.setBounds(0, 0, firstWidth, getHeight());
            slash.setBounds(firstWidth, 0, separatorWidth, getHeight());
            last.setBounds(firstWidth + separatorWidth, 0, Math.max(0, getWidth() - firstWidth - separatorWidth), getHeight());
        }
    }
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    @Override public Dimension getPreferredSize() {
        // Items and segments are laid out at the bar's height; only the text sets a floor, so it never clips.
        return new Dimension(0, Math.max(UIScale.scale(barHeight), Math.max(left.getPreferredSize().height, getFontMetrics(statusFont()).getHeight())));
    }
    @Override public void doLayout() { cells.layout(this); }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        cells.paint(this, g);
    }
}
