package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static org.assertj.core.api.Assertions.assertThat;

/** The real sample jar, staged the way the application image bundles it, through the real runtime to Buddy and the notification center. */
class BundledSamplePluginTest {
    @TempDir Path root;

    @Test void theStagedSampleLoadsFromItsJarAndItsActivityEndsAsANotification() throws Exception {
        Path staged = Path.of(System.getProperty("jasper.stagedPlugins"));
        assertThat(staged.resolve("dev.jasper.sample")).isDirectory();
        var deck = new BuddyTestSupport();
        var center = AppContractTest.onEdtValue(() -> new NotificationCenter(SwingUtilities::invokeLater, Clock.systemUTC(),
            () -> Delivery.Presence.FOCUSED));
        var notifications = new PluginRuntime.NotificationHost(center,
            caused -> SwingUtilities.invokeLater(() -> caused.posted().accept(center.post(caused.request()))), (title, details) -> { });
        var runtime = new AtomicReference<PluginRuntime>();
        dev.jasper.app.contributions.Contributions contributions = AppContractTest.onEdtValue(dev.jasper.app.contributions.Contributions::new);
        onEdt(() -> {
            runtime.set(new PluginRuntime(new PluginRuntime.Options(staged, root.resolve("user"), null, false,
                root.resolve("plugins.toml"), root.resolve("plugins.lock")),
                new ActivityNotifier(deck.companion(), () -> { }), PluginRuntime.SettingsReports.IGNORED, contributions,
                AppContractTest.headlessWindows(), new dev.jasper.app.terminals.TerminalRegistry(), notifications, path -> { }));
            runtime.get().start(Map.of("dev.jasper.sample", Map.<String, Object>of("demo_activity", true, "demo_ui", true, "demo_step_millis", 0L)), true);
        });
        onEdt(() -> {
            assertThat(contributions.action("dev.jasper.sample.demo")).get()
                .satisfies(action -> assertThat(action.icon()).as("an SVG from the plugin's own jar").isNotNull());
            assertThat(contributions.toolbar()).hasSize(2)
                .contains(new dev.jasper.app.contributions.ToolbarEntry.Button("dev.jasper.sample.demo"))
                .anySatisfy(entry -> assertThat(entry).isInstanceOfSatisfying(
                    dev.jasper.app.contributions.ToolbarEntry.Dropdown.class, dropdown -> {
                        assertThat(dropdown.title()).isEqualTo("Sessions");
                        assertThat(dropdown.actionIds()).containsExactly("dev.jasper.remote.sessions.manage");
                    }));
            assertThat(contributions.panels()).extracting(panel -> panel.id()).containsExactlyInAnyOrder("dev.jasper.sample.panel", "dev.jasper.remote.panel", "dev.jasper.remote.sftp.panel", "dev.jasper.remote.tunnels.panel", "dev.jasper.fileserver.panel");
            assertThat(contributions.railActions()).containsExactly("dev.jasper.sample.about");
            assertThat(contributions.menus()).anySatisfy(menu -> {
                assertThat(menu.target()).isEqualTo(dev.jasper.app.contributions.MenuTarget.standard(
                    dev.jasper.app.contributions.MenuTarget.Slot.FILE));
                assertThat(menu.entries()).contains(new dev.jasper.app.contributions.MenuEntry.Item("dev.jasper.vault.open"));
            });
            assertThat(contributions.status()).hasSize(6)
                .anySatisfy(item -> assertThat(item.text()).startsWith("Sample:"))
                .anySatisfy(item -> assertThat(item.text()).isEqualTo("Vault"));
        });
        assertThat(runtime.get().statusLines()).as("the sample and the seven bundled feature plugins").hasSize(8)
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.sample", "0.2.1", "BUNDLED", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.history", "BUNDLED", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.snippets", "BUNDLED", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.vault", "BUNDLED", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.remote", "BUNDLED", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.editor", "BUNDLED", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.fileserver", "BUNDLED", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.indexer", "BUNDLED", "ACTIVE"));
        String outcome = "dev.jasper.sample.activities|INFO|Sample plugin|Ready";
        var lines = new AtomicReference<List<String>>(List.of());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!lines.get().contains(outcome) && System.nanoTime() < deadline) {
            onEdt(() -> lines.set(center.history().stream().map(entry -> String.join("|", entry.request().group(),
                entry.request().severity().name(), entry.request().title(), entry.request().content())).toList()));
            Thread.sleep(20);
        }
        assertThat(lines.get()).contains(outcome);
        onEdt(() -> assertThat(deck.notices()).as("Buddy keeps live work only").isEmpty());
        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        onEdt(() -> pending.set(runtime.get().stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
    }
    @Test void everyBundledPluginShipsAnIconAndRemoteNamesItsHomepage() throws Exception {
        Path staged = Path.of(System.getProperty("jasper.stagedPlugins"));
        var runtime = new AtomicReference<PluginRuntime>();
        onEdt(() -> {
            runtime.set(new PluginRuntime(new PluginRuntime.Options(staged, root.resolve("user"), null, false,
                root.resolve("plugins.toml"), root.resolve("plugins.lock")),
                new ActivityNotifier(new BuddyTestSupport().companion(), () -> { }), PluginRuntime.SettingsReports.IGNORED,
                new dev.jasper.app.contributions.Contributions(), AppContractTest.headlessWindows(),
                new dev.jasper.app.terminals.TerminalRegistry()));
            runtime.get().start(Map.of(), true);
        });
        try {
            var outcome = new CompletableFuture<PluginRuntime.Outcome>();
            onEdt(() -> runtime.get().snapshot(outcome::complete));
            var rows = outcome.get(10, TimeUnit.SECONDS).snapshot().rows();
            assertThat(rows).hasSize(8).allSatisfy(row -> assertThat(row.icon()).as(row.id()).isPresent());
            assertThat(rows).filteredOn(row -> row.id().equals("dev.jasper.remote")).singleElement()
                .satisfies(row -> assertThat(row.homepage()).isEqualTo("https://github.com/an0nn30/Jasper"));
        } finally {
            var pending = new AtomicReference<List<CompletableFuture<?>>>();
            onEdt(() -> pending.set(runtime.get().stop()));
            CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        }
    }

    @Test void remoteLoadsWithoutVaultAndUsesHostIconsForEveryPlacement() throws Exception {
        Path staged = Path.of(System.getProperty("jasper.stagedPlugins"));
        // Not under @TempDir: sqlite-jdbc reads its version through a cached jar: URL it never closes, so this
        // JVM holds the copied jar open until it exits and Windows cannot delete it. Beside build/plugins, not in it.
        Path remoteOnly = staged.resolveSibling("remote-only-plugins");
        PluginMaintenance.deleteRecursively(remoteOnly);
        java.nio.file.Files.createDirectories(remoteOnly);
        Path source = staged.resolve("dev.jasper.remote");
        try (var paths = java.nio.file.Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = remoteOnly.resolve("dev.jasper.remote").resolve(source.relativize(path));
                if (java.nio.file.Files.isDirectory(path)) java.nio.file.Files.createDirectories(target);
                else java.nio.file.Files.copy(path, target);
            }
        }
        var runtime = new AtomicReference<PluginRuntime>();
        var deck = new BuddyTestSupport();
        var contributions = new dev.jasper.app.contributions.Contributions();
        onEdt(() -> {
            ThemeTestSupport.controller(false);
            runtime.set(new PluginRuntime(new PluginRuntime.Options(remoteOnly, root.resolve("user"), null, false,
                root.resolve("plugins.toml"), root.resolve("plugins.lock")), new ActivityNotifier(deck.companion(), () -> {}),
                PluginRuntime.SettingsReports.IGNORED, contributions, AppContractTest.headlessWindows(),
                new dev.jasper.app.terminals.TerminalRegistry()));
            runtime.get().start(Map.of(), true);
        });
        try {
            assertThat(runtime.get().statusLines()).singleElement().asString().contains("dev.jasper.remote", "ACTIVE");
            onEdt(() -> {
                var icon = contributions.action("dev.jasper.remote.hosts").orElseThrow().icon();
                assertThat(icon.getIconWidth()).isEqualTo(16);
                assertThat(icon).isInstanceOfSatisfying(dev.jasper.app.platform.PresentationIcon.class,
                    named -> assertThat(named.name()).isEqualTo("network"));
                assertThat(dev.jasper.app.testsupport.IconPixels.of(icon)).isEqualTo(
                    dev.jasper.app.testsupport.IconPixels.of(new dev.jasper.ui.icons.IconRegistry().icon("network",
                        dev.jasper.app.platform.ThemeZone.theme(null), dev.jasper.ui.model.Nodes.Hue.NONE, 16)));
                assertThat(dev.jasper.app.testsupport.IconPixels.of(contributions.action("dev.jasper.remote.connect").orElseThrow().icon())).isEqualTo(dev.jasper.app.testsupport.IconPixels.of(icon));
                assertThat(dev.jasper.app.testsupport.IconPixels.of(contributions.action("dev.jasper.remote.sessions.manage").orElseThrow().icon())).isEqualTo(dev.jasper.app.testsupport.IconPixels.of(icon));
                assertThat(contributions.panels()).hasSize(3).allSatisfy(panel -> {
                    assertThat(panel.icon()).isNotNull();
                    assertThat(panel.icon()).isInstanceOfSatisfying(dev.jasper.app.platform.PresentationIcon.class,
                        named -> assertThat(named.name()).isIn("folder", "network", "tunnel"));
                });
                assertThat(contributions.status()).hasSize(3).anySatisfy(status -> assertThat(dev.jasper.app.testsupport.IconPixels.of(status.icon())).isEqualTo(dev.jasper.app.testsupport.IconPixels.of(icon)));
                assertThat(contributions.toolbar()).singleElement().isInstanceOfSatisfying(
                    dev.jasper.app.contributions.ToolbarEntry.Dropdown.class, menu -> assertThat(dev.jasper.app.testsupport.IconPixels.of(menu.icon())).isEqualTo(dev.jasper.app.testsupport.IconPixels.of(icon)));
            });
        } finally {
            var pending = new AtomicReference<List<CompletableFuture<?>>>();
            onEdt(() -> pending.set(runtime.get().stop()));
            CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
            onEdt(() -> ThemeTestSupport.controller(true));
        }
    }

}
