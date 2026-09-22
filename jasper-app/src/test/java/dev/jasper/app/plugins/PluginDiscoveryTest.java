package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.PluginJars;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class PluginDiscoveryTest {
    @TempDir Path root;

    @Test void findsOneDescriptorJarPerDirectoryAndCollectsEveryJar() throws Exception {
        Path tool = root.resolve("dev.example.tool");
        PluginJars.build(tool, "tool.jar", PluginJars.descriptor("dev.example.tool", "1.0.0", "fix.tool.Main"),
            Map.of("fix.tool.Main", PluginJars.emptyPlugin("fix.tool", "Main")), List.of());
        PluginJars.build(tool, "library.jar", null, Map.of(), List.of());
        Files.writeString(root.resolve("README.txt"), "not a plugin");
        List<String> problems = new ArrayList<>();
        List<PluginCandidate> found = PluginDiscovery.scan(root, PluginCandidate.Origin.USER, problems);
        assertThat(problems).isEmpty();
        assertThat(found).singleElement().satisfies(candidate -> {
            assertThat(candidate.id()).isEqualTo("dev.example.tool");
            assertThat(candidate.origin()).isEqualTo(PluginCandidate.Origin.USER);
            assertThat(candidate.jars()).extracting(path -> path.getFileName().toString())
                .containsExactly("library.jar", "tool.jar");
        });
    }

    @Test void reportsDirectoriesItCannotUseAndKeepsGoing() throws Exception {
        PluginJars.build(root.resolve("dev.example.nodescriptor"), "a.jar", null, Map.of(), List.of());
        Path twice = root.resolve("dev.example.twice");
        PluginJars.build(twice, "a.jar", PluginJars.descriptor("dev.example.twice", "1.0.0", "x.A"), Map.of(), List.of());
        PluginJars.build(twice, "b.jar", PluginJars.descriptor("dev.example.twice", "1.0.0", "x.A"), Map.of(), List.of());
        PluginJars.build(root.resolve("dev.example.wrongname"), "a.jar",
            PluginJars.descriptor("dev.example.other", "1.0.0", "x.A"), Map.of(), List.of());
        PluginJars.build(root.resolve("dev.example.invalid"), "a.jar", "id = 7\n", Map.of(), List.of());
        PluginJars.build(root.resolve("dev.example.good"), "a.jar",
            PluginJars.descriptor("dev.example.good", "1.0.0", "x.A"), Map.of(), List.of());
        List<String> problems = new ArrayList<>();
        List<PluginCandidate> found = PluginDiscovery.scan(root, PluginCandidate.Origin.USER, problems);
        assertThat(found).extracting(PluginCandidate::id).containsExactly("dev.example.good");
        assertThat(problems).hasSize(4).anySatisfy(p -> assertThat(p).contains("dev.example.nodescriptor", "no plugin.toml"))
            .anySatisfy(p -> assertThat(p).contains("dev.example.twice", "more than one"))
            .anySatisfy(p -> assertThat(p).contains("dev.example.wrongname", "dev.example.other"))
            .anySatisfy(p -> assertThat(p).contains("dev.example.invalid", "id"));
    }

    @Test void aMissingRootIsEmptyAndADevelopmentDirectoryNeedNotBeNamedAfterItsId() throws Exception {
        List<String> problems = new ArrayList<>();
        assertThat(PluginDiscovery.scan(root.resolve("absent"), PluginCandidate.Origin.USER, problems)).isEmpty();
        Path dev = root.resolve("build-output");
        PluginJars.build(dev, "a.jar", PluginJars.descriptor("dev.example.dev", "1.0.0", "x.A"), Map.of(), List.of());
        assertThat(PluginDiscovery.single(dev, PluginCandidate.Origin.DEV, problems)).get()
            .extracting(PluginCandidate::id).isEqualTo("dev.example.dev");
        assertThat(problems).isEmpty();
    }

    @Test void dotDirectoriesBelongToTheApplicationAndAreNotPlugins() throws Exception {
        PluginJars.build(root.resolve(".pending").resolve("dev.example.tool"), "tool.jar",
            PluginJars.descriptor("dev.example.tool", "1.0.0", "fix.tool.Main"), Map.of(), List.of());
        Files.createDirectories(root.resolve(".staging-abc"));
        List<String> problems = new ArrayList<>();
        assertThat(PluginDiscovery.scan(root, PluginCandidate.Origin.USER, problems)).isEmpty();
        assertThat(problems).isEmpty();
    }

        @Test void anInstalledPluginKeepsItsJarsInAJarsSubdirectoryAndOtherFilesAreIgnored() throws Exception {
            Path directory = root.resolve("dev.example.tool");
            PluginJars.build(directory.resolve("jars"), "main.jar", PluginJars.descriptor("dev.example.tool", "1.0.0", "fix.Main"), Map.of(), List.of());
            Files.writeString(directory.resolve("dev.example.tool.toml"), "# settings\n");
            Files.createDirectories(directory.resolve("data"));
            Files.writeString(root.resolve("dev.example.other-1.0.0.zip"), "not read by discovery");
            List<String> problems = new ArrayList<>();
            List<PluginCandidate> found = PluginDiscovery.scan(root, PluginCandidate.Origin.USER, problems);
            assertThat(problems).isEmpty();
            assertThat(found).singleElement().satisfies(candidate -> {
                assertThat(candidate.id()).isEqualTo("dev.example.tool");
                assertThat(candidate.directory()).as("the plugin folder, not its jars directory").isEqualTo(directory);
                assertThat(candidate.jars()).containsExactly(directory.resolve("jars/main.jar"));
            });
            Path flat = root.resolve("flat");
            PluginJars.build(flat, "main.jar", PluginJars.descriptor("dev.example.flat", "1.0.0", "fix.Main"), Map.of(), List.of());
            assertThat(PluginDiscovery.single(flat, PluginCandidate.Origin.DEV, problems)).as("a --plugin-dir directory stays flat")
                .hasValueSatisfying(candidate -> assertThat(candidate.jars()).containsExactly(flat.resolve("main.jar")));
        }
}
