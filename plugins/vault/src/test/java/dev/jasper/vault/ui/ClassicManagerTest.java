package dev.jasper.vault.ui;

import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakeNotifications;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Column;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TableRow;
import dev.jasper.sdk.ui.TreeItem;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.service.VaultService;
import dev.jasper.vault.workstation.Layout;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Plan 10b: the classic manager (spec 14.8) as the 07 Credential Vault mocks compose it. */
class ClassicManagerTest {
    @TempDir Path directory;
    static final PluginInfo INFO = new PluginInfo("dev.jasper.vault", "Vault", "0.1.0", Set.of());
    static final VaultManagerDialog.Status STATUS = new VaultManagerDialog.Status("keychain", "15 min", "/synthetic/vault.jv");
    static final Instant MODIFIED = Instant.parse("2026-09-24T12:00:00Z");
    static final List<String> VERBS = List.of("toolbar.edit", "toolbar.delete", "toolbar.copy-username", "toolbar.copy-password");
    final UUID amp = UUID.randomUUID(), proxmox = UUID.randomUUID();
    final Map<UUID, Object> entries = new HashMap<>();
    final List<String> asked = new ArrayList<>();

    static Account login(UUID id, String name, String username, Instant modified) {
        // Each login has its own synthetic password, so a test can tell whose value a view holds.
        return new Account(id, name, username, new Auth.Password(("synthetic-" + name).toCharArray()), modified, modified);
    }

    /** The mock's two logins: amp, modified on the fixture day, and Proxmox, a day earlier. */
    List<VaultManager.Row> rows() {
        entries.put(amp, login(amp, "amp", "ubuntu", MODIFIED));
        entries.put(proxmox, login(proxmox, "Proxmox", "root", MODIFIED.minusSeconds(86_400)));
        return List.of(new VaultManager.Row(amp, "amp", "ubuntu", VaultManager.Type.LOGIN),
            new VaultManager.Row(proxmox, "Proxmox", "root", VaultManager.Type.LOGIN));
    }

    ManagerPanel panel() {
        return new ManagerPanel(new FakeKit("dev.jasper.vault"), Layout.CLASSIC, id -> Optional.ofNullable(entries.get(id)),
            type -> asked.add("add." + type.name()), id -> asked.add("edit " + id), id -> asked.add("delete " + id),
            id -> asked.add("copy-public " + id), grant -> asked.add("revoke"), () -> asked.add("generate"), () -> asked.add("lock"),
            () -> asked.add("unlock"), () -> asked.add("close"));
    }

    static boolean enabled(UiView view, String id) { return find(view, id).orElseThrow().state().enabled(); }

