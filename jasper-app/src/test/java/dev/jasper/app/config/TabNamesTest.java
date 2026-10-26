package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tab names spec 3: how a pattern becomes a tab's name. */
class TabNamesTest {
    private static String name(String pattern, Map<String, String> pieces, String fallback) {
        return TabNames.format(TitleTemplate.parse(pattern), pieces, fallback);
    }

    @Test void theDefaultIsFolderProcessAndTitle() {
        assertThat(TabNames.DEFAULT).isEqualTo("{dir} {process} {title}");
        var claude = Map.of("dir", "moray", "full", "~/projects/moray", "process", "claude", "title", "Fix tab names");
        assertThat(TabNames.format(TabNames.DEFAULT_PATTERN, claude, "zsh")).isEqualTo("moray claude Fix tab names");
        assertThat(TabNames.format(TabNames.DEFAULT_PATTERN, Map.of("dir", "moray", "process", "zsh", "title", ""), "zsh"))
            .as("no trailing space without a title").isEqualTo("moray zsh");
    }

    @Test void fixedTextCountsSoOnlyAnEmptyResultFallsBack() {
        assertThat(name("Tab {title}", Map.of(), "zsh")).isEqualTo("Tab");
        assertThat(name("{title}", Map.of(), "zsh")).isEqualTo("zsh");
        assertThat(name("  {title}  ", Map.of(), "zsh")).isEqualTo("zsh");
        assertThat(name("{title}", Map.of(), " ")).isEqualTo("Terminal");
    }

    @Test void cleanPutsAValueOnOneTrimmedLine() {
        assertThat(TabNames.clean(" Fix\ntab\tnames\007 ")).isEqualTo("Fix tab names");
        assertThat(TabNames.clean(null)).isEmpty();
    }

    @Test void unknownListsPiecesNothingProvidesOnce() {
        var pattern = TitleTemplate.parse("{dir} {host} {branch} {host} {title}");
        assertThat(TabNames.unknown(pattern, Set.of())).containsExactly("host", "branch");
        assertThat(TabNames.unknown(pattern, Set.of("host"))).containsExactly("branch");
    }

    @Test void aValidPatternIsOneTo200CharactersAndWellFormed() {
        assertThat(TabNames.valid("{dir} | {host} {{x}}")).isTrue();
        assertThat(TabNames.valid("x".repeat(200))).isTrue();
        assertThat(TabNames.valid("x".repeat(201))).isFalse();
        assertThat(TabNames.valid("")).isFalse();
        assertThat(TabNames.valid("   ")).isFalse();
        assertThat(TabNames.valid("{dir")).isFalse();
        assertThat(TabNames.valid("{Dir}")).isFalse();
    }
}
