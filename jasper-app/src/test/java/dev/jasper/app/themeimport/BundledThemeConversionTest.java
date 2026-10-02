package dev.jasper.app.themeimport;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class BundledThemeConversionTest {
    @TempDir Path output;
    private static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot"));

    @Test void regenerationMatchesEveryCheckedInThemeAndNotices() throws Exception {
        Path themes = output.resolve("themes");
        Path notices = output.resolve("THIRD-PARTY-THEMES.md");
        ConvertThemes.main(new String[] {"--manifest", ROOT.resolve("tools/themes/sources.json").toString(),
            "--sources", ROOT.resolve("third_party/themes").toString(), "--output", themes.toString(),
            "--notices", notices.toString()});
        Path bundled = ROOT.resolve("jasper-app/src/main/resources/dev/jasper/app/themes");
        assertThat(Files.readAllBytes(themes.resolve("legacy-aliases.json"))).isEqualTo(Files.readAllBytes(bundled.resolve("legacy-aliases.json")));
        var index = Files.readAllLines(themes.resolve("index.txt"));
        assertThat(index).doesNotHaveDuplicates().contains("jasper-dark.toml", "jasper-light.toml");
        assertThat(Files.readString(themes.resolve("index.txt"))).isEqualTo(Files.readString(bundled.resolve("index.txt")));
        for (String file : index) assertThat(Files.readAllBytes(themes.resolve(file))).as(file).isEqualTo(Files.readAllBytes(bundled.resolve(file)));
        assertThat(Files.readAllBytes(notices)).isEqualTo(Files.readAllBytes(ROOT.resolve("THIRD-PARTY-THEMES.md")));
    }

    @Test void catalogCoversTheApprovedFamiliesWithUniqueNativeValuesAndProvenance() throws Exception {
        var manifest = ThemeSources.read(ROOT.resolve("tools/themes/sources.json"));
        ThemeSources.verify(manifest, ROOT.resolve("third_party/themes"));
        var counts = new java.util.TreeMap<String, Long>();
        var names = new java.util.HashSet<String>();
        var ids = new java.util.HashSet<String>();
        Path bundled = ROOT.resolve("jasper-app/src/main/resources/dev/jasper/app/themes");
        for (var source : manifest.sources()) {
            counts.put(source.id(), source.variants().stream().filter(ThemeSources.Variant::included).count());
            for (var variant : source.variants()) if (variant.included()) {
                assertThat(names.add(variant.name().strip().toLowerCase(java.util.Locale.ROOT))).isTrue();
                assertThat(ids.add(variant.id())).isTrue();
                var document = dev.jasper.app.appearance.ThemeToml.parse(Files.readString(bundled.resolve(variant.id() + ".toml")), variant.id());
                assertThat(document.parent()).isEmpty();
                assertThat(document.metadata()).containsKeys("author", "license", "source", "commit");
                assertThat(document.metadata().get("author")).isNotBlank();
                assertThat(document.metadata().get("commit")).isEqualTo(source.commit()).matches("[0-9a-f]{40}");
                assertThat(Files.size(bundled.resolve(variant.id() + ".toml"))).isLessThanOrEqualTo(dev.jasper.app.appearance.ThemeCatalog.MAX_THEME_FILE);
                dev.jasper.app.appearance.ThemeResolver.resolve(document);
            }
        }
        assertThat(counts).containsExactlyInAnyOrderEntriesOf(java.util.Map.ofEntries(
            java.util.Map.entry("intellij", 2L), java.util.Map.entry("alacritty", 168L),
            java.util.Map.entry("one-dark", 2L), java.util.Map.entry("dracula", 3L), java.util.Map.entry("solarized", 2L),
            java.util.Map.entry("catppuccin", 4L), java.util.Map.entry("material-lite", 88L), java.util.Map.entry("vscode", 3L),
            java.util.Map.entry("nord", 1L), java.util.Map.entry("gruvbox", 6L), java.util.Map.entry("one-dark-modern", 1L), java.util.Map.entry("xcode", 2L)));
        try (var files = Files.list(bundled)) {
            assertThat(files.filter(path -> path.toString().endsWith(".toml")).map(path -> path.getFileName().toString()).toList())
                .containsExactlyInAnyOrderElementsOf(Files.readAllLines(bundled.resolve("index.txt")));
        }
    }

    @Test void rejectsDuplicateIdsEscapesAndBadAssetHashes() throws Exception {
        String original = Files.readString(ROOT.resolve("tools/themes/sources.json"));
        Path modified = output.resolve("sources.json");
        Files.writeString(modified, original.replace("\"id\": \"catppuccin-latte\"", "\"id\": \"jasper-dark\""));
        assertThatThrownBy(() -> ThemeSources.read(modified)).hasMessageContaining("duplicate output ID");
        Files.writeString(modified, original.replace("\"path\": \"LICENSE.txt\"", "\"path\": \"../LICENSE.txt\""));
        assertThatThrownBy(() -> ThemeSources.read(modified)).hasMessageContaining("Escaping path");
        Files.writeString(modified, original.replaceFirst("\"sha256\": \"[0-9a-f]{64}\"", "\"sha256\": \"" + "0".repeat(64) + "\""));
        assertThatThrownBy(() -> ThemeSources.verify(ThemeSources.read(modified), ROOT.resolve("third_party/themes")))
            .hasMessageContaining("hash mismatch");
    }

    @Test void refusesUnrecordedProvidersAndUnknownOutputsBeforePublication() throws Exception {
        var manifest = ThemeSources.read(ROOT.resolve("tools/themes/sources.json"));
        var source = manifest.sources().stream().filter(item -> item.id().equals("one-dark")).findFirst().orElseThrow();
        var missing = new ThemeSources.Source(source.id(), source.repository(), source.commit(), source.license(), source.licenseFile(),
            source.author(), source.kind(), source.plugin(), source.release(), source.assets(), source.variants().subList(1, source.variants().size()));
        assertThatThrownBy(() -> ThemeSources.verify(new ThemeSources.Manifest(java.util.List.of(missing)), ROOT.resolve("third_party/themes")))
            .hasMessageContaining("provider variants");
        Path themes = output.resolve("themes"); Files.createDirectories(themes);
        Files.writeString(themes.resolve("keep.txt"), "user-owned");
        assertThatThrownBy(() -> ConvertThemes.generate(ROOT.resolve("tools/themes/sources.json"), ROOT.resolve("third_party/themes"), themes, output.resolve("notices.md")))
            .hasMessageContaining("Unknown output file");
        assertThat(Files.readString(themes.resolve("keep.txt"))).isEqualTo("user-owned");
        assertThat(themes.resolve("index.txt")).doesNotExist();
    }

    @Test void classicDarculaKeepsItsBundledConsoleScheme() throws Exception {
        var document = dev.jasper.app.appearance.ThemeToml.parse(Files.readString(ROOT.resolve(
            "jasper-app/src/main/resources/dev/jasper/app/themes/darcula.toml")), "darcula");
        var scheme = EditorScheme.named("Darcula", "Darcula", true);
        assertThat(document.seeds().get(dev.jasper.app.appearance.Seed.BACKGROUND)).isEqualTo(scheme.palette().background());
        for (int i = 0; i < 16; i++) assertThat(document.ansi().get(i)).isEqualTo(scheme.palette().ansi().get(i));
    }

    @Test void everyGeneratedFileLoadsThroughTheNativeCatalog() throws Exception {
        Path root = output.resolve("catalog"); Path themes = root.resolve("themes"); Files.createDirectories(themes);
        Path bundled = ROOT.resolve("jasper-app/src/main/resources/dev/jasper/app/themes");
        var index = Files.readAllLines(bundled.resolve("index.txt"));
        for (String file : index) Files.copy(bundled.resolve(file), themes.resolve(file));
        var snapshot = dev.jasper.app.appearance.ThemeCatalog.beside(root.resolve("config.toml")).scan();
        assertThat(snapshot.problems()).isEmpty();
        assertThat(snapshot.themes()).hasSize(index.size());
        assertThat(snapshot.names()).doesNotHaveDuplicates();
    }
}
