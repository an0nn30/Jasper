package dev.jasper.app.about;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThirdPartyNoticesTest {
    private static final Path ROOT = Path.of(System.getProperty("jasper.projectDir", "..")).toAbsolutePath().normalize();

    @Test void everyListedTextResolves() {
        for (var component : ThirdPartyNotices.listed("1.2.0")) {
            assertThat(ThirdPartyNotices.read(component.text())).as(component.name()).isNotBlank();
            component.notice().ifPresent(notice -> assertThat(ThirdPartyNotices.read(notice)).as(component.name()).isNotBlank());
            assertThat(component.licenseName()).as(component.name()).isNotBlank();
        }
    }

    @Test void jasperComesFirstThenTheRestByNameIgnoringCase() {
        var listed = ThirdPartyNotices.listed("1.2.0 + 14 commits");
        assertThat(listed.getFirst().title()).isEqualTo("Jasper 1.2.0 + 14 commits");
        assertThat(listed.getFirst().licenseName()).isEqualTo("PolyForm Noncommercial 1.0.0");
        var rest = listed.subList(1, listed.size()).stream().map(ThirdPartyNotices.Component::name).toList();
        assertThat(rest).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER).contains("FlatLaf", "JediTerm", "jsvg");
        assertThat(ThirdPartyNotices.listed("").getFirst().title()).isEqualTo("Jasper");
    }

    @Test void theBuiltInCatppuccinThemesAreListedWithTheirMitLicense() {
        var catppuccin = ThirdPartyNotices.thirdParty().stream().filter(component -> component.name().equals("Catppuccin for JetBrains"))
            .findFirst().orElseThrow();
        assertThat(catppuccin.title()).isEqualTo("Catppuccin for JetBrains 3.6.1");
        assertThat(catppuccin.licenseName()).isEqualTo("MIT License");
        assertThat(catppuccin.homepage()).isEqualTo("https://github.com/catppuccin/jetbrains");
        assertThat(ThirdPartyNotices.read(catppuccin.text())).startsWith("MIT License\n\nCopyright (c) 2022 Catppuccin\n");
    }

    @Test void jaspersTextIsTheRootLicense() throws Exception {
        String root = Files.readString(ROOT.resolve("LICENSE.md"));
        assertThat(ThirdPartyNotices.read(ThirdPartyNotices.jasper("").text())).isEqualTo(root);
        assertThat(root).startsWith("# PolyForm Noncommercial License 1.0.0")
            .endsWith("Required Notice: Copyright 2026 Dustin McKenna-Watts (https://github.com/an0nn30/Jasper)\n");
    }

    @Test void jarComponentsNameExactCoordinates() {
        for (var component : ThirdPartyNotices.thirdParty())
            assertThat(component.modules()).as(component.name()).allMatch(module -> module.matches("[^:\\s]+:[^:\\s]+:[^:\\s]+"));
    }

    @Test void theIndexReadsOptionalFieldsAndRejectsUnknownLicenses() {
        var parsed = ThirdPartyNotices.parse("""
            [[component]]
            name = "Alpha"
            license = "MIT"
            homepage = "https://alpha.example"
            text = "alpha.txt"
            """);
        assertThat(parsed).containsExactly(new ThirdPartyNotices.Component("Alpha", "", "MIT", "https://alpha.example",
            List.of(), "alpha.txt", Optional.empty()));
        assertThatThrownBy(() -> ThirdPartyNotices.parse("""
            [[component]]
            name = "Beta"
            license = "WTFPL"
            homepage = "https://beta.example"
            text = "beta.txt"
            """)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("WTFPL");
        assertThatThrownBy(() -> ThirdPartyNotices.parse("[[component]\n")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ThirdPartyNotices.parse("[[component]]\nname = \"Gamma\"\n"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("license");
    }

    @Test void aMissingTextIsReported() {
        assertThatThrownBy(() -> ThirdPartyNotices.read("missing.txt")).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("missing.txt");
    }
}
