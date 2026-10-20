package dev.jasper.app.shortcuthelp;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.Nodes;
import org.junit.jupiter.api.Test;

/** Spec 12.2 (CLASSIC): the band's recorder, Cancel and Set, and its hint line with the ⋯ and no Record. */
class KeymapBandTest {
    @Test void theClassicBandsHintLineHasTheEllipsisAndNoRecord() throws Exception {
        edt(() -> {
            var editor = new ShortcutEditPanel(dev.jasper.app.testsupport.KitTestSupport.context(), true,
                edits -> java.util.concurrent.CompletableFuture.completedFuture(null), () -> { }, true);
            editor.begin(KeymapLanesTest.rows().stream().filter(ShortcutEntry::editable).findFirst().orElseThrow());
            var note = (Nodes.Stack) dev.jasper.app.settingsui.ResetMenuTestAccess.find(editor.spec().root(), "editor.note");
            assertThat(note.children()).extracting(Nodes.Node::id).containsExactly("editor.message", "editor.options");
            var plain = new ShortcutEditPanel(dev.jasper.app.testsupport.KitTestSupport.context(), true,
                edits -> java.util.concurrent.CompletableFuture.completedFuture(null), () -> { }, false);
            plain.begin(KeymapLanesTest.rows().stream().filter(ShortcutEntry::editable).findFirst().orElseThrow());
            var plainNote = (Nodes.Stack) dev.jasper.app.settingsui.ResetMenuTestAccess.find(plain.spec().root(), "editor.note");
            assertThat(plainNote.children()).extracting(Nodes.Node::id).contains("editor.record");
        });
    }

    @Test void escapeCancelsTheClassicBandWhileRecording() throws Exception {
        edt(() -> {
            var panel = new ShortcutPanel(dev.jasper.app.testsupport.KitTestSupport.context(), true,
                edits -> java.util.concurrent.CompletableFuture.completedFuture(null), KeymapShape.CLASSIC);
            try {
                panel.setRows(KeymapLanesTest.rows());
                var entry = KeymapLanesTest.rows().stream().filter(ShortcutEntry::editable).findFirst().orElseThrow();
                panel.tree.expanded(ShortcutTree.groupId(entry.group()), true);
                ShortcutTestSupport.activate(panel, ShortcutTree.actionId(entry));
                assertThat(panel.editor.visibleEdit()).as("recording in the band").isTrue();
                assertThat(panel.dispatch(new java.awt.event.KeyEvent(panel, java.awt.event.KeyEvent.KEY_PRESSED, 0, 0,
                    java.awt.event.KeyEvent.VK_ESCAPE, java.awt.event.KeyEvent.CHAR_UNDEFINED))).isTrue();
                assertThat(panel.editor.visibleEdit()).as("Escape closes the band").isFalse();
            } finally { panel.close(); }
        });
    }

    @Test void collapsingTheSectionClosesTheBand() throws Exception {
        edt(() -> {
            var panel = new ShortcutPanel(dev.jasper.app.testsupport.KitTestSupport.context(), true,
                edits -> java.util.concurrent.CompletableFuture.completedFuture(null), KeymapShape.CLASSIC);
            try {
                panel.setRows(KeymapLanesTest.rows());
                var entry = KeymapLanesTest.rows().stream().filter(ShortcutEntry::editable).findFirst().orElseThrow();
                panel.tree.expanded(ShortcutTree.groupId(entry.group()), true);
                ShortcutTestSupport.activate(panel, ShortcutTree.actionId(entry));
                assertThat(panel.editor.visibleEdit()).isTrue();
                panel.tree.expanded(ShortcutTree.groupId(entry.group()), false);
                ShortcutTestSupport.render(panel);
                assertThat(panel.editor.visibleEdit()).as("the edited row left the tree").isFalse();
            } finally { panel.close(); }
        });
    }
}
