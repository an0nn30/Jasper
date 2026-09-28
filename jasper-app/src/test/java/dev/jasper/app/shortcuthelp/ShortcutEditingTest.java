package dev.jasper.app.shortcuthelp;

import dev.jasper.app.config.KeyBindings;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ShortcutEditingTest {
    @Test void clickingBindingCapturesWithoutExecutingAndRequiresExplicitReassignment() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var saved = new ArrayList<Map<String, Optional<String>>>();
            var panel = new ShortcutPanel(true, changes -> { saved.add(changes); return new CompletableFuture<>(); });
            var root = new JRootPane(); root.setContentPane(panel);
            panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
            panel.search.setText("Copy");
            clickShortcut(panel);
            assertThat(panel.editor.isVisible()).isTrue();
            assertThat(panel.dispatch(key(panel, KeyEvent.KEY_PRESSED, KeyEvent.VK_V, InputEvent.META_DOWN_MASK))).isTrue();
            assertThat(panel.dispatch(key(panel, KeyEvent.KEY_RELEASED, KeyEvent.VK_V, 0))).isTrue();
            assertThat(panel.editor.set.isEnabled()).isFalse();
            assertThat(panel.editor.message.getText()).contains("Paste");
            panel.editor.reassign.doClick();
            assertThat(panel.editor.set.isEnabled()).isTrue();
            panel.editor.set.doClick();
            assertThat(saved).containsExactly(Map.of("copy", Optional.of("cmd+v"), "paste", Optional.of("none")));
            assertThat(panel.editor.set.isEnabled()).isFalse();
            panel.close();
        });
    }

    @Test void unassignedCanBeBoundRemovedAndResetAndContextualRowsStayReadOnly() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var saved = new ArrayList<Map<String, Optional<String>>>();
            var panel = new ShortcutPanel(true, changes -> { saved.add(changes); return CompletableFuture.completedFuture(null); });
            panel.setRows(ShortcutCatalog.rows(KeyBindings.withOverrides(true, Map.of("copy", "none")), List.of(), Map.of(), true));
            panel.search.setText("Copy"); clickShortcut(panel);
            assertThat(panel.capture(KeyStroke.getKeyStroke("ctrl H"))).isTrue();
            panel.editor.set.doClick();
            assertThat(saved.getFirst()).isEqualTo(Map.of("copy", Optional.of("ctrl+h")));
            panel.close();
            var remove = new ShortcutPanel(true, changes -> { saved.add(changes); return new CompletableFuture<>(); });
            remove.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
            remove.search.setText("Copy"); clickShortcut(remove); remove.editor.remove.doClick(); remove.editor.set.doClick();
            assertThat(saved.getLast()).isEqualTo(Map.of("copy", Optional.of("none")));
            remove.close();
            var reset = new ShortcutPanel(true, changes -> { saved.add(changes); return new CompletableFuture<>(); });
            reset.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
            reset.search.setText("Copy"); clickShortcut(reset); reset.editor.restore.doClick(); reset.editor.set.doClick();
            assertThat(saved.getLast()).isEqualTo(Map.of("copy", Optional.empty()));
            reset.close();
            var fixed = new ShortcutPanel(true, changes -> { throw new AssertionError("contextual binding saved"); });
            fixed.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
            fixed.search.setText("Close find bar"); clickShortcut(fixed);
            assertThat(fixed.editor.isVisible()).isFalse();
            fixed.close();
        });
    }

    @Test void cancellationFocusLossAndExternalRefreshDoNotSaveOrLeakHeldKeys() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ShortcutPanel(false, changes -> { throw new AssertionError("unexpected save"); });
            var rows = ShortcutCatalog.rows(KeyBindings.defaults(false), List.of(), Map.of(), false);
            panel.setRows(rows); panel.search.setText("Copy"); clickShortcut(panel);
            panel.dispatch(key(panel, KeyEvent.KEY_PRESSED, KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK));
            panel.editor.cancel.doClick();
            assertThat(panel.dispatch(key(panel, KeyEvent.KEY_RELEASED, KeyEvent.VK_H, 0))).isTrue();
            assertThat(panel.editor.isVisible()).isFalse();
            clickShortcut(panel); panel.focusLeftWindow();
            assertThat(panel.capture(KeyStroke.getKeyStroke("ctrl H"))).isFalse();
            assertThat(panel.dispatch(key(panel, KeyEvent.KEY_PRESSED, KeyEvent.VK_H, 0))).isFalse();
            panel.setRows(List.of());
            assertThat(panel.editor.isVisible()).isFalse();
            panel.close();
        });
    }

    @Test void saveFailureRemainsVisibleAndCanBeRetried() throws Exception {
        var failure = new CompletableFuture<Void>();
        ShortcutPanel[] panel = new ShortcutPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new ShortcutPanel(true, changes -> failure);
            panel[0].setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
            panel[0].search.setText("Copy"); clickShortcut(panel[0]);
            panel[0].capture(KeyStroke.getKeyStroke("ctrl H")); panel[0].editor.set.doClick();
        });
        failure.completeExceptionally(new java.io.IOException("Permission denied"));
        SwingUtilities.invokeAndWait(() -> {
            assertThat(panel[0].editor.isVisible()).isTrue();
            assertThat(panel[0].editor.message.getText()).contains("Permission denied");
            assertThat(panel[0].editor.message.getText()).startsWith("Could not set: ");
            assertThat(panel[0].editor.set.isEnabled()).isTrue();
            panel[0].close();
        });
    }

    @Test void nativeCloseAndQuitRemainCapturedUntilEditingStops() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ShortcutPanel(true, changes -> new CompletableFuture<>());
            var root = new JRootPane(); root.setContentPane(panel); panel.addNotify();
            try {
                panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
                panel.search.setText("Copy"); clickShortcut(panel);
                assertThat(dev.jasper.app.platform.WindowInput.captureShortcut(root, KeyStroke.getKeyStroke("meta Q"))).isTrue();
                assertThat(dev.jasper.app.platform.WindowInput.captureShortcut(root, KeyStroke.getKeyStroke("meta Q"))).isTrue();
                assertThat(dev.jasper.app.platform.WindowInput.captureShortcut(root, KeyStroke.getKeyStroke("meta W"))).isTrue();
                panel.editor.cancel.doClick();
                assertThat(dev.jasper.app.platform.WindowInput.captureShortcut(root, KeyStroke.getKeyStroke("meta W"))).isFalse();
            } finally { panel.close(); panel.removeNotify(); }
        });
    }

    @Test void switchingFromSearchCaptureToEditCaptureStopsSearchCapture() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ShortcutPanel(true, changes -> new CompletableFuture<>());
            panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
            panel.search.setText("Copy"); clickShortcut(panel);
            panel.record.doClick();
            assertThat(panel.search.isEditable()).isFalse();
            panel.editor.record.doClick();
            assertThat(panel.search.isEditable()).isTrue();
            assertThat(panel.capture(KeyStroke.getKeyStroke("ctrl H"))).isTrue();
            panel.editor.cancel.doClick();
            assertThat(panel.capture(KeyStroke.getKeyStroke("ctrl H"))).isFalse();
            panel.close();
        });
    }

    @Test void theEditCardSetsRatherThanSaves() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ShortcutPanel(true, changes -> new CompletableFuture<>());
            panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
            panel.search.setText("Copy");
            clickShortcut(panel);
            assertThat(panel.editor.set.getText()).isEqualTo("Set");
            assertThat(panel.editor.message.getText()).contains("click Set");
            panel.editor.record.doClick();
            assertThat(panel.editor.message.getText()).contains("Apply or OK");
            panel.close();
        });
    }

    private static void clickShortcut(ShortcutPanel panel) {
        panel.tree.setSize(900, 400); panel.tree.doLayout();
        var cell = panel.tree.getRowBounds(1);
        panel.tree.dispatchEvent(new MouseEvent(panel.tree, MouseEvent.MOUSE_CLICKED, 0, 0,
            cell.x + 3, cell.y + 3, 1, false, MouseEvent.BUTTON1));
    }
    private static KeyEvent key(ShortcutPanel panel, int type, int code, int modifiers) {
        return new KeyEvent(panel, type, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED);
    }
}
