package dev.jasper.app.appearance;
import dev.jasper.ui.themeio.ThemeToml;
import dev.jasper.ui.theme.Token;
import dev.jasper.ui.theme.ThemeResolver;
import dev.jasper.ui.theme.ThemeFileException;
import dev.jasper.ui.theme.ThemeDocument;
import dev.jasper.ui.theme.Seed;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ThemeResolverTest {
    static final String BASE = """
        name = "Base"
        dark = true
        ui_complete = false
        [colors]
        background = "#101010"
        foreground = "#eeeeee"
        accent = "#3388ff"
        [colors.normal]
        black = "#000000"
        red = "#aa0000"
        green = "#00aa00"
        yellow = "#aaaa00"
        blue = "#0000aa"
        magenta = "#aa00aa"
        cyan = "#00aaaa"
        white = "#aaaaaa"
        [tokens]
        surface = "#151515"
        tab_active = "#202020"
        [ui]
        "*.background" = "#151515"
        "Panel.background" = "#151515"
        """;

    private static ThemeDocument doc(String text) { return ThemeToml.parse(text, "t.toml"); }

    private static Function<String, List<ThemeDocument>> among(ThemeDocument... documents) {
        return name -> List.of(documents).stream()
            .filter(d -> d.name().toLowerCase(Locale.ROOT).equals(name.strip().toLowerCase(Locale.ROOT))).toList();
    }

    @Test void aThemeWithoutExtendsResolvesToItself() {
        ThemeDocument base = doc(BASE);
        assertThat(ThemeResolver.resolve(base)).isEqualTo(base);
    }

    @Test void aChildMergesOverItsParentKeyByKey() {
        ThemeDocument base = doc(BASE);
        ThemeDocument child = doc("""
            name = "Child"
            extends = "  base "
            [colors]
            accent = "#ff8800"
            [colors.bright]
            red = "#ff0000"
            [tokens]
            tab_active = "#303030"
            [ui]
            "*.background" = "#202020"
            "Button.arc" = 6
            """);
        ThemeDocument resolved = ThemeResolver.resolve(child, among(child, base));
        assertThat(resolved.name()).isEqualTo("Child");
        assertThat(resolved.parent()).isEmpty();
        assertThat(resolved.dark()).as("inherited").contains(true);
        assertThat(resolved.uiComplete()).as("inherited").contains(false);
        assertThat(resolved.seeds()).containsEntry(Seed.ACCENT, new Color(0xff8800)).containsEntry(Seed.BACKGROUND, new Color(0x101010));
        assertThat(resolved.ansi()).containsEntry(9, new Color(0xff0000)).containsEntry(1, new Color(0xaa0000));
        assertThat(resolved.tokens()).containsEntry(Token.TAB_ACTIVE, new Color(0x303030)).containsEntry(Token.SURFACE, new Color(0x151515));
        assertThat(List.copyOf(resolved.ui().keySet())).as("the child's keys move last")
            .containsExactly("Panel.background", "*.background", "Button.arc");
        assertThat(resolved.ui().get("*.background")).isEqualTo("#202020");
    }

    @Test void aChainOfThreeResolvesInOrder() {
        ThemeDocument base = doc(BASE);
        ThemeDocument middle = doc("name = \"Middle\"\nextends = \"Base\"\n[tokens]\ntab_active = \"#404040\"\n");
        ThemeDocument top = doc("name = \"Top\"\nextends = \"Middle\"\ndark = false\n");
        ThemeDocument resolved = ThemeResolver.resolve(top, among(top, middle, base));
        assertThat(resolved.dark()).contains(false);
        assertThat(resolved.tokens()).containsEntry(Token.TAB_ACTIVE, new Color(0x404040));
        assertThat(resolved.seeds()).containsKey(Seed.BACKGROUND);
    }

    @Test void aThemeMayExtendTheThemeItReplacesByName() {
        ThemeDocument builtIn = doc(BASE);
        ThemeDocument mine = doc("name = \"Base\"\nextends = \"Base\"\n[colors]\naccent = \"#00ff00\"\n");
        ThemeDocument resolved = ThemeResolver.resolve(mine, among(mine, builtIn));
        assertThat(resolved.seeds()).containsEntry(Seed.ACCENT, new Color(0x00ff00)).containsKey(Seed.BACKGROUND);
    }

    @Test void aMissingParentNamesTheExtendsLine() {
        ThemeDocument child = doc("name = \"Child\"\n\nextends = \"Nowhere\"\n");
        assertThatThrownBy(() -> ThemeResolver.resolve(child, among(child)))
            .isInstanceOfSatisfying(ThemeFileException.class, failure -> {
                assertThat(failure.line()).isEqualTo(3);
                assertThat(failure.getMessage()).contains("\"Nowhere\"").contains("not a theme Jasper has");
            });
    }

    @Test void aCycleIsAnError() {
        ThemeDocument a = doc("name = \"A\"\nextends = \"B\"\n");
        ThemeDocument b = doc("name = \"B\"\nextends = \"A\"\n");
        assertThatThrownBy(() -> ThemeResolver.resolve(a, among(a, b)))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("cycle").hasMessageContaining("A -> B -> A");
    }

    @Test void missingSeedsAreListed() {
        ThemeDocument bare = doc("name = \"Bare\"\n[colors]\nbackground = \"#000000\"\n[colors.normal]\nblack = \"#000000\"\n");
        assertThatThrownBy(() -> ThemeResolver.resolve(bare))
            .isInstanceOf(ThemeFileException.class)
            .hasMessageContaining("[colors] is missing foreground, accent, normal.red, normal.green");
    }
}
