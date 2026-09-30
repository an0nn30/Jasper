package dev.jasper.app.terminalthemes;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsDraft;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.terminal.config.Palette;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(EdtTestExtension.class)
class AlacrittyThemesPageTest {
    @TempDir Path home;
    private TerminalThemeCatalog catalog;
    private SettingsDraft draft;
    private Optional<Path> chosen = Optional.empty();
    private final List<String> questions = new ArrayList<>();
    private final List<Path> revealed = new ArrayList<>();
    private boolean answer = true;

    @BeforeEach void start() {
        catalog = TerminalThemeCatalog.beside(home.resolve("config.toml"));
        catalog.scan();
        draft = new SettingsDraft(ConfigLoader.parse(home.resolve("config.toml"), "", true).values());
    }

    private AlacrittyThemesPage page() {
        var context = new PageContext(draft, id -> { }, Runnable::run, (title, initial) -> chosen, () -> { });
        var page = new AlacrittyThemesPage(context, new AlacrittyThemesPage.Hooks(catalog,
            () -> { catalog.scan(); return CompletableFuture.completedFuture(null); }, revealed::add,
            (parent, question) -> { questions.add(question); return answer; },
            () -> ConfigSnapshot.defaults().viewOptions(13f, Palette.jasperDark()), Runnable::run));
        page.component();
        return page;
    }

    @Test void theListFiltersSelectsAndUsesATheme() {
        var page = page();
        assertThat(page.id()).isEqualTo("themes.alacritty");
        assertThat(page.parent()).contains("themes");
        assertThat(page.listed()).hasSize(177).contains("Dracula");
        page.filter("drac");
        assertThat(page.listed()).allSatisfy(name -> assertThat(name.toLowerCase()).contains("drac"));
        page.select("Dracula");
        assertThat(page.sample().palette()).isEqualTo(catalog.latest().find("Dracula").orElseThrow().palette());
        assertThat(page.removeButton().isVisible()).as("bundled themes cannot be removed").isFalse();
        page.useButton().doClick();
        assertThat(draft.value(CoreSettings.TERMINAL_COLORS)).isEqualTo("Dracula");
        assertThat(page.inUse()).isEqualTo("Dracula");
        assertThat(page.status()).contains("Apply");
        page.filter("");
        assertThat(page.listed()).hasSize(177);
    }

    @Test void installingAFileAddsAndSelectsItAndRemovingAsksFirst() throws Exception {
        Files.createDirectories(home.resolve("Downloads"));
        chosen = Optional.of(Files.writeString(home.resolve("Downloads/my_sunset.toml"), TerminalThemeCatalogTest.theme("#1a1b26")));
        var page = page();
        page.installButton().doClick();
        assertThat(home.resolve("terminal-themes/my_sunset.toml")).exists();
        assertThat(page.listed().getFirst()).isEqualTo("My Sunset");
        assertThat(page.selectedName()).isEqualTo("My Sunset");
        assertThat(page.removeButton().isVisible()).isTrue();
        page.installButton().doClick();
        assertThat(questions).containsExactly("my_sunset.toml is already installed. Replace it?");
        answer = false;
        page.removeButton().doClick();
        assertThat(home.resolve("terminal-themes/my_sunset.toml")).as("declined").exists();
        answer = true;
        page.removeButton().doClick();
        assertThat(questions.getLast()).isEqualTo("Remove My Sunset? This deletes my_sunset.toml.");
        assertThat(home.resolve("terminal-themes/my_sunset.toml")).doesNotExist();
        assertThat(page.listed()).doesNotContain("My Sunset");
    }

        @Test void aUserFileShadowsTheBundledThemeOfTheSameNameUntilRemoved() throws Exception {
        Files.createDirectories(home.resolve("Downloads"));
        chosen = Optional.of(Files.writeString(home.resolve("Downloads/tokyo_night.toml"), TerminalThemeCatalogTest.theme("#1a1b26")));
        var page = page();
        page.installButton().doClick();
        assertThat(catalog.latest().find("Tokyo Night").orElseThrow().bundled()).isFalse();
        assertThat(page.listed()).containsOnlyOnce("Tokyo Night");
        page.removeButton().doClick();
        assertThat(catalog.latest().find("Tokyo Night").orElseThrow().bundled()).isTrue();
        assertThat(page.listed()).containsOnlyOnce("Tokyo Night");
    }

@Test void anUnusableFileIsReportedAndDisposeClosesTheSample() throws Exception {
        Files.createDirectories(home.resolve("Downloads"));
        chosen = Optional.of(Files.writeString(home.resolve("Downloads/light.toml"), TerminalThemeCatalogTest.theme("#ffffff")));
        var page = page();
        page.installButton().doClick();
        assertThat(page.status()).contains("reserved");
        page.dispose();
        assertThat(page.sample().closed()).isTrue();
    }
}
