package dev.jasper.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import javax.swing.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.DesktopTestSupport.*;

@DisabledOnOs(OS.WINDOWS)
class ApplicationActionsTest {
    @org.junit.jupiter.api.AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void sharedActionsResolveFocusedPaneAndDoNotOverrideTextEditing() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending)));
        pending.remove().run();
        TerminalPane[] first = new TerminalPane[1];
        edt(() -> { first[0] = owner[0].currentPane(); owner[0].invoke(ActionId.SPLIT_RIGHT); });
        pending.remove().run();
        edt(() -> {
            WindowContent ui = owner[0];
            ui.invoke(ActionId.FONT_BIGGER);
            assertThat(ui.currentPane().view().fontSize()).isEqualTo(17);
            assertThat(first[0].view().fontSize()).isEqualTo(16);
            ui.invoke(ActionId.FOCUS_PANE_LEFT); ui.invoke(ActionId.FIND);
            assertThat(first[0].findBar().isVisible()).isTrue();
            assertThat(ui.action(ActionId.OPEN_SETTINGS).isEnabled()).isFalse();
            assertThat(ui.action(ActionId.RELOAD_CONFIG).isEnabled()).isFalse();
            assertThat(ui.dispatchShortcut(ui.bindings().strokeFor(ActionId.PASTE).orElseThrow(), new JTextField())).isFalse();
            ui.setToolbarMode(WindowContent.ToolbarMode.HIDDEN);
            assertThat(ui.toolbar().isVisible()).isFalse();
            ui.setStatusVisible(false); assertThat(ui.status().isVisible()).isFalse();
            ui.close();
        });
    }
    @Test void focusingFindFieldRetargetsGlobalPaneActions() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending))); pending.remove().run();
        TerminalPane[] first = new TerminalPane[1];
        edt(() -> {
            first[0] = owner[0].currentPane(); owner[0].invoke(ActionId.FIND);
            owner[0].invoke(ActionId.SPLIT_RIGHT);
        });
        pending.remove().run();
        edt(() -> {
            JTextField field = first[0].findBar().queryField();
            java.awt.event.FocusEvent event = new java.awt.event.FocusEvent(field, java.awt.event.FocusEvent.FOCUS_GAINED);
            for (var listener : field.getFocusListeners()) listener.focusGained(event);
            owner[0].invoke(ActionId.FONT_BIGGER);
            assertThat(first[0].view().fontSize()).isEqualTo(17);
            owner[0].close();
        });
    }
    @Test void rootPaneBindingsExecuteGlobalActionsAndLeaveNativeTextBindingsFirst() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        edt(() -> {
            var original = UIManager.getLookAndFeel();
            com.formdev.flatlaf.FlatDarkLaf.setup();
            try {
                WindowContent owner = content(launcher(pending));
                BindingRoot root = new BindingRoot(); root.setContentPane(owner);
                owner.installRootBindings(root);
                assertThat(root.activate(owner.bindings().strokeFor(ActionId.NEW_TAB).orElseThrow())).isTrue();
                assertThat(owner.tabStrip().getTabCount()).isEqualTo(2);
                BindingField field = new BindingField(); owner.add(field, java.awt.BorderLayout.WEST);
                int[] edits = {0};
                // Override the installed native editor actions, retaining the real focused input map.
                field.getActionMap().put(javax.swing.text.DefaultEditorKit.copyAction, new AbstractAction() {
                    public void actionPerformed(java.awt.event.ActionEvent event) { edits[0]++; }
                });
                field.getActionMap().put(javax.swing.text.DefaultEditorKit.pasteAction, new AbstractAction() {
                    public void actionPerformed(java.awt.event.ActionEvent event) { edits[0]++; }
                });
                boolean mac = System.getProperty("os.name").startsWith("Mac");
                int modifier = mac ? java.awt.event.InputEvent.META_DOWN_MASK : java.awt.event.InputEvent.CTRL_DOWN_MASK;
                KeyStroke copy = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, modifier);
                KeyStroke paste = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, modifier);
                if (mac) {
                    assertThat(copy).isEqualTo(owner.bindings().strokeFor(ActionId.COPY).orElseThrow());
                    assertThat(paste).isEqualTo(owner.bindings().strokeFor(ActionId.PASTE).orElseThrow());
                }
                assertThat(field.activate(copy)).isTrue();
                assertThat(field.activate(paste)).isTrue();
                assertThat(edits[0]).isEqualTo(2);
                owner.close();
                assertThat(root.activate(owner.bindings().strokeFor(ActionId.NEW_TAB).orElseThrow())).isFalse();
            } finally {
                try { UIManager.setLookAndFeel(original); }
                catch (javax.swing.UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
            }
        });
    }

    private static final class BindingRoot extends JRootPane {
        boolean activate(KeyStroke stroke) {
            return processKeyBinding(stroke, new java.awt.event.KeyEvent(this,
                java.awt.event.KeyEvent.KEY_PRESSED, 0, stroke.getModifiers(), stroke.getKeyCode(),
                java.awt.event.KeyEvent.CHAR_UNDEFINED), WHEN_IN_FOCUSED_WINDOW, true);
        }
    }

    private static final class BindingField extends JTextField {
        boolean activate(KeyStroke stroke) {
            return processKeyBinding(stroke, new java.awt.event.KeyEvent(this,
                java.awt.event.KeyEvent.KEY_PRESSED, 0, stroke.getModifiers(), stroke.getKeyCode(),
                java.awt.event.KeyEvent.CHAR_UNDEFINED), WHEN_FOCUSED, true);
        }
    }
}
