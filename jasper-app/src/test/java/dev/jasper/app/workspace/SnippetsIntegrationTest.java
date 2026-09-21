package dev.jasper.app.workspace;

import dev.jasper.app.palette.PaletteTestSupport;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.history.CommandHistory;
import dev.jasper.app.history.ShellHistoryEntry;
import dev.jasper.app.history.ShellHistoryIndex;
import dev.jasper.app.palette.PaletteKeyRouter;
import dev.jasper.app.workspace.PaletteKeyRouterTest;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.palette.builtin.SnippetsScope;
import dev.jasper.app.snippets.SnippetStore;
import dev.jasper.app.testsupport.WorkerTestSupport;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.workspace.DesktopTestSupport.*;

class SnippetsIntegrationTest {
    @TempDir Path dir;

    static SnippetStore inlineStore(Path file, List<Path> opened) {
        return new SnippetStore(file, opened::add, WorkerTestSupport.inlineWorker(), Runnable::run);
    }

    static WindowContent owner(boolean mac, SnippetStore snippets, java.util.Queue<Runnable> pending) {
        return new WindowContent(launcher(pending), HOME, path -> {}, () -> {}, () -> {},
            new ThemeController(), KeyBindings.defaults(mac), System::nanoTime, new CommandHistory(), mac,
            new ShellHistoryIndex(List.of()), snippets);
    }

