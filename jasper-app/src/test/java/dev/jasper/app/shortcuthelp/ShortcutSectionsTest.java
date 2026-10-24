package dev.jasper.app.shortcuthelp;

import static dev.jasper.app.shortcuthelp.ShortcutKit.*;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class ShortcutSectionsTest {
    @Test void actualFillingPageKeepsKeymapFlexibleInsteadOfUsingStatusTextMinimum() throws Exception {
        edt(() -> {
            var panel=panel(changes -> java.util.concurrent.CompletableFuture.completedFuture(null));
            try {
                panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true),List.of(),Map.of(),true));
                assertThat(button(panel,"clear").isVisible()).as("nothing to clear yet").isFalse();
                panel.setQuery("tab");
                var body=new dev.jasper.app.settingsui.FillingPage(panel);var scroll=new JScrollPane(body);scroll.setSize(427,534);
                for(int i=0;i<4;i++)KitTestSupport.layout(scroll);
                assertThat(body.getWidth()).as("width-aware Keymap owns wrapping, not the status label's intrinsic width").isEqualTo(scroll.getViewport().getExtentSize().width);
                var clear=button(panel,"clear");
                var bounds=SwingUtilities.convertRectangle(clear.getParent(),clear.getBounds(),body);
                assertThat(scroll.getViewport().getViewRect().contains(bounds)).as("Clear remains inside visible page").isTrue();
                assertThat(scroll.getHorizontalScrollBar().isVisible()).isFalse();
            } finally {panel.close();}
        });
    }

    @Test void sectionsAreExpandableAndShareThePanelsThemeSurface() throws Exception {
        edt(() -> {
            var bindings = KitTestSupport.bindings(true);
            var panel = new ShortcutPanel(bindings.context(dev.jasper.ui.theme.ThemeRoute.CHROME), true, changes -> { throw new AssertionError("Unexpected save"); });
            try {
                panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
                layout(panel);
                var scroll = KitTestSupport.require(panel, ShortcutTree.ID, JScrollPane.class);
                assertThat(scroll).as("shortcuts are organized into expandable sections").isNotNull();
                int groups = panel.tree.rowCount();
                assertThat(groups).isPositive();
                var first = panel.tree.visibleIds().getFirst();
                KitTestSupport.require(panel, "@tree/" + ShortcutTree.ID + "/" + first + "/expand", JButton.class).doClick(0);
                assertThat(panel.tree.rowCount()).isGreaterThan(groups);
                layout(panel);
                KitTestSupport.require(panel, "@tree/" + ShortcutTree.ID + "/" + first + "/expand", JButton.class).doClick(0);
                assertThat(panel.tree.rowCount()).isEqualTo(groups);
                for (boolean dark : new boolean[] {false, true}) {
                    bindings.binding().publish(ThemeTestSupport.pair(dark));
                    layout(panel);
                    // Nothing between the page and the rows paints a surface of its own: the tree shows the page's.
                    assertThat(panel.isOpaque()).isFalse();
                    assertThat(scroll.isOpaque()).isFalse();
                    assertThat(scroll.getViewport().isOpaque()).isFalse();
                    assertThat(canvas(panel).isOpaque()).isFalse();
                    assertThat(KitTestSupport.require(panel, "@tree/" + ShortcutTree.ID + "/" + first + "/title", JLabel.class).getForeground())
                        .as("rows follow the published theme").isEqualTo(ThemeTestSupport.pair(dark).chrome().semantic().color(SemanticColor.TEXT_PRIMARY));
                }
            } finally { panel.close(); bindings.close(); }
        });
    }

    @Test void searchTemporarilyRevealsGroupsAndLiveRefreshPreservesDisclosureAndSelection() throws Exception {
        edt(() -> {
            var panel = panel(changes -> { throw new AssertionError("Unexpected save"); });
            try {
                var rows = List.of(new ShortcutEntry("alpha", "First", "Alpha", KeyStroke.getKeyStroke("meta A"), "Jasper window", ""),
                    new ShortcutEntry("beta", "Second", "Beta", KeyStroke.getKeyStroke("meta B"), "Other window", ""));
                panel.setRows(rows);
                layout(panel);
                assertThat(panel.tree.rowCount()).isEqualTo(2);
                selectRow(panel, 0);
                key(panel, "ENTER");
                assertThat(panel.tree.rowCount()).as("Enter expands a section").isEqualTo(3);
                selectRow(panel, 1);
                panel.setRows(rows);
                assertThat(panel.tree.selected()).isEqualTo(panel.tree.visibleIds().get(1));
                search(panel).setText("beta");
                assertThat(panel.tree.entryAt(1).id()).isEqualTo("beta");
                layout(panel);
                // Collapsing the section shown while searching is not remembered.
                KitTestSupport.require(panel, "@tree/" + ShortcutTree.ID + "/group/Second/expand", JButton.class).doClick(0);
                assertThat(panel.tree.rowCount()).isEqualTo(1);
                button(panel, "clear").doClick(0);
                assertThat(panel.tree.rowCount()).as("search does not replace saved expansion choices").isEqualTo(3);
                assertThat(panel.tree.isOpenItem("group/First")).isTrue();
                assertThat(panel.tree.isOpenItem("group/Second")).isFalse();
                button(panel, "expand").doClick(0);
                assertThat(panel.tree.rowCount()).isEqualTo(4);
                layout(panel);
                for (var listener : canvas(panel).getKeyListeners()) {
                    listener.keyTyped(new KeyEvent(canvas(panel), KeyEvent.KEY_TYPED, 1000, 0, KeyEvent.VK_UNDEFINED, 'B'));
                    listener.keyTyped(new KeyEvent(canvas(panel), KeyEvent.KEY_TYPED, 1100, 0, KeyEvent.VK_UNDEFINED, 'e'));
                }
                assertThat(panel.tree.visibleIds().indexOf(panel.tree.selected())).as("typing an action name finds its row").isEqualTo(3);
                button(panel, "collapse").doClick(0);
                assertThat(panel.tree.rowCount()).isEqualTo(2);
                panel.setRows(List.of(rows.getFirst()));
                assertThat(panel.tree.rowCount()).as("removed plugin groups disappear").isEqualTo(1);
            } finally { panel.close(); }
        });
    }

    @Test void renderedActionsHaveVisibleTextAndBindingsAndLiteralContextTooltips() throws Exception {
        edt(() -> {
            var panel = panel(changes -> { throw new AssertionError("Unexpected save"); });
            try {
                panel.setRows(List.of(new ShortcutEntry("literal", "<html>Group", "<html>Action", KeyStroke.getKeyStroke("meta A"), "Context", "")));
                button(panel, "expand").doClick(0);
                panel.setSize(700, 400);
                layout(panel);
                var scroll = KitTestSupport.require(panel, ShortcutTree.ID, JScrollPane.class);
                var image = new java.awt.image.BufferedImage(scroll.getWidth()*2, scroll.getHeight()*2, java.awt.image.BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try { graphics.scale(2,2);scroll.paint(graphics); } finally { graphics.dispose(); }
                var title = KitTestSupport.require(panel, "@tree/" + ShortcutTree.ID + "/action/literal/title", JLabel.class);
                assertThat(title.getText()).as("literal, never HTML").isEqualTo("<html>Action");
                var row = SwingUtilities.convertRectangle(title.getParent(), title.getBounds(), scroll);
                int foreground = title.getForeground().getRGB() & 0xffffff;
                int textPixels = 0;
                for (int y = Math.max(0, row.y*2); y < Math.min(image.getHeight(), (row.y + row.height)*2); y++)
                    for (int x = Math.max(0, row.x*2); x < Math.min(image.getWidth(), (row.x + row.width)*2); x++)
                        if ((image.getRGB(x, y) & 0xffffff) == foreground) textPixels++;
                assertThat(textPixels).as("the row actually lays out and paints its labels").isGreaterThan(10);
                var rowComponent = (JComponent) title.getParent().getParent();
                assertThat(rowComponent.getToolTipText()).contains("Context", "Read-only");
                assertThat(rowComponent.createToolTip().getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
            } finally { panel.close(); }
        });
    }

    @Test void rightAlignedShortcutsCanBeClickedAndDescribeTheirContext() throws Exception {
        edt(() -> {
            var panel = panel(changes -> { throw new AssertionError("Unexpected save"); });
            try {
                panel.setRows(List.of(new ShortcutEntry("copy", "Clipboard", "Copy", KeyStroke.getKeyStroke("meta C"),
                    "Jasper window", "", true, "cmd+c")));
                button(panel, "expand").doClick(0);
                panel.setSize(900, 500); layout(panel);
                var binding = KitTestSupport.require(panel, "@tree/" + ShortcutTree.ID + "/action/copy/keys", JLabel.class);
                assertThat(SwingUtilities.convertPoint(binding, binding.getWidth(), 0, canvas(panel)).x)
                    .as("the chord sits at the right").isGreaterThan(canvas(panel).getWidth() * 3 / 4);
                click(binding, 1);
                assertThat(panel.editor.isVisible()).as("clicking the chord at the right opens the editor").isTrue();
                assertThat(binding.getToolTipText()).contains("Jasper window", "Click to edit");
                button(panel, "editor.cancel").doClick(0);
                click(canvas(panel), 1);
                assertThat(panel.editor.isVisible()).as("blank space below the final row is not a target").isFalse();
            } finally { panel.close(); }
        });
    }

    @Test void rowsThatDifferFromTheirDefaultsAreBlueAndTheirSectionsCarryTheModifiedMark() throws Exception {
        edt(() -> {
            var panel = panel(changes -> { throw new AssertionError("Unexpected save"); });
            try {
                panel.setRows(List.of(
                    new ShortcutEntry("new_tab", "Tabs", "New tab", KeyStroke.getKeyStroke("meta Y"), "Jasper window", "", true, "cmd+t"),
                    new ShortcutEntry("close_tab", "Tabs", "Close tab", KeyStroke.getKeyStroke("meta W"), "Jasper window", "", true, "cmd+w"),
                    new ShortcutEntry("copy", "Clipboard", "Copy", KeyStroke.getKeyStroke("meta C"), "Jasper window", "", true, "cmd+c")));
                button(panel, "expand").doClick(0);
                layout(panel);
                var palette = KitTestSupport.context().theme().semantic();
                var link = palette.color(SemanticColor.TEXT_LINK);
                var plain = palette.color(SemanticColor.TEXT_PRIMARY);
                assertThat(link).isNotEqualTo(plain);
                assertThat(nameColor(panel, "group/Tabs")).as("a section holding a changed row keeps its title colour").isEqualTo(plain);
                assertThat(KitTestSupport.find(panel, "@tree/" + ShortcutTree.ID + "/group/Tabs/modified", JComponent.class)).as("and shows the modified mark").isNotNull();
                assertThat(KitTestSupport.find(panel, "@tree/" + ShortcutTree.ID + "/group/Clipboard/modified", JComponent.class)).isNull();
                assertThat(nameColor(panel, "action/new_tab")).isEqualTo(link);
                assertThat(nameColor(panel, "action/close_tab")).isEqualTo(plain);
                assertThat(nameColor(panel, "group/Clipboard")).isEqualTo(plain);
            } finally { panel.close(); }
        });
    }

    private static java.awt.Color nameColor(ShortcutPanel panel, String item) {
        return KitTestSupport.require(panel, "@tree/" + ShortcutTree.ID + "/" + item + "/title", JLabel.class).getForeground();
    }

    @Test void rowsShowWhereTheirShortcutApplies() throws Exception {
        edt(() -> {
            var panel = panel(changes -> { throw new AssertionError("Unexpected save"); });
            try {
                panel.setRows(List.of(new ShortcutEntry("clear_scrollback", "Terminal", "Clear Scrollback",
                    KeyStroke.getKeyStroke("meta K"), "In a terminal pane", "", true, "cmd+k", "Terminal")));
                button(panel, "expand").doClick(0);
                layout(panel);
                var row = KitTestSupport.require(panel, "@tree/" + ShortcutTree.ID + "/action/clear_scrollback/title", JLabel.class).getParent().getParent();
                var texts = new ArrayList<String>();
                collect((Container) row, texts);
                assertThat(texts).contains("Clear Scrollback", "Terminal");
                var where = KitTestSupport.require(panel, "@tree/" + ShortcutTree.ID + "/action/clear_scrollback/context", JLabel.class);
                var binding = KitTestSupport.require(panel, "@tree/" + ShortcutTree.ID + "/action/clear_scrollback/keys", JLabel.class);
                assertThat(where.getHorizontalAlignment()).as("the Where column right-aligns against the shared keys column").isEqualTo(SwingConstants.RIGHT);
                assertThat(where.getX() + where.getWidth()).as("before the keys").isLessThanOrEqualTo(binding.getX());
                assertThat(binding.getIcon()).as("binding renders passive keycaps").isNotNull();
                assertThat(binding.getAccessibleContext().getAccessibleName()).isEqualTo(ShortcutText.format(KeyStroke.getKeyStroke("meta K")));
            } finally { panel.close(); }
        });
    }

    @Test void aRightClickOnARowNeitherSelectsNorEditsNorToggles() throws Exception {
        edt(() -> {
            var panel = panel(changes -> { throw new AssertionError("Unexpected save"); });
            try {
                panel.setRows(List.of(new ShortcutEntry("copy", "Clipboard", "Copy", KeyStroke.getKeyStroke("meta C"), "Jasper window", "", true, "cmd+c")));
                button(panel, "expand").doClick(0);
                layout(panel);
                int rows = panel.tree.rowCount();
                for (String item : List.of("action/copy", "group/Clipboard")) {
                    var title = KitTestSupport.require(panel, "@tree/" + ShortcutTree.ID + "/" + item + "/title", JLabel.class);
                    var press = new java.awt.event.MouseEvent(title, java.awt.event.MouseEvent.MOUSE_PRESSED, 0,
                        java.awt.event.InputEvent.BUTTON3_DOWN_MASK, 3, 3, 1, true, java.awt.event.MouseEvent.BUTTON3);
                    post(press);
                    var release = new java.awt.event.MouseEvent(title, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 3, 3, 1, false, java.awt.event.MouseEvent.BUTTON3);
                    post(release);
                    assertThat(panel.tree.selected()).as("a right click does not select %s", item).isNull();
                    assertThat(panel.editor.isVisible()).as("a right click never edits").isFalse();
                    assertThat(panel.tree.rowCount()).as("nor toggles a section").isEqualTo(rows);
                    layout(panel);
                }
            } finally { panel.close(); }
        });
    }

    @Test void aDoubleClickTogglesASectionOnceAndStartsOneEditOnAShortcut() throws Exception {
        edt(() -> {
            var panel = panel(changes -> { throw new AssertionError("Unexpected save"); });
            try {
                panel.setRows(List.of(new ShortcutEntry("copy", "Clipboard", "Copy", KeyStroke.getKeyStroke("meta C"), "Jasper window", "", true, "cmd+c")));
                layout(panel);
                int closed = panel.tree.rowCount();
                var section = KitTestSupport.require(panel, "@tree/" + ShortcutTree.ID + "/group/Clipboard/title", JLabel.class);
                post(new java.awt.event.MouseEvent(section, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 3, 3, 1, false, java.awt.event.MouseEvent.BUTTON1));
                post(new java.awt.event.MouseEvent(section, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 3, 3, 2, false, java.awt.event.MouseEvent.BUTTON1));
                assertThat(panel.tree.rowCount()).as("a double click opens the section once").isEqualTo(closed + 1);
                layout(panel);
                var row = KitTestSupport.require(panel, "@tree/" + ShortcutTree.ID + "/action/copy/title", JLabel.class);
                post(new java.awt.event.MouseEvent(row, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 3, 3, 1, false, java.awt.event.MouseEvent.BUTTON1));
                button(panel, "editor.record").doClick(0);
                assertThat(message(panel)).as("recording stopped after the first click's edit").contains("Apply or OK");
                post(new java.awt.event.MouseEvent(row, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 3, 3, 2, false, java.awt.event.MouseEvent.BUTTON1));
                assertThat(panel.editor.isVisible()).isTrue();
                assertThat(message(panel)).as("the second click does not start a second edit").contains("Apply or OK");
                key(panel, "ENTER");
                assertThat(message(panel)).as("Enter edits the selected shortcut").contains("Press the new shortcut");
                key(panel, "UP");
                key(panel, "ENTER");
                assertThat(panel.tree.rowCount()).as("Enter on the section closes it").isEqualTo(closed);
            } finally { panel.close(); }
        });
    }

    @Test void theKeymapKeepsItsAccessibleNames() throws Exception {
        edt(() -> {
            var panel = panel(changes -> { throw new AssertionError("Unexpected save"); });
            try {
                panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
                assertThat(search(panel).getAccessibleContext().getAccessibleName()).isEqualTo("Search keyboard shortcuts");
                var tree = KitTestSupport.require(panel, ShortcutTree.ID, JScrollPane.class);
                var treeView = (JComponent) SwingUtilities.getAncestorOfClass(dev.jasper.ui.swing.KitView.class, tree);
                assertThat(treeView.getAccessibleContext().getAccessibleName()).isEqualTo("Keyboard shortcuts grouped by section or plugin");
                var status = KitTestSupport.require(panel, "status.count", JLabel.class);
                var statusView = (JComponent) SwingUtilities.getAncestorOfClass(dev.jasper.ui.swing.KitView.class, status);
                assertThat(statusView.getAccessibleContext().getAccessibleName()).isEqualTo("Shortcut search status");
                assertThat(status.getText()).contains("shortcuts");
            } finally { panel.close(); }
        });
    }

    private static void collect(Container root, List<String> texts) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) texts.add(label.getText());
            if (child instanceof Container nested) collect(nested, texts);
        }
    }
}
