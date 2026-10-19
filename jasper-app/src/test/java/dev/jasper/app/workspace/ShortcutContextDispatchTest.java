package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.KeyContext;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.dock.Slot;
import java.awt.Component;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class ShortcutContextDispatchTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private static KeyStroke mac(String binding) { return KeyBindings.parse(binding, true).orElseThrow(); }

    @Test void theMostSpecificContextAroundFocusWins() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), true);
            var model = new Contributions(); owner.connectContributions(model);
            List<String> ran = new ArrayList<>();
            model.addAction("dev.x.everywhere", "Everywhere", null, List.of(), Optional.of("cmd+alt+j"), Optional.empty(),
                KeyContext.GLOBAL, invocation -> ran.add("global"));
            model.addAction("dev.x.edit", "Edit", null, List.of(), Optional.of("cmd+alt+j"), Optional.empty(),
                KeyContext.content("dev.x"), invocation -> ran.add("content"));
            model.addAction("dev.x.hosts.new", "New Host", null, List.of(), Optional.of("cmd+alt+j"), Optional.empty(),
                KeyContext.panel("dev.x.hosts"), invocation -> ran.add("panel"));
            var panelField = new JTextField();
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> panelField);
            model.requestPanel(new Contributions.PanelRequest(owner.id(), "dev.x.hosts", Contributions.PanelRequest.Op.SHOW));
            TerminalPane pane = owner.currentPane();

            assertThat(owner.dispatchShortcut(mac("cmd+alt+j"), pane)).isTrue();
            assertThat(owner.dispatchShortcut(mac("cmd+alt+j"), panelField)).isTrue();
            var editor = new JPanel(); var inside = new JTextArea(); editor.add(inside);
            model.tabs().open("dev.x", "a", "A", owner.id(), editor);
            assertThat(owner.dispatchShortcut(mac("cmd+alt+j"), inside)).isTrue();
            assertThat(owner.dispatchShortcut(mac("cmd+alt+j"), owner)).isTrue();
            assertThat(ran).containsExactly("global", "panel", "content", "global");
        });
    }

    @Test void disabledKeysInATerminalPaneAreSwallowedWhileInapplicableKeysReachTheFocusedComponent() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), false);
            var model = new Contributions(); owner.connectContributions(model);
            List<String> ran = new ArrayList<>();
            var here = model.addAction("dev.x.here", "Here", null, List.of(), Optional.of("cmd+shift+o"), Optional.empty(),
                KeyContext.TERMINAL, invocation -> ran.add("here"));
            var anywhere = model.addAction("dev.x.anywhere", "Anywhere", null, List.of(), Optional.of("cmd+shift+y"),
                Optional.empty(), KeyContext.GLOBAL, invocation -> ran.add("anywhere"));
            KeyStroke stroke = KeyBindings.parse("ctrl+alt+shift+o", false).orElseThrow();
            KeyStroke global = KeyBindings.parse("ctrl+alt+shift+y", false).orElseThrow();
            TerminalPane pane = owner.currentPane();
            var field = new JTextField();
            assertThat(owner.dispatchShortcut(stroke, pane)).as("the Alt rule, in a terminal pane").isTrue();
            assertThat(owner.dispatchShortcut(stroke, field)).as("outside a terminal pane").isFalse();
            here.setEnabled(false); anywhere.setEnabled(false);
            // X11 would deliver these keys to the shell as control characters, so a pane swallows them (plan Deviation 9).
            assertThat(owner.claimsShortcut(stroke, pane)).as("disabled, in a terminal pane").isTrue();
            assertThat(owner.dispatchShortcut(stroke, pane)).as("disabled, in a terminal pane").isTrue();
            assertThat(owner.dispatchShortcut(global, pane)).as("a disabled Global action, in a terminal pane").isTrue();
            assertThat(owner.dispatchShortcut(stroke, field)).as("disabled and inapplicable outside a pane").isFalse();
            assertThat(owner.claimsShortcut(global, field)).as("disabled, outside a terminal pane").isFalse();
            assertThat(owner.dispatchShortcut(global, field)).as("disabled, outside a terminal pane").isFalse();
            assertThat(ran).as("a disabled action never runs").containsExactly("here");
            assertThat(owner.dispatchShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK), pane))
                .as("Ctrl+K belongs to the shell on Linux").isFalse();
            assertThat(owner.dispatchShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), pane)).isFalse();
        });
    }

    @Test void aDisabledBuiltInIsSwallowedInATerminalPane() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), false);
            TerminalPane pane = owner.currentPane();
            KeyStroke copy = KeyBindings.parse("ctrl+shift+c", false).orElseThrow();
            assertThat(owner.bindings().strokeFor(ActionId.COPY)).contains(copy);
            owner.updateActions();
            assertThat(owner.action(ActionId.COPY).isEnabled()).as("no selection to copy").isFalse();
            assertThat(owner.claimsShortcut(copy, pane)).as("Ctrl+Shift+C would be ^C to the shell").isTrue();
            assertThat(owner.dispatchShortcut(copy, pane)).isTrue();
            assertThat(owner.action(ActionId.COPY).isEnabled()).as("nothing was selected or copied").isFalse();
            assertThat(owner.dispatchShortcut(copy, new JTextField())).as("a text field keeps its own copy").isFalse();
        });
    }

    @Test void theRootInputMapConsumesOnlyKeysAnActionTakes() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), true);
            var root = new BindingRoot(); root.setContentPane(owner); owner.installRootBindings(root);
            assertThat(root.activate(mac("cmd+t"))).as("New Tab").isTrue();
            assertThat(owner.tabCount()).isEqualTo(2);
            assertThat(root.activate(mac("cmd+9"))).as("no ninth tab: the key passes").isFalse();
            assertThat(root.activate(mac("cmd+k"))).as("Clear Scrollback applies only in a terminal pane").isFalse();
        });
    }

    @Test void closePaneClosesTheFocusedPaneThenTheTabWithItsLastPane() throws Exception {
        // A split needs a running shell, so the launcher's queued work runs between the steps, as in TerminalTabTest.
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        TerminalTab[] tab = new TerminalTab[1];
        edt(() -> { owner[0] = content(idleLauncher(pending), true); owner[0].newTab(HOME); });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> { tab[0] = owner[0].currentTab(); tab[0].split(SplitTree.Axis.RIGHT); });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            assertThat(tab[0].panes()).hasSize(2);
            assertThat(owner[0].dispatchShortcut(mac("cmd+w"), owner[0].currentPane())).isTrue();
            assertThat(tab[0].panes()).hasSize(1);
            assertThat(owner[0].tabCount()).isEqualTo(2);
            assertThat(owner[0].dispatchShortcut(mac("cmd+w"), owner[0].currentPane())).isTrue();
            assertThat(owner[0].tabCount()).isEqualTo(1);
        });
    }

    @Test void menuItemsShowTheirAcceleratorsButOnlyTheRootMapTakesKeys() throws Exception {
        for (boolean mac : new boolean[]{true, false}) {
            // Clear Scrollback and Paste are enabled only with a running shell, so the launcher's queued start runs first.
            Queue<Runnable> pending = new ArrayDeque<>();
            WindowContent[] holder = new WindowContent[1];
            edt(() -> holder[0] = content(idleLauncher(pending), mac));
            while (!pending.isEmpty()) pending.remove().run();
            until(() -> holder[0].currentPane().running());
            edt(() -> {
                var owner = holder[0];
                var model = new Contributions(); owner.connectContributions(model);
                var field = new JTextField();
                model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> field);
                model.requestPanel(new Contributions.PanelRequest(owner.id(), "dev.x.hosts", Contributions.PanelRequest.Op.SHOW));
                var previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
                    @Override public Component getFocusOwner() { return field; }
                });
                // A JInternalFrame is a top ancestor for Swing's KeyboardManager, so a key the focused field leaves goes
                // through the real WHEN_IN_FOCUSED_WINDOW path: the root pane's map, then the menu bar's items.
                var frame = new JInternalFrame();
                frame.addVetoableChangeListener(event -> {
                    if (JInternalFrame.IS_SELECTED_PROPERTY.equals(event.getPropertyName()))
                        throw new java.beans.PropertyVetoException("no focus requests headlessly", event);
                });
                try {
                    frame.setContentPane(owner); frame.setJMenuBar(owner.menuBar());
                    owner.installRootBindings(frame.getRootPane());
                    frame.addNotify(); frame.setVisible(true);
                    owner.updateActions();
                    List<String> fired = new ArrayList<>();
                    for (ActionId id : List.of(ActionId.CLEAR_SCROLLBACK, ActionId.PASTE, ActionId.NEW_TAB)) {
                        assertThat(owner.action(id).isEnabled()).as(id + " is enabled").isTrue();
                        menuItem(owner, id).addActionListener(event -> fired.add(id.id()));
                    }
                    KeyStroke clear = owner.bindings().strokeFor(ActionId.CLEAR_SCROLLBACK).orElseThrow();
                    assertThat(clear).isEqualTo(mac ? mac("cmd+k") : KeyBindings.parse("ctrl+shift+k", false).orElseThrow());
                    boolean taken = press(field, clear);
                    assertThat(fired).as("Edit > Clear Scrollback did not fire").isEmpty();
                    assertThat(taken).as("Clear Scrollback applies only in a terminal pane").isFalse();
                    if (!mac) {
                        KeyStroke paste = KeyBindings.parse("ctrl+shift+v", false).orElseThrow();
                        assertThat(owner.bindings().strokeFor(ActionId.PASTE)).contains(paste);
                        taken = press(field, paste);
                        assertThat(fired).as("Edit > Paste did not paste into the shell").isEmpty();
                        assertThat(taken).as("a text field's paste stays with the field").isFalse();
                    }
                    int tabs = owner.tabCount();
                    assertThat(press(field, owner.bindings().strokeFor(ActionId.NEW_TAB).orElseThrow())).isTrue();
                    assertThat(owner.tabCount()).as("New Tab runs exactly once").isEqualTo(tabs + 1);
                    assertThat(fired).as("no menu item fired from the keyboard").isEmpty();
                    assertThat(menuItem(owner, ActionId.CLEAR_SCROLLBACK).getAccelerator()).as("still shown").isEqualTo(clear);
                    menuItem(owner, ActionId.NEW_TAB).doClick(0);
                    assertThat(owner.tabCount()).as("a click still runs the item").isEqualTo(tabs + 2);
                } finally {
                    frame.removeNotify();
                    KeyboardFocusManager.setCurrentKeyboardFocusManager(previous);
                }
            });
            closeOwners();
        }
    }

    /** Delivers a key press the way the focused component's ancestors and window would, after the component itself. */
    private static boolean press(Component focus, KeyStroke stroke) {
        return SwingUtilities.processKeyBindings(new KeyEvent(focus, KeyEvent.KEY_PRESSED, 0,
            stroke.getModifiers(), stroke.getKeyCode(), KeyEvent.CHAR_UNDEFINED));
    }

    private static JMenuItem menuItem(WindowContent owner, ActionId id) {
        for (JMenu menu : owner.chrome().menuBar().menus())
            for (int i = 0; i < menu.getItemCount(); i++)
                if (menu.getItem(i) != null && menu.getItem(i).getAction() == owner.action(id)) return menu.getItem(i);
        throw new AssertionError("no menu item for " + id);
    }

    private static final class BindingRoot extends JRootPane {
        boolean activate(KeyStroke stroke) {
            return processKeyBinding(stroke, new KeyEvent(this, KeyEvent.KEY_PRESSED, 0,
                stroke.getModifiers(), stroke.getKeyCode(), KeyEvent.CHAR_UNDEFINED), WHEN_IN_FOCUSED_WINDOW, true);
        }
    }
}
