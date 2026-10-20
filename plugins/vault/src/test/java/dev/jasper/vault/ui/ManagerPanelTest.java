package dev.jasper.vault.ui;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

class ManagerPanelTest {
    @Test void metadataUsesKitRolesSoMutedTextFollowsTheTheme() throws Exception {
        edt(() -> {
            var panel = panel(new AtomicReference<>());
            var view = panel.view();
            assertThat(node(view, "status.provider", Nodes.Text.class).role()).isEqualTo(Nodes.TextRole.CAPTION);
            var empty = node(view, ManagerPanel.LOCKED, Nodes.Empty.class);
            assertThat(empty.title()).isEqualTo("Vault locked");
            panel.refresh(List.of(), List.of(), new VaultManagerWindow.Status("test", "5 min", "/synthetic/vault.jv"), true);
            assertThat(node(view, ManagerPanel.EMPTY, Nodes.Empty.class).title()).isEqualTo("Nothing to show");
            var row = new VaultManager.Row(UUID.randomUUID(), "Name", "Detail", VaultManager.Type.LOGIN);
            panel.refresh(List.of(row), List.of(), new VaultManagerWindow.Status("test", "5 min", "/synthetic/vault.jv"), true);
            var item = node(view, ManagerPanel.ROWS, Nodes.ItemList.class).items().getFirst();
            assertThat(((Nodes.Text) item.subtitle().getFirst()).role()).isEqualTo(Nodes.TextRole.CAPTION);
            assertThat(item.leading()).hasValueSatisfying(n -> assertThat(n).isInstanceOf(Nodes.Icon.class));
            panel.close();
        });
    }

    @Test void controlsUseKitSemanticIcons() throws Exception {
        edt(() -> {
            var panel = panel(new AtomicReference<>());
            panel.refresh(List.of(), List.of(), new VaultManagerWindow.Status("test", "5 min", "/synthetic/vault.jv"), true);
            var view = panel.view();
            assertThat(node(panel.titleActions(), ManagerPanel.ADD, Nodes.MenuButton.class).icon()).isEqualTo("add");
            assertThat(button(view, ManagerPanel.EDIT).icon()).isEqualTo("edit");
            assertThat(button(view, ManagerPanel.EDIT).tooltip()).isEqualTo("Edit entry");
            assertThat(button(panel.titleActions(), ManagerPanel.LOCK).icon()).isEqualTo("lock");
            assertThat(node(view, ManagerPanel.SEARCH, Nodes.Field.class).kind()).isEqualTo(Nodes.FieldKind.SEARCH);
            panel.refresh(List.of(), List.of(), new VaultManagerWindow.Status("test", "5 min", "/synthetic/vault.jv"), false);
            assertThat(button(view, ManagerPanel.UNLOCK).icon()).isEqualTo("unlock");
            panel.close();
        });
    }

    @Test void searchAndTypeFiltersPreserveOnlyVisibleSelectionAndPaletteNavigationRevealsRows() throws Exception {
        edt(() -> {
            var panel = panel(new AtomicReference<>());
            var view = panel.view();
            var key = new VaultManager.Row(UUID.randomUUID(), "Zulu", "SHA256:fingerprint", VaultManager.Type.SSH_KEY);
            var login = new VaultManager.Row(UUID.randomUUID(), "Alpha", "deploy", VaultManager.Type.LOGIN);
            panel.refresh(List.of(key, login), List.of(), new VaultManagerWindow.Status("test", "5 min", "/synthetic/vault.jv"), true);
            assertThat(panel.visibleRows().getFirst()).isEqualTo(login);
            assertThat(node(view, "status.provider", Nodes.Text.class).text()).isEqualTo("Test");
            var rows = node(view, ManagerPanel.ROWS, Nodes.ItemList.class);
            assertThat(rows.items()).extracting(i -> i.id()).containsExactly(login.id().toString(), key.id().toString());
            assertThat(rows.visibleRows()).hasValue(ManagerPanel.VISIBLE_ROWS);
            assertThat(rows.activateAction()).as("Enter and double click edit").isEqualTo(ManagerPanel.EDIT);
            var first = rows.items().getFirst();
            assertThat(java.util.stream.Stream.of(first.title(), first.subtitle(), first.trailing()).flatMap(List::stream)
                .map(n -> ((Nodes.Text) n).text()).toList()).containsExactly("Alpha", "deploy");
            panel.select(key.id()); emit(view, new UiEvent.Edited(ManagerPanel.SEARCH, "DEPLOY"));
            assertThat(panel.visibleRows()).hasSize(1);
            assertThat(panel.selected()).isEmpty(); assertThat(button(view, ManagerPanel.EDIT).state().enabled()).isFalse();
            panel.select(key.id());
            assertThat(node(view, ManagerPanel.SEARCH, Nodes.Field.class).value()).isEmpty(); assertThat(panel.selected()).contains(key.id());
            assertThat(node(view, ManagerPanel.ROWS, Nodes.ItemList.class).selectedIds()).containsExactly(key.id().toString());
            assertThat(button(view, ManagerPanel.COPY_PUBLIC).state().enabled()).isTrue();
            emit(view, new UiEvent.Selected(ManagerPanel.KIND, Set.of(VaultManager.Type.LOGIN.name()))); assertThat(panel.selected()).isEmpty();
            panel.refresh(List.of(key, login), List.of(), new VaultManagerWindow.Status("test", "5 min", "/synthetic/vault.jv"), true);
            assertThat(panel.visibleRows()).hasSize(1);
            panel.refresh(List.of(key, login), List.of(), new VaultManagerWindow.Status("test", "5 min", "/synthetic/vault.jv"), false);
            assertThat(panel.visibleRows()).isEmpty(); assertThat(find(view, ManagerPanel.COPY_PUBLIC)).isEmpty();
            panel.close();
        });
    }

