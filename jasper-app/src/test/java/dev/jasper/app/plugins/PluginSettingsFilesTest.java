package dev.jasper.app.plugins;

import java.nio.file.attribute.FileTime;
import dev.jasper.app.config.ConfigDiagnostic;
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
        List<ConfigDiagnostic> reports = new ArrayList<>();
        var files = new PluginSettingsFiles(user(), reports::add);
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
        assertThat(reports).singleElement().satisfies(d -> {
            assertThat(d.file()).isEqualTo(file);
            assertThat(d.key()).isEmpty();
            assertThat(d.line()).isEqualTo(2);
            assertThat(d.message()).contains(file.getFileName().toString());
        });
        assertThat(files.position("dev.example.tool", List.of("greeting"))).hasValue(new PluginSettingsFiles.Position(1, 1));
        Files.writeString(file, "greeting = \"hi\"\n[proxy]\nport = 22\n");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 7000));
        assertThat(files.refresh(false)).as("recovering from a broken file counts as a reload").containsExactly("dev.example.tool");
        Files.writeString(file, "greeting = \"again\"\n");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 10000));
        assertThat(files.refresh(true)).containsExactly("dev.example.tool");
        assertThat(files.current("dev.example.tool")).containsEntry("greeting", "again").doesNotContainKey("proxy");
    }

    @Test void theLastGoodTextIsKeptAndSavesEditItAtomically() throws Exception {
        var files = new PluginSettingsFiles(root, diagnostic -> { });
        Path file = files.file("dev.test");
        assertThat(files.text("dev.test")).as("no file yet").isEmpty();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "# Settings\nsize = 13\n");
        files.refresh(true);
        assertThat(files.text("dev.test")).isEqualTo("# Settings\nsize = 13\n");
        Files.writeString(file, "size = \n");
        files.refresh(true);
        assertThat(files.text("dev.test")).as("a broken file keeps the last good text").isEqualTo("# Settings\nsize = 13\n");
        Files.writeString(file, "# Settings\nsize = 13\n");
        files.save("dev.test", text -> text.replace("13", "15"));
        assertThat(Files.readString(file)).isEqualTo("# Settings\nsize = 15\n");
        assertThat(files.text("dev.test")).isEqualTo("# Settings\nsize = 15\n");
        assertThat(files.current("dev.test")).containsEntry("size", 15L);
        try (var listing = Files.list(file.getParent())) {
            assertThat(listing.map(path -> path.getFileName().toString())).containsExactly("dev.test.toml");
        }
    }

    @Test void aSaveCreatesAMissingFileAndLeavesAnUnchangedFileAlone() throws Exception {
        var files = new PluginSettingsFiles(root, diagnostic -> { });
        files.save("dev.new", text -> text + "vim = true\n");
        assertThat(Files.readString(files.file("dev.new"))).isEqualTo("vim = true\n");
        var before = FileTime.fromMillis(1_000_000);
        Files.setLastModifiedTime(files.file("dev.new"), before);
        files.save("dev.new", text -> text);
        assertThat(Files.getLastModifiedTime(files.file("dev.new"))).isEqualTo(before);
        assertThatThrownBy(() -> files.save("dev.new", text -> "x".repeat(1024 * 1024 + 1)))
            .isInstanceOf(java.io.IOException.class).hasMessageContaining("1 MiB");
    }
}