    @Test void theToolbarRowLeadsTheBodyAndTheStatusRowFollows() throws Exception {
        edt(() -> {
            var panel = panel(); var view = panel.view();
            panel.refresh(rows(), List.of(), STATUS, true);
            var body = (Nodes.Split) node(view, ManagerPanel.ROOT, Nodes.Form.class).sections().getFirst();
            assertThat(body.kind()).isEqualTo(Nodes.SplitKind.DOCUMENT);
            assertThat(body.children().getFirst().id()).isEqualTo(ManagerPanel.TOOLBAR);
            assertThat(((Nodes.Split) body.children().get(1)).kind()).isEqualTo(Nodes.SplitKind.THREE_COLUMN);
            var toolbar = node(view, ManagerPanel.TOOLBAR, Nodes.Stack.class);
            assertThat(toolbar.role()).isEqualTo(Nodes.LayoutRole.INLINE);
            assertThat(toolbar.children()).extracting(Nodes.Node::id).containsExactly("toolbar.new", "toolbar.rule.1", "toolbar.edit",
                "toolbar.delete", "toolbar.copy-username", "toolbar.copy-password", "toolbar.rule.2", "toolbar.lock", "toolbar.shortcut",
                ManagerPanel.SEARCH);
            var add = node(view, "toolbar.new", Nodes.MenuButton.class);
            assertThat(add.label()).isEqualTo("New");
            assertThat(add.icon()).isEqualTo("add");
            assertThat(add.items()).extracting(item -> item.action()).containsExactly("add.LOGIN", "add.SSH_KEY", "add.NOTE", "", ManagerPanel.ADD_GENERATE);
            assertThat(button(view, "toolbar.edit").label()).isEqualTo("Edit…");
            assertThat(button(view, "toolbar.edit").action()).isEqualTo(ManagerPanel.EDIT);
            assertThat(button(view, "toolbar.delete").action()).isEqualTo(ManagerPanel.DELETE);
            assertThat(button(view, "toolbar.copy-username").action()).isEqualTo("detail.username.copy");
            assertThat(button(view, "toolbar.copy-password").action()).isEqualTo("detail.password.copy");
            assertThat(button(view, "toolbar.lock").action()).isEqualTo(ManagerPanel.LOCK);
            assertThat(node(view, "toolbar.shortcut", Nodes.Text.class).text()).isEqualTo(ManagerPanel.LOCK_SHORTCUT);
            assertThat(find(panel.view(), ManagerPanel.SEARCH).orElseThrow()).isInstanceOf(Nodes.Field.class);
            for (String verb : VERBS) assertThat(enabled(view, verb)).as(verb + " without a selection").isFalse();
            assertThat(enabled(view, "toolbar.lock")).isTrue();
            emit(view, new UiEvent.Selected(ManagerPanel.ROWS, Set.of(amp.toString())));
            for (String verb : VERBS) assertThat(enabled(view, verb)).as(verb + " with a login").isTrue();
            press(view, ManagerPanel.EDIT);
            assertThat(asked).containsExactly("edit " + amp);
            // The footer status layout keeps the leading cells whole and elides the path (spec 14.8, the mock's status row).
            assertThat(node(view, ManagerPanel.STATUS, Nodes.Stack.class).role()).isEqualTo(Nodes.LayoutRole.FOOTER);
            assertThat(node(view, "status.state", Nodes.StatusDot.class).kind()).isEqualTo(Nodes.StatusKind.SUCCESS);
            assertThat(node(view, "status.provider", Nodes.Text.class).text()).isEqualTo("Unlocked · Keychain");
            assertThat(node(view, "status.autolock", Nodes.Text.class).text()).isEqualTo("Auto-lock: 15 min");
            assertThat(node(view, "status.path", Nodes.Text.class).role()).isEqualTo(Nodes.TextRole.MONO_CAPTION);
            assertThat(button(view, ManagerPanel.OKAY).label()).isEqualTo("Done");
            assertThat(button(view, ManagerPanel.OKAY).kind()).isEqualTo(Nodes.ButtonKind.PRIMARY);
            panel.close();
        });
    }

