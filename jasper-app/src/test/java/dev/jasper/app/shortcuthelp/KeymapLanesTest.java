package dev.jasper.app.shortcuthelp;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.Nodes;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 12.2 (CLASSIC): the Keymap's columns, its text tools and its status line. */
class KeymapLanesTest {
    static List<ShortcutEntry> rows() {
        return ShortcutTestSupport.rows();
    }

    @Test void theClassicTreeHasColumnsAndSectionsCountInTheirShortcutLane() {
        var tree = new ShortcutTree(true, true);
        tree.showRows(rows(), false);
        var descriptor = tree.descriptor();
        assertThat(descriptor.columns()).extracting(dev.jasper.ui.model.Column::title).containsExactly("Action", "Scope", "Shortcut");
        var first = descriptor.roots().getFirst();
        assertThat(first.count()).isEmpty();
        assertThat(first.trailing()).hasSize(2);
        assertThat(first.trailing().get(1)).matches("\\d+ shortcuts?");
        var plain = new ShortcutTree(true, false);
        plain.showRows(rows(), false);
        assertThat(plain.descriptor().columns()).isEmpty();
        assertThat(plain.descriptor().roots().getFirst().count()).isPresent();
    }

    /** {@code entry}'s row: its scope's right edge and its keys' left edge, in the tree's coordinates, after a layout. */
    static int[] lanes(ShortcutPanel panel, ShortcutEntry entry) {
        ShortcutKit.layout(panel);
        String row = "@tree/" + ShortcutTree.ID + "/" + ShortcutTree.actionId(entry);
        var tree = dev.jasper.app.testsupport.KitTestSupport.require(panel, ShortcutTree.ID, javax.swing.JScrollPane.class);
        var scope = dev.jasper.app.testsupport.KitTestSupport.require(panel, row + "/context", javax.swing.JLabel.class);
        var keys = dev.jasper.app.testsupport.KitTestSupport.require(panel, row + "/keys", javax.swing.JLabel.class);
        return new int[] {javax.swing.SwingUtilities.convertPoint(scope, scope.getWidth(), 0, tree).x, javax.swing.SwingUtilities.convertPoint(keys, 0, 0, tree).x};
    }

    @Test void theLanesHoldWhileSearchingAndRecording() throws Exception {
        edt(() -> {
            var panel = new ShortcutPanel(dev.jasper.app.testsupport.KitTestSupport.context(), true, edits -> java.util.concurrent.CompletableFuture.completedFuture(null),
                KeymapShape.CLASSIC);
            try {
                panel.setRows(rows());
                // Tall enough that every row mounts.
                panel.setSize(900, 6000);
                var copy = rows().stream().filter(entry -> entry.name().equals("Copy")).findFirst().orElseThrow();
                panel.tree.expanded(ShortcutTree.groupId(copy.group()), true);
                ShortcutTestSupport.render(panel);
                int[] lanes = lanes(panel, copy);
                assertThat(lanes[1] - lanes[0]).as("the keys 25 into the Shortcut lane, the scope ending 30 before it").isEqualTo(55);
                var tools = (Nodes.Stack) ShortcutTestSupport.toolsSpec(panel).root();
                assertThat(tools.children()).extracting(Nodes.Node::id).containsExactly("clear", "record", "expand", "collapse");
                assertThat(((Nodes.Button) tools.children().get(1)).label()).isEqualTo("Find by Shortcut");
                assertThat(((Nodes.Button) tools.children().get(2)).label()).isEqualTo("Expand All");
                assertThat(((Nodes.Button) tools.children().get(3)).label()).isEqualTo("Collapse All");
                panel.setQuery("copy");
                assertThat(panel.tree.descriptor().columns()).as("searching").hasSize(3);
                assertThat(lanes(panel, copy)).as("searching keeps the lanes").containsExactly(lanes);
                ShortcutTestSupport.action(panel, "record");
                assertThat(((Nodes.Button) ((Nodes.Stack) ShortcutTestSupport.toolsSpec(panel).root()).children().get(1)).label()).isEqualTo("Stop");
                assertThat(panel.tree.descriptor().columns()).as("recording").hasSize(3);
                assertThat(lanes(panel, copy)).as("recording keeps the lanes").containsExactly(lanes);
                assertThat(ShortcutTestSupport.statusSpec(panel).root().toString()).doesNotContain("status.lock");
            } finally { panel.close(); }
        });
    }
}
