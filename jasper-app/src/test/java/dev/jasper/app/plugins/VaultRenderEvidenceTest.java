package dev.jasper.app.plugins;

import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static dev.jasper.app.plugins.StagedVaultHost.click;
import static dev.jasper.app.plugins.StagedVaultHost.type;
import static org.assertj.core.api.Assertions.*;

/**
 * Real-host, dark and light pixel evidence of the migrated Vault (Plan 2 Task 7, controller ruling 2): the staged
 * plugin runs in the application's plugin host ({@link StagedVaultHost}, unbound by {@link UnboundVault}) and its kit
 * views render through the host translation. Output goes to build/theme-migration-evidence/vault-hosted; the frozen
 * before images are untouched. Passwords are synthetic and no clipboard is touched.
 */
class VaultRenderEvidenceTest {
    static final String FAMILY = "vault-hosted";
    @TempDir Path root;

    @Test void capturesTheStagedVaultInTheRealHostDarkAndLight() throws Exception {
        for (String theme : List.of("dark", "light")) capture(theme, root.resolve(theme));
    }

    private void capture(String theme, Path data) throws Exception {
        var controller = onEdtValue(dev.jasper.app.appearance.ThemeController::new);
        onEdt(() -> controller.configure(theme.equals("dark") ? dev.jasper.app.appearance.Theme.DARK : dev.jasper.app.appearance.Theme.JASPER_LIGHT));
        try (var vault = new StagedVaultHost(data, theme.equals("dark"))) {
            vault.invoke("dev.jasper.vault.open");
            var create = vault.surface("Create Vault");
            onEdt(() -> UnboundVault.assertUnbound(StagedVaultHost.kitView(create)));
            save(theme, "create", create, 580, 380);
            onEdt(() -> click(create, "submit"));
            save(theme, "create-invalid", create, 580, 380);
            onEdt(() -> {
                type(create, "password.input", "synthetic-host-pass"); type(create, "confirm.input", "synthetic-host-pass");
                UnboundVault.assertUnbound(StagedVaultHost.kitView(create));
                click(create, "submit");
            });
            var manager = vault.awaitSurface("Credential Vault");
            save(theme, "manager-empty", manager, 900, 580);
            vault.invoke("dev.jasper.vault.generate_key");
            var generator = vault.surface("Generate SSH Key");
            save(theme, "generate-key", generator, 640, 560);
            onEdt(() -> generator.requestClose());
            vault.invoke("dev.jasper.vault.lock");
            save(theme, "manager-locked", manager, 900, 580);
            onEdt(() -> click(manager, "unlock"));
            save(theme, "manager-unlock", manager, 900, 580);
            onEdt(() -> manager.requestClose());
            vault.invoke("dev.jasper.vault.open");
            var unlock = vault.surface("Unlock Vault");
            save(theme, "unlock", unlock, 580, 300);
            onEdt(() -> unlock.requestClose());
            assertThat(vault.problems).isEmpty();
        } finally {
            onEdt(() -> { controller.close(); new dev.jasper.app.appearance.ThemeController(); });
        }
    }

    static void save(String theme, String state, AuxiliarySurface surface, int width, int height) throws Exception {
        onEdt(() -> {
            JComponent holder = surface.holder();
            ThemeMigrationCapture.save(FAMILY, theme, state, holder, width, height);
        });
        assertThat(Files.size(Path.of("build/theme-migration-evidence", FAMILY, theme, state + ".png"))).isPositive();
    }
}
