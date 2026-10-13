package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ThemeTestSupport;
import com.formdev.flatlaf.util.UIScale;

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
 * overlay as a connection attempt does, with synthetic text and no connection. Task 8b adds the Tunnels panel and its
 * dialogs; Task 8c the SFTP explorer, a transfer row and the SFTP/transfer dialogs. Output goes to
 * build/theme-migration-evidence/remote-hosted; the frozen before images are untouched.
 */
class RemoteRenderEvidenceTest {
    @Test void capturesOriginalIconOwnersAtCalibratedAllocations() throws Exception {
        for(String theme:List.of("dark","light")) {
            var controller=onEdtValue(() -> ThemeTestSupport.controller(true));
            onEdt(()->controller.configure(theme.equals("dark"), dev.jasper.app.config.TerminalSchemeChoice.MATCH));
            try(var remote=new StagedRemoteHost(root.resolve("icon-owners-"+theme),theme.equals("dark"))) {
                remote.openTunnels();
                for(int[] allocation:new int[][]{{903,824},{1454,970},{500,824}}) {
                    onEdt(()->dev.jasper.app.workspace.DesktopTestSupport.captureIconOwners(remote.window,theme,allocation[0],allocation[1]));
                }
            }finally{onEdt(()->{controller.close();ThemeTestSupport.controller(true);});}
        }
    }
    static final String FAMILY = "remote-hosted";
    static final String FINGERPRINT = "SHA256:q7Lx3mY0f9Vt2bQkR8sNcW1eZpH4uJdA6gTo5iKyXvE";
    @TempDir Path root;

    @Test @Timeout(value = 150, unit = TimeUnit.SECONDS)
    void capturesTheStagedSessionsPanelAndHostDialogsInTheRealHostDarkAndLight() throws Exception {
        for (String theme : List.of("dark", "light")) capture(theme, root.resolve(theme));
    }

