package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.app.workspace.WindowContent;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.ui.swing.KitView;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static dev.jasper.app.plugins.StagedRemoteHost.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Headless real-host acceptance for the staged File Server panel, borrowed header and editor. */
class FileServerRenderEvidenceTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.fileserver", "File Server", "0.4.0",
        Set.of(Capabilities.NETWORK_LISTEN, Capabilities.TERMINAL_OBSERVE, Capabilities.PALETTE_CONTRIBUTE));
    @TempDir Path root;

    @Test @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void stagedPanelBorrowsItsHeaderAndEditorKeepsNoEnterSubmission() throws Exception {
        for (String theme : List.of("dark", "light")) capture(theme);
    }

    private void capture(String theme) throws Exception {
        var controller = onEdtValue(dev.jasper.app.appearance.ThemeController::new);
        onEdt(() -> controller.configure(theme.equals("dark") ? dev.jasper.app.appearance.Theme.DARK : dev.jasper.app.appearance.Theme.JASPER_LIGHT));
        Path data = Files.createDirectories(root.resolve(theme));
        Path home = Files.createDirectories(data.resolve("synthetic-home"));
        var contributions = onEdtValue(Contributions::new);
        var auxiliary = onEdtValue(AppContractTest::headlessWindows);
        var terminals = onEdtValue(TerminalFixture::new);
        var problems = new CopyOnWriteArrayList<String>();
        var host = AppContractTest.host(data.resolve("plugins"), Duration.ofMillis(200), contributions, new AtomicBoolean(theme.equals("dark")),
            auxiliary, terminals.registry, problems, new CopyOnWriteArrayList<>());
        Path staged = Path.of(System.getProperty("jasper.stagedPlugins")).resolve(INFO.id());
        try (var loader = new PluginClassLoader(INFO.id(), ManagedVaultLoadingTest.jars(staged), getClass().getClassLoader(), Map.of())) {
            var pluginClass = loader.loadClass("dev.jasper.fileserver.FileServerPlugin");
            var environmentClass = loader.loadClass("dev.jasper.fileserver.FileServerPlugin$Environment");
            var environment = environmentClass.getDeclaredConstructor(Executor.class, Supplier.class, IntPredicate.class,
                Consumer.class, Consumer.class, Path.class, String.class, java.time.ZoneId.class, BiFunction.class);
            environment.setAccessible(true);
            Object settings = environment.newInstance((Executor) SwingUtilities::invokeLater, (Supplier<List<?>>) List::of,
                (IntPredicate) port -> true, (Consumer<String>) text -> { }, (Consumer<Path>) path -> { }, home,
                "Mac OS X", ZoneOffset.UTC, (BiFunction<Duration, Runnable, Runnable>) (delay, task) -> () -> { });
            var constructor = pluginClass.getDeclaredConstructor(environmentClass); constructor.setAccessible(true);
            Plugin plugin = (Plugin) constructor.newInstance(settings);
            onEdt(() -> host.start(new HostedPlugin(INFO, Set.of(), Set.of("dev.jasper.vault"), Set.of(), loader, () -> plugin)));
            WindowContent window = onEdtValue(() -> {
                var content = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
                content.connectTerminals(terminals.registry, () -> { });
                content.connectContributions(contributions);
                return content;
            });
            try {
                onEdt(() -> contributions.requestPanel(new Contributions.PanelRequest(window.id(), "dev.jasper.fileserver.panel",
                    Contributions.PanelRequest.Op.TOGGLE)));
                StagedVaultHost.await(() -> find(window, "fileserver.panel") != null, "File Server panel mounted");
                onEdt(() -> {
                    KitView panel = find(window, "fileserver.panel");
                    assertThat(panel).isNotNull();
                    var header = panel.panelHeader("fileserver.panel");
                    assertThat(labels(header)).contains("File Servers");
                    assertThat(names(header)).contains("New File Server…", "Options", "Hide");
                    assertThat(labels(window).stream().filter("File Servers"::equals).count()).isEqualTo(1);
                    ThemeMigrationCapture.save("fileserver-hosted", theme, "empty", panel, 540, 420);
                    button(header, "New File Server…").doClick(0);
                });
                StagedVaultHost.await(() -> auxiliary.open().stream().anyMatch(surface -> surface.title().equals("New File Server") && !surface.closed()),
                    "editor dialog");
                var editor = onEdtValue(() -> auxiliary.open().stream().filter(surface -> surface.title().equals("New File Server") && !surface.closed()).findFirst().orElseThrow());
                onEdt(() -> {
                    ThemeMigrationCapture.save("fileserver-hosted", theme, "new-server", editor.holder(), 820, 650);
                    ((JTextComponent) StagedVaultHost.named(editor.holder(), "server.name.input")).setText("Fixture");
                    ((JTextComponent) StagedVaultHost.named(editor.holder(), "server.folder.input")).setText(home.toString());
                    press(StagedVaultHost.named(editor.holder(), "server.name.input"), KeyEvent.VK_D, 'd');
                    press(StagedVaultHost.named(editor.holder(), "server.name.input"), KeyEvent.VK_ENTER, '\n');
                    assertThat(editor.closed()).as("Enter on the old editor had no default button").isFalse();
                    StagedVaultHost.named(editor.holder(), "server.save");
                    ((javax.swing.AbstractButton) StagedVaultHost.named(editor.holder(), "server.save")).doClick(0);
                });
                StagedVaultHost.await(editor::closed, "server saved");
                StagedVaultHost.await(() -> labels(find(window, "fileserver.panel").panelHeader("fileserver.panel")).contains("1"), "borrowed header count");
                onEdt(() -> {
                    KitView panel = find(window, "fileserver.panel");
                    ThemeMigrationCapture.save("fileserver-hosted", theme, "one-server", panel, 540, 500);
                    var row = firstRow(panel);
                    assertThat(row).as("a mounted File Server row").isNotNull();
                    StagedRemoteTunnelsTest.click(row, 1);
                    var canvas = StagedRemoteTunnelsTest.canvas(row);
                    press(canvas, KeyEvent.VK_D, 'd');
                    assertThat(StagedVaultHost.named(panel, "fileserver.details").isVisible()).isTrue();
                    press(canvas, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
                    assertThat(StagedVaultHost.named(panel, "fileserver.details").isVisible()).isFalse();
                });
                assertThat(problems).isEmpty();
            } finally {
                var pending = onEdtValue(host::stop);
                CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
                onEdt(auxiliary::close);
                DesktopTestSupport.closeOwners();
            }
        } finally { onEdt(() -> { controller.close(); new dev.jasper.app.appearance.ThemeController(); }); }
    }

    private static java.awt.Component firstRow(Container root) {
        for (var child : root.getComponents()) {
            if (child.getClass().getName().endsWith("InteractiveRows$Row")) return child;
            if (child instanceof Container nested) { var found = firstRow(nested); if (found != null) return found; }
        }
        return null;
    }

    private static KitView find(Container root, String id) {
        for (var child : root.getComponents()) {
            if (child instanceof KitView view && !view.isClosed() && view.spec().root().id().equals(id)) return view;
            if (child instanceof Container nested) { var found = find(nested, id); if (found != null) return found; }
        }
        return null;
    }
}
