package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.sdk.plugin.Plugin;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class PluginLoaderTest {
    @TempDir Path root;
    private final ClassLoader sdk = Plugin.class.getClassLoader();

    private PluginCandidate candidate(String id, String descriptorExtra, Map<String, String> sources, List<Path> classpath) throws Exception {
        String entry = sources.keySet().stream().filter(name -> name.endsWith(".Main")).findFirst().orElse("x.Main");
        PluginJars.build(root.resolve(id), id + ".jar", PluginJars.descriptor(id, "1.0.0", entry) + descriptorExtra, sources, classpath);
        List<String> problems = new ArrayList<>();
        PluginCandidate found = PluginDiscovery.single(root.resolve(id), PluginCandidate.Origin.DEV, problems).orElseThrow();
        assertThat(problems).isEmpty();
        return found;
    }

    @Test void instantiatesTheEntryClassInItsOwnLoaderAndSharesTheSdk() throws Exception {
        var loaded = PluginLoader.load(candidate("fix.tool", "", Map.of("fix.tool.Main", PluginJars.emptyPlugin("fix.tool", "Main")), List.of()), Map.of(), sdk);
        try (var loader = loaded.loader()) {
            Plugin plugin = loaded.instantiate();
            assertThat(plugin.getClass().getClassLoader()).isSameAs(loader);
            assertThat(plugin.getClass().getName()).isEqualTo("fix.tool.Main");
            assertThat(loader.loadClass("dev.jasper.sdk.plugin.Plugin")).isSameAs(Plugin.class);
        }
    }

    @Test void hidesTheApplicationAndItsLibraries() throws Exception {
        var loaded = PluginLoader.load(candidate("fix.tool", "", Map.of("fix.tool.Main", PluginJars.emptyPlugin("fix.tool", "Main")), List.of()), Map.of(), sdk);
        try (var loader = loaded.loader()) {
            for (String hidden : List.of("dev.jasper.app.Main", "dev.jasper.app.plugins.PluginRuntime",
                    "dev.jasper.terminal.session.TerminalSession", "dev.jasper.buddy.view.BuddyCompanion", "org.tomlj.Toml"))
                assertThatThrownBy(() -> loader.loadClass(hidden)).as(hidden).isInstanceOf(ClassNotFoundException.class);
            assertThat(loader.getResource("dev/jasper/app/Main.class")).isNull();
            assertThat(loader.loadClass("javax.swing.JPanel")).isNotNull();
        }
    }

    @Test void importsOnlyTheExportedPackagesOfDeclaredDependencies() throws Exception {
        var vaultCandidate = candidate("fix.vault", "exports = [\"fix.vault.api\"]\n", Map.of(
            "fix.vault.Main", PluginJars.emptyPlugin("fix.vault", "Main"),
            "fix.vault.api.Api", "package fix.vault.api;\npublic interface Api { String secret(); }\n",
            "fix.vault.impl.Secret", "package fix.vault.impl;\npublic final class Secret { }\n"), List.of());
        var vault = PluginLoader.load(vaultCandidate, Map.of(), sdk);
        var sshCandidate = candidate("fix.ssh", "[[requires]]\nid = \"fix.vault\"\n", Map.of(
            "fix.ssh.Main", "package fix.ssh;\npublic final class Main implements dev.jasper.sdk.plugin.Plugin {\n"
                + "    public static Class<?> api() { return fix.vault.api.Api.class; }\n"
                + "    @Override public void start(dev.jasper.sdk.plugin.PluginContext context) { }\n}\n"),
            vaultCandidate.jars());
        var ssh = PluginLoader.load(sshCandidate, Map.of("fix.vault", vault), sdk);
        try (var vaultLoader = vault.loader(); var sshLoader = ssh.loader()) {
            Class<?> seenBySsh = (Class<?>) ssh.instantiate().getClass().getMethod("api").invoke(null);
            assertThat(seenBySsh).isSameAs(vaultLoader.loadClass("fix.vault.api.Api"));
            assertThatThrownBy(() -> sshLoader.loadClass("fix.vault.impl.Secret")).isInstanceOf(ClassNotFoundException.class);
            assertThatThrownBy(() -> sshLoader.loadClass("fix.vault.Main")).isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test void rejectsPluginsThatDefineReservedOrImportedPackages() throws Exception {
        var intruder = candidate("fix.intruder", "", Map.of(
            "fix.intruder.Main", PluginJars.emptyPlugin("fix.intruder", "Main"),
            "dev.jasper.sdk.evil.Shadow", "package dev.jasper.sdk.evil;\npublic final class Shadow { }\n"), List.of());
        assertThatThrownBy(() -> PluginLoader.load(intruder, Map.of(), sdk))
            .isInstanceOf(PluginLoader.LoadFailure.class).hasMessageContaining("dev.jasper.sdk.evil");

        var vaultCandidate = candidate("fix.vault", "exports = [\"fix.vault.api\"]\n", Map.of(
            "fix.vault.Main", PluginJars.emptyPlugin("fix.vault", "Main"),
            "fix.vault.api.Api", "package fix.vault.api;\npublic interface Api { }\n"), List.of());
        var vault = PluginLoader.load(vaultCandidate, Map.of(), sdk);
        try (var vaultLoader = vault.loader()) {
            var squatter = candidate("fix.squatter", "[[requires]]\nid = \"fix.vault\"\n", Map.of(
                "fix.squatter.Main", PluginJars.emptyPlugin("fix.squatter", "Main"),
                "fix.vault.api.Fake", "package fix.vault.api;\npublic final class Fake { }\n"), List.of());
            assertThatThrownBy(() -> PluginLoader.load(squatter, Map.of("fix.vault", vault), sdk))
                .isInstanceOf(PluginLoader.LoadFailure.class).hasMessageContaining("fix.vault.api");
        }
    }

    @Test void reportsAnEntryClassThatIsMissingOrNotAPlugin() throws Exception {
        var missing = PluginLoader.load(candidate("fix.missing", "", Map.of(), List.of()), Map.of(), sdk);
        try (var loader = missing.loader()) {
            assertThatThrownBy(missing::instantiate).isInstanceOf(PluginLoader.LoadFailure.class).hasMessageContaining("x.Main");
        }
        var wrong = PluginLoader.load(candidate("fix.wrong", "", Map.of("fix.wrong.Main",
            "package fix.wrong;\npublic final class Main { }\n"), List.of()), Map.of(), sdk);
        try (var loader = wrong.loader()) {
            assertThatThrownBy(wrong::instantiate).isInstanceOf(PluginLoader.LoadFailure.class).hasMessageContaining("Plugin");
        }
    }
}
