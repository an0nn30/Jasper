package dev.jasper.vault.ui;

import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static dev.jasper.sdk.contracts.ThemeMigrationCapture.save;
import static org.assertj.core.api.Assertions.assertThat;

class ThemeMigrationEvidenceTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void capturesRealVaultOwnersAndConditionalStatesWithoutSecretFixtures() throws Exception {
        ThemeMigrationCapture.themes(theme -> {
            var create = PasswordPanel.create(true, (password, bind) -> java.util.Arrays.fill(password, '\0'), () -> { });
            save("vault", theme, "create", create, 580, 380);
            create.primary.doClick();
            assertThat(create.message.getText()).contains("at least 8");
            save("vault", theme, "create-invalid", create, 580, 380);
            var unlock = PasswordPanel.unlock(dev.jasper.vault.service.UnlockCaptureSupport.prompt(directory));
            save("vault", theme, "unlock", unlock, 580, 300);
            var manager = new ManagerPanel(id -> Optional.empty(), type -> { }, id -> { }, id -> { }, id -> { }, grant -> { },
                () -> { }, () -> { }, () -> { }, () -> { }, dev.jasper.sdk.testing.FakeNamedIcon::new);
            try {
                save("vault", theme, "manager-empty", manager, 900, 580);
                manager.refresh(List.of(new VaultManager.Row(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Example login", "fixture-user", VaultManager.Type.LOGIN)), List.of(), "Unlocked", true);
                save("vault", theme, "manager-populated", manager, 900, 580);
                manager.search.setText("no matches");
                save("vault", theme, "manager-filtered-empty", manager, 900, 580);
                save("vault", theme, "add-menu", manager.addMenu, 300, 220);
                manager.refresh(List.of(), List.of(), "Locked", false);
                save("vault", theme, "manager-locked", manager, 900, 580);
            } finally { manager.close(); }
            var login = EntryEditor.login(null, value -> { value.auth().zero(); return CompletableFuture.completedFuture(null); }, () -> { });
            try {
                save("vault", theme, "login-password", login, 600, 430);
                login.auth.setSelectedIndex(2);
                save("vault", theme, "login-key-and-password", login, 600, 430);
            } finally { login.close(); }
        });
    }
}
