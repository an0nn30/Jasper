package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 6.2: every file the converter's tests expect it to write is a theme the Java loader reads as intended. */
class ConvertedThemesTest {
    static final Path EXPECTED = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize()
        .resolve("tools/themes/testdata/expected");

    /** A golden file's UI variants and its schemes' keys. */
    record Shape(boolean dark, boolean light, List<String> schemes) { }

    static final Map<String, Shape> SHAPES = Map.of(
        "gruvbox-dark", new Shape(false, false, List.of("dark")),
        "paper-light", new Shape(false, false, List.of("light")),
        "ocean", new Shape(true, false, List.of("dark")),
        "ocean-dawn", new Shape(false, true, List.of("light")),
        "stray", new Shape(true, false, List.of("dark")));

    @Test void everyConvertedThemeLoadsWithItsVariantsAndSchemes() throws Exception {
        List<Path> files;
        try (var listing = Files.list(EXPECTED)) { files = listing.filter(path -> path.toString().endsWith(".json")).sorted().toList(); }
        assertThat(files).extracting(path -> path.getFileName().toString().replace(".json", ""))
            .containsExactlyInAnyOrderElementsOf(SHAPES.keySet());
        for (Path file : files) {
            String id = file.getFileName().toString().replace(".json", "");
            var theme = JasperTheme.parse(Files.readString(file), id);
            var shape = SHAPES.get(id);
            assertThat(theme.has(true)).as(id).isEqualTo(shape.dark());
            assertThat(theme.has(false)).as(id).isEqualTo(shape.light());
            assertThat(theme.schemes()).as(id).extracting(scheme -> scheme.id().key()).isEqualTo(shape.schemes());
            for (boolean dark : new boolean[] {true, false})
                if (theme.has(dark)) assertThat(theme.generated(dark).semantic().color(SemanticColor.SURFACE_TERMINAL))
                    .as("%s: the terminal surface meets the text area", id).isEqualTo(theme.matching(dark).colors().background());
        }
    }

    @Test void oceanKeepsItsOwnColoursAndItsEditorScheme() throws Exception {
        var ocean = JasperTheme.parse(Files.readString(EXPECTED.resolve("ocean.json")), "ocean");
        assertThat(ocean.name()).isEqualTo("Ocean");
        assertThat(ocean.author()).isEqualTo("Jasper fixtures");
        assertThat(ocean.palette(true).color(SemanticColor.SURFACE_PANEL)).isEqualTo(new Color(0x16242F));
        assertThat(ocean.syntax(true).get(SyntaxRole.KEYWORD)).isEqualTo(new SyntaxStyle(new Color(0xC792EA), true, false));
        assertThat(ocean.matching(true).name()).isEqualTo("Ocean");
    }
}
