package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.testsupport.LookFixtures;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 10.11: the runtime names each plugin of the selection just before it loads, for the splash's steps. */
class PluginStartingStepsTest {
    @TempDir Path root;

    @Test void theRuntimeNamesEachSelectedPluginBeforeItLoads() throws Exception {
        Path dev = LookFixtures.plugin(root.resolve("dev"), "dev.example.tool", LookFixtures.descriptor("dev.example.tool", "Example Tool"), Map.of());
        var dirs = new AppDirs(root.resolve("home"), root.resolve("home/config.toml"), root.resolve("home/logs"));
        var selection = PluginSelection.of(dirs, null, "", dev, false);
        assertThat(selection.loading()).containsExactly("Example Tool");
        var named = new ArrayList<String>();
        var runtime = new AtomicReference<PluginRuntime>();
        SwingUtilities.invokeAndWait(() -> {
            runtime.set(new PluginRuntime(selection.options(), new dev.jasper.app.notifications.ActivityNotifier(
                new dev.jasper.buddy.view.BuddyTestSupport().companion(), () -> { }), PluginRuntime.SettingsReports.IGNORED,
                new dev.jasper.app.contributions.Contributions(), AppContractTest.headlessWindows(), new dev.jasper.app.terminals.TerminalRegistry()));
            runtime.get().starting(named::add);
            runtime.get().start(selection, Map.of(), true);
            assertThatThrownBy(() -> runtime.get().starting(name -> { })).isInstanceOf(IllegalStateException.class);
        });
        assertThat(named).as("named before loading, although it then fails to load: it has no classes").containsExactly("Example Tool");
        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        SwingUtilities.invokeAndWait(() -> pending.set(runtime.get().stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get();
    }
}
