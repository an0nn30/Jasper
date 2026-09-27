package dev.jasper.app.shortcuthelp;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.AppIcons;
import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.*;

/** Section disclosure state belongs to this window; searching reveals matches temporarily. */
final class ShortcutTree extends JTree {
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    private final DefaultTreeModel rows = new DefaultTreeModel(root);
    private final Set<String> expanded = new HashSet<>();
    private boolean filtering, rebuilding;

    ShortcutTree(Consumer<ShortcutEntry> edit) {
        setModel(rows); setRootVisible(false); setShowsRootHandles(true);
        setToggleClickCount(1);
        setCellRenderer(new Row());
        setBorder(BorderFactory.createEmptyBorder(UIScale.scale(4), UIScale.scale(4), UIScale.scale(4), UIScale.scale(4)));
        putClientProperty(FlatClientProperties.STYLE, "icon.arrowType: chevron");
        putClientProperty(FlatClientProperties.TREE_WIDE_SELECTION, true);
        putClientProperty(FlatClientProperties.TREE_WIDE_CELL_RENDERER, true);
        getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        getAccessibleContext().setAccessibleName("Keyboard shortcuts grouped by section or plugin");
        addTreeExpansionListener(new TreeExpansionListener() {
            @Override public void treeExpanded(TreeExpansionEvent e) { remember(e, true); }
            @Override public void treeCollapsed(TreeExpansionEvent e) { remember(e, false); }
            private void remember(TreeExpansionEvent e, boolean open) {
                if (filtering || rebuilding) return;
                Object value = ((DefaultMutableTreeNode) e.getPath().getLastPathComponent()).getUserObject();
                if (value instanceof String group) { if (open) expanded.add(group); else expanded.remove(group); }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                var path = pathAt(e.getPoint());
                if (path != null && entry(path) != null) edit.accept(entry(path));
            }
        });
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "editShortcut");
        getActionMap().put("editShortcut", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                var path = getSelectionPath();
                if (path == null) return;
                var item = entry(path);
                if (item != null) edit.accept(item);
                else if (isExpanded(path)) collapsePath(path); else expandPath(path);
            }
        });
    }

    @Override public void updateUI() {
        super.updateUI();
        setBackground(UIManager.getColor("Panel.background"));
        if (getFont() != null) setRowHeight(getFontMetrics(getFont()).getHeight() + UIScale.scale(12));
    }

    @Override public boolean getScrollableTracksViewportWidth() { return true; }

    @Override public String convertValueToText(Object value, boolean selected, boolean expanded,
            boolean leaf, int row, boolean focused) {
        Object item = value instanceof DefaultMutableTreeNode node ? node.getUserObject() : value;
        return item instanceof ShortcutEntry shortcut ? shortcut.name() : item == null ? "" : item.toString();
    }

    // FlatLaf widens painting and selection independently of JTree's preferred-width hit bounds.
    private TreePath pathAt(Point point) {
        if (!contains(point)) return null;
        var path = getClosestPathForLocation(point.x, point.y);
        var bounds = path == null ? null : getPathBounds(path);
        return bounds != null && point.x >= bounds.x && point.y >= bounds.y && point.y < bounds.y + bounds.height ? path : null;
    }

    @Override public String getToolTipText(MouseEvent event) {
        var path = pathAt(event.getPoint());
        if (path == null) return null;
        var item = entry(path);
        return item != null ? item.context() + (item.editable() ? " · Click to edit" : " · Read-only contextual key")
            : isExpanded(path) ? "Collapse section" : "Expand section";
    }

    void showRows(List<ShortcutEntry> matches, boolean searching) {
        ShortcutEntry selection = entry(getSelectionPath());
        Object selectedGroup = getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node ? node.getUserObject() : null;
        filtering = searching; rebuilding = true;
        try {
            root.removeAllChildren();
            var groups = new LinkedHashMap<String, DefaultMutableTreeNode>();
            for (var item : matches) {
                var group = groups.computeIfAbsent(item.group(), name -> {
                    var created = new DefaultMutableTreeNode(name); root.add(created); return created;
                });
                group.add(new DefaultMutableTreeNode(item, false));
            }
            rows.reload();
            for (var group : groups.values()) {
                var path = new TreePath(group.getPath());
                if (searching || expanded.contains(group.getUserObject())) expandPath(path);
                if (selection == null && group.getUserObject().equals(selectedGroup)) setSelectionPath(path);
                if (selection != null) for (int i = 0; i < group.getChildCount(); i++) {
                    var child = (DefaultMutableTreeNode) group.getChildAt(i);
                    if (((ShortcutEntry) child.getUserObject()).id().equals(selection.id()) && isExpanded(path))
                        setSelectionPath(new TreePath(child.getPath()));
                }
            }
        } finally { rebuilding = false; }
    }

    void expandSections(boolean open) {
        for (int i = 0; i < root.getChildCount(); i++) {
            var path = new TreePath(((DefaultMutableTreeNode) root.getChildAt(i)).getPath());
            if (open) expandPath(path); else collapsePath(path);
        }
    }

    ShortcutEntry entryAt(int row) { return entry(getPathForRow(row)); }

    private static ShortcutEntry entry(TreePath path) {
        return path != null && ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject() instanceof ShortcutEntry item ? item : null;
    }

    /** Wide rows keep the binding aligned to the right; long action names ellipsize. */
    private final class Row extends JPanel implements TreeCellRenderer {
        private final JLabel name = new JLabel(), binding = new JLabel();
        Row() {
            super(new BorderLayout(UIScale.scale(20), 0));
            setOpaque(false);
            name.putClientProperty("html.disable", true); binding.putClientProperty("html.disable", true);
            name.setIconTextGap(UIScale.scale(8));
            add(name, BorderLayout.CENTER); add(binding, BorderLayout.EAST);
            setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(4), 0, UIScale.scale(12)));
        }
        @Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean open, boolean leaf, int row, boolean focused) {
            var node = (DefaultMutableTreeNode) value;
            var item = node.getUserObject();
            Color foreground = UIManager.getColor(selected && tree.hasFocus() ? "Tree.selectionForeground" : "Label.foreground");
            name.setForeground(foreground);
            binding.setForeground(selected && tree.hasFocus() ? foreground : UIManager.getColor("Label.disabledForeground"));
            name.setFont(tree.getFont().deriveFont(item instanceof ShortcutEntry ? Font.PLAIN : Font.BOLD));
            binding.setFont(tree.getFont());
            if (item instanceof ShortcutEntry shortcut) {
                name.setText(shortcut.name()); name.setIcon(null);
                binding.setText(ShortcutText.format(shortcut.stroke()));
                setToolTipText(shortcut.context() + (shortcut.editable() ? " · Click to edit" : " · Read-only contextual key"));
            } else {
                name.setText(String.valueOf(item));
                name.setIcon(AppIcons.bundled(open ? "general/open" : "nodes/folder"));
                binding.setText(String.valueOf(node.getChildCount()));
                setToolTipText(open ? "Collapse section" : "Expand section");
            }
            getAccessibleContext().setAccessibleName(name.getText() + ", " + binding.getText());
            getAccessibleContext().setAccessibleDescription(getToolTipText());
            return this;
        }
        @Override public void setBounds(int x, int y, int width, int height) {
            super.setBounds(x, y, width, height);
            doLayout(); // CellRendererPane does not validate compound tree renderers.
        }
        @Override public Dimension getPreferredSize() {
            return new Dimension(UIScale.scale(320), getRowHeight());
        }
    }
}