    private void capture(String theme, Path data) throws Exception {
        var controller = onEdtValue(() -> ThemeTestSupport.controller(true));
        onEdt(() -> controller.configure(theme.equals("dark"), dev.jasper.app.config.TerminalSchemeChoice.MATCH));
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
            onEdt(() -> { controller.close(); ThemeTestSupport.controller(true); });
        }
    }

    /**
     * Plan 2 Task 8b: the Tunnels panel (empty; a failed and a stopped row; a starting row with its pending switch and an
     * up row) and the New Tunnel and Forward a Port dialogs, dark and light. Every tunnel goes through a missing host, so
     * nothing connects; the starting and up states are handed to the live panel as the plugin would after the manager
     * confirmed them, with no tunnel running.
     */
    @Test @Timeout(value = 150, unit = TimeUnit.SECONDS)
    void capturesTheStagedTunnelsPanelAndDialogsInTheRealHostDarkAndLight() throws Exception {
        for (String theme : List.of("dark", "light")) captureTunnels(theme, root.resolve("tunnels-" + theme));
    }

    private void captureTunnels(String theme, Path data) throws Exception {
        var controller = onEdtValue(() -> ThemeTestSupport.controller(true));
        onEdt(() -> controller.configure(theme.equals("dark"), dev.jasper.app.config.TerminalSchemeChoice.MATCH));
        try (var remote = new StagedRemoteHost(data, theme.equals("dark"))) {
            remote.openTunnels();
            save(theme, "tunnels-empty", remote.window, 900, 560);
            onEdt(() -> button(remote.tunnelsHeader(), "New Tunnel…").doClick(0));
            var editor = remote.awaitSurface("New Tunnel");
            awaitNamed(editor, "tunnel.save");
            save(theme, "tunnel-new", editor.holder(), 620, 640);
            onEdt(() -> ((AbstractButton) require(editor, "tunnel.save")).doClick(0));
            save(theme, "tunnel-new-invalid", editor.holder(), 620, 640);
            onEdt(editor::requestClose);
            StagedRemoteTunnelsTest.tunnels(data, "db", "web");
            remote.pollTunnels();
            StagedVaultHost.await(() -> StagedRemoteHost.labels(remote.tunnelsHeader()).contains("2"), "the tunnels are listed");
            onEdt(() -> {
                var view = remote.tunnels();
                view.setSize(300, 480);
                StagedRemoteTunnelsTest.layout(view);
                var db = StagedRemoteTunnelsTest.row(view, "db");
                StagedRemoteTunnelsTest.click(db, 1);
                StagedRemoteHost.press(StagedRemoteTunnelsTest.canvas(db), java.awt.event.KeyEvent.VK_ENTER, '\n');
            });
            StagedVaultHost.await(() -> StagedRemoteTunnelsTest.problems(remote.tunnels()) == 1, "db failed: its host is missing");
            StagedVaultHost.settle();
            save(theme, "tunnels-failed-stopped", remote.window, 900, 560);
            onEdt(() -> remote.tunnelStates("Connecting…"));
            StagedVaultHost.settle(); // the switches' knobs finish moving
            save(theme, "tunnels-starting-up", remote.window, 900, 560);
            var forward = new AtomicReference<PluginDialog>();
            onEdt(() -> {
                try {
                    var context = remote.context();
                    var handle = context.terminals().window(remote.window.id()).orElseThrow();
                    forward.set(context.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Forward a Port", handle, true)));
                    Object panel = remote.loader.loadClass("dev.jasper.remote.ui.tunnels.ForwardPanel")
                        .getConstructor(UiKit.class, List.class, java.util.Optional.class, java.util.function.IntPredicate.class, java.util.function.Consumer.class, Runnable.class)
                        .newInstance(context.kit(), List.of(), java.util.Optional.empty(), (java.util.function.IntPredicate) port -> true,
                            (java.util.function.Consumer<Object>) tunnel -> { }, (Runnable) () -> { });
                    forward.get().setContent((UiView) panel.getClass().getMethod("view").invoke(panel));
                    forward.get().show();
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            var surface = remote.awaitSurface("Forward a Port");
            awaitNamed(surface, "forward.more");
            save(theme, "forward-port", surface.holder(), 560, 520);
            onEdt(() -> forward.get().close());
            assertThat(remote.problems).isEmpty();
        } finally {
            onEdt(() -> { controller.close(); ThemeTestSupport.controller(true); });
        }
    }

    /**
     * Plan 2 Task 8c: the SFTP explorer (not connected; a listing with a transfer row) and the New folder and Items already
     * exist dialogs, dark and light. The listing and the transfer row are handed to the live explorer as its controller and
     * the transfer UI would, with no connection and no transfer running.
     */
    @Test @Timeout(value = 150, unit = TimeUnit.SECONDS)
    void capturesTheStagedSftpPanelAndDialogsInTheRealHostDarkAndLight() throws Exception {
        for (String theme : List.of("dark", "light")) captureSftp(theme, root.resolve("sftp-" + theme));
    }

    private void captureSftp(String theme, Path data) throws Exception {
        var controller = onEdtValue(() -> ThemeTestSupport.controller(true));
        onEdt(() -> controller.configure(theme.equals("dark"), dev.jasper.app.config.TerminalSchemeChoice.MATCH));
        try (var remote = new StagedRemoteHost(data, theme.equals("dark"))) {
            remote.openSftp();
            save(theme, "sftp-disconnected", remote.window, 900, 560);
            remote.quietTransfers();
            onEdt(() -> {
                remote.sftpListing("Example host", "/fixture/app", List.of(remote.fileEntry("logs", "DIRECTORY", 0, 1_000),
                    remote.fileEntry("example.txt", "FILE", 42, 1_000), remote.fileEntry("current", "LINK", 10, 1_000)));
                remote.transferRows(List.<Object[]>of(new Object[] {java.util.UUID.fromString("00000000-0000-4000-8000-0000000000c2"),
                    "example.txt → Example host:/fixture/app", "50% · 1.0 MiB/s · 3 s left", 0.5, false}));
            });
            StagedVaultHost.settle();
            onEdt(() -> assertThat(StagedRemoteHost.labels(remote.sftp())).contains("example.txt", "Transfers"));
            save(theme, "sftp-connected", remote.window, 900, 560);
            var folder = new AtomicReference<PluginDialog>();
            onEdt(() -> folder.set(StagedRemoteSftpTest.mount(remote, "New folder", "dev.jasper.remote.ui.sftp.FolderNamePanel",
                new Class<?>[] {UiKit.class, java.util.function.Function.class, Runnable.class},
                (java.util.function.Function<String, java.util.Optional<String>>) name -> java.util.Optional.empty(), (Runnable) () -> { })));
            var surface = remote.awaitSurface("New folder");
            awaitNamed(surface, "new-folder.name.input");
            save(theme, "sftp-new-folder", surface.holder(), 460, 220);
            onEdt(() -> folder.get().close());
            var existing = new AtomicReference<PluginDialog>();
            onEdt(() -> existing.set(StagedRemoteSftpTest.mount(remote, "Items already exist", "dev.jasper.remote.ui.transfers.ExistingItemsPanel",
                new Class<?>[] {UiKit.class, String.class, java.util.function.Consumer.class, Runnable.class},
                "2 of 3 items already exist in Example host:/fixture/app.", (java.util.function.Consumer<Object>) choice -> { }, (Runnable) () -> { })));
            var ask = remote.awaitSurface("Items already exist");
            awaitNamed(ask, "existing.replace");
            save(theme, "transfers-existing-items", ask.holder(), 520, 200);
            onEdt(() -> existing.get().close());
            assertThat(remote.problems).isEmpty();
        } finally {
            onEdt(() -> { controller.close(); ThemeTestSupport.controller(true); });
        }
    }

    /** Captures and checks the frame shows content: a blank, uniform frame fails. */
    static void save(String theme, String state, JComponent component, int width, int height) throws Exception {
        onEdt(() -> ThemeMigrationCapture.save(FAMILY, theme, state, component, UIScale.scale(width), UIScale.scale(height)));
        var image = ImageIO.read(Path.of("build/theme-migration-evidence", FAMILY, theme, state + ".png").toFile());
        var colours = new HashSet<Integer>();
        for (int y = 0; y < image.getHeight(); y += 2) for (int x = 0; x < image.getWidth(); x += 2) colours.add(image.getRGB(x, y));
        assertThat(colours.size()).as("%s/%s shows content, not a uniform frame", theme, state).isGreaterThanOrEqualTo(8);
    }

    static void save(String theme, String state, AuxiliarySurface surface, int width, int height) throws Exception {
        save(theme, state, surface.holder(), width, height);
    }
}
