package dev.jasper.app.plugins;

import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static org.assertj.core.api.Assertions.assertThat;

/** The real sample jar, staged the way the application image bundles it, through the real runtime to Buddy. */
class BundledSamplePluginTest {
    @TempDir Path root;

    @Test void theStagedSampleLoadsFromItsJarAndItsActivityReachesBuddy() throws Exception {
        Path staged = Path.of(System.getProperty("jasper.stagedPlugins"));
        assertThat(staged.resolve("dev.jasper.sample")).isDirectory();
        var deck = new BuddyTestSupport();
        var runtime = new AtomicReference<PluginRuntime>();
        dev.jasper.app.contributions.Contributions contributions = AppContractTest.onEdtValue(dev.jasper.app.contributions.Contributions::new);
        onEdt(() -> {
            runtime.set(new PluginRuntime(new PluginRuntime.Options(staged, root.resolve("user"), null, false,
                root.resolve("plugins.toml"), root.resolve("plugins.lock")),
                new ActivityNotifier(deck.companion(), () -> { }), PluginRuntime.SettingsReports.IGNORED, contributions,
                AppContractTest.headlessWindows(), new dev.jasper.app.terminals.TerminalRegistry()));
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
            assertThat(contributions.panels()).extracting(panel -> panel.id()).containsExactlyInAnyOrder("dev.jasper.sample.panel", "dev.jasper.remote.panel", "dev.jasper.remote.sftp.panel");
            assertThat(contributions.railActions()).containsExactly("dev.jasper.sample.about");
            assertThat(contributions.menus()).anySatisfy(menu -> {
                assertThat(menu.target()).isEqualTo(dev.jasper.app.contributions.MenuTarget.standard(
                    dev.jasper.app.contributions.MenuTarget.Slot.FILE));
                assertThat(menu.entries()).contains(new dev.jasper.app.contributions.MenuEntry.Item("dev.jasper.vault.open"));
            });
            assertThat(contributions.status()).hasSize(4)
                .anySatisfy(item -> assertThat(item.text()).startsWith("Sample:"))
                .anySatisfy(item -> assertThat(item.text()).isEqualTo("Vault"));
        });
        assertThat(runtime.get().statusLines()).as("the sample and the six bundled feature plugins").hasSize(7)
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.sample", "0.2.1", "BUNDLED", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.history", "BUNDLED", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.snippets", "BUNDLED", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.vault", "BUNDLED", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.remote", "BUNDLED", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.editor", "BUNDLED", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.fileserver", "BUNDLED", "ACTIVE"));
        var state = new AtomicReference<BuddyNotice.State>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (state.get() != BuddyNotice.State.DONE && System.nanoTime() < deadline) {
            onEdt(() -> state.set(deck.notices().isEmpty() ? null : deck.notices().get(0).state()));
            Thread.sleep(20);
        }
        onEdt(() -> assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.id().source()).isEqualTo("dev.jasper.sample");
            assertThat(notice.title()).isEqualTo("Sample plugin");
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.DONE);
            assertThat(notice.detail().get()).isEqualTo("Ready");
        }));
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
            assertThat(rows).hasSize(7).allSatisfy(row -> assertThat(row.icon()).as(row.id()).isPresent());
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
            new dev.jasper.app.appearance.ThemeController(dev.jasper.app.appearance.Theme.LIGHT);
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
                assertThat(icon).isSameAs(dev.jasper.app.platform.AppIcons.named("NETWORK"));
                assertThat(contributions.action("dev.jasper.remote.connect").orElseThrow().icon()).isSameAs(icon);
                assertThat(contributions.action("dev.jasper.remote.sessions.manage").orElseThrow().icon()).isSameAs(icon);
                assertThat(contributions.panels()).hasSize(2).allSatisfy(panel -> {
                    assertThat(panel.icon()).isNotNull();
                    assertThat(panel.icon()).isIn(dev.jasper.app.platform.AppIcons.named("FOLDER"), dev.jasper.app.platform.AppIcons.named("NETWORK"));
                });
                assertThat(contributions.status()).hasSize(2).anySatisfy(status -> assertThat(status.icon()).isSameAs(icon));
                assertThat(contributions.toolbar()).singleElement().isInstanceOfSatisfying(
                    dev.jasper.app.contributions.ToolbarEntry.Dropdown.class, menu -> assertThat(menu.icon()).isSameAs(icon));
            });
        } finally {
            var pending = new AtomicReference<List<CompletableFuture<?>>>();
            onEdt(() -> pending.set(runtime.get().stop()));
            CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
            onEdt(() -> new dev.jasper.app.appearance.ThemeController());
        }
    }

}
