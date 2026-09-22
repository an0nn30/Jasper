package dev.jasper.app.pluginmanager;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.restart.ResidentControl;
import dev.jasper.app.restart.RestartFlow;
import dev.jasper.app.restart.RestartMode;
import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.swing.JButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.assertThat;

/** The manager over a real runtime and real files; only the native shells and the restart are fakes. */
class PluginManagerTest {
    @TempDir Path root;
    private final BuddyTestSupport deck = new BuddyTestSupport();
    private final List<RestartMode> restarts = new CopyOnWriteArrayList<>();
    private final List<String> quits = new CopyOnWriteArrayList<>();
    private final List<Path> edited = new CopyOnWriteArrayList<>();
    private final List<Path> revealed = new CopyOnWriteArrayList<>();
    private PluginRuntime runtime;
    private AuxiliaryWindows windows;
    private PluginManager manager;
    private Path chosenZip;
    private boolean residentLive;

    private Path user() { return root.resolve("plugins"); }

    private void start(boolean safeMode, boolean standaloneNotice) throws Exception {
        edt(() -> {
            windows = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { },
                title -> { }, () -> new java.awt.Rectangle(0, 0, 10, 10), (title, initial) -> java.util.Optional.empty()));
            runtime = new PluginRuntime(new PluginRuntime.Options(null, user(), null, safeMode, root.resolve("plugins.toml"),
                root.resolve("plugins.lock")), new ActivityNotifier(deck.companion(), () -> { }),
                (key, message) -> { }, new Contributions(), windows, new dev.jasper.app.terminals.TerminalRegistry());
            runtime.start(Map.of(), true);
            var control = new ResidentControl(() -> residentLive, () -> false);
            var flow = new RestartFlow(control, mode -> { restarts.add(mode); return true; }, Runnable::run, Runnable::run,
                Duration.ofMillis(20), Duration.ofMillis(1));
            manager = new PluginManager(runtime, windows, new PluginManager.Hooks(surface -> Optional.ofNullable(chosenZip), flow,
                () -> quits.add("quit"), control, standaloneNotice, Runnable::run, edited::add, revealed::add));
        });
    }

    @AfterEach void stop() throws Exception {
        var pending = new CompletableFuture<List<CompletableFuture<?>>>();
        edt(() -> pending.complete(runtime.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        edt(() -> windows.close());
    }

    private Path zipOf(String id, String version, String extraToml) throws Exception {
        Path built = Files.createTempDirectory(root, "built");
        PluginJars.build(built, "main.jar", PluginJars.descriptor(id, version, "fix.Main") + extraToml, Map.of(), List.of());
        Path zip = root.resolve(id + ".zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("main.jar"));
            out.write(Files.readAllBytes(built.resolve("main.jar")));
            out.closeEntry();
        }
        return zip;
    }

    private List<String> bannerButtons() { return manager.panel().bannerButtons().stream().map(JButton::getText).toList(); }

    @Test void installsAZipAfterConsentAndOffersTheRestart() throws Exception {
        start(false, false);
        chosenZip = zipOf("dev.example.zipped", "1.0.0", "capabilities = [\"terminal.inject\"]\n");
        edt(manager::open);
        until(() -> manager.panel().details().contains("No plugins"));
        edt(() -> {
            assertThat(windows.open()).singleElement().satisfies(surface -> {
                assertThat(surface.id()).isEqualTo(PluginManager.WINDOW_ID);
                assertThat(surface.title()).isEqualTo("Plugins");
            });
            assertThat(manager.panel().bannerText()).isEmpty();
            manager.panel().install.doClick();
        });
        until(() -> manager.consent() != null);
        edt(() -> {
            assertThat(manager.consent().text()).contains("zipped 1.0.0", "Type into your terminals (terminal.inject)");
            assertThat(windows.open()).as("the consent dialog belongs to the manager window").hasSize(2);
            manager.consent().allow.doClick();
        });
        until(() -> manager.panel().listed().size() == 1);
        edt(() -> {
            assertThat(windows.open()).hasSize(1);
            assertThat(manager.consent()).isNull();
            assertThat(manager.panel().listed()).containsExactly("dev.example.zipped  1.0.0 — Not loaded yet (restart to apply)");
            assertThat(manager.panel().messageText()).isEqualTo("dev.example.zipped 1.0.0 will be installed when Jasper restarts.");
            assertThat(manager.panel().bannerText()).isEqualTo("Restart Jasper to apply your changes.");
            assertThat(bannerButtons()).containsExactly("Restart Now");
            manager.panel().bannerButtons().get(0).doClick();
        });
        assertThat(restarts).containsExactly(RestartMode.SAME);
        assertThat(user().resolve(".pending").resolve("dev.example.zipped").resolve("main.jar")).exists();
    }

    @Test void decliningConsentDiscardsTheStagedPluginAndABadZipIsExplained() throws Exception {
        start(false, false);
        chosenZip = zipOf("dev.example.zipped", "1.0.0", "");
        edt(manager::open);
        until(() -> manager.panel().details().contains("No plugins"));
        edt(() -> manager.panel().install.doClick());
        until(() -> manager.consent() != null);
        edt(() -> manager.consent().cancel.doClick());
        until(() -> {
            try (var all = Files.list(user())) { return all.noneMatch(path -> path.getFileName().toString().startsWith(".staging-")); }
            catch (java.io.IOException failure) { return false; }
        });
        edt(() -> assertThat(manager.panel().listed()).isEmpty());

        Files.writeString(root.resolve("bad.zip"), "not a zip");
        chosenZip = root.resolve("bad.zip");
        edt(() -> manager.panel().install.doClick());
        until(() -> !manager.panel().messageText().isEmpty());
        edt(() -> assertThat(manager.panel().messageText()).contains("could not be read"));

        chosenZip = null;
        edt(() -> manager.panel().install.doClick());
        edt(() -> assertThat(manager.consent()).as("cancelling the file dialog does nothing").isNull());
    }

    @Test void reviewingAFoundPluginConsentsToExactlyWhatWasShownAndRefreshesOnActivation() throws Exception {
        PluginJars.build(user().resolve("dev.example.found"), "main.jar", PluginJars.descriptor("dev.example.found", "1.0.0", "fix.Main")
            + "capabilities = [\"terminal.observe\"]\n", Map.of(), List.of());
        start(false, false);
        edt(manager::open);
        until(() -> manager.panel().listed().size() == 1);
        edt(() -> {
            assertThat(manager.panel().listed()).containsExactly("dev.example.found  1.0.0 — Needs review");
            manager.panel().review.doClick();
            assertThat(manager.consent().allow.getText()).isEqualTo("Allow and Enable");
            manager.consent().allow.doClick();
        });
        until(() -> manager.panel().bannerText().startsWith("Restart Jasper"));
        assertThat(Files.readString(root.resolve("plugins.toml"))).contains("consented = [\"terminal.observe\"]");

        // Another process disables it; the manager notices when its window regains focus.
        Files.writeString(root.resolve("plugins.toml"), "version = 1\n\n[plugins.\"dev.example.found\"]\nenabled = false\nconsented = [\"terminal.observe\"]\nremove = false\n");
        edt(() -> windows.open().get(0).notifyActivated());
        until(() -> manager.panel().bannerText().isEmpty());
    }

    @Test void safeModeOffersRestartNormallyAndWalksTheResidentConversation() throws Exception {
        residentLive = true;
        start(true, false);
        edt(manager::open);
        until(() -> manager.panel().bannerText().startsWith("Safe mode"));
        edt(() -> {
            assertThat(bannerButtons()).containsExactly("Restart Normally");
            manager.panel().bannerButtons().get(0).doClick();
            assertThat(manager.panel().bannerText()).contains("Another Jasper process is running with the previous plugin set");
            assertThat(bannerButtons()).containsExactly("Quit It and Restart", "Launch Anyway", "Cancel");
            manager.panel().bannerButtons().get(0).doClick();
            assertThat(manager.panel().bannerText()).as("the fake resident refuses").contains("did not quit");
            assertThat(bannerButtons()).containsExactly("Try Again", "Launch Anyway", "Cancel");
            assertThat(restarts).isEmpty();
            manager.panel().bannerButtons().get(1).doClick();
        });
        assertThat(restarts).containsExactly(RestartMode.STANDALONE);
    }

    @Test void aStandaloneReplacementSaysThatTheOtherProcessStillRunsThePreviousSet() throws Exception {
        residentLive = true;
        start(false, true);
        edt(manager::open);
        until(() -> !manager.panel().noticeText().isEmpty());
        edt(() -> assertThat(manager.panel().noticeText()).contains("still running with the plugin set it started with"));
        residentLive = false;
        edt(() -> windows.open().get(0).notifyActivated());
        until(() -> manager.panel().noticeText().isEmpty());
    }

        @Test void removingAsksFirstAndTheOpenersGoThroughTheHooks() throws Exception {
            PluginJars.build(user().resolve("dev.example.gone/jars"), "main.jar", PluginJars.descriptor("dev.example.gone", "1.0.0", "fix.Main"), Map.of(), List.of());
            Files.writeString(root.resolve("plugins.toml"), "version = 1\n[plugins.\"dev.example.gone\"]\nenabled = true\nconsented = []\n");
            start(false, false);
            // Built before start so the launch discovers it; consented so Remove is offered.
            edt(manager::open);
            until(() -> manager.panel().listed().stream().anyMatch(line -> line.contains("dev.example.gone")));
            edt(() -> manager.panel().select("dev.example.gone"));
            edt(() -> manager.panel().remove.doClick());
            until(() -> manager.confirm() != null);
            edt(() -> assertThat(manager.confirm().text()).contains("dev.example.gone", "jars, settings and data"));
            edt(() -> manager.confirm().cancel.doClick());
            edt(() -> assertThat(manager.panel().remove.getText()).isEqualTo("Remove\u2026"));
            edt(() -> manager.panel().remove.doClick());
            until(() -> manager.confirm() != null);
            edt(() -> manager.confirm().ok.doClick());
            until(() -> manager.panel().remove.getText().equals("Keep"));
            edt(() -> { manager.panel().clickMenu("Open Settings"); manager.panel().clickMenu("Open Plugin Folder"); manager.panel().clickMenu("Open Data Folder"); });
            until(() -> edited.size() == 1 && revealed.size() == 2);
            assertThat(edited).containsExactly(user().resolve("dev.example.gone/dev.example.gone.toml"));
            assertThat(user().resolve("dev.example.gone/dev.example.gone.toml")).as("seeded before opening").isRegularFile();
            assertThat(revealed).containsExactly(user().resolve("dev.example.gone"), user().resolve("dev.example.gone/data"));
            assertThat(user().resolve("dev.example.gone/data")).isDirectory();
        }
}
