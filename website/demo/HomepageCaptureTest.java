package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ThemeTestSupport;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

/** Local marketing demo: production plugin views, synthetic data, no network or user secrets. */
class HomepageCaptureTest {
    @TempDir Path home;

    @Test void capturePopulatedPluginViews() throws Exception {
        var controller = onEdtValue(() -> ThemeTestSupport.controller(true));
        var library = dev.jasper.app.appearance.ThemeLibrary.bundledOnly();
        assertThat(library.problems()).isEmpty();
        var selection = dev.jasper.app.appearance.ThemeSelection.of(library, "slate-cobalt", "jasper-meadow", dev.jasper.app.config.TerminalSchemeChoice.MATCH, (key,message) -> { throw new AssertionError(message); });
        onEdt(() -> controller.configure(false, selection, dev.jasper.app.config.UiFontConfig.defaults()));
        try {
            try (var remote = new HomepageRemoteHost(home.resolve("remote"), false, onEdtValue(() -> new WorkspaceAppearanceFixture(controller.current())))) {
                remote.openSftp(); remote.quietTransfers();
                onEdt(() -> {
                    long modified = java.time.Instant.parse("2026-10-19T10:30:00Z").toEpochMilli();
                    remote.sftpListing("Staging gateway", "/srv/gateway", List.of(
                        remote.fileEntry("config", "DIRECTORY", 0, modified),
                        remote.fileEntry("logs", "DIRECTORY", 0, modified),
                        remote.fileEntry("releases", "DIRECTORY", 0, modified),
                        remote.fileEntry("deployment.yaml", "FILE", 2840, modified),
                        remote.fileEntry("nginx.conf", "FILE", 1720, modified),
                        remote.fileEntry("README.md", "FILE", 4350, modified)));
                    remote.transferRows(List.<Object[]>of(new Object[]{UUID.randomUUID(), "gateway-release.tar.gz", "18.4 MB / 32 MB", 0.575, false}));
                });
                StagedVaultHost.settle();
                onEdt(() -> {
                    save("sftp", remote.sftp(), 560, 420);
                    assertThat(HomepageRemoteHost.labels(remote.sftp())).contains("deployment.yaml", "nginx.conf");
                });
                assertThat(remote.problems).isEmpty();
            }
            var appearance = onEdtValue(() -> new WorkspaceAppearanceFixture(controller.current()));
            try (var vault = new StagedVaultHost(home.resolve("vault"), false, appearance)) {
                vault.createVault("local-synthetic-demo-only");
                var ids = vault.seedLogins(new String[][]{
                    {"Staging gateway", "demo-operator", "fictional-demo-password-1"},
                    {"Build server", "demo-build", "fictional-demo-password-2"},
                    {"Metrics dashboard", "demo-observer", "fictional-demo-password-3"}});
                vault.dispatchToManager(new dev.jasper.sdk.ui.UiEvent.Selected("rows", java.util.Set.of(ids.getFirst().toString())));
                var manager = vault.surface("Credential Vault");
                onEdt(() -> {
                    var rows = (dev.jasper.ui.model.Nodes.ItemList) StagedVaultHost.node(manager,"rows");
                    assertThat(rows.items()).hasSize(3);
                    assertThat(rows.selectedIds()).containsExactly(ids.getFirst().toString());
                    save("vault", manager.holder(), 900, 300);
                });
                assertThat(vault.problems).isEmpty();
            }
        } finally { onEdt(controller::close); }
    }

    private static void save(String name, JComponent root, int width, int height) {
        try {
            root.setSize(width, height);
            for (int i=0;i<4;i++) layout(root);
            var image = new BufferedImage(width*2,height*2,BufferedImage.TYPE_INT_ARGB);
            var g=image.createGraphics();
            try { g.scale(2,2); root.printAll(g); } finally { g.dispose(); }
            Path output=Path.of("build/homepage-demo"); Files.createDirectories(output);
            ImageIO.write(image,"png",output.resolve(name+".png").toFile());
        } catch(java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
    }
    private static void layout(Container root) {
        root.doLayout();
        for(var child:root.getComponents()) if(child instanceof Container c && child.isVisible()) layout(c);
    }
}
