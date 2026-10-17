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

    @Test void theLanesHoldWhileSearchingAndRecording() throws Exception {
        edt(() -> {
            var panel = new ShortcutPanel(dev.jasper.app.testsupport.KitTestSupport.context(), true, edits -> java.util.concurrent.CompletableFuture.completedFuture(null),
                KeymapShape.CLASSIC);
            try {
                panel.setRows(rows());
                var tools = (Nodes.Stack) ShortcutTestSupport.toolsSpec(panel).root();
                assertThat(tools.children()).extracting(Nodes.Node::id).containsExactly("clear", "record", "expand", "collapse");
                assertThat(((Nodes.Button) tools.children().get(1)).label()).isEqualTo("Find by Shortcut");
                assertThat(((Nodes.Button) tools.children().get(2)).label()).isEqualTo("Expand All");
                assertThat(((Nodes.Button) tools.children().get(3)).label()).isEqualTo("Collapse All");
                panel.setQuery("copy");
                assertThat(panel.tree.descriptor().columns()).as("searching").hasSize(3);
                ShortcutTestSupport.action(panel, "record");
                assertThat(((Nodes.Button) ((Nodes.Stack) ShortcutTestSupport.toolsSpec(panel).root()).children().get(1)).label()).isEqualTo("Stop");
                assertThat(panel.tree.descriptor().columns()).as("recording").hasSize(3);
                assertThat(ShortcutTestSupport.statusSpec(panel).root().toString()).doesNotContain("status.lock");
            } finally { panel.close(); }
        });
    }
}
