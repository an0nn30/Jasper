package dev.jasper.app.settingsui;

import static dev.jasper.app.settingsui.PageKit.node;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.appearance.ThemeFolder;
import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.SettingsEdit;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/** The gears beside Settings › Appearance's theme dropdowns import a theme and pick it there, and remove the theme a dropdown shows. */
@ExtendWith(EdtTestExtension.class)
class ThemeMenuTest {
    static final String DARK = "ui.appearance:dark-gear", LIGHT = "ui.appearance:light-gear", TERMINAL = "terminal.scheme:gear";
    @TempDir Path home;
    final ArrayDeque<Path> chosen = new ArrayDeque<>();
    final List<ThemeImports.Question> asked = new ArrayList<>();
    final List<ThemeImports.Notice> told = new ArrayList<>();
    final List<SettingsEdit> saved = new ArrayList<>();
    final ArrayDeque<Runnable> queued = new ArrayDeque<>();
    final int[] reloads = new int[1];
    boolean agree = true, failSaves, failReloads, holdWork;
    /** What the application's library holds: it changes only when the application reads the folder, as after a reload or a saved change. */
    ThemeLibrary library = ThemeLibrary.bundledOnly();
    SettingsDraft draft;
    SchemaPage page;

    Path folder() { return home.resolve("themes"); }

    Path download(String fileName, String text) throws Exception {
        return Files.writeString(Files.createDirectories(home.resolve("downloads")).resolve(fileName), text);
    }

    void install(String fileName, String text) throws Exception {
        Files.writeString(Files.createDirectories(folder()).resolve(fileName), text);
    }

    ThemeLibrary read() { return ThemeLibrary.read(folder(), ThemeLibrary.bundledOnly()); }

    /** The dropdowns' choices over the library, as the application builds them. */
    InstalledThemes installed() {
        java.util.function.Function<Boolean, List<InstalledThemes.ThemeOption>> themes = dark -> library.themes(dark).stream()
            .map(theme -> new InstalledThemes.ThemeOption(theme.id(), library.label(theme), AppearanceFixtures.preview(dark))).toList();
        return new InstalledThemes(themes.apply(true), themes.apply(false), library.schemes().stream()
            .map(scheme -> new InstalledThemes.SchemeOption(scheme.id().toString(), library.label(scheme))).toList(),
            library.themes().stream().collect(Collectors.toMap(theme -> theme.id(), library::label)));
    }

