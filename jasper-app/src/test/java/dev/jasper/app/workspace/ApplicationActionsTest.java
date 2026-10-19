package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.ToolbarMode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import javax.swing.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.workspace.DesktopTestSupport.*;

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
            assertThat(ui.currentPane().view().fontSize()).isEqualTo(13);
            assertThat(first[0].view().fontSize()).isEqualTo(12);
            ui.invoke(ActionId.FOCUS_PANE_LEFT); ui.invoke(ActionId.FIND);
            assertThat(first[0].findBar().isVisible()).isTrue();
            assertThat(ui.action(ActionId.OPEN_SETTINGS).isEnabled()).isFalse();
            assertThat(ui.action(ActionId.RELOAD_CONFIG).isEnabled()).isFalse();
            assertThat(ui.dispatchShortcut(ui.bindings().strokeFor(ActionId.PASTE).orElseThrow(), new JTextField())).isFalse();
            ui.setToolbarMode(ToolbarMode.HIDDEN);
            assertThat(ui.toolbar().isVisible()).isFalse();
            ui.setStatusVisible(false); assertThat(ui.status().isVisible()).isFalse();
            ui.close();
        });
    }
    /** Spec 13: File › Open File… asks the window's chooser from the pane's folder and opens the file as a Cmd-click does. */
    @Test void openFileAsksTheChooserAndOpensTheChosenFileAsACmdClickDoes() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending)));
        pending.remove().run();
        var file = java.nio.file.Path.of("notes.txt").toAbsolutePath();
        var answers = new ArrayDeque<Optional<java.nio.file.Path>>(List.of(Optional.of(file), Optional.empty()));
        var asked = new ArrayList<Optional<java.nio.file.Path>>();
        var opened = new ArrayList<String>();
        edt(() -> {
            WindowContent ui = owner[0];
            ui.fileChooser = folder -> { asked.add(folder); return answers.remove(); };
            ui.onOpenFile = (chosen, line) -> opened.add(chosen + ":" + line);
            assertThat(ui.action(ActionId.OPEN_FILE).isEnabled()).isTrue();
            assertThat(ui.action(ActionId.OPEN_FILE).getValue(Action.NAME)).isEqualTo("Open File…");
            assertThat(ui.action(ActionId.OPEN_FILE).getValue(Action.SHORT_DESCRIPTION)).isEqualTo("Open a file with the default editor");
            assertThat(ui.action(ActionId.OPEN_FILE).getValue(Action.ACCELERATOR_KEY)).as("no default binding").isNull();
            JMenu fileMenu = null;
            for (int i = 0; i < ui.menuBar().getMenuCount(); i++) if ("File".equals(ui.menuBar().getMenu(i).getText())) fileMenu = ui.menuBar().getMenu(i);
            var labels = new ArrayList<String>();
            for (var child : fileMenu.getMenuComponents()) if (child instanceof JMenuItem item) labels.add(item.getText());
            assertThat(labels).containsSubsequence("New Window", "Open File…");
            var command = ui.commands().find("open_file").orElseThrow();
            assertThat(command.title()).as("spec 14.7: also a palette command").isEqualTo("Open File…");
            assertThat(command.keywords()).contains("open file", "open", "editor", "browse");
            assertThat(dev.jasper.app.commands.CommandSearch.find(ui.commands().entries(), "open file", List.of(), 5).stream()
                .map(dev.jasper.app.commands.Command::id)).as("the palette finds it").contains("open_file");
            ui.invoke(ActionId.OPEN_FILE);
            assertThat(asked).singleElement().as("the chooser starts in the pane's folder").isEqualTo(Optional.ofNullable(ui.directory()));
            assertThat(opened).containsExactly(file + ":0");
            ui.invoke(ActionId.OPEN_FILE);
            assertThat(opened).as("cancelling opens nothing").containsExactly(file + ":0");
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
            assertThat(first[0].view().fontSize()).isEqualTo(13);
            owner[0].close();
        });
    }
    @Test void rootPaneBindingsExecuteGlobalActionsAndLeaveNativeTextBindingsFirst() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        edt(() -> {
            var original = UIManager.getLookAndFeel();
            dev.jasper.app.appearance.ThemeTestSupport.install(true);
            try {
                WindowContent owner = content(launcher(pending));
                BindingRoot root = new BindingRoot(); root.setContentPane(owner);
                owner.installRootBindings(root);
                assertThat(root.activate(owner.bindings().strokeFor(ActionId.NEW_TAB).orElseThrow())).isTrue();
                assertThat(owner.tabCount()).isEqualTo(2);
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
