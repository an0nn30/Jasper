package dev.jasper.app.settingsui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/** The Settings category tree: bold top-level pages, chevrons, blue modified pages and search counts. EDT only. */
final class PageTree extends JTree {
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    private final DefaultTreeModel model = new DefaultTreeModel(root);
    private final Set<String> expanded = new HashSet<>();
    private final Map<String, DefaultMutableTreeNode> nodes = new HashMap<>();
    private Map<String, Integer> counts = Map.of();
    private Predicate<String> modified = id -> false;
    private boolean rebuilding;
    private boolean searching;

    PageTree(Consumer<String> selected) {
        setModel(model);
        setRootVisible(false);
        setShowsRootHandles(true);
        setToggleClickCount(1);
        setCellRenderer(new Row());
        setBorder(BorderFactory.createEmptyBorder(UIScale.scale(4), UIScale.scale(4), UIScale.scale(4), UIScale.scale(4)));
        putClientProperty(FlatClientProperties.STYLE, "icon.arrowType: chevron");
        putClientProperty(FlatClientProperties.TREE_WIDE_SELECTION, true);
        putClientProperty(FlatClientProperties.TREE_WIDE_CELL_RENDERER, true);
        getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        getAccessibleContext().setAccessibleName("Settings categories");
        addTreeSelectionListener(event -> {
            if (!rebuilding) selectedId().ifPresent(selected);
        });
        addTreeExpansionListener(new TreeExpansionListener() {
            @Override public void treeExpanded(TreeExpansionEvent event) { remember(event, true); }
            @Override public void treeCollapsed(TreeExpansionEvent event) { remember(event, false); }
            private void remember(TreeExpansionEvent event, boolean open) {
                if (rebuilding || searching) return;
                if (((DefaultMutableTreeNode) event.getPath().getLastPathComponent()).getUserObject() instanceof SettingsPage page) {
                    if (open) expanded.add(page.id()); else expanded.remove(page.id());
                }
            }
        });
    }

    @Override public void updateUI() {
        super.updateUI();
        setBackground(UIManager.getColor("Panel.background"));
        if (getFont() != null) setRowHeight(getFontMetrics(getFont()).getHeight() + UIScale.scale(10));
    }

    /**
     * Shows the pages {@code visible} accepts, with their ancestors; a page that hides while childless never shows
     * without children. Searching expands everything; otherwise remembered expansion applies (groups start open).
     */
    void show(List<SettingsPage> pages, Predicate<SettingsPage> visible, Map<String, Integer> counts, boolean searching) {
        String selection = selectedId().orElse(null);
        this.counts = Map.copyOf(counts);
        this.searching = searching;
        rebuilding = true;
        try {
            if (expanded.isEmpty() && !searching)
                for (SettingsPage page : pages) if (page.parent().isEmpty()) expanded.add(page.id());
            root.removeAllChildren();
            nodes.clear();
            var keep = new HashSet<String>();
            for (SettingsPage page : pages) {
                boolean childless = pages.stream().noneMatch(other -> other.parent().equals(Optional.of(page.id())));
                if (page.hiddenWhenChildless() && childless) continue;
                if (!visible.test(page)) continue;
                for (String id = page.id(); id != null; id = find(pages, id).flatMap(SettingsPage::parent).orElse(null)) keep.add(id);
            }
            for (SettingsPage page : pages) {
                if (!keep.contains(page.id())) continue;
                var node = new DefaultMutableTreeNode(page);
                nodes.put(page.id(), node);
                page.parent().map(nodes::get).orElse(root).add(node);
            }
            model.reload();
            for (var node : nodes.values()) {
                var page = (SettingsPage) node.getUserObject();
                if (searching || expanded.contains(page.id())) expandPath(new TreePath(node.getPath()));
            }
            if (selection != null && nodes.containsKey(selection)) setSelectionPath(new TreePath(nodes.get(selection).getPath()));
        } finally { rebuilding = false; }
    }

    @Override public boolean getScrollableTracksViewportWidth() { return true; }

    private static Optional<SettingsPage> find(List<SettingsPage> pages, String id) {
        return pages.stream().filter(page -> page.id().equals(id)).findFirst();
    }

    /** Selects {@code id} without reporting it back, expanding its ancestors. */
    void select(String id) {
        var node = nodes.get(id);
        if (node == null) return;
        rebuilding = true;
        try {
            var path = new TreePath(node.getPath());
            if (path.getParentPath() != null) expandPath(path.getParentPath());
            setSelectionPath(path);
            scrollPathToVisible(path);
        } finally { rebuilding = false; }
    }

    Optional<String> selectedId() {
        return getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof SettingsPage page
            ? Optional.of(page.id()) : Optional.empty();
    }

    void modifiedBy(Predicate<String> value) { modified = value; repaint(); }

    boolean isModified(String id) { return modified.test(id); }

    int count(String id) { return counts.getOrDefault(id, 0); }

    /** Page ids in display order; for tests. */
    List<String> visibleIds() {
        var ids = new ArrayList<String>();
        Enumeration<?> all = root.preorderEnumeration();
        while (all.hasMoreElements())
            if (((DefaultMutableTreeNode) all.nextElement()).getUserObject() instanceof SettingsPage page) ids.add(page.id());
        return ids;
    }

    /** Title at left (bold at the top level, blue when modified) and a search count at right. */
    private final class Row extends JPanel implements TreeCellRenderer {
        private final JLabel title = new JLabel();
        private final JLabel count = new JLabel();

        Row() {
            super(new BorderLayout(UIScale.scale(8), 0));
            setOpaque(false);
            title.putClientProperty("html.disable", true);
            count.putClientProperty("html.disable", true);
            add(title, BorderLayout.CENTER);
            add(count, BorderLayout.EAST);
            setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(2), 0, UIScale.scale(10)));
        }

        @Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean open,
                                                                boolean leaf, int row, boolean focused) {
            var page = (SettingsPage) ((DefaultMutableTreeNode) value).getUserObject();
            if (page == null) return this;
            title.setText(page.title());
            title.setFont(tree.getFont().deriveFont(page.parent().isEmpty() ? Font.BOLD : Font.PLAIN));
            Color foreground = UIManager.getColor(selected && tree.hasFocus() ? "Tree.selectionForeground" : "Label.foreground");
            if (modified.test(page.id()) && !(selected && tree.hasFocus())) {
                Color link = UIManager.getColor("Component.linkColor");
                if (link != null) foreground = link;
            }
            title.setForeground(foreground);
            int matches = counts.getOrDefault(page.id(), 0);
            count.setText(matches > 0 ? Integer.toString(matches) : "");
            count.setForeground(UIManager.getColor("Label.disabledForeground"));
            count.setFont(tree.getFont());
            getAccessibleContext().setAccessibleName(page.title() + (modified.test(page.id()) ? ", modified" : ""));
            return this;
        }

        @Override public void setBounds(int x, int y, int width, int height) {
            super.setBounds(x, y, width, height);
            doLayout(); // CellRendererPane does not validate compound tree renderers.
        }

        @Override public Dimension getPreferredSize() { return new Dimension(UIScale.scale(200), getRowHeight()); }
    }
}
