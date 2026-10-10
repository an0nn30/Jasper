package dev.jasper.app.themeimport;
import dev.jasper.ui.themeimport.ThemeSources;
import dev.jasper.ui.themeimport.LegacyThemeTestSupport;

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
        Path bundled = ROOT.resolve("jasper-ui/src/main/resources/dev/jasper/ui/themeimport/native");
        assertThat(Files.readAllBytes(themes.resolve("legacy-aliases.json"))).isEqualTo(Files.readAllBytes(ROOT.resolve("jasper-app/src/main/resources/dev/jasper/app/themes/legacy-aliases.json")));
        var index = Files.readAllLines(themes.resolve("index.txt"));
        assertThat(index).doesNotHaveDuplicates().contains("jasper-dark.toml", "jasper-light.toml");
        assertThat(Files.readString(themes.resolve("index.txt"))).isEqualTo(Files.readString(bundled.resolve("index.txt")));
        for (String file : index) {
            var before = dev.jasper.ui.themeio.ThemeToml.parse(Files.readString(bundled.resolve(file)), file);
            var after = dev.jasper.ui.themeio.ThemeToml.parse(Files.readString(themes.resolve(file)), file);
            assertThat(after.seeds()).as(file).isEqualTo(before.seeds());
            assertThat(after.ansi()).as(file).isEqualTo(before.ansi());
            assertThat(after.tokens()).as(file).isEqualTo(before.tokens());
            assertThat(after.syntax()).as(file).isEqualTo(before.syntax());
            assertThat(after.metadata()).as(file).isEqualTo(before.metadata());
            assertThat(after.workspace()).as(file).isEqualTo(before.workspace());
            assertThat(after.icons()).as(file).isEqualTo(before.icons());
            assertThat(after.semanticOverrides()).as(file).isEqualTo(before.semanticOverrides());
            var colors = new java.util.LinkedHashMap<String,Object>();
            before.ui().forEach((key,value) -> { if (dev.jasper.ui.theme.UiColorKeys.supports(key,value)) colors.put(key,value); });
            assertThat(after.ui()).as(file + " color-only conversion").isEqualTo(colors);
            if (!before.uiComplete().orElse(false)) assertThat(Files.readAllBytes(themes.resolve(file))).isEqualTo(Files.readAllBytes(bundled.resolve(file)));
        }
        var lines = Files.readAllLines(notices);
        var policyWarnings = lines.stream().filter(line -> line.startsWith("  - Conversion:")).toList();
        assertThat(policyWarnings).isNotEmpty().allMatch(line -> line.contains("discarded non-color override") || (line.contains("no longer controls Jasper native UI") && line.contains("use [semantic]")));
        assertThat(String.join("\n", lines.stream().filter(line -> !line.startsWith("  - Conversion:")).toList()) + "\n")
            .isEqualTo(Files.readString(ROOT.resolve("THIRD-PARTY-THEMES.md")));
    }

    @Test void catalogShipsOnlyNordFromTheLockedThirdPartySources() throws Exception {
        var manifest = ThemeSources.read(ROOT.resolve("tools/themes/sources.json"));
        ThemeSources.verify(manifest, ROOT.resolve("third_party/themes"));
        var counts = new java.util.TreeMap<String, Long>();
        var names = new java.util.HashSet<String>();
        var ids = new java.util.HashSet<String>();
        Path bundled = ROOT.resolve("jasper-ui/src/main/resources/dev/jasper/ui/themeimport/native");
        for (var source : manifest.sources()) {
            if (!source.id().equals("nord")) continue;
            counts.put(source.id(), source.variants().stream().filter(ThemeSources.Variant::included).count());
            for (var variant : source.variants()) if (variant.included()) {
                assertThat(names.add(variant.name().strip().toLowerCase(java.util.Locale.ROOT))).isTrue();
                assertThat(ids.add(variant.id())).isTrue();
                var document = dev.jasper.ui.themeio.ThemeToml.parse(Files.readString(bundled.resolve(variant.id() + ".toml")), variant.id());
                assertThat(document.parent()).isEmpty();
                assertThat(document.metadata()).containsKeys("author", "license", "source", "commit");
                assertThat(document.metadata().get("author")).isNotBlank();
                assertThat(document.metadata().get("commit")).isEqualTo(source.commit()).matches("[0-9a-f]{40}");
                assertThat(Files.size(bundled.resolve(variant.id() + ".toml"))).isLessThanOrEqualTo(dev.jasper.app.appearance.ThemeCatalog.MAX_THEME_FILE);
                dev.jasper.ui.theme.ThemeResolver.resolve(document);
            }
        }
        assertThat(counts).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("nord", 1L));
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
        var document = dev.jasper.ui.themeio.ThemeToml.parse(Files.readString(ROOT.resolve(
            "jasper-app/src/test/resources/dev/jasper/ui/themeimport/native/darcula.toml")), "darcula");
        var scheme = LegacyThemeTestSupport.scheme("Darcula", true);
        assertThat(document.seeds().get(dev.jasper.ui.theme.Seed.BACKGROUND)).isEqualTo(scheme.palette().background());
        for (int i = 0; i < 16; i++) assertThat(document.ansi().get(i)).isEqualTo(scheme.palette().ansi().get(i));
    }

    @Test void everyGeneratedFileLoadsThroughTheNativeCatalog() throws Exception {
        Path root = output.resolve("catalog"); Path themes = root.resolve("themes"); Files.createDirectories(themes);
        Path bundled = ROOT.resolve("jasper-ui/src/main/resources/dev/jasper/ui/themeimport/native");
        var index = Files.readAllLines(bundled.resolve("index.txt"));
        for (String file : index) Files.copy(bundled.resolve(file), themes.resolve(file));
        var snapshot = dev.jasper.app.appearance.ThemeCatalog.beside(root.resolve("config.toml")).scan();
        assertThat(snapshot.problems()).allSatisfy(problem -> {
            assertThat(problem.advisory()).isTrue();
            assertThat(problem.message()).contains("no longer controls Jasper native UI", "use [semantic]");
        });
        assertThat(snapshot.themes()).hasSize(index.size());
        assertThat(snapshot.names()).doesNotHaveDuplicates();
    }
}