    @Test void lockingDisablesTheToolbarAndClosesTheSheet() throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
                PluginContext context = host.start(INFO, Set.of(), Set.of(), c -> { });
                host.addTerminalWindow(); WindowHandle owner = context.terminals().windows().getFirst();
                var service = new VaultService(f.lock, () -> Optional.of(owner), prompt -> { }, p -> { }, p -> { },
                    new FakeNotifications().group("dev.jasper.vault"));
                var window = new VaultManagerDialog(context, f.lock, f.manager, service, () -> STATUS, () -> { }, Layout.CLASSIC);
                f.manager.saveAccount(login(amp, "amp", "ubuntu", MODIFIED)); f.drain();
                window.show(owner, Optional.of(amp));
                var view = window.panel.view();
                assertThat(window.panel.layout()).isEqualTo(Layout.CLASSIC);
                assertThat(enabled(view, "toolbar.copy-password")).isTrue();
                var sheet = new AtomicReference<EntryEditor>();
                window.dialog("Add Login", close -> {
                    var form = EntryEditor.login(context.kit(), null, f.manager::saveAccount, close); sheet.set(form); return form;
                });
                type(sheet.get().view(), EntryEditor.PASSWORD, "synthetic-draft");
                f.manager.invalidate(); f.lock.lock(); window.changed();
                assertThat(closed(sheet.get().view())).as("locking closes the sheet and erases its draft").isTrue();
                for (String id : List.of("toolbar.new", "toolbar.edit", "toolbar.delete", "toolbar.copy-username", "toolbar.copy-password",
                    "toolbar.lock", ManagerPanel.SEARCH)) assertThat(enabled(view, id)).as(id + " while locked").isFalse();
                assertThat(find(view, "detail")).as("no Details pane while locked").isEmpty();
                assertThat(node(view, "status.provider", Nodes.Text.class).text()).isEqualTo("Locked");
                assertThat(node(view, "status.state", Nodes.StatusDot.class).kind()).isEqualTo(Nodes.StatusKind.IDLE);
                assertThat(node(view, ManagerPanel.LOCKED, Nodes.Empty.class).actions()).extracting(n -> ((Nodes.Button) n).action())
                    .containsExactly(ManagerPanel.UNLOCK);
                window.close();
            }
        });
    }

    static String date(Instant timestamp) {
        return DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH).withZone(ZoneId.systemDefault()).format(timestamp);
    }

    @Test void theCategoriesAreATreeWithCountsAndTheItemsATable() throws Exception {
        edt(() -> {
            var panel = panel(); var view = panel.view();
            panel.refresh(rows(), List.of(), STATUS, true);
            var tree = node(view, ManagerPanel.KIND, Nodes.Tree.class);
            var all = tree.roots().getFirst();
            assertThat(all.title()).isEqualTo("All Items");
            assertThat(all.count()).hasValue(2);
            assertThat(all.children()).extracting(TreeItem::title).containsExactly("Logins", "SSH Keys", "Secure Notes");
            assertThat(all.children()).extracting(item -> item.count().getAsInt()).containsExactly(2, 0, 0);
            assertThat(tree.expandedIds()).containsExactly(ManagerPanel.ALL_TYPES);
            assertThat(tree.selectedIds()).containsExactly(ManagerPanel.ALL_TYPES);
            assertThat(node(view, "categories.header", Nodes.Header.class).state().visible()).isTrue();
            var table = node(view, ManagerPanel.ROWS, Nodes.Table.class);
            assertThat(table.columns()).extracting(Column::title).containsExactly("Name", "Username", "Type", "Modified");
            assertThat(table.columns().getFirst().sorted()).hasValue(true);
            assertThat(table.activateAction()).as("Enter and double-click edit").isEqualTo(ManagerPanel.EDIT);
            assertThat(table.rows()).extracting(TableRow::id).containsExactly(amp.toString(), proxmox.toString());
            var first = table.rows().getFirst();
            assertThat(first.cells()).containsEntry(ManagerPanel.COLUMN_USERNAME, "ubuntu").containsEntry(ManagerPanel.COLUMN_TYPE, "Password")
                .containsEntry(ManagerPanel.COLUMN_MODIFIED, date(MODIFIED));
            var name = (Nodes.Stack) first.cellNodes().get(ManagerPanel.COLUMN_NAME);
            var swatch = (Nodes.Icon) name.children().getFirst();
            assertThat(swatch.size()).isEqualTo(Nodes.IconSize.SWATCH);
            assertThat(swatch.hue()).isEqualTo(Nodes.Hue.BLUE);
            assertThat(((Nodes.Text) name.children().get(1)).text()).isEqualTo("amp");
            assertThat(node(view, ManagerPanel.ITEMS_FOOTER, Nodes.Text.class).text()).isEqualTo("2 of 2 items");
            emit(view, new UiEvent.Expanded(ManagerPanel.KIND, ManagerPanel.ALL_TYPES, false));
            assertThat(node(view, ManagerPanel.KIND, Nodes.Tree.class).expandedIds()).as("the user collapsed All Items").isEmpty();
            emit(view, new UiEvent.Selected(ManagerPanel.KIND, Set.of(VaultManager.Type.SSH_KEY.name())));
            assertThat(node(view, ManagerPanel.ITEMS_FOOTER, Nodes.Text.class).text()).isEqualTo("0 of 0 items");
            panel.close();
        });
    }

    @Test void aSearchThatMatchesNothingKeepsTheHeadersAndCountsZero() throws Exception {
        edt(() -> {
            var panel = panel(); var view = panel.view();
            panel.refresh(rows(), List.of(), STATUS, true);
            emit(view, new UiEvent.Selected(ManagerPanel.ROWS, Set.of(amp.toString())));
            emit(view, new UiEvent.Selected(ManagerPanel.KIND, Set.of(VaultManager.Type.LOGIN.name())));
            emit(view, new UiEvent.Edited(ManagerPanel.SEARCH, "no such entry"));
            var table = node(view, ManagerPanel.ROWS, Nodes.Table.class);
            assertThat(table.rows()).isEmpty();
            assertThat(table.columns()).hasSize(4);
            assertThat(table.empty()).as("no empty state replaces the headers").isEmpty();
            assertThat(table.emptyText()).isEqualTo("No matching entries");
            assertThat(node(view, ManagerPanel.ITEMS_FOOTER, Nodes.Text.class).text()).isEqualTo("0 of 2 items");
            for (String verb : VERBS) assertThat(enabled(view, verb)).as(verb + " without a shown selection").isFalse();
            emit(view, new UiEvent.Selected(ManagerPanel.KIND, Set.of(ManagerPanel.ALL_TYPES)));
            assertThat(node(view, ManagerPanel.ITEMS_FOOTER, Nodes.Text.class).text()).isEqualTo("0 of 2 items");
            assertThat(node(view, ManagerPanel.ROWS, Nodes.Table.class).emptyText()).isEqualTo("No matching entries");
            panel.close();
        });
    }

    @Test void theSortSurvivesARefreshAndTheSelectionStays() throws Exception {
        edt(() -> {
            var panel = panel(); var view = panel.view();
            var rows = rows();
            panel.refresh(rows, List.of(), STATUS, true);
            emit(view, new UiEvent.Selected(ManagerPanel.ROWS, Set.of(proxmox.toString())));
            emit(view, new UiEvent.Sorted(ManagerPanel.ROWS, ManagerPanel.COLUMN_MODIFIED));
            assertThat(node(view, ManagerPanel.ROWS, Nodes.Table.class).rows()).extracting(TableRow::id).as("oldest first")
                .containsExactly(proxmox.toString(), amp.toString());
            emit(view, new UiEvent.Sorted(ManagerPanel.ROWS, ManagerPanel.COLUMN_MODIFIED));
            panel.refresh(rows, List.of(), STATUS, true);
            var table = node(view, ManagerPanel.ROWS, Nodes.Table.class);
            assertThat(table.rows()).extracting(TableRow::id).as("newest first, after the refresh").containsExactly(amp.toString(), proxmox.toString());
            assertThat(table.selectedIds()).containsExactly(proxmox.toString());
            assertThat(table.columns()).filteredOn(column -> column.sorted().isPresent()).singleElement().satisfies(column -> {
                assertThat(column.id()).isEqualTo(ManagerPanel.COLUMN_MODIFIED);
                assertThat(column.sorted()).hasValue(false);
            });
            panel.close();
        });
    }

    @Test void theDetailsPaneFollowsTheSelection() throws Exception {
        edt(() -> { try (var host = new FakePluginHost()) {
            var panel = panel(); var view = panel.view();
            panel.copies(CopyCards.declare(host.start(INFO, Set.of(), Set.of(), c -> { })));
            panel.refresh(rows(), List.of(new Grant("dev.jasper.remote", amp)), STATUS, true);
            assertThat(node(view, "detail.header", Nodes.Header.class).title()).isEqualTo("Details");
            assertThat(node(view, "detail.none", Nodes.Text.class).text()).isEqualTo("Select an item to view its details.");
            emit(view, new UiEvent.Selected(ManagerPanel.ROWS, Set.of(amp.toString())));
            assertThat(find(view, "detail.none")).isEmpty();
            assertThat(node(view, "detail.icon", Nodes.Icon.class).size()).isEqualTo(Nodes.IconSize.SWATCH);
            assertThat(node(view, "detail.name", Nodes.Text.class).text()).isEqualTo("amp");
            assertThat(node(view, "detail.type", Nodes.Text.class).text()).isEqualTo("Login \u00B7 Password");
            assertThat(node(view, "detail.fields.section", Nodes.Section.class).title()).isEqualTo("Fields");
            assertThat(node(view, "detail.username.row", Nodes.FormRow.class).label()).isEqualTo("Username");
            assertThat(node(view, "detail.username", Nodes.Text.class).role()).isEqualTo(Nodes.TextRole.MONO);
            assertThat(button(view, "detail.username.copy").label()).isEqualTo("Copy");
            assertThat(button(view, "detail.username.copy").action()).isEqualTo("detail.username.copy");
            String first = "detail.secret." + amp;
            assertThat(node(view, first, Nodes.Secret.class).kind()).isEqualTo(Nodes.SecretKind.DETAIL);
            assertThat(node(view, first, Nodes.Secret.class).copyAction()).isEqualTo("detail.password.copy");
            assertThat(secret(view, first)).isEqualTo("synthetic-amp");
            var properties = node(view, "detail.properties", Nodes.Properties.class);
            assertThat(properties.title()).as("untitled: label-colon lines (10a Rulings 4)").isEmpty();
            assertThat(properties.rows()).extracting(row -> row.label() + "=" + row.value()).containsExactly("Modified=" + date(MODIFIED), "Used by=Remote");
            assertThat(button(view, ManagerPanel.EDIT).label()).isEqualTo("Edit\u2026");
            assertThat(button(view, ManagerPanel.DELETE).label()).isEqualTo("Delete\u2026");
            assertThat(view.spec().toString()).as("no secret in the descriptor").doesNotContain("synthetic-amp");
            emit(view, new UiEvent.Selected(ManagerPanel.ROWS, Set.of(proxmox.toString())));
            assertThat(find(view, first)).as("the first entry's value is gone with its control").isEmpty();
            assertThat(secret(view, "detail.secret." + proxmox)).as("the selected entry's own value").isEqualTo("synthetic-Proxmox");
            assertThat(node(view, "detail.properties", Nodes.Properties.class).rows().get(1).value()).isEqualTo("Not used");
            press(view, "detail.password.copy");
            assertThat(host.clipboard().copies()).as("Copy copies the selected entry's own password")
                .containsExactly("secret|dev.jasper.vault|synthetic-Proxmox");
            press(view, ManagerPanel.DELETE);
            assertThat(asked).containsExactly("delete " + proxmox);
            panel.close();
        } });
    }

    @Test void anSshKeysDetailsListItsFilesAndCopyThePublicKey() throws Exception {
        edt(() -> { try (var host = new FakePluginHost()) {
            var panel = panel(); var view = panel.view();
            panel.copies(CopyCards.declare(host.start(INFO, Set.of(), Set.of(), c -> { })));
            UUID laptop = UUID.randomUUID();
            entries.put(laptop, new dev.jasper.vault.model.SshKey(laptop, "laptop", "ssh-ed25519", "SHA256:fixture", "",
                directory.resolve("laptop"), directory.resolve("laptop.pub"), MODIFIED));
            panel.refresh(List.of(new VaultManager.Row(laptop, "laptop", "SHA256:fixture", VaultManager.Type.SSH_KEY)), List.of(), STATUS, true);
            emit(view, new UiEvent.Selected(ManagerPanel.ROWS, Set.of(laptop.toString())));
            assertThat(node(view, "detail.type", Nodes.Text.class).text()).isEqualTo("SSH Key \u00B7 ssh-ed25519");
            assertThat(node(view, "detail.private.row", Nodes.FormRow.class).label()).isEqualTo("Private key");
            assertThat(node(view, "detail.public.row", Nodes.FormRow.class).label()).isEqualTo("Public key");
            assertThat(node(view, "detail.fingerprint", Nodes.Text.class).text()).isEqualTo("SHA256:fixture");
            // Every row has its Copy (spec 14.8); a key's fields are ordinary copies.
            for (String field : List.of("algorithm", "fingerprint", "private", "public"))
                assertThat(button(view, "detail." + field + ".copy").label()).as(field).isEqualTo("Copy");
            press(view, "detail.fingerprint.copy");
            press(view, "detail.public.copy");
            assertThat(host.clipboard().copies()).containsExactly("copy|dev.jasper.vault|SHA256:fixture",
                "copy|dev.jasper.vault|" + directory.resolve("laptop.pub"));
            // A key row copies the file's path and says so; "the public key" is Copy Public Key's, which copies the key itself.
            assertThat(host.notifications().lines()).contains("dev.jasper.vault.copies|INFO|Copied the fingerprint for laptop|",
                "dev.jasper.vault.copies|INFO|Copied the public key path for laptop|");
            assertThat(host.notifications().lines()).noneMatch(line -> line.contains("Copied the public key for laptop"));
            press(view, ManagerPanel.COPY_PUBLIC);
            assertThat(button(view, ManagerPanel.COPY_PUBLIC).label()).isEqualTo("Copy Public Key");
            assertThat(asked).containsExactly("copy-public " + laptop);
            panel.close();
        } });
    }
}
