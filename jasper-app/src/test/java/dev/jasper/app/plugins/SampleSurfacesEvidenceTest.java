package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.app.windows.DialogFit;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.awt.Component;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static dev.jasper.app.plugins.EditorEvidence.bounds;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 3 Task 8: the real staged Sample plugin's panel, About tab and details sheet through the plugin runtime with
 * the application's appearance wiring, at device scale 2 in dark and light: the panel body and the About tab are the
 * kit's centered empty state with one secondary action, and the details sheet is a form card with one primary.
 */
class SampleSurfacesEvidenceTest {
    @TempDir Path root;

    @Test @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void samplePanelAboutTabAndSheetUseTheCanonicalComponents() throws Exception {
        var holders = new java.util.LinkedHashMap<AuxiliarySurface, StagedRemoteHost.PackedDialog>();
        var themes = onEdtValue(() -> ThemeTestSupport.controller(true));
        var contributions = onEdtValue(Contributions::new);
        var runtime = new AtomicReference<PluginRuntime>();
        // The main window's workspace, where the About tab opens (workspace tabs plan 4b).
        var terminals = onEdtValue(TerminalFixture::new);
        var workspace = onEdtValue(() -> new javax.swing.JPanel(new java.awt.BorderLayout()));
        UUID main = onEdtValue(() -> {
            UUID id = terminals.addMainWindow();
            contributions.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(id,
                tab -> workspace.add(tab.component()), tab -> workspace.remove(tab.component()), tab -> tab.active(true), () -> { }));
            return id;
        });
        AuxiliaryWindows windows = onEdtValue(() -> new AuxiliaryWindows(dev.jasper.app.persistence.UiState.inMemory(), surface -> {
            if (surface.kind() == AuxiliarySurface.Kind.DIALOG && surface.id().isEmpty()) {
                var dialog = new StagedRemoteHost.PackedDialog(surface.holder());
                dialog.fit = new DialogFit(surface.holder(), dialog); surface.fitWith(dialog.fit); holders.put(surface, dialog);
                return new AuxiliarySurface.Shell(() -> { dialog.bounds.setSize(dialog.packedSize()); dialog.fit.packed(); }, () -> { }, () -> { },
                    title -> { }, () -> dialog.bounds, (title, initial) -> Optional.empty());
            }
            return new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { }, () -> new java.awt.Rectangle(0, 0, 360, 200), (title, initial) -> Optional.empty());
        }));
        try {
            onEdt(() -> {
                runtime.set(new PluginRuntime(new PluginRuntime.Options(Path.of(System.getProperty("jasper.stagedPlugins")), root.resolve("user"), null, false,
                    root.resolve("plugins.toml"), root.resolve("plugins.lock")), new dev.jasper.app.notifications.ActivityNotifier(new BuddyTestSupport().companion(), () -> { }),
                    PluginRuntime.SettingsReports.IGNORED, contributions, windows, terminals.registry));
                runtime.get().appearance(themes::current, handler -> themes.subscribe((value, changed) -> handler.accept(value)));
                runtime.get().start(Map.of("dev.jasper.sample", Map.<String, Object>of("demo_ui", true, "demo_step_millis", 0L)), true);
            });
            for (String theme : List.of("dark", "light")) {
                onEdt(() -> themes.configure(theme.equals("dark"), dev.jasper.app.config.TerminalSchemeChoice.MATCH));
                onEdt(() -> {
                    var entry = contributions.panels().stream().filter(panel -> panel.id().equals("dev.jasper.sample.panel")).findFirst().orElseThrow();
                    var panel = (JComponent) entry.factory().apply(new PanelSite(UUID.randomUUID(), () -> { }, () -> { }, () -> true));
                    EditorEvidence.capture(theme, "sample-panel", panel, 260, 420, "sample.panel", "sample.body", "sample.icon", "sample.run");
                    var run = WorkspaceContentRouteTest.named(panel, "sample.run", AbstractButton.class);
                    var body = bounds(panel, WorkspaceContentRouteTest.named(panel, "sample.body", Component.class));
                    var button = bounds(panel, run);
                    assertThat(Math.abs(button.x + button.width / 2 - (body.x + body.width / 2))).as("the action is centered in the body").isLessThanOrEqualTo(1);
                    contributions.action("dev.jasper.sample.about").orElseThrow().invoke(new Contributions.Invocation(main, Optional.empty()));
                });
                var about = onEdtValue(() -> contributions.tabs().hosted().stream()
                    .filter(tab -> tab.title().equals("About Sample") && !tab.closed()).findFirst().orElseThrow());
                onEdt(() -> {
                    EditorEvidence.capture(theme, "sample-about", about.component(), 360, 200, "sample.about", "sample.about.icon", "sample.about.details");
                    WorkspaceContentRouteTest.named(about.component(), "sample.about.details", AbstractButton.class).doClick();
                });
                var details = await(windows, "Sample Details");
                onEdt(() -> {
                    var size = holders.get(details).size();
                    EditorEvidence.capture(theme, "sample-details", details.holder(), size.width, size.height, "sample.details.card", "sample.version.row", "sample.close");
                    details.requestClose(); about.close();
                });
            }
        } finally {
            var done = new CompletableFuture<List<CompletableFuture<?>>>();
            onEdt(() -> done.complete(runtime.get() == null ? List.of() : runtime.get().stop()));
            CompletableFuture.allOf(done.get().toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            onEdt(() -> { windows.close(); themes.close(); ThemeTestSupport.controller(true); });
        }
    }

    private static AuxiliarySurface await(AuxiliaryWindows windows, String title) throws InterruptedException {
        StagedVaultHost.await(() -> windows.open().stream().anyMatch(surface -> surface.title().equals(title) && !surface.closed()), title);
        return onEdtValue(() -> windows.open().stream().filter(surface -> surface.title().equals(title) && !surface.closed()).findFirst().orElseThrow());
    }
}