    /** The page over {@code config}, with hooks that run at once (unless {@link #holdWork}) and answer questions with {@link #agree}. */
    SchemaPage page(String config) {
        draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), config, true).values());
        library = read();
        var imports = new ThemeImports(new ThemeFolder(folder()), surface -> Optional.ofNullable(chosen.poll()),
            (surface, question) -> { asked.add(question); if (agree) question.onYes().run(); },
            (surface, notice) -> told.add(notice),
            edit -> {
                saved.add(edit);
                if (failSaves) return CompletableFuture.failedFuture(new java.util.concurrent.CompletionException(new IllegalStateException("config.toml has errors")));
                library = read();
                return CompletableFuture.completedFuture(null);
            },
            () -> {
                reloads[0]++;
                library = read();
                if (failReloads) return CompletableFuture.failedFuture(new IllegalStateException("the service is closed"));
                page.refresh();
                return CompletableFuture.completedFuture(null);
            },
            work -> { if (holdWork) queued.add(work); else work.run(); }, Runnable::run);
        var choices = new AppearanceChoices(100f, false, Optional::empty, ignored -> new Subscription(() -> { }), this::installed, Optional.of(imports));
        var system = new CorePages.SystemActions(() -> Path.of("config.toml"), () -> { }, () -> { }, () -> { }, () -> { }, () -> false, Optional.empty());
        page = CorePages.specs(choices, system, ShellChoices.NONE, id -> { }).stream().filter(spec -> spec.id().equals("appearance"))
            .findFirst().orElseThrow().build(PageKit.context(draft, id -> { }, () -> { }));
        page.component();
        return page;
    }

    void choose(String gear, String item) { page.event(new UiEvent.Action(gear + ":" + item)); }
    Nodes.MenuButton gear(String id) { return (Nodes.MenuButton) node(page, id); }
    MenuItem item(String gear, String item) {
        return gear(gear).items().stream().filter(entry -> entry.id().equals(gear + ":" + item)).findFirst().orElseThrow();
    }

    @Test void eachThemeDropdownHasAGearThatImportsAndRemovesTheThemeItShows() {
        page("");
        for (var id : List.of(DARK, LIGHT, TERMINAL)) {
            var gear = gear(id);
            assertThat(gear.icon()).isEqualTo("settings");
            assertThat(gear.kind()).isEqualTo(Nodes.ButtonKind.GHOST);
            assertThat(gear.items()).extracting(MenuItem::label).containsExactly("Import Theme…", "", "Remove Theme");
            assertThat(item(id, "import").enabled()).isTrue();
            assertThat(item(id, "remove").enabled()).as("Slate Steel and Same as UI theme cannot be removed").isFalse();
        }
        assertThat(page.searchTerms()).contains("Import theme", "Remove theme");
        assertThatThrownBy(() -> node(page, "section-imported-themes")).as("no Imported themes group").hasMessage("No node section-imported-themes");
    }

    @Test void cancellingTheChooserDoesNothing() {
        page("");
        choose(DARK, "import");
        assertThat(reloads[0]).isZero();
        assertThat(told).isEmpty();
        assertThat(draft.modified()).isFalse();
    }

    @Test void importingFromTheDarkGearCopiesTheFileInAndPicksItThere() throws Exception {
        page("");
        var file = download("ocean_theme.json", ThemeTestSupport.uiThemeJson("Ocean"));
        chosen.add(file);
        choose(DARK, "import");
        assertThat(Files.readAllBytes(folder().resolve("ocean.json"))).isEqualTo(Files.readAllBytes(file));
        assertThat(reloads[0]).isEqualTo(1);
        assertThat(draft.value(CoreSettings.UI_THEME_DARK)).isEqualTo("ocean");
        assertThat(draft.modified(CoreSettings.UI_THEME_DARK)).as("staged, like any pick").isTrue();
        assertThat(draft.modified(CoreSettings.UI_THEME_LIGHT)).isFalse();
        assertThat(item(DARK, "remove").label()).isEqualTo("Remove Ocean");
        assertThat(item(DARK, "remove").enabled()).isTrue();
        assertThat(told).isEmpty();
        assertThat(asked).isEmpty();
    }

    @Test void importingFromTheLightGearPicksItsLightVariant() throws Exception {
        page("");
        chosen.add(download("ocean.json", ThemeTestSupport.uiThemeJson("Ocean")));
        choose(LIGHT, "import");
        assertThat(draft.value(CoreSettings.UI_THEME_LIGHT)).isEqualTo("ocean");
        assertThat(draft.modified(CoreSettings.UI_THEME_DARK)).isFalse();
    }

    @Test void importingFromTheTerminalGearPicksTheThemesFirstScheme() throws Exception {
        page("");
        chosen.add(download("gruvbox_dark.json", ThemeTestSupport.terminalThemeJson("Gruvbox Dark")));
        choose(TERMINAL, "import");
        assertThat(draft.value(CoreSettings.TERMINAL_SCHEME)).isEqualTo("gruvbox-dark/dark");
        assertThat(item(TERMINAL, "remove").label()).isEqualTo("Remove Gruvbox Dark");
    }

    @Test void aThemeWithNothingForThatDropdownIsImportedAndTheNoticeSaysWhereToPickIt() throws Exception {
        page("");
        chosen.add(download("gruvbox_dark.json", ThemeTestSupport.terminalThemeJson("Gruvbox Dark")));
        choose(DARK, "import");
        assertThat(folder().resolve("gruvbox-dark.json")).exists();
        assertThat(told).containsExactly(new ThemeImports.Notice("Imported Gruvbox Dark",
            "Gruvbox Dark is a terminal scheme; pick it under Terminal scheme."));
        chosen.add(download("dusk.json", ThemeTestSupport.uiThemeJson("Dusk").replaceAll("(?s),\\s*\"(light|high-contrast)\": \\{[^}]*}", "")));
        choose(LIGHT, "import");
        assertThat(told).last().isEqualTo(new ThemeImports.Notice("Imported Dusk", "Dusk has no Light variant; pick it under Dark theme."));
        assertThat(draft.modified()).as("nothing is picked").isFalse();
    }

    @Test void aFileThatCannotBeImportedSaysWhyAndWritesNothing() throws Exception {
        page("");
        chosen.add(download("half.json", ThemeTestSupport.terminalThemeJson("Half").replace("\"fg.bold\": \"#FBF1C7\",", "")));
        choose(DARK, "import");
        assertThat(told).containsExactly(new ThemeImports.Notice("Could not import half.json", "terminal.dark: Missing colour \"fg.bold\""));
        assertThat(folder()).doesNotExist();
        assertThat(reloads[0]).isZero();
        chosen.add(download("steel.json", ThemeTestSupport.uiThemeJson("Slate Steel")));
        choose(LIGHT, "import");
        assertThat(told).last().isEqualTo(new ThemeImports.Notice("Could not import steel.json", "Slate Steel is built in and can't be replaced"));
        assertThat(item(LIGHT, "import").enabled()).as("the gear is not left busy").isTrue();
    }

    @Test void importingTheSameThemeAgainAsksBeforeReplacingIt() throws Exception {
        page("");
        chosen.add(download("one.json", ThemeTestSupport.terminalThemeJson("Gruvbox Dark")));
        choose(TERMINAL, "import");
        var newer = download("two.json", ThemeTestSupport.terminalThemeJson("Gruvbox Dark").replace("#282828", "#1D2021"));
        agree = false;
        chosen.add(newer);
        choose(TERMINAL, "import");
        assertThat(asked).singleElement().satisfies(question -> {
            assertThat(question.question()).isEqualTo("Replace Gruvbox Dark?");
            assertThat(question.consequence()).isEqualTo("The imported Gruvbox Dark is replaced by two.json.");
            assertThat(question.yes()).isEqualTo("Replace");
        });
        assertThat(Files.readString(folder().resolve("gruvbox-dark.json"))).as("Cancel keeps the old file").doesNotContain("#1D2021");
        agree = true;
        chosen.add(newer);
        choose(TERMINAL, "import");
        assertThat(Files.readString(folder().resolve("gruvbox-dark.json"))).contains("#1D2021");
        assertThat(told).isEmpty();
    }

    @Test void theGearIsDisabledWhileItsFileWorkRuns() throws Exception {
        page("");
        holdWork = true;
        chosen.add(download("ocean.json", ThemeTestSupport.uiThemeJson("Ocean")));
        choose(DARK, "import");
        assertThat(gear(DARK).state().enabled()).isFalse();
        assertThat(gear(DARK).state().busy()).isTrue();
        chosen.add(download("dusk.json", ThemeTestSupport.uiThemeJson("Dusk")));
        choose(DARK, "import");
        assertThat(queued).as("a busy gear starts nothing more").hasSize(1);
        assertThat(chosen).as("nor asks for a file").hasSize(1);
        chosen.clear();
        while (!queued.isEmpty()) queued.poll().run();
        assertThat(gear(DARK).state().enabled()).isTrue();
        assertThat(draft.value(CoreSettings.UI_THEME_DARK)).isEqualTo("ocean");
    }

    @Test void aReloadThatFailsAfterAnImportSaysSo() throws Exception {
        page("");
        failReloads = true;
        chosen.add(download("ocean.json", ThemeTestSupport.uiThemeJson("Ocean")));
        choose(DARK, "import");
        assertThat(folder().resolve("ocean.json")).exists();
        assertThat(told).containsExactly(new ThemeImports.Notice("Imported Ocean",
            "Settings could not read the themes folder again: the service is closed"));
        assertThat(draft.modified()).isFalse();
    }

    @Test void removingTheShownThemeReturnsItsSavedAndStagedPicksToTheirDefaults() throws Exception {
        install("ocean.json", ThemeTestSupport.uiThemeJson("Ocean"));
        page("[ui.theme]\ndark = \"ocean\"\n[terminal]\nscheme = \"ocean/high-contrast\"\n");
        draft.stage(CoreSettings.UI_THEME_LIGHT, Optional.of("ocean"));
        draft.stage(CoreSettings.UI_FONT_SIZE, Optional.of(15f));
        assertThat(item(DARK, "remove").label()).isEqualTo("Remove Ocean");
        choose(DARK, "remove");
        assertThat(asked).singleElement().satisfies(question -> {
            assertThat(question.question()).isEqualTo("Remove Ocean?");
            assertThat(question.consequence()).isEqualTo("Its file, ocean.json, is deleted from the themes folder. "
                + "It is your Dark theme; Slate Steel will be used instead. It is your Light theme; Slate Steel will be used instead. "
                + "Its scheme is your terminal scheme; Same as UI theme will be used instead.");
        });
        assertThat(folder().resolve("ocean.json")).doesNotExist();
        assertThat(saved).singleElement().satisfies(edit -> {
            assertThat(edit.changes()).containsOnly(Map.entry(CoreSettings.UI_THEME_DARK, Optional.empty()),
                Map.entry(CoreSettings.TERMINAL_SCHEME, Optional.empty()));
            assertThat(edit.expected()).containsOnly(Map.entry(CoreSettings.UI_THEME_DARK, Optional.of("ocean")),
                Map.entry(CoreSettings.TERMINAL_SCHEME, Optional.of("ocean/high-contrast")));
        });
        assertThat(draft.value(CoreSettings.UI_THEME_LIGHT)).isEqualTo("slate-steel");
        assertThat(draft.modified(CoreSettings.UI_THEME_LIGHT)).as("the staged pick is gone, not replaced").isFalse();
        assertThat(draft.value(CoreSettings.UI_FONT_SIZE)).as("other staged changes stay").isEqualTo(15f);
        assertThat(told).isEmpty();
    }

    @Test void theTerminalGearRemovesTheThemeOfTheSchemeItShows() throws Exception {
        install("zenburn.json", ThemeTestSupport.terminalThemeJson("Zenburn"));
        page("[terminal]\nscheme = \"zenburn/dark\"\n");
        assertThat(item(TERMINAL, "remove").label()).isEqualTo("Remove Zenburn");
        choose(TERMINAL, "remove");
        assertThat(asked.getFirst().consequence()).isEqualTo("Its file, zenburn.json, is deleted from the themes folder. "
            + "Its scheme is your terminal scheme; Same as UI theme will be used instead.");
        assertThat(folder().resolve("zenburn.json")).doesNotExist();
        assertThat(saved).singleElement().satisfies(edit ->
            assertThat(edit.changes()).containsOnly(Map.entry(CoreSettings.TERMINAL_SCHEME, Optional.empty())));
    }

    @Test void removingAThemeThatIsOnlyStagedSavesNothingAndReloads() throws Exception {
        install("zenburn.json", ThemeTestSupport.terminalThemeJson("Zenburn"));
        page("");
        draft.stage(CoreSettings.TERMINAL_SCHEME, Optional.of("zenburn/dark"));
        choose(TERMINAL, "remove");
        assertThat(asked.getFirst().consequence()).isEqualTo("Its file, zenburn.json, is deleted from the themes folder. "
            + "Its scheme is your terminal scheme; Same as UI theme will be used instead.");
        assertThat(saved).isEmpty();
        assertThat(reloads[0]).isEqualTo(1);
        assertThat(draft.modified(CoreSettings.TERMINAL_SCHEME)).isFalse();
        assertThat(told).isEmpty();
    }

    @Test void decliningTheQuestionKeepsTheThemeAndItsPicks() throws Exception {
        install("ocean.json", ThemeTestSupport.uiThemeJson("Ocean"));
        page("[ui.theme]\ndark = \"ocean\"\n");
        agree = false;
        choose(DARK, "remove");
        assertThat(asked).hasSize(1);
        assertThat(folder().resolve("ocean.json")).exists();
        assertThat(saved).isEmpty();
        assertThat(draft.value(CoreSettings.UI_THEME_DARK)).isEqualTo("ocean");
    }

    @Test void aRemovalWhoseSettingsCannotBeSavedSaysSoAndStillDropsTheTheme() throws Exception {
        install("ocean.json", ThemeTestSupport.uiThemeJson("Ocean"));
        page("[ui.theme]\ndark = \"ocean\"\n");
        failSaves = true;
        choose(DARK, "remove");
        assertThat(folder().resolve("ocean.json")).doesNotExist();
        assertThat(reloads[0]).as("a failed save reads nothing back, so the gear has the folder read").isEqualTo(1);
        assertThat(told).containsExactly(new ThemeImports.Notice("Removed Ocean", "Its settings were not saved: config.toml has errors"));
        assertThat(installed().names()).as("the library no longer lists it").doesNotContainKey("ocean");
        assertThat(item(DARK, "remove").enabled()).isFalse();
        assertThat(gear(DARK).state().enabled()).as("the gear is not left busy").isTrue();
    }

    @Test void aRemovalWhoseSaveAndReloadBothFailStillEndsAndSaysSo() throws Exception {
        install("ocean.json", ThemeTestSupport.uiThemeJson("Ocean"));
        page("[ui.theme]\ndark = \"ocean\"\n");
        failSaves = true;
        failReloads = true;
        choose(DARK, "remove");
        assertThat(told).containsExactly(new ThemeImports.Notice("Removed Ocean", "Its settings were not saved: config.toml has errors"));
        assertThat(gear(DARK).state().enabled()).isTrue();
    }

    @Test void aRemovalWithNothingToSaveThatCannotReloadSaysSoWithoutReloadingAgain() throws Exception {
        install("zenburn.json", ThemeTestSupport.terminalThemeJson("Zenburn"));
        page("");
        draft.stage(CoreSettings.TERMINAL_SCHEME, Optional.of("zenburn/dark"));
        failReloads = true;
        choose(TERMINAL, "remove");
        assertThat(reloads[0]).isEqualTo(1);
        assertThat(told).containsExactly(new ThemeImports.Notice("Removed Zenburn",
            "Settings could not read the themes folder again: the service is closed"));
        assertThat(gear(TERMINAL).state().enabled()).isTrue();
    }

    /** A path whose file system fails with an unchecked exception, as a broken provider would, but that names a file. */
    static Path broken(String name) {
        return (Path) java.lang.reflect.Proxy.newProxyInstance(Path.class.getClassLoader(), new Class<?>[] {Path.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getFileName", "toString" -> method.getName().equals("toString") ? name : Path.of(name);
                default -> throw new IllegalStateException("the file system failed");
            });
    }

    @Test void anUnexpectedFailureWhilePreparingAnImportIsReportedAndLeavesTheGearUsable() throws Exception {
        page("");
        chosen.add(broken("odd.json"));
        choose(DARK, "import");
        assertThat(told).containsExactly(new ThemeImports.Notice("Could not import odd.json", "the file system failed"));
        assertThat(gear(DARK).state().enabled()).isTrue();
        chosen.add(download("ocean.json", ThemeTestSupport.uiThemeJson("Ocean")));
        choose(DARK, "import");
        assertThat(draft.value(CoreSettings.UI_THEME_DARK)).isEqualTo("ocean");
    }

    @Test void aFailedDeleteIsShownAndChangesNothing() throws Exception {
        install("ocean.json", ThemeTestSupport.uiThemeJson("Ocean"));
        page("[ui.theme]\ndark = \"ocean\"\n");
        Files.delete(folder().resolve("ocean.json"));
        choose(DARK, "remove");
        assertThat(told).containsExactly(new ThemeImports.Notice("Could not remove Ocean", "the file is no longer there."));
        assertThat(saved).isEmpty();
        assertThat(draft.modified()).isFalse();
    }
}
