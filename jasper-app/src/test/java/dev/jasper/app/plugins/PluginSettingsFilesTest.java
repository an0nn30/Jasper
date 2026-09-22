package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.PluginJars;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class PluginSettingsFilesTest {
    @TempDir Path root;
    private Path user() { return root.resolve("plugins"); }

    private PluginCandidate candidate(String id, String templateOrNull) throws Exception {
        Path directory = root.resolve("build").resolve(id);
        PluginJars.build(directory, "main.jar", PluginJars.descriptor(id, "1.0.0", "fix.Main"), Map.of(), List.of());
        if (templateOrNull != null) PluginJars.addResource(directory.resolve("main.jar"), "settings.toml", templateOrNull);
        List<String> problems = new ArrayList<>();
        return PluginDiscovery.single(directory, PluginCandidate.Origin.DEV, problems).orElseThrow(() -> new AssertionError(problems));
    }

    @Test void prepareSeedsFromTheOldTableThenTheTemplateThenAHeaderAndNeverRewrites() throws Exception {
        PluginCandidate withTemplate = candidate("dev.example.templated", "# example\n# greeting = \"hi\"\n");
        PluginCandidate plain = candidate("dev.example.plain", null);
        PluginSettingsFiles.prepare(user(), withTemplate, Map.of("greeting", "from config", "count", 2L));
        PluginSettingsFiles.prepare(user(), plain, null);
        Path templated = PluginSettingsFiles.file(user(), "dev.example.templated");
        assertThat(templated).hasParent(user().resolve("dev.example.templated"));
        assertThat(Files.readString(templated)).startsWith("# Settings for dev.example.templated 1.0.0").contains("greeting = \"from config\"", "count = 2");
        assertThat(PluginSettingsFiles.data(user(), "dev.example.templated")).isDirectory();
        assertThat(Files.readString(PluginSettingsFiles.file(user(), "dev.example.plain"))).startsWith("# Settings for dev.example.plain 1.0.0").contains("dev.example.plain");
        Files.delete(templated);
        PluginSettingsFiles.prepare(user(), withTemplate, Map.of());
        assertThat(Files.readString(templated)).as("an empty old table is no seed; the template is").isEqualTo("# example\n# greeting = \"hi\"\n");
        Files.writeString(templated, "greeting = \"mine\"\n");
        PluginSettingsFiles.prepare(user(), withTemplate, Map.of("greeting", "from config"));
        assertThat(Files.readString(templated)).as("never rewritten").isEqualTo("greeting = \"mine\"\n");
    }

    @Test void readsPollsAndKeepsTheLastGoodValuesWhenTheFileBreaks() throws Exception {
        List<String> reports = new ArrayList<>();
        var files = new PluginSettingsFiles(user(), (key, message) -> reports.add(key + ": " + message));
        Path file = PluginSettingsFiles.file(user(), "dev.example.tool");
        assertThat(files.current("dev.example.tool")).as("no file yet").isEmpty();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "greeting = \"hi\"\n[proxy]\nport = 22\n");
        assertThat(files.refresh(false)).containsExactly("dev.example.tool");
        assertThat(files.current("dev.example.tool")).containsEntry("greeting", "hi").containsEntry("proxy", Map.of("port", 22L));
        assertThat(files.refresh(false)).as("unchanged file, no change").isEmpty();
        Files.writeString(file, "greeting = \"hi\"\n[proxy\n");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
        assertThat(files.refresh(false)).isEmpty();
        assertThat(files.current("dev.example.tool")).as("last good kept").containsEntry("greeting", "hi");
        assertThat(reports).singleElement().asString().startsWith("plugins.dev.example.tool: ").contains(file.getFileName().toString());
        Files.writeString(file, "greeting = \"again\"\n");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 10000));
        assertThat(files.refresh(true)).containsExactly("dev.example.tool");
        assertThat(files.current("dev.example.tool")).containsEntry("greeting", "again").doesNotContainKey("proxy");
    }
}
