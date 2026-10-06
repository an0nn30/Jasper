package dev.jasper.app.plugins;

import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import dev.jasper.sdk.ui.OverlaySpec;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import java.awt.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static dev.jasper.app.plugins.StagedRemoteHost.awaitNamed;
import static dev.jasper.app.plugins.StagedRemoteHost.button;
import static dev.jasper.app.plugins.StagedRemoteHost.require;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-host, dark and light pixel evidence of the migrated Remote Sessions panel and host dialogs (Plan 2 Task 8a,
 * controller ruling G10, following Task 7 ruling 2): the staged plugin runs in the application's plugin host
 * ({@link StagedRemoteHost}: no Vault, no network, no shell, an isolated ~/.ssh) and its kit views render through the
 * host translation. Each capture waits for its named content to mount and must not be a uniform frame. The host-key
 * question is asked through the plugin's own entry with a synthetic key; the connecting overlay is mounted on an
 * overlay as a connection attempt does, with synthetic text and no connection. Output goes to
 * build/theme-migration-evidence/remote-hosted; the frozen before images are untouched.
 */
class RemoteRenderEvidenceTest {
    static final String FAMILY = "remote-hosted";
    static final String FINGERPRINT = "SHA256:q7Lx3mY0f9Vt2bQkR8sNcW1eZpH4uJdA6gTo5iKyXvE";
    @TempDir Path root;

    @Test @Timeout(value = 150, unit = TimeUnit.SECONDS)
    void capturesTheStagedSessionsPanelAndHostDialogsInTheRealHostDarkAndLight() throws Exception {
        for (String theme : List.of("dark", "light")) capture(theme, root.resolve(theme));
    }

    private void capture(String theme, Path data) throws Exception {
        var controller = onEdtValue(dev.jasper.app.appearance.ThemeController::new);
        onEdt(() -> controller.configure(theme.equals("dark") ? dev.jasper.app.appearance.Theme.DARK : dev.jasper.app.appearance.Theme.JASPER_LIGHT));
        try (var remote = new StagedRemoteHost(data, theme.equals("dark"))) {
            Files.writeString(remote.ssh.resolve("config"), "Host fixture\n HostName fixture.example.invalid\n User fixture-user\nHost broken\n User fixture-user\n IdentityFile %x\n");
            remote.openSessions();
            save(theme, "sessions-empty", remote.window, 900, 560);

            onEdt(() -> button(remote.header(), "New session").doClick(0));
            var editor = remote.awaitSurface("Add Host");
            awaitNamed(editor, "host.save");
            save(theme, "add-host", editor.holder(), 620, 620);
            onEdt(() -> ((AbstractButton) require(editor, "host.save")).doClick(0));
            save(theme, "add-host-invalid", editor.holder(), 620, 620);
            onEdt(editor::requestClose);
            remote.addHost("example", "example.invalid", "fixture-user");
            save(theme, "sessions-host-selected", remote.window, 900, 560);

            onEdt(() -> button(remote.header(), "Import sessions").doClick(0));
            var importing = remote.awaitSurface("Import from ~/.ssh/config");
            awaitNamed(importing, "import.import");
            onEdt(() -> assertThat(StagedRemoteHost.labels(importing.holder())).as("each candidate reads its host beside its switch")
                .anyMatch(text -> text.startsWith("New: fixture")));
            save(theme, "import-preview", importing.holder(), 700, 420);
            onEdt(importing::requestClose);

            var question = remote.askHostKey("fixture.example.invalid", 2222, "ssh-ed25519", FINGERPRINT);
            var trust = remote.awaitSurface("Verify host key fixture.example.invalid");
            awaitNamed(trust, "host-key.fingerprint");
            save(theme, "host-key-trust", trust.holder(), 640, 300);
            onEdt(() -> {
                Component fingerprint = require(trust, "host-key.fingerprint");
                assertThat(fingerprint.getWidth()).as("the whole fingerprint shows: no ellipsis may hide part of it")
                    .isGreaterThanOrEqualTo(fingerprint.getPreferredSize().width);
                ((AbstractButton) require(trust, "host-key.cancel")).doClick(0);
            });
            StagedVaultHost.await(question::isDone, "Cancel answers the host-key question");

            var context = remote.context();
            var overlay = new AtomicReference<PluginDialog>();
            var panel = new AtomicReference<Object>();
            onEdt(() -> {
                try {
                    // As a connection attempt does (RemotePlugin.ConnectAttempt), without connecting.
                    var handle = context.terminals().window(remote.window.id()).orElseThrow();
                    overlay.set(context.windows().overlay(new OverlaySpec("Connecting to example", handle)));
                    panel.set(remote.loader.loadClass("dev.jasper.remote.ui.ConnectionPanel")
                        .getConstructor(UiKit.class, String.class, String.class, Runnable.class, Runnable.class)
                        .newInstance(context.kit(), "example", "fixture-user@example.invalid:22", (Runnable) () -> { }, (Runnable) () -> { }));
                    overlay.get().setContent((UiView) panel.get().getClass().getMethod("view").invoke(panel.get()));
                    overlay.get().show();
                    panel.get().getClass().getMethod("status", String.class).invoke(panel.get(), "Authenticating…");
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            var connecting = remote.awaitSurface("Connecting to example");
            awaitNamed(connecting, "connection.progress");
            save(theme, "connection-busy", connecting.holder(), 460, 260);
            onEdt(() -> {
                try { panel.get().getClass().getMethod("failed", String.class).invoke(panel.get(), "Fixture connection failure"); }
                catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            save(theme, "connection-error", connecting.holder(), 460, 260);
            onEdt(() -> overlay.get().close());
            assertThat(remote.problems).isEmpty();
        } finally {
            onEdt(() -> { controller.close(); new dev.jasper.app.appearance.ThemeController(); });
        }
    }

    /** Captures and checks the frame shows content: a blank, uniform frame fails. */
    static void save(String theme, String state, JComponent component, int width, int height) throws Exception {
        onEdt(() -> ThemeMigrationCapture.save(FAMILY, theme, state, component, width, height));
        var image = ImageIO.read(Path.of("build/theme-migration-evidence", FAMILY, theme, state + ".png").toFile());
        var colours = new HashSet<Integer>();
        for (int y = 0; y < image.getHeight(); y += 2) for (int x = 0; x < image.getWidth(); x += 2) colours.add(image.getRGB(x, y));
        assertThat(colours.size()).as("%s/%s shows content, not a uniform frame", theme, state).isGreaterThanOrEqualTo(8);
    }

    static void save(String theme, String state, AuxiliarySurface surface, int width, int height) throws Exception {
        save(theme, state, surface.holder(), width, height);
    }
}
