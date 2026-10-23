package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.KeyContext;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.dock.ToolWindowMode;
import dev.jasper.app.platform.WindowInput;
import dev.jasper.app.terminals.TerminalRegistry;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Floating tool windows plan 1 Task 7: Jasper's shortcuts on a detached tool window's root pane. Close Pane, Close Tab and
 * Close Window hide the tool window and touch nothing of the main window; the panel's own shortcuts run in place; every
 * other shortcut raises the main window and runs there. The main window has macOS's keymap whatever the host; the
 * detached window's root is a bare root pane holding the tool window's frame, keys reach its bindings as Swing's
 * {@code WHEN_IN_FOCUSED_WINDOW} dispatch would deliver them, and the focus owner is a stand-in focus manager's.
 */
class DetachedShortcutsTest {
    private static final String A = "dev.x.alpha";
    private static final KeyStroke CLOSE_PANE = mac("cmd+w"), CLOSE_TAB = mac("alt+cmd+w"), CLOSE_WINDOW = mac("cmd+shift+w");
    private static final KeyStroke NEW_TAB = mac("cmd+t"), PANEL_KEY = mac("cmd+alt+j");

    private final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();

    @AfterEach void restore() throws Exception {
        edt(() -> KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager));
        DesktopTestSupport.closeOwners();
    }

    private static KeyStroke mac(String binding) { return KeyBindings.parse(binding, true).orElseThrow(); }

    private static void close(AutoCloseable binding) {
        try { binding.close(); } catch (Exception failure) { throw new AssertionError(failure); }
    }

    /** Makes {@code focus} the focus owner Swing reports. */
    private static void focus(Component focus) {
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
            @Override public Component getFocusOwner() { return focus; }
        });
    }

    /**
     * A main window in its own root pane with panel {@link #A} floated, its shortcuts' runs and the times it was raised;
     * the panel's window is a recording shell, whose opening {@link #root} holds the tool window's frame.
     */
    private static final class Fixture {
        final Contributions model = new Contributions();
        final WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), true);
        final JRootPane mainRoot = new JRootPane();
        final JRootPane root = new JRootPane();
        final JLabel view = new JLabel("alpha");
        final List<String> ran = new ArrayList<>();
        final List<String> shells = new ArrayList<>();
        DetachedToolWindows.Opening opening;
        int raised;

        Fixture() {
            owner.connectContributions(model);
            owner.connectTerminals(new TerminalRegistry(), () -> raised++);
            mainRoot.setContentPane(owner);
            owner.installRootBindings(mainRoot);
            DetachedToolWindows windows = owner.detachedWindows();
            windows.ready = () -> true;
            var screen = new Rectangle(0, 0, 1920, 1080);
            windows.displays = () -> List.of(new WindowPlacement.Display(screen, screen));
            owner.detachedShells((opening, events) -> {
                this.opening = opening;
                return new DetachedToolWindows.Shell(() -> shells.add("show"), () -> shells.add("toFront"), () -> shells.add("dispose"),
                    () -> true, bounds -> { }, frame -> { });
            });
            model.addPanel(A, "Alpha", new ImageIcon(), Slot.LEFT_TOP, site -> view);
            owner.dock().detach(A, ToolWindowMode.FLOAT, new Rectangle(300, 200, 400, 300));
            assertThat(opening).as("the panel's window opened").isNotNull();
            root.setContentPane(new JPanel(new BorderLayout()));
            root.getContentPane().add(opening.frame(), BorderLayout.CENTER);
            focus(view);
        }

        /** Binds Jasper's shortcuts on the detached window's root, its Close keys counted in {@link #ran}. */
        AutoCloseable bind() { return owner.bindDetachedRoot(root, () -> ran.add("hide")); }

        /** Delivers {@code stroke} to the root's binding as Swing would: whether an action took it. */
        boolean press(KeyStroke stroke) {
            Object key = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke);
            Action action = key == null ? null : root.getActionMap().get(key);
            if (action == null || !action.accept(root)) return false;
            action.actionPerformed(new ActionEvent(root, ActionEvent.ACTION_PERFORMED, null));
            return true;
        }

        /** A Panel-context action of {@link #A}'s on {@code binding}, which records {@code name} when it runs. */
        void panelAction(String id, String binding, String name) {
            model.addAction(id, name, null, List.of(), Optional.of(binding), Optional.empty(), KeyContext.panel(A),
                invocation -> ran.add(name));
        }
    }

    @Test void closeKeysHideTheToolWindow() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            assertThat(fixture.owner.bindings().strokeFor(ActionId.CLOSE_PANE)).contains(CLOSE_PANE);
            assertThat(fixture.owner.bindings().strokeFor(ActionId.CLOSE_TAB)).contains(CLOSE_TAB);
            assertThat(fixture.owner.bindings().strokeFor(ActionId.CLOSE_WINDOW)).contains(CLOSE_WINDOW);
            int tabs = fixture.owner.tabCount();
            fixture.bind();
            for (KeyStroke close : List.of(CLOSE_PANE, CLOSE_TAB, CLOSE_WINDOW))
                assertThat(fixture.press(close)).as(close + " is taken").isTrue();
            assertThat(fixture.ran).containsExactly("hide", "hide", "hide");
            assertThat(fixture.owner.tabCount()).as("no tab of the main window closed").isEqualTo(tabs);
            assertThat(fixture.raised).as("the main window stays where it is").isZero();

            focus(null);
            assertThat(fixture.press(CLOSE_PANE)).as("with no focus owner").isTrue();
            assertThat(fixture.ran).hasSize(4);

            fixture.root.putClientProperty(WindowInput.SHORTCUT_RECORDER, new WindowInput.ShortcutRecorder(CLOSE_PANE::equals));
            assertThat(fixture.press(CLOSE_PANE)).isTrue();
            assertThat(fixture.ran).as("a recording window takes the key for itself").hasSize(4);
            assertThat(fixture.press(CLOSE_TAB)).isTrue();
            assertThat(fixture.ran).as("a key it does not record still hides").hasSize(5);
        });
    }

    @Test void theWindowsCloseKeyHidesItsToolWindowWhichStaysDetached() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            int tabs = fixture.owner.tabCount();
            fixture.opening.keys().apply(fixture.root);
            assertThat(fixture.press(CLOSE_PANE)).isTrue();
            assertThat(fixture.owner.dock().visible(A)).as("hidden").isFalse();
            assertThat(fixture.owner.panelDetached(A)).as("and still detached").isTrue();
            assertThat(fixture.shells).as("its window closes with it").containsExactly("show", "dispose");
            assertThat(fixture.owner.tabCount()).isEqualTo(tabs);
        });
    }

    @Test void aGlobalShortcutRaisesTheMainWindowAndRuns() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            int tabs = fixture.owner.tabCount();
            fixture.bind();
            assertThat(fixture.press(NEW_TAB)).isTrue();
            assertThat(fixture.raised).isEqualTo(1);
            assertThat(fixture.owner.tabCount()).as("New Tab ran once, in the main window").isEqualTo(tabs + 1);
            assertThat(fixture.press(mac("cmd+k"))).as("Clear Scrollback applies only in a terminal pane").isFalse();
            assertThat(fixture.raised).as("a key no action takes raises nothing").isEqualTo(1);
        });
    }

    @Test void theToolWindowsToggleFromItsOwnWindowHidesIt() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            fixture.owner.setBindings(KeyBindings.withOverrides(true, Map.of(A + ".toggle", "cmd+alt+0")));
            fixture.bind();
            // Raising the main window activates it only once this key's event is handled: its own window is still active.
            assertThat(fixture.press(mac("cmd+alt+0"))).isTrue();
            assertThat(fixture.owner.dock().visible(A)).as("shown and active: the activating toggle hides it").isFalse();
            assertThat(fixture.raised).isEqualTo(1);
        });
    }

    @Test void thePanelsOwnShortcutRunsInPlace() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            fixture.panelAction("dev.x.alpha.refresh", "cmd+alt+j", "refresh");
            fixture.bind();
            assertThat(fixture.press(PANEL_KEY)).isTrue();
            assertThat(fixture.ran).containsExactly("refresh");
            assertThat(fixture.raised).as("the main window stays behind").isZero();

            focus(fixture.opening.frame());
            assertThat(fixture.press(PANEL_KEY)).as("outside the panel, in its header, the key is no one's").isFalse();
            assertThat(fixture.ran).hasSize(1);
        });
    }

    @Test void thePanelsOwnShortcutOnACloseKeyRunsInsteadOfHiding() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            fixture.panelAction("dev.x.alpha.close", "cmd+w", "close item");
            assertThat(fixture.owner.bindings().strokeFor("dev.x.alpha.close")).as("a Panel context may reuse Close Pane's key").contains(CLOSE_PANE);
            fixture.bind();
            assertThat(fixture.press(CLOSE_PANE)).isTrue();
            assertThat(fixture.ran).as("the most specific context wins, as when docked").containsExactly("close item");
            focus(fixture.opening.frame());
            assertThat(fixture.press(CLOSE_PANE)).isTrue();
            assertThat(fixture.ran).as("outside the panel it is Close Pane").containsExactly("close item", "hide");
            assertThat(fixture.raised).isZero();
        });
    }

    @Test void theMainWindowsOverlayHoldsEveryKeyButClose() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            fixture.panelAction("dev.x.alpha.refresh", "cmd+alt+j", "refresh");
            int tabs = fixture.owner.tabCount();
            fixture.bind();
            fixture.mainRoot.putClientProperty(WindowInput.OVERLAY, new Object());
            assertThat(fixture.press(NEW_TAB)).as("swallowed").isTrue();
            assertThat(fixture.owner.tabCount()).isEqualTo(tabs);
            assertThat(fixture.raised).isZero();
            assertThat(fixture.press(PANEL_KEY)).as("a contributed action waits for the overlay wherever it runs from").isTrue();
            assertThat(fixture.ran).isEmpty();
            assertThat(fixture.press(CLOSE_PANE)).isTrue();
            assertThat(fixture.ran).as("the detached window is not under the overlay: it still hides").containsExactly("hide");

            fixture.mainRoot.putClientProperty(WindowInput.OVERLAY, null);
            assertThat(fixture.press(PANEL_KEY)).isTrue();
            assertThat(fixture.ran).containsExactly("hide", "refresh");
        });
    }

    @Test void aKeymapChangeRebindsTheDetachedRoot() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            int tabs = fixture.owner.tabCount();
            fixture.bind();
            fixture.owner.setBindings(KeyBindings.withOverrides(true, Map.of("new_tab", "cmd+alt+y")));
            assertThat(fixture.root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(NEW_TAB)).as("the old key is gone").isNull();
            assertThat(fixture.press(mac("cmd+alt+y"))).isTrue();
            assertThat(fixture.owner.tabCount()).isEqualTo(tabs + 1);

            fixture.panelAction("dev.x.alpha.refresh", "cmd+alt+j", "refresh");
            assertThat(fixture.press(PANEL_KEY)).as("a contributed action's key is bound as it registers").isTrue();
            assertThat(fixture.ran).containsExactly("refresh");
        });
    }

    @Test void closingTheBindingRemovesIt() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            var input = fixture.root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            var other = new AbstractAction() { @Override public void actionPerformed(ActionEvent event) { } };
            KeyStroke otherKey = mac("cmd+alt+shift+z");
            input.put(otherKey, "jasper.other");
            fixture.root.getActionMap().put("jasper.other", other);
            int inputs = input.size(), actions = fixture.root.getActionMap().size();

            AutoCloseable binding = fixture.bind();
            assertThat(input.get(NEW_TAB)).isNotNull();
            assertThat(input.size()).isGreaterThan(inputs);
            close(binding);
            assertThat(input.get(NEW_TAB)).isNull();
            assertThat(fixture.press(CLOSE_PANE)).isFalse();
            assertThat(input.size()).as("exactly its bindings went").isEqualTo(inputs);
            assertThat(fixture.root.getActionMap().size()).isEqualTo(actions);
            assertThat(input.get(otherKey)).isEqualTo("jasper.other");
            assertThat(fixture.root.getActionMap().get("jasper.other")).isSameAs(other);

            close(binding);
            fixture.owner.setBindings(KeyBindings.defaults(true));
            assertThat(input.size()).as("a keymap change binds nothing on a closed binding's root").isEqualTo(inputs);
            assertThat(fixture.ran).isEmpty();
        });
    }

    @Test void closingTheMainWindowRemovesItsDetachedBindings() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            var input = fixture.root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            int inputs = input.size();
            fixture.bind();
            fixture.owner.close();
            assertThat(input.size()).isEqualTo(inputs);
            assertThat(fixture.owner.bindDetachedRoot(fixture.root, () -> { })).as("a closed window binds nothing").isNotNull();
            assertThat(input.size()).isEqualTo(inputs);
        });
    }
}
