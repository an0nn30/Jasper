package dev.jasper.app.shortcuthelp;

import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.testsupport.KitTestSupport;
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
import static dev.jasper.app.shortcuthelp.ShortcutKit.*;
import static org.assertj.core.api.Assertions.*;

class ShortcutEditingTest {
    @Test void ordinaryReturnStagesAndEscapeDismissesWithoutCapturingTheirOwnedTails() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var saved=new ArrayList<Map<String,Optional<String>>>();
            var panel=panel(true,changes->{saved.add(changes);return new CompletableFuture<>();});
            panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true),List.of(),Map.of(),true));
            search(panel).setText("Copy");clickShortcut(panel);
            assertThat(panel.dispatch(key(panel,KeyEvent.KEY_PRESSED,KeyEvent.VK_ENTER,0))).isTrue();
            panel.dispatch(key(panel,KeyEvent.KEY_RELEASED,KeyEvent.VK_ENTER,0));
            assertThat(saved).isEmpty();
            panel.capture(KeyStroke.getKeyStroke("ctrl H"));
            panel.dispatch(key(panel,KeyEvent.KEY_PRESSED,KeyEvent.VK_ENTER,0));
            assertThat(saved).containsExactly(Map.of("copy",Optional.of("ctrl+h")));
            assertThat(panel.dispatch(key(panel,KeyEvent.KEY_PRESSED,KeyEvent.VK_ENTER,0))).isTrue();
            panel.dispatch(key(panel,KeyEvent.KEY_RELEASED,KeyEvent.VK_ENTER,0));
            assertThat(saved).hasSize(1);
            panel.close();
            var cancel=panel(true,changes->{throw new AssertionError("cancel saved");});
            cancel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true),List.of(),Map.of(),true));
            search(cancel).setText("Copy");clickShortcut(cancel);
            cancel.dispatch(key(cancel,KeyEvent.KEY_PRESSED,KeyEvent.VK_ESCAPE,0));
            assertThat(cancel.editor.isVisible()).isFalse();
            assertThat(cancel.dispatch(key(cancel,KeyEvent.KEY_RELEASED,KeyEvent.VK_ESCAPE,0))).isTrue();
            cancel.close();
        });
    }

    @Test void literalReturnAndEscapeAreOneShotAndPopupKeysDoNotReachTheRecorder() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            for(int key:new int[]{KeyEvent.VK_ENTER,KeyEvent.VK_ESCAPE}) {
                var saved=new ArrayList<Map<String,Optional<String>>>();var panel=panel(true,changes->{saved.add(changes);return new CompletableFuture<>();});
                try {
                    panel.setRows(ShortcutCatalog.rows(KeyBindings.withOverrides(true,Map.of("copy","none")),List.of(),Map.of(),true));
                    search(panel).setText("Copy");clickShortcut(panel);
                    var options=KitTestSupport.require(panel,"editor.options",javax.swing.JComponent.class).getComponentPopupMenu();
                    javax.swing.MenuSelectionManager.defaultManager().setSelectedPath(new javax.swing.MenuElement[]{options});
                    assertThat(panel.dispatch(key(panel,KeyEvent.KEY_PRESSED,key,0))).isFalse();assertThat(panel.editor.isVisible()).isTrue();
                    javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
                    editorAction(panel,"Record any key…");assertThat(message(panel)).contains("literally");
                    assertThat(panel.dispatch(key(panel,KeyEvent.KEY_PRESSED,key,0))).isTrue();panel.dispatch(key(panel,KeyEvent.KEY_RELEASED,key,0));
                    assertThat(panel.editor.isVisible()).isTrue();assertThat(saved).isEmpty();
                    assertThat(button(panel,"editor.value").getAccessibleContext().getAccessibleName()).isEqualTo(ShortcutText.format(KeyStroke.getKeyStroke(key,0)));
                    panel.dispatch(key(panel,KeyEvent.KEY_PRESSED,KeyEvent.VK_ENTER,0));panel.dispatch(key(panel,KeyEvent.KEY_RELEASED,KeyEvent.VK_ENTER,0));
                    assertThat(saved).hasSize(1);
                    panel.dispatch(key(panel,KeyEvent.KEY_PRESSED,KeyEvent.VK_ESCAPE,0));panel.dispatch(key(panel,KeyEvent.KEY_RELEASED,KeyEvent.VK_ESCAPE,0));
                    assertThat(panel.editor.isVisible()).as("busy staging cannot be dismissed").isTrue();
                }finally{javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();panel.close();}
            }
        });
    }

    @Test void clickingBindingCapturesWithoutExecutingAndRequiresExplicitReassignment() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var saved = new ArrayList<Map<String, Optional<String>>>();
            var panel = panel(true, changes -> { saved.add(changes); return new CompletableFuture<>(); });
            var root = new JRootPane(); root.setContentPane(panel);
            panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
            search(panel).setText("Copy");
            clickShortcut(panel);
            assertThat(panel.editor.isVisible()).isTrue();
            assertThat(panel.dispatch(key(panel, KeyEvent.KEY_PRESSED, KeyEvent.VK_V, InputEvent.META_DOWN_MASK))).isTrue();
            assertThat(panel.dispatch(key(panel, KeyEvent.KEY_RELEASED, KeyEvent.VK_V, 0))).isTrue();
            assertThat(button(panel, "editor.set").isEnabled()).isFalse();
            assertThat(message(panel)).contains("Paste");
            reassign(panel).doClick(0);
            assertThat(button(panel, "editor.set").isEnabled()).isTrue();
            button(panel, "editor.set").doClick(0);
            assertThat(saved).containsExactly(Map.of("copy", Optional.of("cmd+v"), "paste", Optional.of("none")));
            assertThat(button(panel, "editor.set").isEnabled()).isFalse();
            panel.close();
        });
    }

    @Test void unassignedCanBeBoundRemovedAndResetAndContextualRowsStayReadOnly() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var saved = new ArrayList<Map<String, Optional<String>>>();
            var panel = panel(true, changes -> { saved.add(changes); return CompletableFuture.completedFuture(null); });
            panel.setRows(ShortcutCatalog.rows(KeyBindings.withOverrides(true, Map.of("copy", "none")), List.of(), Map.of(), true));
            search(panel).setText("Copy"); clickShortcut(panel);
            assertThat(panel.capture(KeyStroke.getKeyStroke("ctrl H"))).isTrue();
            button(panel, "editor.set").doClick(0);
            assertThat(saved.getFirst()).isEqualTo(Map.of("copy", Optional.of("ctrl+h")));
            panel.close();
            var remove = panel(true, changes -> { saved.add(changes); return new CompletableFuture<>(); });
            remove.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
            search(remove).setText("Copy"); clickShortcut(remove); editorAction(remove,"Remove shortcut"); button(remove, "editor.set").doClick(0);
            assertThat(saved.getLast()).isEqualTo(Map.of("copy", Optional.of("none")));
            remove.close();
            var reset = panel(true, changes -> { saved.add(changes); return new CompletableFuture<>(); });
            reset.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
            search(reset).setText("Copy"); clickShortcut(reset); editorAction(reset,"Restore default"); button(reset, "editor.set").doClick(0);
            assertThat(saved.getLast()).isEqualTo(Map.of("copy", Optional.empty()));
            reset.close();
            var fixed = panel(true, changes -> { throw new AssertionError("contextual binding saved"); });
            fixed.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
            search(fixed).setText("Close find bar"); clickShortcut(fixed);
            assertThat(fixed.editor.isVisible()).isFalse();
            fixed.close();
        });
    }

    @Test void cancellationFocusLossAndExternalRefreshDoNotSaveOrLeakHeldKeys() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = panel(false, changes -> { throw new AssertionError("unexpected save"); });
            var rows = ShortcutCatalog.rows(KeyBindings.defaults(false), List.of(), Map.of(), false);
            panel.setRows(rows); search(panel).setText("Copy"); clickShortcut(panel);
            panel.dispatch(key(panel, KeyEvent.KEY_PRESSED, KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK));
            button(panel, "editor.cancel").doClick(0);
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
            panel[0] = panel(true, changes -> failure);
            panel[0].setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
            search(panel[0]).setText("Copy"); clickShortcut(panel[0]);
            panel[0].capture(KeyStroke.getKeyStroke("ctrl H")); button(panel[0], "editor.set").doClick(0);
        });
        failure.completeExceptionally(new java.io.IOException("Permission denied"));
        SwingUtilities.invokeAndWait(() -> {
            assertThat(panel[0].editor.isVisible()).isTrue();
            assertThat(message(panel[0])).contains("Permission denied");
            assertThat(message(panel[0])).startsWith("Could not set: ");
            assertThat(button(panel[0], "editor.set").isEnabled()).isTrue();
            panel[0].close();
        });
    }

    @Test void nativeCloseAndQuitRemainCapturedUntilEditingStops() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = panel(true, changes -> new CompletableFuture<>());
            var root = new JRootPane(); root.setContentPane(panel); panel.addNotify();
            try {
                panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
                search(panel).setText("Copy"); clickShortcut(panel);
                assertThat(dev.jasper.app.platform.WindowInput.captureShortcut(root, KeyStroke.getKeyStroke("meta Q"))).isTrue();
                assertThat(dev.jasper.app.platform.WindowInput.captureShortcut(root, KeyStroke.getKeyStroke("meta Q"))).isTrue();
                assertThat(dev.jasper.app.platform.WindowInput.captureShortcut(root, KeyStroke.getKeyStroke("meta W"))).isTrue();
                button(panel, "editor.cancel").doClick(0);
                assertThat(dev.jasper.app.platform.WindowInput.captureShortcut(root, KeyStroke.getKeyStroke("meta W"))).isFalse();
            } finally { panel.close(); panel.removeNotify(); }
        });
    }

    @Test void switchingFromSearchCaptureToEditCaptureStopsSearchCapture() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = panel(true, changes -> new CompletableFuture<>());
            panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
            search(panel).setText("Copy"); clickShortcut(panel);
            button(panel, "record").doClick(0);
            assertThat(search(panel).isEditable()).isFalse();
            assertThat(panel.editor.isVisible()).as("clearing the search hides the action's collapsed row and detaches its editor").isFalse();
            button(panel,"record").doClick(0);
            search(panel).setText("Copy");clickShortcut(panel);
            assertThat(search(panel).isEditable()).isTrue();
            assertThat(panel.capture(KeyStroke.getKeyStroke("ctrl H"))).isTrue();
            button(panel, "editor.cancel").doClick(0);
            assertThat(panel.capture(KeyStroke.getKeyStroke("ctrl H"))).isFalse();
            panel.close();
        });
    }

    @Test void theEditCardSetsRatherThanSaves() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = panel(true, changes -> new CompletableFuture<>());
            panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
            search(panel).setText("Copy");
            clickShortcut(panel);
            assertThat(button(panel, "editor.set").getText()).isEqualTo("Set");
            assertThat(message(panel)).contains("Return to set", "Esc to cancel");
            button(panel, "editor.record").doClick(0);
            assertThat(message(panel)).contains("Apply or OK");
            panel.close();
        });
    }

    /**
     * Container.validateTree lays out hidden children too, so the hidden edit card is laid out at 0x0 before its first
     * edit. Its wrapped note then measured a negative width and asked for a card ten times too tall.
     */
    @Test void theEditCardKeepsItsHeightAfterBeingLaidOutWhileHidden() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = panel(true, changes -> new CompletableFuture<>());
            panel.setRows(ShortcutCatalog.rows(KeyBindings.withOverrides(true, Map.of("copy", "none")), List.of(), Map.of(), true));
            panel.setSize(560, 600);
            layoutAll(panel);
            search(panel).setText("Copy"); clickShortcut(panel);
            assertThat(panel.editor.isVisible()).isTrue();
            layoutAll(panel);
            assertThat(panel.editor.getPreferredSize().height).as("the card's preferred height").isLessThan(200);
            assertThat(panel.editor.getHeight()).as("the card's height").isLessThan(200);
            panel.close();
        });
    }

    /** A narrow inline edit retains reachable actions and its overflow menu without clipping. */
    @Test void theEditButtonsWrapInsteadOfBeingCutOff() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = panel(true, changes -> new CompletableFuture<>());
            panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
            panel.setSize(440, 560);
            layoutAll(panel);
            search(panel).setText("Copy"); clickShortcut(panel);
            layoutAll(panel);
            var set = button(panel, "editor.set");
            assertThat(set.getWidth()).isGreaterThan(0);
            assertThat(set.getX()+set.getWidth()).isLessThanOrEqualTo(set.getParent().getWidth());
            var options=KitTestSupport.require(panel,"editor.options",javax.swing.JComponent.class);
            assertThat(options.getWidth()).isGreaterThan(0);
            editorAction(panel,"Restore default");
            assertThat(set.isEnabled()).isTrue();
            panel.close();
        });
    }

    @Test void clickingTheValueStartsRecordingAgain() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var saved = new ArrayList<Map<String, Optional<String>>>();
            var panel = panel(true, changes -> { saved.add(changes); return new CompletableFuture<>(); });
            panel.setRows(ShortcutCatalog.rows(KeyBindings.withOverrides(true, Map.of("copy", "none")), List.of(), Map.of(), true));
            search(panel).setText("Copy"); clickShortcut(panel);
            button(panel, "editor.record").doClick(0);
            assertThat(panel.capture(KeyStroke.getKeyStroke("ctrl H"))).as("recording stopped").isFalse();
            assertThat(message(panel)).contains("Apply or OK");
            button(panel, "editor.value").doClick(0);
            assertThat(message(panel)).as("clicking the value records").contains("Press the new shortcut");
            assertThat(panel.capture(KeyStroke.getKeyStroke("ctrl H"))).isTrue();
            assertThat(button(panel, "editor.value").getAccessibleContext().getAccessibleName()).isEqualTo(ShortcutText.format(KeyStroke.getKeyStroke("ctrl H")));
            button(panel, "editor.set").doClick(0);
            assertThat(saved).containsExactly(Map.of("copy", Optional.of("ctrl+h")));
            panel.close();
        });
    }

    /** Lays out every container, hidden ones included, as Container.validateTree does. */
    private static void layoutAll(java.awt.Container container) {
        container.doLayout();
        for (var child : container.getComponents()) if (child instanceof java.awt.Container nested) layoutAll(nested);
    }

    /** Clicks the first shortcut row of the current search, as the old tree's row 1. */
    private static void clickShortcut(ShortcutPanel panel) {
        if (panel.getWidth() == 0) panel.setSize(900, 600);
        clickRow(panel, 1);
    }
    private static KeyEvent key(ShortcutPanel panel, int type, int code, int modifiers) {
        return new KeyEvent(panel, type, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED);
    }
}
