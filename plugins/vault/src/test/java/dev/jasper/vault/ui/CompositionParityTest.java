package dev.jasper.vault.ui;

import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.SshKey;
import dev.jasper.vault.workstation.Layout;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Spec 14.7: each default-look gesture of the Vault manager has a classic path with the same effect. Every gesture runs
 * on a rows panel and on a classic panel over the same entries, each recording what the plugin is asked to do.
 */
class CompositionParityTest {
    @TempDir Path directory;
    static final VaultManagerDialog.Status STATUS = new VaultManagerDialog.Status("keychain", "15 min", "/synthetic/vault.jv");
    final UUID login = UUID.randomUUID(), key = UUID.randomUUID(), note = UUID.randomUUID();
    final Map<UUID, Object> entries = new HashMap<>();

    record Run(ManagerPanel panel, List<String> asked) { }

    List<VaultManager.Row> rows() {
        entries.put(login, new Account(login, "amp", "ubuntu", new Auth.Password("synthetic-amp".toCharArray()), Instant.EPOCH, Instant.EPOCH));
        entries.put(key, new SshKey(key, "laptop", "ssh-ed25519", "SHA256:fixture", "", directory.resolve("laptop"), directory.resolve("laptop.pub"), Instant.EPOCH));
        entries.put(note, new Note(note, "Recovery", "synthetic-note".toCharArray(), Instant.EPOCH));
        return List.of(new VaultManager.Row(login, "amp", "ubuntu", VaultManager.Type.LOGIN),
            new VaultManager.Row(key, "laptop", "SHA256:fixture", VaultManager.Type.SSH_KEY),
            new VaultManager.Row(note, "Recovery", "", VaultManager.Type.NOTE));
    }

    Run run(Layout layout) {
        var asked = new ArrayList<String>();
        var panel = new ManagerPanel(new FakeKit("dev.jasper.vault"), layout, id -> Optional.ofNullable(entries.get(id)),
            type -> asked.add("add " + type), id -> asked.add("edit " + id), id -> asked.add("delete " + id), id -> asked.add("copy-public " + id),
            grant -> asked.add("revoke " + grant.pluginId()), () -> asked.add("generate"), () -> asked.add("lock"), () -> asked.add("unlock"),
            () -> asked.add("close"));
        return new Run(panel, asked);
    }

    /** Every action the shown, enabled nodes of {@code views} can send: buttons, menu items, activations and copies. */
    static Set<String> reachable(UiView... views) {
        var actions = new TreeSet<String>();
        for (var view : views) for (var node : all(view)) {
            if (!node.state().visible() || !node.state().enabled()) continue;
            switch (node) {
                case Nodes.Button button -> { if (!button.action().isBlank()) actions.add(button.action()); }
                case Nodes.MenuButton menu -> menu.items().stream().filter(item -> !item.separator()).map(MenuItem::action).forEach(actions::add);
                case Nodes.ItemList list -> { if (!list.activateAction().isBlank()) actions.add(list.activateAction()); }
                case Nodes.Table table -> { if (!table.activateAction().isBlank()) actions.add(table.activateAction()); }
                case Nodes.Secret secret -> { if (!secret.copyAction().isBlank()) actions.add(secret.copyAction()); }
                default -> { }
            }
        }
        return actions;
    }

    static void select(Run run, UUID id) { emit(run.panel().view(), new UiEvent.Selected(ManagerPanel.ROWS, Set.of(id.toString()))); }

    static void assertParity(String state, Run rows, Run classic) {
        assertThat(reachable(classic.panel().view())).as(state).containsAll(reachable(rows.panel().view()));
    }

    @Test void everyRowsActionHasAClassicPathInEachState() throws Exception {
        edt(() -> {
            var rows = run(Layout.ROWS); var classic = run(Layout.CLASSIC);
            var list = rows(); var grants = List.of(new Grant("dev.jasper.remote", login));
            for (var run : List.of(rows, classic)) run.panel().refresh(list, grants, STATUS, true);
            assertParity("nothing selected", rows, classic);
            for (UUID id : List.of(login, key, note)) {
                select(rows, id); select(classic, id);
                assertParity("selected " + entries.get(id).getClass().getSimpleName(), rows, classic);
            }
            for (var run : List.of(rows, classic)) run.panel().refresh(list, List.of(), STATUS, false);
            assertParity("locked", rows, classic);
            for (var run : List.of(rows, classic)) { run.panel().refresh(list, grants, STATUS, true); run.panel().showGrants(); }
            assertParity("the grants card", rows, classic);
            rows.panel().close(); classic.panel().close();
        });
    }

    @Test void eachGestureHasTheSameEffectInBothLayouts() throws Exception {
        edt(() -> {
            var rows = run(Layout.ROWS); var classic = run(Layout.CLASSIC);
            var list = rows();
            for (var run : List.of(rows, classic)) { run.panel().refresh(list, List.of(), STATUS, true); select(run, key); }
            var actions = new ArrayList<>(reachable(rows.panel().view()));
            // The copies go through the plugin's CopyCards by the same act() case in both layouts; Done is checked last.
            actions.removeIf(action -> action.startsWith("detail.") || action.equals(ManagerPanel.CLOSE));
            assertThat(actions).contains(ManagerPanel.EDIT, ManagerPanel.DELETE, ManagerPanel.COPY_PUBLIC, ManagerPanel.LOCK, "add.LOGIN",
                "add.SSH_KEY", "add.NOTE", ManagerPanel.ADD_GENERATE);
            for (String action : actions) { press(rows.panel().view(), action); press(classic.panel().view(), action); }
            assertThat(classic.asked()).isEqualTo(rows.asked());
            for (var run : List.of(rows, classic)) {
                emit(run.panel().view(), new UiEvent.Edited(ManagerPanel.SEARCH, "lap"));
                emit(run.panel().view(), new UiEvent.Selected(ManagerPanel.KIND, Set.of(VaultManager.Type.SSH_KEY.name())));
            }
            assertThat(classic.panel().visibleRows()).isEqualTo(rows.panel().visibleRows()).hasSize(1);
            for (var run : List.of(rows, classic)) { run.panel().refresh(list, List.of(), STATUS, false); press(run.panel().view(), ManagerPanel.UNLOCK); }
            press(rows.panel().view(), ManagerPanel.CLOSE); press(classic.panel().view(), ManagerPanel.CLOSE);
            assertThat(classic.asked()).isEqualTo(rows.asked());
            rows.panel().close(); classic.panel().close();
        });
    }
}