    @Test void aFilterWithNoMatchesKeepsTheSearch() throws Exception {
        edt(() -> {
            var panel = panel(new AtomicReference<>());
            var view = panel.view();
            panel.refresh(List.of(new VaultManager.Row(UUID.randomUUID(), "Alpha", "deploy", VaultManager.Type.LOGIN)), List.of(), new VaultManagerWindow.Status("test", "5 min", "/synthetic/vault.jv"), true);
            emit(view, new UiEvent.Edited(ManagerPanel.SEARCH, "no matches"));
            assertThat(node(view, ManagerPanel.EMPTY, Nodes.Empty.class).title()).isEqualTo("No matching entries");
            assertThat(node(view, ManagerPanel.SEARCH, Nodes.Field.class).value()).isEqualTo("no matches");
            panel.close();
        });
    }

    @Test void addMenuKeepsEveryExistingTypeAndKeyGenerationReachable() throws Exception {
        edt(() -> {
            var added = new AtomicReference<VaultManager.Type>(); boolean[] generated = {false};
            var panel = new ManagerPanel(new FakeKit("dev.jasper.vault"), id -> Optional.empty(), added::set, id -> { }, id -> { }, id -> { }, grant -> { },
                () -> generated[0] = true, () -> { }, () -> { }, () -> { });
            panel.refresh(List.of(), List.of(), new VaultManagerWindow.Status("test", "5 min", "/synthetic/vault.jv"), true);
            var view = panel.view();
            var items = node(panel.titleActions(), ManagerPanel.ADD, Nodes.MenuButton.class).items();
            for (var type : VaultManager.Type.values()) {
                var item = items.stream().filter(i -> i.label().equals(type.toString())).findFirst().orElseThrow();
                press(panel.titleActions(), item.action());
                assertThat(added.get()).isEqualTo(type);
            }
            assertThat(items).anyMatch(i -> i.separator());
            press(panel.titleActions(), items.getLast().action()); assertThat(generated[0]).isTrue();
            // As depicted, the category rail has no footer; password and grants are Vault commands.
            assertThat(node(view, "categories", Nodes.Panel.class).footer()).isEmpty();
            panel.close();
        });
    }

    @Test void okayIsThePrimaryAndEveryCloseControlClosesTheWindow() throws Exception {
        edt(() -> {
            int[] closes = {0};
            var panel = new ManagerPanel(new FakeKit("dev.jasper.vault"), id -> Optional.empty(), t -> { }, id -> { }, id -> { }, id -> { }, grant -> { },
                () -> { }, () -> { }, () -> { }, () -> closes[0]++);
            var view = panel.view();
            var form = node(view, ManagerPanel.ROOT, Nodes.Form.class);
            assertThat(form.actions()).filteredOn(b -> b.kind() == Nodes.ButtonKind.PRIMARY).singleElement()
                .satisfies(b -> assertThat(b.id()).isEqualTo(ManagerPanel.OKAY));
            press(view, button(view, ManagerPanel.OKAY).action()); press(view, form.cancelAction());
            assertThat(closes[0]).isEqualTo(2);
            panel.close();
        });
    }

    private static ManagerPanel panel(AtomicReference<VaultManager.Type> added) {
        return new ManagerPanel(new FakeKit("dev.jasper.vault"), id -> Optional.empty(), added::set, id -> { }, id -> { }, id -> { }, grant -> { },
            () -> { }, () -> { }, () -> { }, () -> { });
    }
}
