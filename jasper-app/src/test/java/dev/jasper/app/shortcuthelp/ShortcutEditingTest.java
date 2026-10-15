package dev.jasper.app.shortcuthelp;

import dev.jasper.app.appearance.ThemeTestSupport;
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
    @Test void ordinaryRecorderAllowsTraversalAndKeyboardMoreWithoutLosingRecordableKeys() throws Exception {
        inMenuLookAndFeels(() -> {
            var panel=panel(true,changes->new CompletableFuture<>());
            var root=new KeyboardRoot();root.setContentPane(panel);root.setFocusCycleRoot(true);
            root.setFocusTraversalPolicy(new MountedTraversalPolicy());
            var lightweightHost=new javax.swing.JPanel();lightweightHost.add(root);
            var priorFocus=java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var focus=new TraversalFocus(root);java.awt.KeyboardFocusManager.setCurrentKeyboardFocusManager(focus);
            lightweightHost.addNotify();
            try {
                panel.setRows(ShortcutCatalog.rows(KeyBindings.withOverrides(true,Map.of("copy","none")),List.of(),Map.of(),true));
                search(panel).setText("Copy");clickShortcut(panel);
                root.setSize(900,600);layoutAll(root);
                var receiver=button(panel,"editor.value");
                focus.focus(receiver);
                traverse(panel,focus,0);
                assertThat(focus.getFocusOwner()).isNotSameAs(receiver);
                traverse(panel,focus,InputEvent.SHIFT_DOWN_MASK);
                assertThat(focus.getFocusOwner()).isSameAs(receiver);
                var more=button(panel,"editor.options.main");
                for(int steps=0;focus.getFocusOwner()!=more && steps<30;steps++)traverse(panel,focus,0);
                assertThat(focus.getFocusOwner()).as("mounted traversal adapter reaches More").isSameAs(more);
                keyboardBinding(panel,more,KeyEvent.KEY_PRESSED,KeyEvent.VK_SPACE);
                keyboardBinding(panel,more,KeyEvent.KEY_RELEASED,KeyEvent.VK_SPACE);
                var popup=KitTestSupport.require(panel,"editor.options",javax.swing.JComponent.class).getComponentPopupMenu();
                assertThat(popup.isVisible()).isTrue();
                var literal=(javax.swing.JMenuItem)popup.getComponent(0);
                javax.swing.MenuSelectionManager.defaultManager().setSelectedPath(new javax.swing.MenuElement[]{popup,literal});
                // BasicPopupMenuUI requests root focus; only the OS transfer is supplied by this headless fixture.
                focus.focus(root);
                var enter=event(root,KeyEvent.KEY_PRESSED,KeyEvent.VK_ENTER,0);
                assertThat(panel.dispatch(enter)).isFalse();
                assertThat(javax.swing.MenuSelectionManager.defaultManager().getSelectedPath()).containsExactly(popup,literal);
                assertThat(root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStrokeForEvent(enter))).isEqualTo("return");
                assertThat(root.windowBinding(enter)).as("actual selected popup keyboard binding").isTrue();
                assertThat(message(panel)).contains("literally");
                assertThat(javax.swing.MenuSelectionManager.defaultManager().getSelectedPath()).isEmpty();
                assertThat(panel.dispatch(event(receiver,KeyEvent.KEY_PRESSED,KeyEvent.VK_ENTER,0))).isTrue();
                assertThat(panel.dispatch(new KeyEvent(receiver,KeyEvent.KEY_TYPED,0,0,KeyEvent.VK_UNDEFINED,'\n'))).isTrue();
                assertThat(message(panel)).contains("literally");
                assertThat(panel.dispatch(event(receiver,KeyEvent.KEY_RELEASED,KeyEvent.VK_ENTER,0))).isTrue();
                assertThat(panel.dispatch(event(receiver,KeyEvent.KEY_PRESSED,KeyEvent.VK_TAB,0))).isTrue();
                panel.dispatch(event(receiver,KeyEvent.KEY_RELEASED,KeyEvent.VK_TAB,0));
                assertThat(receiver.getAccessibleContext().getAccessibleName()).isEqualTo("Tab");
                assertThat(panel.dispatch(event(receiver,KeyEvent.KEY_PRESSED,KeyEvent.VK_SPACE,0))).isTrue();
                panel.dispatch(event(receiver,KeyEvent.KEY_RELEASED,KeyEvent.VK_SPACE,0));
                assertThat(receiver.getAccessibleContext().getAccessibleName()).isEqualTo("Space");
                assertThat(panel.dispatch(event(receiver,KeyEvent.KEY_PRESSED,KeyEvent.VK_TAB,InputEvent.CTRL_DOWN_MASK))).isTrue();
                panel.dispatch(event(receiver,KeyEvent.KEY_RELEASED,KeyEvent.VK_TAB,0));
            } finally {javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();panel.close();lightweightHost.removeNotify();java.awt.KeyboardFocusManager.setCurrentKeyboardFocusManager(priorFocus);}
        });
    }

    @Test void popupEscapeTailsCannotCancelTheEditorAfterDismissal() throws Exception {
        inMenuLookAndFeels(() -> {
            var panel=panel(true,changes->new CompletableFuture<>());
            var root=new KeyboardRoot();root.setContentPane(panel);
            try {
                panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true),List.of(),Map.of(),true));
                search(panel).setText("Copy");clickShortcut(panel);
                var popup=KitTestSupport.require(panel,"editor.options",javax.swing.JComponent.class).getComponentPopupMenu();
                keyboardBinding(panel,button(panel,"editor.options.main"),KeyEvent.KEY_PRESSED,KeyEvent.VK_SPACE);
                keyboardBinding(panel,button(panel,"editor.options.main"),KeyEvent.KEY_RELEASED,KeyEvent.VK_SPACE);
                assertThat(javax.swing.MenuSelectionManager.defaultManager().getSelectedPath()).contains(popup);
                for(int repeat=0;repeat<2;repeat++) {
                    var down=key(panel,KeyEvent.KEY_PRESSED,KeyEvent.VK_DOWN,0);
                    assertThat(panel.dispatch(down)).isFalse();assertThat(root.windowBinding(down)).isTrue();
                }
                assertThat(panel.dispatch(key(panel,KeyEvent.KEY_RELEASED,KeyEvent.VK_DOWN,0))).isFalse();
                assertThat(panel.dispatch(key(panel,KeyEvent.KEY_PRESSED,KeyEvent.VK_ESCAPE,0))).isFalse();
                assertThat(root.windowBinding(key(panel,KeyEvent.KEY_PRESSED,KeyEvent.VK_ESCAPE,0))).isTrue();
                assertThat(javax.swing.MenuSelectionManager.defaultManager().getSelectedPath()).isEmpty();
                assertThat(panel.dispatch(key(panel,KeyEvent.KEY_PRESSED,KeyEvent.VK_ESCAPE,0))).isTrue();
                assertThat(panel.editor.isVisible()).as("popup-owned Escape repeat does not dismiss edit").isTrue();
                assertThat(panel.dispatch(new KeyEvent(panel,KeyEvent.KEY_TYPED,0,0,KeyEvent.VK_UNDEFINED,(char)27))).isTrue();
                assertThat(panel.dispatch(key(panel,KeyEvent.KEY_RELEASED,KeyEvent.VK_ESCAPE,0))).isTrue();
                assertThat(panel.dispatch(key(panel,KeyEvent.KEY_PRESSED,KeyEvent.VK_ESCAPE,0))).isTrue();
                assertThat(panel.editor.isVisible()).isFalse();
                panel.dispatch(key(panel,KeyEvent.KEY_RELEASED,KeyEvent.VK_ESCAPE,0));
            } finally {javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();panel.close();}
        });
    }

    @Test void popupOwnershipIgnoresFreshForeignKeysAndClearsAtFocusLossHideAndClose() throws Exception {
        inMenuLookAndFeels(() -> {
            for(int boundary=0;boundary<3;boundary++) {
                var panel=panel(true,changes->new CompletableFuture<>());var root=new KeyboardRoot();root.setContentPane(panel);
                try {
                    panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true),List.of(),Map.of(),true));
                    search(panel).setText("Copy");clickShortcut(panel);
                    keyboardBinding(panel,button(panel,"editor.options.main"),KeyEvent.KEY_PRESSED,KeyEvent.VK_SPACE);
                    keyboardBinding(panel,button(panel,"editor.options.main"),KeyEvent.KEY_RELEASED,KeyEvent.VK_SPACE);
                    assertThat(panel.dispatch(key(panel,KeyEvent.KEY_PRESSED,KeyEvent.VK_ESCAPE,0))).isFalse();
                    assertThat(root.windowBinding(key(panel,KeyEvent.KEY_PRESSED,KeyEvent.VK_ESCAPE,0))).isTrue();
                    var foreign=new JRootPane();var other=new javax.swing.JButton("Other");foreign.setContentPane(other);
                    assertThat(panel.dispatch(event(other,KeyEvent.KEY_PRESSED,KeyEvent.VK_F1,0))).isFalse();
                    assertThat(panel.dispatch(new KeyEvent(panel,KeyEvent.KEY_TYPED,0,0,KeyEvent.VK_UNDEFINED,(char)27))).isTrue();
                    assertThat(panel.editor.isVisible()).isTrue();
                    switch(boundary) {case 0 -> panel.focusLeftWindow();case 1 -> panel.removeNotify();default -> panel.close();}
                    assertThat(panel.dispatch(key(panel,KeyEvent.KEY_RELEASED,KeyEvent.VK_ESCAPE,0))).isFalse();
                    assertThat(panel.dispatch(key(panel,KeyEvent.KEY_PRESSED,KeyEvent.VK_H,0))).isFalse();
                    assertThat(panel.dispatch(event(other,KeyEvent.KEY_PRESSED,KeyEvent.VK_ESCAPE,0))).isFalse();
                } finally {javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();panel.close();}
            }
        });
    }

    /** Exercise default Swing and the app's installed Snapshot/FlatLaf independent of suite order. */
    private static void inMenuLookAndFeels(Runnable scenario) throws Exception {
        for(boolean flat:new boolean[]{false,true})SwingUtilities.invokeAndWait(()->{
            var prior=javax.swing.UIManager.getLookAndFeel();
            try {
                if(flat) {
                    assertThat(dev.jasper.app.appearance.ThemeTestSupport.install(true)).isTrue();
                    assertThat(javax.swing.UIManager.getLookAndFeel().getClass().getName()).contains("SnapshotLaf");
                } else javax.swing.UIManager.setLookAndFeel(new javax.swing.plaf.metal.MetalLookAndFeel());
                scenario.run();
            } catch(javax.swing.UnsupportedLookAndFeelException failure) {throw new AssertionError(failure);}
            finally {
                try {javax.swing.UIManager.setLookAndFeel(prior);}
                catch(javax.swing.UnsupportedLookAndFeelException failure) {throw new AssertionError(failure);}
            }
        });
    }

    /** Calls the real installed selected-window map without requiring a native focused window. */
    private static final class KeyboardRoot extends JRootPane {
        // Lightweight peers support Swing traversal; popup positioning stays at the headless boundary.
        @Override public boolean isShowing(){return false;}
        java.awt.Component traversalTarget(java.awt.Component source,boolean previous) {
            return previous?getFocusTraversalPolicy().getComponentBefore(this,source):getFocusTraversalPolicy().getComponentAfter(this,source);
        }
        boolean windowBinding(KeyEvent event) {
            return processKeyBinding(KeyStroke.getKeyStrokeForEvent(event),event,
                    javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW,event.getID()==KeyEvent.KEY_PRESSED);
        }
    }
    /** Real AWT enumeration plus Swing eligibility, without LayoutComparator's native Window requirement. */
    private static final class MountedTraversalPolicy extends java.awt.ContainerOrderFocusTraversalPolicy {
        private final SwingEligibility swing=new SwingEligibility();
        @Override protected boolean accept(java.awt.Component component){return swing.eligible(component);}
    }
    private static final class SwingEligibility extends javax.swing.LayoutFocusTraversalPolicy {
        boolean eligible(java.awt.Component component){return super.accept(component);}
    }
    private static final class TraversalFocus extends java.awt.DefaultKeyboardFocusManager {
        private final KeyboardRoot root;
        TraversalFocus(KeyboardRoot root){this.root=root;}
        void focus(java.awt.Component component){setGlobalFocusOwner(component);setGlobalPermanentFocusOwner(component);}
        @Override public void focusNextComponent(java.awt.Component source){focus(root.traversalTarget(source,false));}
        @Override public void focusPreviousComponent(java.awt.Component source){focus(root.traversalTarget(source,true));}
    }
    private static void traverse(ShortcutPanel panel,TraversalFocus focus,int modifiers) {
        var source=focus.getFocusOwner();var press=event(source,KeyEvent.KEY_PRESSED,KeyEvent.VK_TAB,modifiers);
        assertThat(panel.dispatch(press)).as("Tab reaches AWT traversal").isFalse();focus.processKeyEvent(source,press);
        var release=event(focus.getFocusOwner(),KeyEvent.KEY_RELEASED,KeyEvent.VK_TAB,modifiers);
        assertThat(panel.dispatch(release)).isFalse();focus.processKeyEvent(focus.getFocusOwner(),release);
    }
    private static KeyEvent event(java.awt.Component source,int type,int code,int modifiers) {
        return new KeyEvent(source,type,0,modifiers,code,KeyEvent.CHAR_UNDEFINED);
    }
    /** Preserve FlatLaf menu bindings/listeners; replace only its screen-dependent popup allocation. */
    private static final class HeadlessFlatPopup extends com.formdev.flatlaf.ui.FlatPopupMenuUI {
        @Override public javax.swing.Popup getPopup(javax.swing.JPopupMenu popup,int x,int y) {
            return new javax.swing.Popup() {
                @Override public void show() {}
                @Override public void hide() {}
            };
        }
    }
    private static void keyboardBinding(ShortcutPanel panel,javax.swing.JComponent source,int type,int code) {
        var popup=KitTestSupport.require(panel,"editor.options",javax.swing.JComponent.class).getComponentPopupMenu();
        if(popup.getUI() instanceof com.formdev.flatlaf.ui.FlatPopupMenuUI && !(popup.getUI() instanceof HeadlessFlatPopup))
            popup.setUI(new HeadlessFlatPopup());
        var event=event(source,type,code,0);
        assertThat(panel.dispatch(event)).as("auxiliary key reaches real Swing binding").isFalse();
        assertThat(SwingUtilities.processKeyBindings(event)).isTrue();
    }

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
                    var optionsButton=KitTestSupport.require(panel,"editor.options",javax.swing.JComponent.class);
                    var options=optionsButton.getComponentPopupMenu();
                    // A shown popup has its invoker; FlatLaf's Linux popup canceller looks up the invoker's window.
                    options.setInvoker(optionsButton);
                    javax.swing.MenuSelectionManager.defaultManager().setSelectedPath(new javax.swing.MenuElement[]{options});
                    assertThat(panel.dispatch(key(panel,KeyEvent.KEY_PRESSED,key,0))).isFalse();assertThat(panel.editor.isVisible()).isTrue();
                    javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
                    assertThat(panel.dispatch(key(panel,KeyEvent.KEY_RELEASED,key,0))).as("release popup-owned press before a fresh literal gesture").isTrue();
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
