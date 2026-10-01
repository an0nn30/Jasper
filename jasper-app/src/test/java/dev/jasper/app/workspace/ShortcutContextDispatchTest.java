package dev.jasper.app.workspace;

import dev.jasper.app.commands.KeyContext;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelRegion;
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
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), PanelRegion.LEFT, site -> panelField);
            model.requestPanel(new Contributions.PanelRequest(owner.id(), "dev.x.hosts", Contributions.PanelRequest.Op.SHOW));
            TerminalPane pane = owner.currentPane();

            assertThat(owner.dispatchShortcut(mac("cmd+alt+j"), pane)).isTrue();
            assertThat(owner.dispatchShortcut(mac("cmd+alt+j"), panelField)).isTrue();
            var editor = new JPanel(); var inside = new JTextArea(); editor.add(inside);
            model.content().open("dev.x", "a", "A", owner.id(), editor);
            assertThat(owner.dispatchShortcut(mac("cmd+alt+j"), inside)).isTrue();
            assertThat(owner.dispatchShortcut(mac("cmd+alt+j"), owner)).isTrue();
            assertThat(ran).containsExactly("global", "panel", "content", "global");
        });
    }

    @Test void disabledAndInapplicableActionsLeaveTheirKeysToTheFocusedComponent() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), false);
            var model = new Contributions(); owner.connectContributions(model);
            List<String> ran = new ArrayList<>();
            var here = model.addAction("dev.x.here", "Here", null, List.of(), Optional.of("cmd+shift+o"), Optional.empty(),
                KeyContext.TERMINAL, invocation -> ran.add("here"));
            KeyStroke stroke = KeyBindings.parse("ctrl+alt+shift+o", false).orElseThrow();
            TerminalPane pane = owner.currentPane();
            assertThat(owner.dispatchShortcut(stroke, pane)).as("the Alt rule, in a terminal pane").isTrue();
            assertThat(owner.dispatchShortcut(stroke, new JTextField())).as("outside a terminal pane").isFalse();
            here.setEnabled(false);
            assertThat(owner.dispatchShortcut(stroke, pane)).as("disabled").isFalse();
            assertThat(ran).containsExactly("here");
            assertThat(owner.dispatchShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK), pane))
                .as("Ctrl+K belongs to the shell on Linux").isFalse();
            assertThat(owner.dispatchShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), pane)).isFalse();
        });
    }

    @Test void theRootInputMapConsumesOnlyKeysAnActionTakes() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), true);
            var root = new BindingRoot(); root.setContentPane(owner); owner.installRootBindings(root);
            assertThat(root.activate(mac("cmd+t"))).as("New Tab").isTrue();
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(2);
            assertThat(root.activate(mac("cmd+9"))).as("no ninth tab: the key passes").isFalse();
            assertThat(root.activate(mac("cmd+k"))).as("Clear Scrollback applies only in a terminal pane").isFalse();
        });
    }

    @Test void closePaneClosesTheFocusedPaneThenTheTabWithItsLastPane() throws Exception {
        // A split needs a running shell, so the launcher's queued work runs between the steps, as in TerminalTabTest.
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        TerminalTab[] tab = new TerminalTab[1];
        edt(() -> { owner[0] = content(launcher(pending), true); owner[0].newTab(HOME); });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> { tab[0] = owner[0].currentTab(); tab[0].split(SplitTree.Axis.RIGHT); });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            assertThat(tab[0].panes()).hasSize(2);
            assertThat(owner[0].dispatchShortcut(mac("cmd+w"), owner[0].currentPane())).isTrue();
            assertThat(tab[0].panes()).hasSize(1);
            assertThat(owner[0].tabStrip().getTabCount()).isEqualTo(2);
            assertThat(owner[0].dispatchShortcut(mac("cmd+w"), owner[0].currentPane())).isTrue();
            assertThat(owner[0].tabStrip().getTabCount()).isEqualTo(1);
        });
    }

    private static final class BindingRoot extends JRootPane {
        boolean activate(KeyStroke stroke) {
            return processKeyBinding(stroke, new KeyEvent(this, KeyEvent.KEY_PRESSED, 0,
                stroke.getModifiers(), stroke.getKeyCode(), KeyEvent.CHAR_UNDEFINED), WHEN_IN_FOCUSED_WINDOW, true);
        }
    }
}
