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

    /** Plan 12 Rulings 5: one component names both IntelliJ icon sets, ships the Apache text and keeps upstream's NOTICE. */
    @Test void theIntelliJIconsComponentNamesBothSetsAndKeepsTheUpstreamNotice() {
        var icons = ThirdPartyNotices.thirdParty().stream().filter(c -> c.name().startsWith("IntelliJ Platform icons")).toList();
        assertThat(icons).singleElement().satisfies(c -> {
            assertThat(c.name()).isEqualTo("IntelliJ Platform icons (New UI and Classic)");
            assertThat(c.license()).isEqualTo("Apache-2.0");
            assertThat(ThirdPartyNotices.read(c.text())).contains("Apache License").contains("Version 2.0, January 2004")
                .doesNotContain("OPEN-SOURCE BUILD TERMS");
            assertThat(ThirdPartyNotices.read(c.notice().orElseThrow()))
                .contains("8062c061ff33e22041d9f1844db5b8b8a6b5e4d7", "60b8fbadc2f08ef18a6ecab0c138a849e14f13fb")
                .contains("This software includes code from IntelliJ IDEA\nCopyright (C) JetBrains s.r.o.\nhttps://www.jetbrains.com/idea/");
        });
    }

    /** Plan 12: the Enterprise theme's port is listed with the Apache text, its pinned commits and upstream's NOTICE. */
    @Test void theIntelliJThemesComponentNamesThePortAndKeepsTheUpstreamNotice() {
        var themes = ThirdPartyNotices.thirdParty().stream().filter(c -> c.name().startsWith("IntelliJ Platform themes")).toList();
        assertThat(themes).singleElement().satisfies(c -> {
            assertThat(c.name()).isEqualTo("IntelliJ Platform themes (the Enterprise theme)");
            assertThat(c.license()).isEqualTo("Apache-2.0");
            assertThat(ThirdPartyNotices.read(c.text())).contains("Apache License").contains("Version 2.0, January 2004");
            assertThat(ThirdPartyNotices.read(c.notice().orElseThrow()))
                .contains("f7377708b654b73b206da40bb382ecb4d44e8f12", "8062c061ff33e22041d9f1844db5b8b8a6b5e4d7")
                .contains("This software includes code from IntelliJ IDEA\nCopyright (C) JetBrains s.r.o.\nhttps://www.jetbrains.com/idea/");
        });
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
