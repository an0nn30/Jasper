package dev.jasper.app.workspace;

import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.history.CommandHistory;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.palette.PaletteKeyRouter;
import dev.jasper.app.workspace.PaletteKeyRouterTest;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.session.TerminalSession;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.ArrayDeque;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.workspace.DesktopTestSupport.*;

class CommandPaletteShortcutsTest {
    @Test void paletteUsesLiteralPlatformModifierAndPreservesClearShortcut() {
        for (boolean mac : new boolean[]{true, false}) {
            var keys = KeyBindings.defaults(mac);
            int primary = mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
            assertThat(keys.strokeFor(ActionId.COMMAND_PALETTE)).contains(KeyStroke.getKeyStroke(KeyEvent.VK_K, primary));
            assertThat(keys.strokeFor(ActionId.CLEAR_SCROLLBACK)).contains(
                KeyStroke.getKeyStroke(KeyEvent.VK_K, primary | InputEvent.SHIFT_DOWN_MASK));
        }
        var override = KeyBindings.withOverrides(true,
            Map.of("command_palette", "none", "clear_scrollback", "cmd+k"));
        assertThat(override.strokeFor(ActionId.COMMAND_PALETTE)).isEmpty();
        assertThat(override.strokeFor(ActionId.CLEAR_SCROLLBACK)).contains(
            KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.META_DOWN_MASK));
    }

    @Test void realRootBindingUsesPalettePolicyThenReturnsToNumberedTabs() throws Exception {
        for (boolean mac : new boolean[]{true, false}) edt(() -> {
            try (var owner = owner(mac)) {
                var root = new BindingRoot(); root.setContentPane(owner); owner.installRootBindings(root);
                var first = owner.currentTab(); owner.newTab(HOME); var second = owner.currentTab();
                int primary = primary(mac);
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_K, primary))).isTrue();
                assertThat(owner.commandPalette().isOpen()).isTrue();
                owner.commandPalette().component().queryField().setText("no such command");
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_1, primary))).isTrue();
                assertThat(owner.currentTab()).isSameAs(second);
                owner.action(ActionId.NEW_TAB).actionPerformed(new ActionEvent(root, 0, "menu"));
                owner.windowCommands().view("view.status_bar").actionPerformed(new ActionEvent(root, 0, "menu"));
                assertThat(owner.tabStrip().getTabCount()).isEqualTo(2);
                assertThat(owner.status().isVisible()).isTrue();
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_K, primary))).isTrue();
                assertThat(owner.commandPalette().isOpen()).isFalse();
                root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_1, primary));
                assertThat(owner.currentTab()).isSameAs(first);
                assertThat(owner.commands().entries()).noneMatch(e -> e.command().id().equals("command_palette"));
                var view = owner.chrome().menuBar().getMenu(2);
                assertThat(view.getItem(0).getAction()).isSameAs(owner.action(ActionId.COMMAND_PALETTE));
                assertThat(view.getItem(1).getAction()).isSameAs(owner.action(ActionId.HISTORY_PALETTE));
                assertThat(view.getItem(2).getAction()).isSameAs(owner.action(ActionId.SNIPPETS_PALETTE));
                assertThat(view.getMenuComponent(3)).isInstanceOf(JSeparator.class);
            }
        });
    }

    @Test void historyShortcutIsCmdROnMacAndCtrlShiftRElsewhereAndIsInertWithoutTheScope() throws Exception {
        assertThat(KeyBindings.defaults(true).strokeFor(ActionId.HISTORY_PALETTE))
            .contains(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.META_DOWN_MASK));
        assertThat(KeyBindings.defaults(false).strokeFor(ActionId.HISTORY_PALETTE))
            .contains(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        assertThat(KeyBindings.defaults(false).actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK))).isEmpty();
        assertThat(KeyBindings.effectiveDefaultBinding(ActionId.HISTORY_PALETTE, false)).isEqualTo("ctrl+shift+r");
        assertThat(dev.jasper.app.palette.PaletteTestSupport.scopeFor(ActionId.HISTORY_PALETTE)).isEqualTo(PaletteScope.HISTORY_ID);
        edt(() -> {
            try (var owner = owner(false)) {
                var root = install(owner); var router = PaletteKeyRouterTest.router(owner, false, root);
                assertThat(owner.action(ActionId.HISTORY_PALETTE).isEnabled()).isFalse();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_R,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).isTrue();
                assertThat(owner.commandPalette().isOpen()).isFalse();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK))).isFalse();
                assertThat(owner.commands().entries()).noneMatch(e -> e.command().id().equals("history_palette"));
            }
        });
    }

    @Test void liveRootRemappingAndDisableRemoveOldStrokesAndUpdateMenu() throws Exception {
        edt(() -> {
            try (var owner = owner(false)) {
                var root = new BindingRoot(); root.setContentPane(owner); owner.installRootBindings(root);
                owner.setBindings(KeyBindings.withOverrides(false, Map.of("command_palette", "alt+p")));
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_K, primary(false)))).isFalse();
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK))).isTrue();
                assertThat(owner.commandPalette().isOpen()).isTrue();
                assertThat(owner.action(ActionId.COMMAND_PALETTE).getValue(Action.ACCELERATOR_KEY))
                    .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK));
                owner.commandPalette().dismiss();
                owner.setBindings(KeyBindings.withOverrides(false, Map.of("command_palette", "none")));
                assertThat(root.activate(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK))).isFalse();
                assertThat(owner.action(ActionId.COMMAND_PALETTE).getValue(Action.ACCELERATOR_KEY)).isNull();
            }
        });
    }

    @Test void installedDispatcherRetainsCloseTailAcrossWindowsAndRemovesItOnRelease() throws Exception {
        edt(() -> {
            var prior = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var manager = new RecordingFocusManager(); KeyboardFocusManager.setCurrentKeyboardFocusManager(manager);
            try (var owner = owner(true); var other = owner(true)) {
                var root = install(owner); install(other);
                assertThat(manager.dispatchers).hasSize(2);
                var dispatcher = manager.dispatchers.getFirst();
                assertThat(dispatcher.dispatchKeyEvent(PaletteKeyRouterTest.press(owner, KeyEvent.VK_K, primary(true)))).isTrue();
                owner.commandPalette().component().queryField().setText("close.fixture");
                owner.commands().register(PaletteKeyRouterTest.command("close.fixture", owner::close));
                dispatcher.dispatchKeyEvent(PaletteKeyRouterTest.release(owner, KeyEvent.VK_K));
                assertThat(dispatcher.dispatchKeyEvent(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, 0))).isTrue();
                assertThat(manager.dispatchers).contains(dispatcher);
                assertThat(dispatcher.dispatchKeyEvent(PaletteKeyRouterTest.press(other, KeyEvent.VK_ENTER, 0))).isTrue();
                assertThat(dispatcher.dispatchKeyEvent(PaletteKeyRouterTest.typed(other, '\n'))).isTrue();
                assertThat(dispatcher.dispatchKeyEvent(PaletteKeyRouterTest.press(other, KeyEvent.VK_A, 0))).isFalse();
                assertThat(dispatcher.dispatchKeyEvent(PaletteKeyRouterTest.typed(other, 'a'))).isFalse();
                assertThat(dispatcher.dispatchKeyEvent(PaletteKeyRouterTest.release(other, KeyEvent.VK_ENTER))).isTrue();
                assertThat(manager.dispatchers).doesNotContain(dispatcher);
                assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(KeyEvent.VK_K, primary(true)))).isNull();
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(prior); }
        });
    }

    @Test void newerOwnersHeldCommandCannotExecuteInOlderDestinationDispatcher() throws Exception {
        for (boolean closeSource : new boolean[]{false, true}) edt(() -> {
            var previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var manager = new RecordingFocusManager(); KeyboardFocusManager.setCurrentKeyboardFocusManager(manager);
            try (var older = owner(true); var newer = owner(true)) {
                install(older); install(newer);
                var calls = new java.util.concurrent.atomic.AtomicInteger();
                older.commands().register(PaletteKeyRouterTest.command("destination.fixture", calls::incrementAndGet));
                newer.commands().register(PaletteKeyRouterTest.command("focus.fixture", () -> {
                    newer.setActive(false); older.commandPalette().toggle();
                    older.commandPalette().component().queryField().setText("destination.fixture");
                    if (closeSource) ((Runnable) newer::close).run();
                }));
                manager.send(PaletteKeyRouterTest.press(newer, KeyEvent.VK_K, primary(true)));
                manager.send(PaletteKeyRouterTest.release(newer, KeyEvent.VK_K));
                newer.commandPalette().component().queryField().setText("focus.fixture");
                assertThat(manager.send(PaletteKeyRouterTest.press(newer, KeyEvent.VK_1, primary(true)))).isTrue();
                assertThat(older.commandPalette().isOpen()).isTrue();
                assertThat(manager.send(PaletteKeyRouterTest.press(older, KeyEvent.VK_1, primary(true)))).isTrue();
                assertThat(manager.send(PaletteKeyRouterTest.typed(older, '1'))).isTrue();
                assertThat(calls.get()).isZero();
                assertThat(manager.send(PaletteKeyRouterTest.release(older, KeyEvent.VK_1))).isTrue();
                assertThat(manager.dispatchers).hasSize(closeSource ? 1 : 2);
                assertThat(manager.send(PaletteKeyRouterTest.press(older, KeyEvent.VK_1, primary(true)))).isTrue();
                assertThat(calls.get()).isEqualTo(1);
                manager.send(PaletteKeyRouterTest.release(older, KeyEvent.VK_1));
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(previous); }
        });
    }

    @Test void missingReleaseTimesOutAndRootDetachRemovesIdleDispatcher() throws Exception {
        var prior = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        var manager = new RecordingFocusManager();
        try {
            edt(() -> {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(manager);
                var owner = owner(false); var root = install(owner);
                var dispatcher = manager.dispatchers.getFirst();
                root.setContentPane(new JPanel()); assertThat(manager.dispatchers).isEmpty();
                root.setContentPane(owner); assertThat(manager.dispatchers).containsExactly(dispatcher);
                dispatcher.dispatchKeyEvent(PaletteKeyRouterTest.press(owner, KeyEvent.VK_K, primary(false)));
                owner.close(); assertThat(manager.dispatchers).containsExactly(dispatcher);
            });
            until(() -> manager.dispatchers.isEmpty());
        } finally { edt(() -> KeyboardFocusManager.setCurrentKeyboardFocusManager(prior)); }
    }

    @Test void focusedPaletteFieldEditsDigitsAndKeepsNativeClipboardBindings() throws Exception {
        edt(() -> {
            var previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var laf = UIManager.getLookAndFeel();
            com.formdev.flatlaf.FlatDarkLaf.setup();
            try (var owner = owner(System.getProperty("os.name").startsWith("Mac"))) {
                var root = install(owner); owner.commandPalette().toggle();
                var field = owner.commandPalette().component().queryField();
                var router = PaletteKeyRouterTest.router(owner, System.getProperty("os.name").startsWith("Mac"), root);
                KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
                    @Override public Component getFocusOwner() { return field; }
                });
                // Redispatch through JTextComponent's real processKeyEvent, bypassing only native focus delivery.
                field.addKeyListener(new KeyAdapter() {});
                var digit = PaletteKeyRouterTest.typed(field, '1');
                assertThat(router.dispatch(PaletteKeyRouterTest.press(field, KeyEvent.VK_1, 0))).isFalse();
                assertThat(router.dispatch(digit)).isFalse();
                KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(field, digit);
                assertThat(field.getText()).isEqualTo("1");
                var clipboard = new java.awt.datatransfer.Clipboard("palette fixture");
                field.selectAll();
                field.getActionMap().put(javax.swing.text.DefaultEditorKit.copyAction, new AbstractAction() {
                    @Override public void actionPerformed(ActionEvent event) {
                        field.getTransferHandler().exportToClipboard(field, clipboard, TransferHandler.COPY);
                    }
                });
                field.getActionMap().put(javax.swing.text.DefaultEditorKit.pasteAction, new AbstractAction() {
                    @Override public void actionPerformed(ActionEvent event) {
                        field.getTransferHandler().importData(field, clipboard.getContents(null));
                    }
                });
                int mod = primary(System.getProperty("os.name").startsWith("Mac"));
                var copy = PaletteKeyRouterTest.press(field, KeyEvent.VK_C, mod);
                assertThat(router.dispatch(copy)).isFalse();
                KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(field, copy);
                field.setCaretPosition(1);
                var paste = PaletteKeyRouterTest.press(field, KeyEvent.VK_V, mod);
                assertThat(router.dispatch(paste)).isFalse();
                KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(field, paste);
                assertThat(field.getText()).isEqualTo("11");
                assertThat(owner.commandPalette().component().isFocusCycleRoot()).isTrue();
                assertThat(owner.commandPalette().component().getFocusTraversalPolicy()
                    .getComponentAfter(owner.commandPalette().component(), field)).isSameAs(field);
            } finally {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previous);
                try { UIManager.setLookAndFeel(laf); }
                catch (UnsupportedLookAndFeelException error) { throw new AssertionError(error); }
            }
        });
    }

    @Test @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void completePaletteSequencesSendZeroBytesToTheRealTerminalConnector(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        for (boolean mac : new boolean[]{true, false}) {
            var ready = directory.resolve("ready-" + mac); var received = directory.resolve("received-" + mac);
            var environment = new java.util.HashMap<>(System.getenv());
            environment.put("JASPER_READY", ready.toString()); environment.put("JASPER_RECEIVED", received.toString());
            var pending = new ArrayDeque<Runnable>(); WindowContent[] owner = new WindowContent[1];
            try {
                edt(() -> {
                    var launcher = new ShellLauncher(pending::add, path -> {
                        try {
                            return dev.jasper.terminal.session.TerminalSession.start(SessionLaunchOptions.builder().command(java.util.List.of("/bin/sh", "-c",
                                "stty -echo -icanon min 1 time 0; : > \"$JASPER_READY\"; dd bs=1 count=1 of=\"$JASPER_RECEIVED\" 2>/dev/null")).environment(environment).workingDirectory(directory).grid(new GridSize(80, 24)).scrollback(100).build());
                        } catch (java.io.IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
                    }, "controlled input fixture");
                    owner[0] = new WindowContent(launcher, directory, path -> {}, () -> {}, () -> {},
                        new ThemeController(), KeyBindings.defaults(mac), System::nanoTime, new CommandHistory(), mac);
                    install(owner[0]);
                });
                pending.remove().run();
                until(() -> owner[0].currentPane().view() != null && java.nio.file.Files.exists(ready));
                edt(() -> {
                    var content = owner[0]; var view = content.currentPane().view();
                    var router = PaletteKeyRouterTest.router(content, mac, SwingUtilities.getRootPane(content));
                    java.util.function.Consumer<KeyEvent> send = event -> {
                        if (!router.dispatch(event)) dev.jasper.terminal.view.TerminalKeyTestSupport.handleKey(view, event);
                    };
                    var calls = new java.util.concurrent.atomic.AtomicInteger();
                    content.commands().register(PaletteKeyRouterTest.command("byte.fixture", calls::incrementAndGet));
                    send.accept(PaletteKeyRouterTest.press(view, KeyEvent.VK_K, primary(mac)));
                    send.accept(PaletteKeyRouterTest.press(view, KeyEvent.VK_K, primary(mac)));
                    send.accept(PaletteKeyRouterTest.typed(view, 'k'));
                    send.accept(PaletteKeyRouterTest.release(view, KeyEvent.VK_K));
                    content.commandPalette().component().queryField().setText("byte.fixture");
                    for (int code : new int[]{KeyEvent.VK_UP, KeyEvent.VK_DOWN}) {
                        send.accept(PaletteKeyRouterTest.press(view, code, 0));
                        send.accept(PaletteKeyRouterTest.release(view, code));
                    }
                    send.accept(PaletteKeyRouterTest.press(view, KeyEvent.VK_5, primary(mac)));
                    send.accept(PaletteKeyRouterTest.typed(view, '5'));
                    send.accept(PaletteKeyRouterTest.release(view, KeyEvent.VK_5));
                    send.accept(PaletteKeyRouterTest.press(view, KeyEvent.VK_1, primary(mac)));
                    send.accept(PaletteKeyRouterTest.press(view, KeyEvent.VK_1, primary(mac)));
                    send.accept(PaletteKeyRouterTest.typed(view, '1'));
                    send.accept(PaletteKeyRouterTest.release(view, KeyEvent.VK_1));
                    assertThat(calls.get()).isEqualTo(1);
                    send.accept(PaletteKeyRouterTest.press(view, KeyEvent.VK_K, primary(mac)));
                    send.accept(PaletteKeyRouterTest.release(view, KeyEvent.VK_K));
                    send.accept(PaletteKeyRouterTest.press(view, KeyEvent.VK_ESCAPE, 0));
                    send.accept(PaletteKeyRouterTest.typed(view, (char) 27));
                    send.accept(PaletteKeyRouterTest.release(view, KeyEvent.VK_ESCAPE));
                    // A sentinel proves the PTY input stream contains no earlier palette bytes.
                    send.accept(PaletteKeyRouterTest.typed(view, 'Z'));
                });
                owner[0].currentPane().session().exitFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(java.nio.file.Files.readAllBytes(received)).containsExactly((byte) 'Z');
            } finally { edt(() -> { if (owner[0] != null) owner[0].close(); }); }
        }
    }

    static final class RecordingFocusManager extends DefaultKeyboardFocusManager {
        final java.util.List<KeyEventDispatcher> dispatchers = new java.util.ArrayList<>();
        boolean send(KeyEvent event) {
            for (var dispatcher : java.util.List.copyOf(dispatchers))
                if (dispatcher.dispatchKeyEvent(event)) return true;
            return false;
        }
        @Override public void addKeyEventDispatcher(KeyEventDispatcher dispatcher) {
            super.addKeyEventDispatcher(dispatcher); dispatchers.add(dispatcher);
        }
        @Override public void removeKeyEventDispatcher(KeyEventDispatcher dispatcher) {
            super.removeKeyEventDispatcher(dispatcher); dispatchers.remove(dispatcher);
        }
    }

    static int primary(boolean mac) { return mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK; }
    static WindowContent owner(boolean mac) {
        return new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
            new ThemeController(), KeyBindings.defaults(mac), System::nanoTime, new CommandHistory(), mac);
    }
    static JRootPane install(WindowContent owner) {
        var root = new JRootPane(); root.setContentPane(owner); owner.installRootBindings(root);
        root.setSize(900, 600); LayoutTestSupport.layoutTree(root); return root;
    }
    private static final class BindingRoot extends JRootPane {
        boolean activate(KeyStroke stroke) {
            return processKeyBinding(stroke, new KeyEvent(this, KeyEvent.KEY_PRESSED, 0,
                stroke.getModifiers(), stroke.getKeyCode(), KeyEvent.CHAR_UNDEFINED), WHEN_IN_FOCUSED_WINDOW, true);
        }
    }
}
