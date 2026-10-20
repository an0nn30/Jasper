package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.EnterpriseResources;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Plan 15a Task 7: the shipped Enterprise kit's {@code [defaults."dev.jasper.editor"]} table reaches the Editor's key. */
class EnterpriseEditorDefaultTest {
    private static final String ID = "dev.jasper.editor";
    @TempDir Path data;
    private final List<ConfigDiagnostic> diagnostics = new CopyOnWriteArrayList<>();
    private final List<String> lookProblems = new CopyOnWriteArrayList<>();
    private PluginHost host;

    @BeforeEach void open() {
        host = AppContractTest.host(data, Duration.ofMillis(200), onEdtValue(Contributions::new), new AtomicBoolean(true),
            onEdtValue(AppContractTest::headlessWindows), onEdtValue(TerminalRegistry::new), new CopyOnWriteArrayList<>(),
            new CopyOnWriteArrayList<>(), diagnostics);
    }

    @AfterEach void stop() {
        var pending = new AtomicReference<List<CompletableFuture<?>>>(List.of());
        onEdt(() -> pending.set(host.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).join();
    }

    @Test void underEnterpriseTheEditorsFontIsPlexMono() {
        host.defaults = EnterpriseResources.facts().parsed().orElseThrow().defaults();
        host.lookDefaultProblem = lookProblems::add;
        var family = new AtomicReference<Setting<String>>();
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo(ID, "Editor", "0.1.0", Set.of()), Set.of(), Set.of(), Set.of(),
            EnterpriseEditorDefaultTest.class.getClassLoader(),
            () -> started -> family.set(started.settings().declare(SettingSpec.of("font_family", SettingKind.text(), "Monospaced").label("Font"))))));
        assertThat(host.active(ID)).as("started").isTrue();
        assertThat(family.get().get()).isEqualTo("IBM Plex Mono");
        assertThat(lookProblems).isEmpty();
    }
}
