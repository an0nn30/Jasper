package dev.jasper.app;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.DesktopTestSupport.*;

class ShellHistoryIntegrationTest {
    private static ShellHistoryIndex inlineIndex() {
        return new ShellHistoryIndex(List.of(), new java.util.concurrent.AbstractExecutorService() {
            @Override public void execute(Runnable task) { task.run(); }
            @Override public void shutdown() {}
            @Override public List<Runnable> shutdownNow() { return List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) { return true; }
        }, Runnable::run);
    }

    private static WindowContent owner(boolean mac, ShellHistoryIndex index) {
        return new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
            new ThemeController(), KeyBindings.defaults(mac), System::nanoTime, new CommandHistory(), mac, index);
    }

    @Test void historyShortcutOpensTheScopeAndTheSettingRegistersItLive() throws Exception {
        edt(() -> {
            try (var index = inlineIndex(); var owner = owner(true, index)) {
                index.record(ShellHistoryEntry.of("git status", 10, "zsh"));
                var root = CommandPaletteShortcutsTest.install(owner);
                var router = PaletteKeyRouterTest.router(owner, true, root);
                assertThat(owner.action(ActionId.HISTORY_PALETTE).isEnabled()).isTrue();
                assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_R, InputEvent.META_DOWN_MASK))).isTrue();
                assertThat(owner.commandPalette().activeScopeId()).isEqualTo(PaletteScope.HISTORY_ID);
                var card = owner.commandPalette().component();
                assertThat(card.chip().getText()).isEqualTo("History");
                assertThat(card.resultList().getModel().getElementAt(0).title()).isEqualTo("git status");
                card.queryField().setText(">");
                assertThat(card.resultList().getModel().getSize()).isEqualTo(2);
                assertThat(card.resultList().getModel().getElementAt(1).tag()).isEqualTo("⌘R");
                owner.commandPalette().dismiss();
                var defaults = ConfigSnapshot.defaults();
                var disabled = new ConfigSnapshot(defaults.tabHeight(), defaults.toolbar(), defaults.statusBar(), defaults.font(),
                    defaults.variant(), Map.of(), defaults.columns(), defaults.lines(), defaults.terminal(), defaults.buddyEnabled(), false);
                owner.applyConfiguration(disabled, true);
                assertThat(owner.scopes().find(PaletteScope.HISTORY_ID)).isEmpty();
                assertThat(owner.action(ActionId.HISTORY_PALETTE).isEnabled()).isFalse();
                owner.invoke(ActionId.HISTORY_PALETTE);
                assertThat(owner.commandPalette().isOpen()).isFalse();
                owner.applyConfiguration(defaults, true);
                assertThat(owner.scopes().find(PaletteScope.HISTORY_ID)).isPresent();
                assertThat(owner.action(ActionId.HISTORY_PALETTE).isEnabled()).isTrue();
            }
        });
        edt(() -> {
            try (var owner = CommandPaletteShortcutsTest.owner(true)) {
                assertThat(owner.scopes().find(PaletteScope.HISTORY_ID)).isEmpty();
            }
        });
    }

    @Test void maxResultsCapsEveryScopeLiveAndTheCardShowsExactlyThatMany() throws Exception {
        edt(() -> {
            try (var index = inlineIndex(); var owner = owner(true, index)) {
                for (int i = 0; i < 8; i++) index.record(ShellHistoryEntry.of("cmd " + i, 10 + i, "zsh"));
                CommandPaletteShortcutsTest.install(owner);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.open(PaletteScope.HISTORY_ID);
                assertThat(card.resultList().getModel().getSize()).isEqualTo(5);
                assertThat(card.resultList().getVisibleRowCount()).isEqualTo(5);
                var defaults = ConfigSnapshot.defaults();
                var three = new ConfigSnapshot(defaults.tabHeight(), defaults.toolbar(), defaults.statusBar(), defaults.font(),
                    defaults.variant(), Map.of(), defaults.columns(), defaults.lines(), defaults.terminal(),
                    defaults.buddyEnabled(), defaults.historyEnabled(), 3);
                owner.applyConfiguration(three, true);
                assertThat(palette.isOpen()).isTrue();
                assertThat(card.resultList().getModel().getSize()).isEqualTo(3);
                assertThat(card.resultList().getVisibleRowCount()).isEqualTo(3);
                assertThat(card.resultList().getModel().getElementAt(0).title()).isEqualTo("cmd 7");
                palette.open(PaletteScope.COMMANDS_ID);
                card.queryField().setText("tab");
                assertThat(card.resultList().getModel().getSize()).isEqualTo(3);
                owner.applyConfiguration(defaults, true);
                assertThat(card.resultList().getModel().getSize()).isEqualTo(5);
            }
        });
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void commandsRunInARealShellReachTheIndexWithTheirShellTag(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        var pending = new ArrayDeque<Runnable>();
        // The shell waits for a go file so its marks arrive only after the pane has attached its listener.
        Path go = directory.resolve("go");
        var environment = new java.util.HashMap<>(System.getenv());
        environment.put("JASPER_GO", go.toString());
        var launcher = new ShellLauncher(pending::add, path -> {
            try {
                return dev.jasper.terminal.TerminalSession.start(List.of("/bin/sh", "-c",
                    "while [ ! -e \"$JASPER_GO\" ]; do sleep 0.05; done; "
                        + "printf '\\033]133;A\\007$ \\033]133;B\\007ls -la\\n\\033]133;C\\007out\\n\\033]133;D;3\\007'"),
                    environment, directory, 80, 24, 100);
            } catch (java.io.IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, "sh");
        var index = new ShellHistoryIndex(List.of());
        WindowContent[] owner = new WindowContent[1];
        try {
            edt(() -> {
                owner[0] = new WindowContent(launcher, directory, path -> {}, () -> {}, () -> {}, new ThemeController(),
                    KeyBindings.defaults(true), System::nanoTime, new CommandHistory(), true, index);
                CommandPaletteShortcutsTest.install(owner[0]);
            });
            pending.remove().run();
            until(() -> owner[0].currentPane().view() != null);
            java.nio.file.Files.createFile(go);
            until(() -> !index.snapshot().entries().isEmpty());
            edt(() -> {
                var entry = index.snapshot().entries().getFirst();
                assertThat(entry.command()).isEqualTo("ls -la");
                assertThat(entry.shells()).isEqualTo(Set.of("sh"));
                assertThat(entry.exitStatus()).isEqualTo(3);
                assertThat(entry.timestamp()).isPositive();
            });
        } finally {
            edt(() -> { if (owner[0] != null) owner[0].close(); index.close(); });
        }
    }
}
