package dev.jasper.app.appearance;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class WorkspaceGlassThemeTest {
    private static final String BASE = """
        name = "Glass"
        [colors]
        background = "#212734"
        foreground = "#e6e6e6"
        accent = "#67b5ed"
        [colors.normal]
        black = "#35424c"
        red = "#b45648"
        green = "#6caa71"
        yellow = "#c4ac62"
        blue = "#6d96b4"
        magenta = "#bd7bcd"
        cyan = "#7ccbcd"
        white = "#dee5eb"
        """;

    @Test void aThemeWithoutTheTableIsOpaque() {
        var document = ThemeToml.parse(BASE, "glass.toml");
        assertThat(document.workspace()).isEqualTo(WorkspaceGlass.NONE);
        assertThat(ThemeGenerator.generate(document).glass()).isEqualTo(GlassLevels.OPAQUE);
        assertThat(GlassLevels.OPAQUE.translucent()).isFalse();
    }

    @Test void theTableSetsOpacityAndBlur() {
        var document = ThemeToml.parse(BASE + "[workspace]\nopacity = 0.95\nblur = true\n", "glass.toml");
        assertThat(document.workspace()).isEqualTo(new WorkspaceGlass(Optional.of(0.95), Optional.of(true)));
        var glass = ThemeGenerator.generate(document).glass();
        assertThat(glass).isEqualTo(new GlassLevels(0.95, true));
        assertThat(glass.translucent()).isTrue();
    }

    @Test void wholeNumbersAreAccepted() {
        var document = ThemeToml.parse(BASE + "[workspace]\nopacity = 1\nblur = false\n", "glass.toml");
        assertThat(ThemeGenerator.generate(document).glass()).isEqualTo(new GlassLevels(1, false));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "opacity = 0.05 | \"opacity\" must be from 0.1 to 1",
        "opacity = 1.5 | \"opacity\" must be from 0.1 to 1",
        "blur = 2 | \"blur\" must be true or false",
        "blur = 'on' | \"blur\" must be true or false",
        "opacity = 'high' | \"opacity\" must be a number",
        "tint = 0.5 | unknown key \"tint\" in [workspace]"})
    void invalidValuesNameTheirLine(String line, String message) {
        String text = BASE + "[workspace]\n" + line + "\n";
        int expectedLine = (int) BASE.lines().count() + 2;
        assertThatThrownBy(() -> ThemeToml.parse(text, "glass.toml"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining(message)
            .satisfies(failure -> assertThat(((ThemeFileException) failure).line()).isEqualTo(expectedLine));
    }

    @Test void aChildInheritsAndOverridesItsParentsValues() {
        var parent = ThemeToml.parse(BASE + "[workspace]\nopacity = 0.9\nblur = true\n", "parent.toml");
        var child = ThemeToml.parse("name = \"Child\"\nextends = \"Glass\"\n[workspace]\nblur = false\n", "child.toml");
        var resolved = ThemeResolver.resolve(child, name -> name.equals("Glass") ? List.of(parent) : List.of());
        assertThat(ThemeGenerator.generate(resolved).glass()).isEqualTo(new GlassLevels(0.9, false));
    }

    @Test void theWriterRoundTripsTheTable() {
        var document = ThemeToml.parse(BASE + "[workspace]\nopacity = 0.93\nblur = true\n", "glass.toml");
        String written = ThemeTomlWriter.write(document);
        assertThat(written).contains("\n[workspace]\n", "\n\"blur\" = true\n");
        assertThat(ThemeToml.parse(written, "glass.toml").workspace()).isEqualTo(document.workspace());
        assertThat(ThemeTomlWriter.write(ThemeToml.parse(BASE, "glass.toml"))).doesNotContain("[workspace]");
    }

    @Test void levelsRejectValuesOutsideTheirRanges() {
        assertThatThrownBy(() -> new GlassLevels(0.05, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GlassLevels(1.5, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GlassLevels(Double.NaN, false)).isInstanceOf(IllegalArgumentException.class);
    }
}
