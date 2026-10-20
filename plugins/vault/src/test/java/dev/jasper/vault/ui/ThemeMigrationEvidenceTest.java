package dev.jasper.vault.ui;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Descriptor-scenario evidence for the migrated Vault views (Plan 2 Task 7, controller ruling 2): the same states the
 * frozen pre-migration captures recorded, asserted as kit descriptors with synthetic data and no secret text. The
 * descriptors are written under build/theme-migration-evidence/vault-descriptors; pixels of the real host rendering
 * come from the app-side VaultRenderEvidenceTest. The frozen before images are untouched.
 */
class ThemeMigrationEvidenceTest {
    @org.junit.jupiter.api.io.TempDir Path directory;
    static final String SYNTHETIC = "synthetic-pass-1";

    static void save(String state, UiView view) throws Exception {
        String text = view.spec().toString();
        assertThat(text).doesNotContain(SYNTHETIC);
        Path output = Path.of("build/theme-migration-evidence/vault-descriptors");
        Files.createDirectories(output);
        Files.writeString(output.resolve(state + ".txt"), text + "\n");
    }

    @Test void describesRealVaultOwnersAndConditionalStatesWithoutSecretFixtures() throws Exception {
        edt(() -> {
            var kit = new FakeKit("dev.jasper.vault");
            var create = PasswordPanel.create(kit, true, (password, bind) -> java.util.Arrays.fill(password, '\0'), () -> { });
            save("create", create.view());
            assertThat(node(create.view(), create.bind, Nodes.Toggle.class).checked()).isTrue();
            type(create.view(), create.password, SYNTHETIC);
            press(create.view(), create.submit);
            assertThat(error(create.view(), create.confirm)).contains("do not match");
            save("create-invalid", create.view());
            create.close();
            var unlock = PasswordPanel.unlock(kit, dev.jasper.vault.service.UnlockCaptureSupport.prompt(directory));
            save("unlock", unlock.view());
            unlock.close();
            var manager = new ManagerPanel(kit, id -> Optional.empty(), type -> { }, id -> { }, id -> { }, id -> { }, grant -> { },
                () -> { }, () -> { }, () -> { }, () -> { });
            try {
                manager.refresh(List.of(), List.of(), new VaultManagerDialog.Status("test", "5 min", "/synthetic/vault.jv"), true);
                save("manager-empty", manager.view());
                assertThat(node(manager.view(), ManagerPanel.EMPTY, Nodes.Empty.class).title()).isEqualTo("Nothing to show");
                manager.refresh(List.of(new VaultManager.Row(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Example login", "fixture-user", VaultManager.Type.LOGIN)), List.of(), new VaultManagerDialog.Status("test", "5 min", "/synthetic/vault.jv"), true);
                save("manager-populated", manager.view());
                emit(manager.view(), new UiEvent.Edited(ManagerPanel.SEARCH, "no matches"));
                save("manager-filtered-empty", manager.view());
                assertThat(node(manager.view(), ManagerPanel.EMPTY, Nodes.Empty.class).title()).isEqualTo("No matching entries");
                assertThat(node(manager.view(), ManagerPanel.ADD, Nodes.MenuButton.class).items()).hasSize(5);
                save("add-menu", manager.view());
                manager.refresh(List.of(), List.of(), new VaultManagerDialog.Status("test", "5 min", "/synthetic/vault.jv"), false);
                save("manager-locked", manager.view());
                assertThat(find(manager.view(), ManagerPanel.LOCKED)).isPresent();
            } finally { manager.close(); }
            var login = EntryEditor.login(kit, null, value -> { value.auth().zero(); return CompletableFuture.completedFuture(null); }, () -> { });
            try {
                save("login-password", login.view());
                emit(login.view(), new UiEvent.Selected(EntryEditor.AUTH, Set.of(EntryEditor.AUTH_KEY)));
                emit(login.view(), new UiEvent.Toggled(EntryEditor.ALSO_PASSWORD, true));
                type(login.view(), EntryEditor.PASSWORD, SYNTHETIC);
                save("login-key-and-password", login.view());
            } finally { login.close(); }
            var key = EntryEditor.key(kit, null, value -> { throw new AssertionError("Empty/invalid key must not save"); }, () -> { });
            try {
                save("ssh-key-empty", key.view());
                emit(key.view(), new UiEvent.Edited(EntryEditor.NAME, "Fixture SSH key")); press(key.view(), EditorForm.SAVE);
                assertThat(button(key.view(), EditorForm.SAVE).state().enabled()).isFalse();
                assertThat(error(key.view(), EntryEditor.PRIVATE_PATH)).isEmpty();
                assertThat(button(key.view(), EditorForm.SAVE).state().busy()).isFalse();
                save("ssh-key-invalid-path", key.view());
            } finally { key.close(); }
            var existing = new dev.jasper.vault.model.SshKey(UUID.fromString("00000000-0000-0000-0000-000000000004"),
                "Fixture SSH key", "ed25519", "SHA256:fixture", "Fixture comment",
                Path.of("/fixture/keys/id_ed25519"), Path.of("/fixture/keys/id_ed25519.pub"), java.time.Instant.EPOCH);
            var edit = EntryEditor.key(kit, existing, value -> { throw new AssertionError("Edit capture must not save"); }, () -> { });
            try {
                assertThat(node(edit.view(), EntryEditor.PRIVATE_PATH, Nodes.Field.class).value()).isEqualTo(existing.privatePath().toString());
                assertThat(node(edit.view(), EntryEditor.PUBLIC_PATH, Nodes.Field.class).value()).isEqualTo(existing.publicPath().toString());
                assertThat(node(edit.view(), EntryEditor.COMMENT, Nodes.Field.class).value()).isEqualTo("Fixture comment");
                save("ssh-key-edit", edit.view());
            } finally { edit.close(); }
            assertThat(kit.failures()).isEmpty();
        });
    }
}
