package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.PluginJars;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginInstallerTest {
    private static final Version SDK = Version.parse("0.3.0");
    @TempDir Path root;

    private Path user() { return root.resolve("plugins"); }
    private PluginStateStore store() {
        return new PluginStateStore(root.resolve("plugins.toml"), root.resolve("plugins.lock"), Duration.ofSeconds(2));
    }

    /** Builds the jars of one plugin and returns their bytes by file name. */
    private Map<String, byte[]> jars(String id, String version, String extraToml) throws Exception {
        Path built = Files.createTempDirectory(root, "built");
        PluginJars.build(built, "main.jar", PluginJars.descriptor(id, version, "fix.Main") + extraToml, Map.of(), List.of());
        PluginJars.build(built, "library.jar", null, Map.of(), List.of());
        Map<String, byte[]> result = new LinkedHashMap<>();
        result.put("main.jar", Files.readAllBytes(built.resolve("main.jar")));
        result.put("library.jar", Files.readAllBytes(built.resolve("library.jar")));
        return result;
    }

    private Path zip(String name, Map<String, byte[]> entries) throws Exception {
        Path file = root.resolve(name);
        try (var out = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return file;
    }

    private static Map<String, byte[]> under(String prefix, Map<String, byte[]> jars) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        jars.forEach((name, bytes) -> result.put(prefix + name, bytes));
        return result;
    }

    private long stagingDirectories() throws Exception {
        if (!Files.isDirectory(user())) return 0;
        try (var all = Files.list(user())) {
            return all.filter(path -> path.getFileName().toString().startsWith(".staging-")).count();
        }
    }

    @Test void stagesJarsFromTheRootOrFromOneFolderAndIgnoresEverythingElse() throws Exception {
        Map<String, byte[]> atRoot = new LinkedHashMap<>(jars("dev.example.tool", "1.2.0", "capabilities = [\"terminal.observe\"]\n"));
        atRoot.put("README.md", "read me".getBytes());
        atRoot.put("__MACOSX/._main.jar", new byte[]{1});
        var staged = PluginInstaller.stage(zip("root.zip", atRoot), user(), SDK);
        assertThat(staged.candidate().id()).isEqualTo("dev.example.tool");
        assertThat(staged.candidate().descriptor().capabilities()).containsExactly("terminal.observe");
        assertThat(staged.candidate().jars()).extracting(path -> path.getFileName().toString()).containsExactly("library.jar", "main.jar");
        assertThat(staged.directory().getParent()).isEqualTo(user());
        PluginInstaller.discard(staged.directory());

        var folder = PluginInstaller.stage(zip("folder.zip", under("dev.example.tool/", jars("dev.example.tool", "1.2.0", ""))), user(), SDK);
        assertThat(folder.candidate().jars()).hasSize(2);
        PluginInstaller.discard(folder.directory());
        assertThat(stagingDirectories()).isZero();
    }

    @Test void rejectsUnsafeOrAmbiguousZipsAndLeavesNoStagingBehind() throws Exception {
        Map<String, byte[]> good = jars("dev.example.tool", "1.0.0", "");
        assertThatThrownBy(() -> PluginInstaller.stage(zip("deep.zip", under("a/b/", good)), user(), SDK))
            .isInstanceOf(PluginInstaller.InstallFailure.class).hasMessageContaining("at its root or in one folder");
        Map<String, byte[]> two = new LinkedHashMap<>(under("a/", good));
        two.put("b/other.jar", good.get("library.jar"));
        assertThatThrownBy(() -> PluginInstaller.stage(zip("two.zip", two), user(), SDK))
            .isInstanceOf(PluginInstaller.InstallFailure.class).hasMessageContaining("at its root or in one folder");
        assertThatThrownBy(() -> PluginInstaller.stage(zip("escape.zip", Map.of("../evil.jar", good.get("main.jar"))), user(), SDK))
            .isInstanceOf(PluginInstaller.InstallFailure.class).hasMessageContaining("unsafe");
        assertThatThrownBy(() -> PluginInstaller.stage(zip("odd.zip", Map.of("bad:name.jar", good.get("main.jar"))), user(), SDK))
            .isInstanceOf(PluginInstaller.InstallFailure.class).hasMessageContaining("unsafe");
        assertThatThrownBy(() -> PluginInstaller.stage(zip("empty.zip", Map.of("README.md", new byte[]{1})), user(), SDK))
            .isInstanceOf(PluginInstaller.InstallFailure.class).hasMessageContaining("no jar");
        assertThatThrownBy(() -> PluginInstaller.stage(zip("library.zip", Map.of("library.jar", good.get("library.jar"))), user(), SDK))
            .isInstanceOf(PluginInstaller.InstallFailure.class).hasMessageContaining("Not a Jasper plugin").hasMessageContaining("no plugin.toml");
        Files.writeString(root.resolve("text.zip"), "this is not a zip");
        assertThatThrownBy(() -> PluginInstaller.stage(root.resolve("text.zip"), user(), SDK))
            .isInstanceOf(PluginInstaller.InstallFailure.class).hasMessageContaining("could not be read");
        assertThat(root.getParent().resolve("evil.jar")).doesNotExist();
        assertThat(stagingDirectories()).isZero();
    }

    @Test void rejectsAPluginThatNeedsAnotherSdk() throws Exception {
        Path built = Files.createTempDirectory(root, "future");
        PluginJars.build(built, "main.jar", PluginJars.descriptor("dev.example.future", "1.0.0", "fix.Main")
            .replace("sdk = \">=0.1\"", "sdk = \">=9.0\""), Map.of(), List.of());
        Path file = zip("future.zip", Map.of("main.jar", Files.readAllBytes(built.resolve("main.jar"))));
        assertThatThrownBy(() -> PluginInstaller.stage(file, user(), SDK))
            .isInstanceOf(PluginInstaller.InstallFailure.class).hasMessageContaining(">=9.0").hasMessageContaining("0.3.0");
        assertThat(stagingDirectories()).isZero();
    }

    @Test void committingMovesToPendingAndRecordsConsentAndLaunchMaintenanceInstallsIt() throws Exception {
        var staged = PluginInstaller.stage(zip("tool.zip", jars("dev.example.tool", "1.2.0", "capabilities = [\"terminal.inject\"]\n")), user(), SDK);
        PluginInstaller.commit(staged, user(), store());
        assertThat(staged.directory()).doesNotExist();
        assertThat(user().resolve(".pending").resolve("dev.example.tool").resolve("main.jar")).exists();
        assertThat(store().read().get("dev.example.tool")).isEqualTo(new PluginStateStore.Entry(true, Set.of("terminal.inject"), false));
        assertThat(user().resolve("dev.example.tool")).as("not installed until the next launch").doesNotExist();
        PluginMaintenance.apply(user(), store());
        assertThat(user().resolve("dev.example.tool").resolve("main.jar")).exists();
    }

    @Test void discardingAPendingInstallForgetsAConsentThatHasNothingInstalledBehindIt() throws Exception {
        PluginInstaller.commit(PluginInstaller.stage(zip("fresh.zip", jars("dev.example.fresh", "1.0.0", "")), user(), SDK), user(), store());
        PluginInstaller.discardPending("dev.example.fresh", user(), store());
        assertThat(user().resolve(".pending").resolve("dev.example.fresh")).doesNotExist();
        assertThat(store().read()).isEmpty();

        Files.createDirectories(user().resolve("dev.example.tool"));
        PluginInstaller.commit(PluginInstaller.stage(zip("update.zip", jars("dev.example.tool", "2.0.0", "")), user(), SDK), user(), store());
        PluginInstaller.discardPending("dev.example.tool", user(), store());
        assertThat(store().read()).as("the installed version keeps its entry").containsKey("dev.example.tool");
    }
}
