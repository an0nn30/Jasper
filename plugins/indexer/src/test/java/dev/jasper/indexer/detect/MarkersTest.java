package dev.jasper.indexer.detect;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MarkersTest {
    @Test void parsingSplitsStrongFromWeakAndSkipsBlanks() {
        var markers = Markers.parse(List.of("!.git", " ! *.sln ", "Cargo.toml", "  ", "!"));
        assertThat(markers.strong()).containsExactly(".git", "*.sln");
        assertThat(markers.weak()).containsExactly("Cargo.toml");
    }

    @Test void globsMatchAnyTextAndLiteralsMatchExactly() {
        assertThat(Markers.glob("*.sln", "Jasper.sln")).isTrue();
        assertThat(Markers.glob("*.sln", "Jasper.sln.bak")).isFalse();
        assertThat(Markers.glob("a*b*c", "aXbYc")).isTrue();
        assertThat(Markers.glob("a*b*c", "ac")).isFalse();
        assertThat(Markers.glob("ab*ba", "aba")).as("parts may not overlap").isFalse();
        assertThat(Markers.glob("Makefile", "makefile")).as("case-sensitive").isFalse();
        assertThat(Markers.glob("*", "anything")).isTrue();
    }

    @Test void aMatchFollowsPatternOrderAndNamesItsKind() {
        var markers = Markers.parse(Markers.DEFAULTS);
        assertThat(markers.strongMatch(Set.of("Jasper.sln", ".git"))).contains(new Markers.Match(".git", ".git"));
        assertThat(markers.weakMatch(Set.of("README.md", "package.json", "Cargo.toml")))
            .contains(new Markers.Match("Cargo.toml", "Cargo.toml"));
        assertThat(markers.weakMatch(Set.of("README.md"))).isEmpty();
        assertThat(new Markers.Match("*.sln", "Jasper.sln").kind()).isEqualTo("Visual Studio solution");
        assertThat(new Markers.Match(".git", ".git").kind()).isEqualTo("Git repository");
        assertThat(new Markers.Match("WORKSPACE", "WORKSPACE").kind()).isEqualTo("Contains WORKSPACE");
    }

    @Test void literalsAndGlobsTellAProbeWhatToLookFor() {
        var markers = Markers.parse(List.of("!.git", "*.csproj", "go.mod"));
        assertThat(markers.literals()).containsExactlyInAnyOrder(".git", "go.mod");
        assertThat(markers.hasGlobs()).isTrue();
        assertThat(Markers.parse(List.of("go.mod")).hasGlobs()).isFalse();
        assertThat(markers.matchesAny("App.csproj")).isTrue();
        assertThat(markers.matchesAny("README.md")).isFalse();
    }
}
