package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.testsupport.LookFixtures;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 4.8: one pre-pass before Swing; its selection feeds the configuration and the runtime, with no second scan. */
class PluginSelectionTest {
    static final String KIT = "kit = 1\nbase = \"default\"\n[defaults]\n\"palette.visible_rows\" = 7\n";
    @TempDir Path root;

    AppDirs dirs() { return new AppDirs(root.resolve("home"), root.resolve("home/config.toml"), root.resolve("home/logs")); }

    Path provider() throws Exception {
        return LookFixtures.plugin(root.resolve("dev"), "dev.example.look",
            LookFixtures.descriptor("dev.example.look", "Example") + LookFixtures.provider("look.kit.toml"), Map.of("look.kit.toml", KIT));
    }

    @Test void theWinnersPresentationCarriesItsKitDefaultsAndAttempt() throws Exception {
        var selection = PluginSelection.of(dirs(), null, "/app/jasper-app.jar@1", provider(), false);
        assertThat(selection.presentation().look()).isEqualTo("dev.example.look");
        assertThat(selection.presentation().provider().orElseThrow().name()).isEqualTo("Example");
        assertThat(selection.presentation().defaults().value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(7);
        assertThat(selection.attempt().records()).isTrue();
        assertThat(selection.attempt().id()).isEqualTo("dev.example.look");
        assertThat(selection.build()).isEqualTo("/app/jasper-app.jar@1");
        assertThat(selection.candidates()).extracting(PluginCandidate::origin).containsExactly(PluginCandidate.Origin.DEV);
        assertThat(selection.skipped()).isEmpty();
    }

    @Test void safeModeAndNoProviderSelectTheStandardLook() throws Exception {
        var safe = PluginSelection.of(dirs(), null, "", provider(), true);
        assertThat(safe.presentation().look()).isEqualTo("default");
        assertThat(safe.attempt().records()).isFalse();
        assertThat(safe.resolution().rejected()).as("without reading its files, the provider is disabled for safe mode")
            .singleElement().satisfies(status -> {
                assertThat(status.state()).isEqualTo(PluginStatus.State.DISABLED);
                assertThat(status.reason()).isEqualTo(PluginStatus.SAFE_MODE_LOOK);
            });
        assertThat(PluginSelection.none().presentation().look()).isEqualTo("default");
        assertThat(PluginSelection.none().isNone()).isTrue();
        assertThat(PluginSelection.of(dirs(), null, "", null, false).presentation().provider()).isEmpty();
    }

    @Test void aSkippedLookIsNamedAndBlamingTheKitFallsBackKeepingTheDefaults() throws Exception {
        Path devPlugins = provider();
        var first = PluginSelection.of(dirs(), null, "B", devPlugins, false);
        first.attempt().pending();
        var again = PluginSelection.of(dirs(), null, "B", devPlugins, false);
        assertThat(again.presentation().look()).isEqualTo("default");
        assertThat(again.skipped()).hasValueSatisfying(skipped -> assertThat(skipped.name()).isEqualTo("Example"));
        again.skipped().orElseThrow().attempt().clear();
        var cleared = PluginSelection.of(dirs(), null, "B", devPlugins, false);
        assertThat(cleared.presentation().look()).as("Try Again").isEqualTo("dev.example.look");
        var blamed = cleared.blamed();
        assertThat(blamed.presentation().look()).isEqualTo("default");
        assertThat(blamed.presentation().kit()).isSameAs(dev.jasper.ui.kit.Kit.DEFAULT);
        assertThat(blamed.presentation().defaults()).isSameAs(cleared.presentation().defaults());
        assertThat(blamed.resolution().load()).extracting(PluginCandidate::id).doesNotContain("dev.example.look");
        assertThat(blamed.resolution().rejected()).anySatisfy(status -> assertThat(status.reason()).isEqualTo(PluginStatus.LOOK_FAILED));
        assertThat(blamed.attempt()).isSameAs(cleared.attempt());
    }

    @Test void theRuntimeStartsWhatTheSelectionChoseWithoutAnotherScan() throws Exception {
        Path devPlugins = provider();
        var selection = PluginSelection.of(dirs(), null, "", devPlugins, false);
        PluginMaintenance.deleteRecursively(devPlugins);
        var runtime = new AtomicReference<PluginRuntime>();
        SwingUtilities.invokeAndWait(() -> {
            runtime.set(new PluginRuntime(selection.options(), new dev.jasper.app.notifications.ActivityNotifier(
                new dev.jasper.buddy.view.BuddyTestSupport().companion(), () -> { }), PluginRuntime.SettingsReports.IGNORED,
                new dev.jasper.app.contributions.Contributions(), AppContractTest.headlessWindows(), new dev.jasper.app.terminals.TerminalRegistry()));
            runtime.get().start(selection, Map.of(), true);
        });
        assertThat(runtime.get().statusLines()).as("the deleted plugin is still the selection's").singleElement()
            .satisfies(line -> assertThat(line).contains("dev.example.look", "[DEV]"));
        var pending = new AtomicReference<List<java.util.concurrent.CompletableFuture<?>>>();
        SwingUtilities.invokeAndWait(() -> pending.set(runtime.get().stop()));
        java.util.concurrent.CompletableFuture.allOf(pending.get().toArray(java.util.concurrent.CompletableFuture[]::new)).get();
    }
}