    @Test void cmdJOpensSnippetsThePickerListsItAndAPlaceholderSnippetOpensTheFillInStep() throws Exception {
        Path file = dir.resolve("snippets.toml");
        Files.writeString(file, "[[snippet]]\nname = \"Rebase\"\ncommand = \"git rebase {{branch}}\"\n\n[[snippet]]\nname = \"Plain\"\ncommand = \"ls\"\n");
        assertThat(KeyBindings.defaults(true).strokeFor(ActionId.SNIPPETS_PALETTE))
            .contains(KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.META_DOWN_MASK));
        assertThat(KeyBindings.defaults(false).strokeFor(ActionId.SNIPPETS_PALETTE))
            .contains(KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        assertThat(KeyBindings.effectiveDefaultBinding(ActionId.SNIPPETS_PALETTE, false)).isEqualTo("ctrl+shift+j");
        assertThat(dev.jasper.app.palette.PaletteTestSupport.scopeFor(ActionId.SNIPPETS_PALETTE)).isEqualTo(PaletteScope.SNIPPETS_ID);
        var pending = new ArrayDeque<Runnable>();
        var store = new SnippetStore[1];
        var win = new WindowContent[1];
        try {
            edt(() -> { store[0] = inlineStore(file, new ArrayList<>()); win[0] = owner(true, store[0], pending); store[0].reload(); });
            // The default pending launcher only queues its launch task; run and wait for it so the
            // origin pane is live, as the Paste verb now requires (Important 1).
            pending.remove().run();
            until(() -> win[0].currentPane().running());
            edt(() -> {
                var root = CommandPaletteShortcutsTest.install(win[0]);
                var router = PaletteKeyRouterTest.router(win[0], true, root);
                assertThat(win[0].action(ActionId.SNIPPETS_PALETTE).isEnabled()).isTrue();
                assertThat(win[0].commands().entries()).noneMatch(e -> e.command().id().equals("snippets_palette"));
                var view = win[0].chrome().menuBar().getMenu(2);
                assertThat(view.getItem(2).getAction()).isSameAs(win[0].action(ActionId.SNIPPETS_PALETTE));
                assertThat(view.getMenuComponent(3)).isInstanceOf(javax.swing.JSeparator.class);
                assertThat(router.dispatch(PaletteKeyRouterTest.press(win[0], KeyEvent.VK_J, InputEvent.META_DOWN_MASK))).isTrue();
                var palette = win[0].commandPalette(); var card = palette.component();
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.SNIPPETS_ID);
                assertThat(PaletteTestSupport.chip(card).getText()).isEqualTo("Snippets");
                assertThat(PaletteTestSupport.footer(card).getText()).isEqualTo("⏎ Paste  ⌘⏎ Paste and run  ⇧⏎ Edit file");
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(card).getModel().getSize()).isEqualTo(2);
                card.queryField().setText(">snip");
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(card).getModel().getSize()).isEqualTo(1);
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(card).getSelectedValue().tag()).isEqualTo("⌘J");
                palette.tabPressed();
                dev.jasper.app.palette.PaletteTestSupport.selectRow(card, dev.jasper.app.palette.builtin.ScopeTestSupport.rowId("Rebase"));
                palette.enterPressed(0);
                assertThat(palette.stepOpen()).isTrue();
                assertThat(dev.jasper.app.palette.PaletteTestSupport.stepFields(card)).hasSize(1);
                assertThat(PaletteTestSupport.sectionLabel(card).getText()).isEqualTo("Rebase");
                palette.escape();
                assertThat(palette.stepOpen()).isFalse();
                router.dispatch(PaletteKeyRouterTest.release(win[0], KeyEvent.VK_J));
                router.dispatch(PaletteKeyRouterTest.press(win[0], KeyEvent.VK_J, InputEvent.META_DOWN_MASK));
                assertThat(palette.isOpen()).isFalse();
            });
        } finally {
            edt(() -> { if (win[0] != null) win[0].close(); if (store[0] != null) store[0].close(); });
        }
        edt(() -> {
            try (var owner = CommandPaletteShortcutsTest.owner(true)) {
                assertThat(owner.scopes().find(PaletteScope.SNIPPETS_ID)).isEmpty();
                assertThat(owner.action(ActionId.SNIPPETS_PALETTE).isEnabled()).isFalse();
            }
        });
    }

    @Test void shiftEnterOnAHistoryRowSavesASnippetAndReopensTheSnippetsScopeOnTheNewRow() throws Exception {
        Path file = dir.resolve("snippets.toml");
        edt(() -> {
            try (var store = inlineStore(file, new ArrayList<>()); var index = new ShellHistoryIndex(List.of(),
                    WorkerTestSupport.inlineWorker(), Runnable::run)) {
                var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
                    new ThemeController(), KeyBindings.defaults(true), System::nanoTime, new CommandHistory(), true, index, store);
                try (owner) {
                    index.record(ShellHistoryEntry.of("docker compose up -d", 5, "zsh"));
                    var root = CommandPaletteShortcutsTest.install(owner);
                    var router = PaletteKeyRouterTest.router(owner, true, root);
                    var palette = owner.commandPalette(); var card = palette.component();
                    palette.open(PaletteScope.HISTORY_ID);
                    assertThat(PaletteTestSupport.footer(card).getText()).endsWith("⇧⏎ Save as snippet…");
                    assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                    router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_ENTER));
                    assertThat(palette.stepOpen()).isTrue();
                    assertThat(dev.jasper.app.palette.PaletteTestSupport.stepFields(card).getFirst().getText()).isEqualTo("docker compose");
                    dev.jasper.app.palette.PaletteTestSupport.stepFields(card).getFirst().setText("Compose up");
                    assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, 0))).isTrue();
                    assertThat(palette.isOpen()).isTrue();
                    assertThat(palette.stepOpen()).isFalse();
                    assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.SNIPPETS_ID);
                    assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(card).getSelectedValue().title()).isEqualTo("Compose up");
                    assertThat(Files.readString(file)).contains("command = \"docker compose up -d\"");
                    palette.open(PaletteScope.HISTORY_ID);
                    palette.enterPressed(2);
                    dev.jasper.app.palette.PaletteTestSupport.stepFields(card).getFirst().setText("compose UP");
                    palette.enterPressed(0);
                    assertThat(palette.stepOpen()).isTrue();
                    assertThat(PaletteTestSupport.stepError(card).getText()).isEqualTo("A snippet named Compose up exists");
                    assertThat(Files.readString(file).split("\\n\\[\\[snippet]]")).hasSize(2);
                }
            } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        });
    }

    @Test void reopenAfterSavingBeyondTheResultCapSetsTheQueryToTheNewName() throws Exception {
        Path file = dir.resolve("snippets.toml");
        var names = List.of("Alpha one", "Bravo two", "Charlie three", "Delta four", "Echo five", "Foxtrot six");
        var seed = new StringBuilder();
        for (String name : names) seed.append("[[snippet]]\nname = \"").append(name).append("\"\ncommand = \"echo ").append(name).append("\"\n\n");
        Files.writeString(file, seed.toString());
        edt(() -> {
            try (var store = inlineStore(file, new ArrayList<>()); var index = new ShellHistoryIndex(List.of(),
                    WorkerTestSupport.inlineWorker(), Runnable::run)) {
                var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
                    new ThemeController(), KeyBindings.defaults(true), System::nanoTime, new CommandHistory(), true, index, store);
                try (owner) {
                    index.record(ShellHistoryEntry.of("echo zed", 5, "zsh"));
                    var root = CommandPaletteShortcutsTest.install(owner);
                    var router = PaletteKeyRouterTest.router(owner, true, root);
                    var palette = owner.commandPalette(); var card = palette.component();
                    palette.setMaxResults(3);
                    palette.open(PaletteScope.HISTORY_ID);
                    assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                    router.dispatch(PaletteKeyRouterTest.release(owner, KeyEvent.VK_ENTER));
                    assertThat(palette.stepOpen()).isTrue();
                    dev.jasper.app.palette.PaletteTestSupport.stepFields(card).getFirst().setText("Zed last");
                    assertThat(router.dispatch(PaletteKeyRouterTest.press(owner, KeyEvent.VK_ENTER, 0))).isTrue();
                    assertThat(palette.isOpen()).isTrue();
                    assertThat(palette.stepOpen()).isFalse();
                    assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.SNIPPETS_ID);
                    assertThat(card.queryField().getText()).isEqualTo("Zed last");
                    assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(card).getSelectedValue().title()).isEqualTo("Zed last");
                }
            }
        });
    }

    @Test void pasteOnADeadTargetIsRefusedButSaveAsSnippetStillOpensItsStep() throws Exception {
        Path file = dir.resolve("snippets.toml");
        edt(() -> {
            try (var store = inlineStore(file, new ArrayList<>()); var index = new ShellHistoryIndex(List.of(),
                    WorkerTestSupport.inlineWorker(), Runnable::run)) {
                // The default pending launcher never runs its launch task, so the pane's shell never
                // starts and the target stays dead for the whole test.
                var owner = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {},
                    new ThemeController(), KeyBindings.defaults(true), System::nanoTime, new CommandHistory(), true, index, store);
                try (owner) {
                    index.record(ShellHistoryEntry.of("npm run dev", 5, "zsh"));
                    CommandPaletteShortcutsTest.install(owner);
                    var palette = owner.commandPalette(); var card = palette.component();
                    palette.open(PaletteScope.HISTORY_ID);
                    palette.enterPressed(0);
                    assertThat(palette.isOpen()).isTrue();
                    assertThat(palette.stepOpen()).isFalse();
                    palette.enterPressed(2);
                    assertThat(palette.stepOpen()).isTrue();
                    assertThat(dev.jasper.app.palette.PaletteTestSupport.stepFields(card).getFirst().getText()).isEqualTo("npm run");
                }
            }
        });
    }
}
