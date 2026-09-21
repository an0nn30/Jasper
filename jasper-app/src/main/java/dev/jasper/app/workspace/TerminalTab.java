package dev.jasper.app.workspace;

import dev.jasper.app.launch.ShellLauncher;
import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;

/** Split-tree renderer that reparents existing panes without replacing sessions. */
public final class TerminalTab extends JPanel implements AutoCloseable {
    private final ShellLauncher launcher;
    private final UUID id = UUID.randomUUID();
    private final TabState state = new TabState();
    private final Map<UUID, TerminalPane> panes = new LinkedHashMap<>();
    private final SplitTree tree;
    private final Map<UUID, JSplitPane> splits = new LinkedHashMap<>();
    private long themeGeneration;
    private boolean updatingTheme;
    private long renderGeneration;
    private boolean closed;
    private boolean active = true;
    Runnable onChanged = () -> {};
    Runnable onEmpty = () -> {};
    Consumer<String> onError = message -> {};
    Consumer<TerminalPane> onPaneCreated = pane -> {};
    Consumer<TerminalPane> configure = pane -> {};

    TerminalTab(Path directory, ShellLauncher launcher) {
        super(new BorderLayout());
        this.launcher = launcher;
        TerminalPane pane = createPane(directory);
        tree = new SplitTree(pane.id());
        render();
    }

    void start() { focusedPane().start(); }

    private TerminalPane createPane(Path directory) {
        TerminalPane pane = new TerminalPane(directory, launcher);
        panes.put(pane.id(), pane);
        pane.onChanged = () -> onChanged.run();
        pane.onFocused = () -> focus(pane);
        pane.onClose = () -> closePane(pane);
        pane.onFailure = message -> {
            onError.accept(message);
            closePane(pane);
        };
        pane.onReady = view -> configure.accept(pane);
        onPaneCreated.accept(pane);
        return pane;
    }

    public UUID id() { return id; }
    TerminalPane focusedPane() { return tree.focused().map(panes::get).orElse(null); }
    public java.util.List<TerminalPane> panes() { return java.util.List.copyOf(panes.values()); }
    SplitTree tree() { return tree; }
    String title() {
        TerminalPane pane = focusedPane();
        return state.title(pane == null ? null : pane.tabTitle(), pane == null ? null : pane.directory());
    }
    void rename(String name) { state.rename(name); onChanged.run(); }

    public void split(SplitTree.Axis axis) { split(focusedPane(), axis, null); }

    /** Splits a given pane, starting the new one in {@code directoryOrNull} or where the target is. Null when it cannot. */
    TerminalPane split(TerminalPane target, SplitTree.Axis axis, Path directoryOrNull) {
        if (closed || target == null || panes.get(target.id()) != target || !target.running()) return null;
        if (focusedPane() != target) focus(target);
        TerminalPane pane = createPane(directoryOrNull == null ? target.directory() : directoryOrNull);
        tree.split(pane.id(), axis); render(); onChanged.run(); pane.start();
        return pane;
    }

    void focus(TerminalPane pane) {
        if (closed || !panes.containsKey(pane.id())) return;
        boolean changed = focusedPane() != pane;
        tree.focus(pane.id());
        if (tree.zoomed() && changed) render(); else refreshActive();
        onChanged.run();
    }

    void navigate(SplitTree.Direction direction) {
        tree.navigate(direction);
        if (tree.zoomed()) render(); else refreshActive();
        if (focusedPane() != null) focusedPane().focusTerminal();
        onChanged.run();
    }

    void toggleZoom() { tree.toggleZoom(); render(); focusTerminal(); onChanged.run(); }
    void focusTerminal() { if (focusedPane() != null) focusedPane().focusTerminal(); }
    void setActive(boolean value) { active = value; refreshActive(); }
    private void refreshActive() {
        panes.values().forEach(pane -> pane.setActive(active && pane == focusedPane()));
    }

    void closePane(TerminalPane pane) {
        if (closed || panes.remove(pane.id()) == null) return;
        tree.close(pane.id()); pane.close(); render(); onChanged.run();
        if (panes.isEmpty()) onEmpty.run(); else focusTerminal();
    }

    private void render() {
        long generation = ++renderGeneration;
        removeAll(); splits.clear();
        if (!panes.isEmpty()) add(tree.zoomed() ? focusedPane() : renderNode(tree.root().orElseThrow(), generation));
        refreshActive(); revalidate(); repaint();
    }

    private JComponent renderNode(SplitTree.Node node, long generation) {
        if (node instanceof SplitTree.Leaf leaf) return panes.get(leaf.paneId());
        SplitTree.Branch branch = (SplitTree.Branch) node;
        JSplitPane split = new JSplitPane(branch.axis() == SplitTree.Axis.RIGHT
            ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT,
            renderNode(branch.first(), generation), renderNode(branch.second(), generation));
        splits.put(branch.id(), split);
        split.setContinuousLayout(true); split.setBorder(null);
        split.setResizeWeight(branch.ratio());
        boolean[] restoring = {true};
        Runnable restore = () -> {
            if (closed || generation != renderGeneration) return;
            int size = split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT ? split.getWidth() : split.getHeight();
            if (size <= split.getDividerSize()) return;
            restoring[0] = true;
            split.setDividerLocation(currentRatio(tree.root().orElseThrow(), branch.id()));
            restoring[0] = false;
        };
        split.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                if (restoring[0]) restore.run();
            }
        });
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
            if (closed || generation != renderGeneration || restoring[0] || updatingTheme) return;
            int size = (split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT ? split.getWidth() : split.getHeight())
                - split.getDividerSize();
            if (size > 0) {
                double ratio = Math.clamp((double) split.getDividerLocation() / size, 0.1, 0.9);
                tree.setRatio(branch.id(), ratio); split.setResizeWeight(ratio);
            }
        });
        SwingUtilities.invokeLater(restore);
        return split;
    }

    void beginThemeUpdate() { updatingTheme = true; ++themeGeneration; }

    void endThemeUpdate() {
        long generation = themeGeneration;
        restoreThemeDividers();
        // FlatLaf replacement and validation can queue another layout on the EDT.
        SwingUtilities.invokeLater(() -> {
            if (closed || generation != themeGeneration) return;
            restoreThemeDividers(); updatingTheme = false;
        });
    }

    private void restoreThemeDividers() {
        for (var entry : splits.entrySet()) {
            JSplitPane split = entry.getValue();
            double ratio = currentRatio(tree.root().orElseThrow(), entry.getKey());
            split.setResizeWeight(ratio);
            split.doLayout();
            int size = split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT ? split.getWidth() : split.getHeight();
            if (size > split.getDividerSize()) split.setDividerLocation(ratio);
        }
    }

    private static double currentRatio(SplitTree.Node node, UUID id) {
        if (node instanceof SplitTree.Branch branch) {
            if (branch.id().equals(id)) return branch.ratio();
            double first = currentRatio(branch.first(), id);
            return Double.isNaN(first) ? currentRatio(branch.second(), id) : first;
        }
        return Double.NaN;
    }

    @Override public void close() {
        if (closed) return;
        closed = true; ++renderGeneration;
        panes.values().forEach(TerminalPane::close); panes.clear(); splits.clear(); removeAll();
        onChanged = () -> {}; onEmpty = () -> {}; configure = pane -> {}; onPaneCreated = pane -> {}; onError = message -> {};
    }
}
