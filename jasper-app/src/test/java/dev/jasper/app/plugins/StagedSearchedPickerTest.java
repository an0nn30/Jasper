package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static dev.jasper.app.plugins.StagedAnchoredPickerTest.*;
import static dev.jasper.app.plugins.StagedVaultHost.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Browser plan 8's L36 through the real host, kit and staged Vault: a pick with a starting search, as a page's field
 * asks, has no active row until the user moves to one, so Enter on the starting search, or on what the user types,
 * chooses nothing and the chooser stays open; Down then Enter chooses the first match, and a press on a match chooses it
 * (L40). The search takes the focus with its text unselected and the caret at its end. A pick without a starting search
 * still chooses its first row on Enter.
 */
class StagedSearchedPickerTest {
    static final PluginInfo CONSUMER = new PluginInfo("test.consumer", "Consumer", "0.1.0", Set.of());
    @TempDir Path root;

    @SuppressWarnings("try") // Staged host shutdown is awaited by the test; interruption is propagated.
    @Test void aStartingSearchChoosesNothingUntilTheUserMovesToARow() throws Exception {
        var theme = onEdtValue(() -> ThemeTestSupport.controller(true));
        try (var vault = new StagedVaultHost(root.resolve("home"), true)) {
            vault.createVault("synthetic-picker-pass");
            onEdt(() -> vault.surface("Credential Vault").requestClose());
            var ids = vault.seedLogins(new String[][]{{"example.com", "alice", "pw"}, {"example.com work", "bob", "pw"}, {"other.test", "carol", "pw"}});
            Class<?> apiType = vault.loader.loadClass("dev.jasper.vault.api.VaultApi");
            Class<?> requestType = vault.loader.loadClass("dev.jasper.vault.api.CredentialPickRequest");
            var api = new AtomicReference<Object>(); var context = new AtomicReference<PluginContext>();
            vault.start(CONSUMER, Set.of(UnboundVault.ID), new Plugin() {
                @Override public void start(PluginContext started) { context.set(started); api.set(started.services().require(apiType)); }
            });
            // A page's field button: a button in a dialog of the consumer's, and a pick on its anchor.
            java.util.function.Function<String, CompletableFuture<?>> pick = search -> onEdtValue(() -> {
                var consumer = context.get();
                WindowHandle owner = consumer.terminals().window(vault.window).orElseThrow();
                var view = consumer.kit().create(new ViewSpec(new Nodes.Button("key", ControlState.READY, "Key", "", Nodes.ButtonKind.GHOST, "key", "")), event -> { });
                var dialog = consumer.windows().dialog(new DialogSpec("Fixture page", owner, false));
                dialog.setContent(view); dialog.show();
                try {
                    Object request = requestType.getConstructor(UiAnchor.class, Optional.class, boolean.class, String.class).newInstance(view.anchor("key"), Optional.empty(), false, search);
                    return (CompletableFuture<?>) apiType.getMethod("pick", WindowHandle.class, requestType).invoke(api.get(), owner, request);
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });

            var picked = pick.apply("example.com");
            await(() -> menus(vault).size() == 1, "the chooser");
            onEdt(() -> {
                var menu = menu(vault); var field = (JTextField) named(menu, "credential.menu.search");
                assertThat(field.getCaret()).as("precondition: FlatLaf's caret, which selects all on a first focus").isInstanceOf(com.formdev.flatlaf.ui.FlatCaret.class);
                var caret = (java.awt.event.FocusListener) field.getCaret();
                caret.focusGained(new java.awt.event.FocusEvent(field, java.awt.event.FocusEvent.FOCUS_GAINED));
                try {
                    assertThat(field.getText()).isEqualTo("example.com");
                    assertThat(field.getSelectedText()).as("nothing selected").isNull();
                    assertThat(field.getCaretPosition()).as("the caret at the end").isEqualTo("example.com".length());
                } finally { caret.focusLost(new java.awt.event.FocusEvent(field, java.awt.event.FocusEvent.FOCUS_LOST)); }
                assertThat(rows(menu).getSelectedValue()).as("no row active").isNull();
                enter(field);
            });
            settle();
            assertThat(picked).as("Enter on the starting search").isNotDone();
            assertThat(onEdtValue(() -> menus(vault))).as("the chooser stays open").hasSize(1);
            onEdt(() -> {
                var menu = menu(vault); var field = (JTextField) named(menu, "credential.menu.search");
                key(menu, "down");
                assertThat(rows(menu).getSelectedIndex()).as("Down: the first match").isZero();
                // Both rows still match, so only the menu's empty active row can drop the user's.
                append(field, " ");
                assertThat(rows(menu).getModel().getSize()).as("the same two rows and the footer").isEqualTo(3);
                assertThat(rows(menu).getSelectedValue()).as("typing leaves no row active").isNull();
                enter(field);
                append(field, "w");
                enter(field);
            });
            settle();
            assertThat(picked).as("Enter on what the user typed").isNotDone();
            onEdt(() -> {
                var menu = menu(vault);
                assertThat(item(rows(menu), 0).label()).isEqualTo("example.com work");
                key(menu, "down"); enter(named(menu, "credential.menu.search"));
            });
            assertThat(chosen(picked)).as("Down then Enter: the first match").isEqualTo(ids.get(1));

            // L40: a press on a match the user had not moved to chooses it. The look's press handler cannot run headless,
            // so its press is replayed as BasicListUI makes it, then the kit's own press listener runs.
            var pressed = pick.apply("example.com");
            await(() -> menus(vault).size() == 1, "the chooser again");
            var target = new AtomicReference<String>();
            onEdt(() -> {
                var menu = menu(vault); menu.setSize(menu.getPreferredSize()); layout(menu);
                var rows = rows(menu); assertThat(rows.getSelectedValue()).as("no row active").isNull();
                target.set(item(rows, 1).id());
                var cell = rows.getCellBounds(1, 1);
                var press = new java.awt.event.MouseEvent(rows, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                    java.awt.event.InputEvent.BUTTON1_DOWN_MASK, cell.x + 4, cell.y + 4, 1, false, java.awt.event.MouseEvent.BUTTON1);
                rows.setValueIsAdjusting(true); rows.setSelectionInterval(1, 1);
                for (var listener : rows.getMouseListeners())
                    if (listener.getClass().getName().startsWith("dev.jasper.ui.swing.PickerMenus$")) listener.mousePressed(press);
            });
            assertThat(chosen(pressed)).as("a press on the second match").isEqualTo(UUID.fromString(target.get()));

            var first = pick.apply("");
            await(() -> menus(vault).size() == 1, "the chooser without a starting search");
            var row = new AtomicReference<String>();
            onEdt(() -> { var menu = menu(vault); row.set(item(rows(menu), 0).id()); enter(named(menu, "credential.menu.search")); });
            assertThat(chosen(first)).as("Enter chooses the first row as before").isEqualTo(UUID.fromString(row.get()));
            assertThat(vault.problems).isEmpty();
        } finally { onEdt(theme::close); }
    }

    static JList<?> rows(dev.jasper.ui.swing.KitView menu) { return (JList<?>) named(menu, "credential.menu.rows"); }
    static dev.jasper.ui.model.Nodes.PickerItem item(JList<?> rows, int index) { return (dev.jasper.ui.model.Nodes.PickerItem) rows.getModel().getElementAt(index); }
    /** Types {@code text} at the end of the search, as a key press does. */
    static void append(JTextField field, String text) {
        try { field.getDocument().insertString(field.getDocument().getLength(), text, null); }
        catch (javax.swing.text.BadLocationException failure) { throw new AssertionError(failure); }
    }
    /** The id of the credential a pick chose, through the staged Vault's own result types. */
    static UUID chosen(CompletableFuture<?> picked) throws Exception {
        Object result = picked.get(20, TimeUnit.SECONDS);
        assertThat(result.getClass().getSimpleName()).isEqualTo("Chosen");
        Object descriptor = result.getClass().getMethod("credential").invoke(result);
        return (UUID) descriptor.getClass().getMethod("id").invoke(descriptor);
    }
}
