package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginIconFilesTest {
    static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 16 16\">"
        + "<rect width=\"16\" height=\"16\" rx=\"3\" fill=\"#3574F0\"/></svg>";
    @TempDir Path root;

    private PluginCandidate candidate(String id, String extraToml) throws Exception {
        String toml = PluginJars.descriptor(id, "1.0.0", "fix.Main") + extraToml;
        Path directory = root.resolve(id);
        Path jar = PluginJars.build(directory, "main.jar", toml, Map.of(), List.of());
        return new PluginCandidate(DescriptorParser.parse(toml), directory, List.of(jar), PluginCandidate.Origin.USER);
    }

    @Test void readsTheNamedIconOnceAndGivesNoneWhenMissingOrTooLarge() throws Exception {
        var withIcon = candidate("dev.example.icon", "icon = \"icons/tool.svg\"\n");
        PluginJars.addResource(withIcon.jars().get(0), "icons/tool.svg", SVG);
        int before = PluginIconFiles.reads();
        var icon = PluginIconFiles.read(withIcon).orElseThrow();
        assertThat(icon.svg()).isTrue();
        assertThat(new String(icon.data(), StandardCharsets.UTF_8)).isEqualTo(SVG);
        assertThat(PluginIconFiles.read(withIcon)).contains(icon);
        assertThat(PluginIconFiles.reads() - before).as("read once per plugin version").isEqualTo(1);

        assertThat(PluginIconFiles.read(candidate("dev.example.missing", "icon = \"icons/none.png\"\n"))).isEmpty();
        var large = candidate("dev.example.large", "icon = \"icons/big.svg\"\n");
        PluginJars.addResource(large.jars().get(0), "icons/big.svg", "x".repeat(PluginIconFiles.MAX_BYTES + 1));
        assertThat(PluginIconFiles.read(large)).isEmpty();
        assertThat(PluginIconFiles.read(candidate("dev.example.plain", ""))).isEmpty();
    }

    @Test void theRuntimesRowsCarryTheIconTheHomepageAndTheExports() throws Exception {
        Path user = root.resolve("user");
        Path jar = PluginJars.build(user.resolve("dev.example.shown/jars"), "main.jar",
            PluginJars.descriptor("dev.example.shown", "1.0.0", "fix.Main")
                + "icon = \"icons/shown.svg\"\nhomepage = \"https://example.dev/shown\"\nexports = [\"dev.example.shown.api\", \"dev.example.shown.a\"]\n",
            Map.of(), List.of());
        PluginJars.addResource(jar, "icons/shown.svg", SVG);
        var runtime = new AtomicReference<PluginRuntime>();
        onEdt(() -> {
            runtime.set(new PluginRuntime(new PluginRuntime.Options(null, user, null, false, root.resolve("plugins.toml"),
                root.resolve("plugins.lock")), new ActivityNotifier(new BuddyTestSupport().companion(), () -> { }),
                PluginRuntime.SettingsReports.IGNORED, new dev.jasper.app.contributions.Contributions(),
                AppContractTest.headlessWindows(), new dev.jasper.app.terminals.TerminalRegistry()));
            runtime.get().start(Map.of(), true);
        });
        try {
            var outcome = new CompletableFuture<PluginRuntime.Outcome>();
            onEdt(() -> runtime.get().snapshot(outcome::complete));
            var row = outcome.get(10, TimeUnit.SECONDS).snapshot().rows().stream()
                .filter(candidate -> candidate.id().equals("dev.example.shown")).findFirst().orElseThrow();
            assertThat(row.homepage()).isEqualTo("https://example.dev/shown");
            assertThat(row.exports()).containsExactly("dev.example.shown.a", "dev.example.shown.api");
            assertThat(row.icon()).hasValueSatisfying(icon -> assertThat(icon.svg()).isTrue());
        } finally {
            var pending = new AtomicReference<List<CompletableFuture<?>>>();
            onEdt(() -> pending.set(runtime.get().stop()));
            CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        }
    }
}
