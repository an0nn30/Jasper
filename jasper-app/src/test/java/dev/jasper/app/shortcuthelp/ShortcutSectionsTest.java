package dev.jasper.app.shortcuthelp;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.KeyBindings;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class ShortcutSectionsTest {
    @Test void sectionsAreExpandableAndShareThePanelsThemeSurface() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            var panel = new ShortcutPanel(true, changes -> { throw new AssertionError("Unexpected save"); });
            try {
                panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
                var tree = find(panel, JTree.class);
                assertThat(tree).as("shortcuts are organized into expandable sections").isNotNull();
                int groups = tree.getRowCount();
                tree.expandRow(0);
                assertThat(tree.getRowCount()).isGreaterThan(groups);
                tree.collapseRow(0);
                assertThat(tree.getRowCount()).isEqualTo(groups);
                for (var theme : List.of(Theme.DARK, Theme.INTELLIJ_LIGHT)) {
                    themes.configure(theme); SwingUtilities.updateComponentTreeUI(panel);
                    assertThat(tree.getBackground()).isEqualTo(panel.getBackground());
                    assertThat(find(panel, JScrollPane.class).getViewport().getBackground()).isEqualTo(panel.getBackground());
                }
            } finally { panel.close(); themes.configure(Theme.DARK); }
        });
    }

    @Test void searchTemporarilyRevealsGroupsAndLiveRefreshPreservesDisclosureAndSelection() throws Exception {
        edt(() -> {
            var panel = new ShortcutPanel(true, changes -> { throw new AssertionError("Unexpected save"); });
            try {
                var rows = List.of(new ShortcutEntry("alpha", "First", "Alpha", KeyStroke.getKeyStroke("meta A"), "Terminal window", ""),
                    new ShortcutEntry("beta", "Second", "Beta", KeyStroke.getKeyStroke("meta B"), "Other window", ""));
                panel.setRows(rows);
                assertThat(panel.tree.getRowCount()).isEqualTo(2);
                panel.tree.setSelectionRow(0);
                panel.tree.getActionMap().get("editShortcut").actionPerformed(null);
                assertThat(panel.tree.getRowCount()).as("Enter expands a section").isEqualTo(3);
                panel.tree.setSelectionRow(1);
                panel.setRows(rows);
                assertThat(panel.tree.getSelectionRows()).containsExactly(1);
                panel.search.setText("beta");
                assertThat(panel.tree.entryAt(1).id()).isEqualTo("beta");
                panel.tree.collapseRow(0);
                panel.clear.doClick();
                assertThat(panel.tree.getRowCount()).as("search does not replace saved expansion choices").isEqualTo(3);
                assertThat(panel.tree.isExpanded(0)).isTrue();
                assertThat(panel.tree.isExpanded(2)).isFalse();
                panel.tree.expandSections(true);
                assertThat(panel.tree.getRowCount()).isEqualTo(4);
                assertThat(panel.tree.getRowForPath(panel.tree.getNextMatch("Beta", 0, javax.swing.text.Position.Bias.Forward)))
                    .as("typing an action name finds its row").isEqualTo(3);
                panel.tree.expandSections(false);
                assertThat(panel.tree.getRowCount()).isEqualTo(2);
                panel.setRows(List.of(rows.getFirst()));
                assertThat(panel.tree.getRowCount()).as("removed plugin groups disappear").isEqualTo(1);
            } finally { panel.close(); }
        });
    }

    @Test void renderedActionsHaveVisibleTextAndBindingsAndLiteralContextTooltips() throws Exception {
        edt(() -> {
            var panel = new ShortcutPanel(true, changes -> { throw new AssertionError("Unexpected save"); });
            try {
                panel.setRows(List.of(new ShortcutEntry("literal", "<html>Group", "<html>Action", KeyStroke.getKeyStroke("meta A"), "Context", "")));
                panel.tree.expandSections(true);
                panel.tree.setSize(700, 120);
                var image = new java.awt.image.BufferedImage(700, 120, java.awt.image.BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try { panel.tree.paint(graphics); } finally { graphics.dispose(); }
                var bounds = panel.tree.getRowBounds(1);
                int foreground = UIManager.getColor("Label.foreground").getRGB() & 0xffffff;
                int textPixels = 0;
                for (int y = bounds.y; y < bounds.y + bounds.height; y++)
                    for (int x = bounds.x; x < 250; x++) if ((image.getRGB(x, y) & 0xffffff) == foreground) textPixels++;
                assertThat(textPixels).as("compound renderer actually lays out and paints its labels").isGreaterThan(10);
                var tip = panel.tree.getToolTipText(new java.awt.event.MouseEvent(panel.tree,
                    java.awt.event.MouseEvent.MOUSE_MOVED, 0, 0, bounds.x + 10, bounds.y + 10, 0, false));
                assertThat(tip).contains("Context", "Read-only");
            } finally { panel.close(); }
        });
    }

    @Test void rightAlignedShortcutsCanBeClickedAndDescribeTheirContext() throws Exception {
        edt(() -> {
            var panel = new ShortcutPanel(true, changes -> { throw new AssertionError("Unexpected save"); });
            try {
                panel.setRows(List.of(new ShortcutEntry("copy", "Clipboard", "Copy", KeyStroke.getKeyStroke("meta C"),
                    "Terminal window", "", true, "cmd+c")));
                panel.tree.expandSections(true); panel.tree.setSize(900, 300);
                var row = panel.tree.getRowBounds(1);
                var click = new java.awt.event.MouseEvent(panel.tree, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0,
                    850, row.y + row.height / 2, 1, false, java.awt.event.MouseEvent.BUTTON1);
                panel.tree.dispatchEvent(click);
                assertThat(panel.editor.isVisible()).as("clicking the chord at the right opens the editor").isTrue();
                assertThat(panel.tree.getToolTipText(click)).contains("Terminal window", "Click to edit");
                panel.editor.cancel.doClick();
                panel.tree.dispatchEvent(new java.awt.event.MouseEvent(panel.tree, java.awt.event.MouseEvent.MOUSE_CLICKED,
                    0, 0, 850, 250, 1, false, java.awt.event.MouseEvent.BUTTON1));
                assertThat(panel.editor.isVisible()).as("blank space below the final row is not a target").isFalse();
            } finally { panel.close(); }
        });
    }

    @Test void rowsAndSectionsThatDifferFromTheirDefaultsAreBlue() throws Exception {
        edt(() -> {
            new ThemeController();
            var panel = new ShortcutPanel(true, changes -> { throw new AssertionError("Unexpected save"); });
            try {
                panel.setRows(List.of(
                    new ShortcutEntry("new_tab", "Tabs", "New tab", KeyStroke.getKeyStroke("meta Y"), "Terminal window", "", true, "cmd+t"),
                    new ShortcutEntry("close_tab", "Tabs", "Close tab", KeyStroke.getKeyStroke("meta W"), "Terminal window", "", true, "cmd+w"),
                    new ShortcutEntry("copy", "Clipboard", "Copy", KeyStroke.getKeyStroke("meta C"), "Terminal window", "", true, "cmd+c")));
                panel.tree.expandSections(true);
                var link = UIManager.getColor("Component.linkColor");
                var plain = UIManager.getColor("Label.foreground");
                assertThat(link).isNotNull();
                assertThat(nameColor(panel.tree, "Tabs")).as("a section holding a changed row").isEqualTo(link);
                assertThat(nameColor(panel.tree, "New tab")).isEqualTo(link);
                assertThat(nameColor(panel.tree, "Close tab")).isEqualTo(plain);
                assertThat(nameColor(panel.tree, "Clipboard")).isEqualTo(plain);
            } finally { panel.close(); }
        });
    }

    private static java.awt.Color nameColor(JTree tree, String text) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            Object node = tree.getPathForRow(row).getLastPathComponent();
            var cell = (Container) tree.getCellRenderer().getTreeCellRendererComponent(tree, node, false, tree.isExpanded(row),
                tree.getModel().isLeaf(node), row, false);
            var name = (JLabel) cell.getComponent(0);
            if (name.getText().equals(text)) return name.getForeground();
        }
        throw new AssertionError("no row named " + text);
    }

    private static <T> T find(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) { T found = find(nested, type); if (found != null) return found; }
        }
        return null;
    }

    @Test void rowsShowWhereTheirShortcutApplies() throws Exception {
        edt(() -> {
            new ThemeController();
            var panel = new ShortcutPanel(true, changes -> { throw new AssertionError("Unexpected save"); });
            try {
                panel.setRows(List.of(new ShortcutEntry("clear_scrollback", "Terminal", "Clear Scrollback",
                    KeyStroke.getKeyStroke("meta K"), "In a terminal pane", "", true, "cmd+k", "Terminal")));
                panel.tree.expandSections(true);
                assertThat(labels(panel.tree, "Clear Scrollback"))
                    .contains("Clear Scrollback", "Terminal", ShortcutText.format(KeyStroke.getKeyStroke("meta K")));
            } finally { panel.close(); }
        });
    }

    private static List<String> labels(JTree tree, String name) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            Object node = tree.getPathForRow(row).getLastPathComponent();
            var cell = (Container) tree.getCellRenderer().getTreeCellRendererComponent(tree, node, false, tree.isExpanded(row),
                tree.getModel().isLeaf(node), row, false);
            if (!((JLabel) cell.getComponent(0)).getText().equals(name)) continue;
            List<String> texts = new java.util.ArrayList<>();
            collect(cell, texts);
            return texts;
        }
        throw new AssertionError("no row named " + name);
    }

    private static void collect(Container root, List<String> texts) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) texts.add(label.getText());
            if (child instanceof Container nested) collect(nested, texts);
        }
    }
}
