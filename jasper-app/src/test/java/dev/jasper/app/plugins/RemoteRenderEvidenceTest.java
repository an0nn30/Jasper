package dev.jasper.app.plugins;

import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.StagedRemoteHost.button;
import static dev.jasper.app.plugins.StagedRemoteHost.require;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-host, dark and light pixel evidence of the migrated Remote Sessions panel and host dialogs (Plan 2 Task 8a,
 * controller ruling G10, following Task 7 ruling 2): the staged plugin runs in the application's plugin host
 * ({@link StagedRemoteHost}: no Vault, no network, no shell, an isolated ~/.ssh) and its kit views render through the
 * host translation. Output goes to build/theme-migration-evidence/remote-hosted; the frozen before images are untouched.
 * Host names and addresses are synthetic.
 */
class RemoteRenderEvidenceTest {
    static final String FAMILY = "remote-hosted";
    @TempDir Path root;

    @Test @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void capturesTheStagedSessionsPanelAndHostDialogsInTheRealHostDarkAndLight() throws Exception {
        for (String theme : List.of("dark", "light")) capture(theme, root.resolve(theme));
    }

    private void capture(String theme, Path data) throws Exception {
        var controller = AppContractTest.onEdtValue(dev.jasper.app.appearance.ThemeController::new);
        onEdt(() -> controller.configure(theme.equals("dark") ? dev.jasper.app.appearance.Theme.DARK : dev.jasper.app.appearance.Theme.JASPER_LIGHT));
        try (var remote = new StagedRemoteHost(data, theme.equals("dark"))) {
            Files.writeString(remote.ssh.resolve("config"), "Host fixture\n HostName fixture.example.invalid\n User fixture-user\nHost broken\n IdentityFile %x\n");
            remote.openSessions();
            save(theme, "sessions-empty", remote.window, 900, 560);
            onEdt(() -> button(remote.header(), "New session").doClick(0));
            var editor = remote.awaitSurface("Add Host");
            save(theme, "add-host", editor.holder(), 620, 620);
            onEdt(() -> ((AbstractButton) require(editor, "host.save")).doClick(0));
            save(theme, "add-host-invalid", editor.holder(), 620, 620);
            onEdt(editor::requestClose);
            remote.addHost("example", "example.invalid", "fixture-user");
            save(theme, "sessions-host-selected", remote.window, 900, 560);
            onEdt(() -> button(remote.header(), "Import sessions").doClick(0));
            var importing = remote.awaitSurface("Import from ~/.ssh/config");
            save(theme, "import-preview", importing.holder(), 700, 420);
            onEdt(importing::requestClose);
            assertThat(remote.problems).isEmpty();
        } finally {
            onEdt(() -> { controller.close(); new dev.jasper.app.appearance.ThemeController(); });
        }
    }

    static void save(String theme, String state, JComponent component, int width, int height) throws Exception {
        onEdt(() -> ThemeMigrationCapture.save(FAMILY, theme, state, component, width, height));
        assertThat(Files.size(Path.of("build/theme-migration-evidence", FAMILY, theme, state + ".png"))).isPositive();
    }

    static void save(String theme, String state, AuxiliarySurface surface, int width, int height) throws Exception {
        save(theme, state, surface.holder(), width, height);
    }
}
