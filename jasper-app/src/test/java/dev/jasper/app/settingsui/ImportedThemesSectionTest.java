package dev.jasper.app.settingsui;

import static dev.jasper.app.settingsui.PageKit.kit;
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
import dev.jasper.ui.model.Nodes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/** spec 4.1–4.3: the Imported themes group lists the imported themes and imports and removes theme files at once. */
@ExtendWith(EdtTestExtension.class)
class ImportedThemesSectionTest {
    @TempDir Path home;
    final ArrayDeque<Path> chosen = new ArrayDeque<>();
    final List<ThemeImports.Question> asked = new ArrayList<>();
    final List<SettingsEdit> saved = new ArrayList<>();
    final int[] reloads = new int[1];
    boolean agree = true, failSaves;
    SettingsDraft draft;
    SchemaPage page;

    Path folder() { return home.resolve("themes"); }

    Path download(String fileName, String text) throws Exception {
        return Files.writeString(Files.createDirectories(home.resolve("downloads")).resolve(fileName), text);
    }

    /** The page over {@code config}, with hooks that run at once and answer questions with {@link #agree}. */
    SchemaPage page(String config) {
        draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), config, true).values());
        var imports = new ThemeImports(new ThemeFolder(folder()),
            () -> ThemeLibrary.read(folder(), ThemeLibrary.bundledOnly()).imported().stream()
                .map(theme -> new ThemeImports.Imported(theme.id(), theme.name(), theme.has(true) ? "UI theme" : "Terminal scheme")).toList(),
            surface -> Optional.ofNullable(chosen.poll()),
            (surface, question) -> { asked.add(question); if (agree) question.onYes().run(); },
            edit -> { saved.add(edit); return failSaves ? CompletableFuture.failedFuture(new java.util.concurrent.CompletionException(
                new IllegalStateException("config.toml has errors"))) : CompletableFuture.completedFuture(null); },
            () -> { reloads[0]++; return CompletableFuture.completedFuture(null); },
            Runnable::run, Runnable::run);
        var themes = AppearanceFixtures.CHOICES.themes();
        var choices = new AppearanceChoices(100f, false, Optional::empty, ignored -> new Subscription(() -> { }), themes, Optional.of(imports));
        var system = new CorePages.SystemActions(() -> Path.of("config.toml"), () -> { }, () -> { }, () -> { }, () -> { }, () -> false);
        page = CorePages.specs(choices, system, ShellChoices.NONE, id -> { }).stream().filter(spec -> spec.id().equals("appearance"))
            .findFirst().orElseThrow().build(PageKit.context(draft, id -> { }, () -> { }));
        page.component();
        return page;
    }

    void click(String id) { kit(page, id, JButton.class).doClick(); }
    String title(String id) { return ((Nodes.SettingRow) node(page, id)).title(); }
    Nodes.Text message() { return (Nodes.Text) node(page, "imported-themes:message"); }

    @Test void withNothingImportedTheGroupOffersImport() {
        page("");
        assertThat(title("imported-themes:import-row")).isEqualTo("No imported themes");
        assertThat(node(page, "section-imported-themes")).isInstanceOfSatisfying(Nodes.Section.class,
            section -> assertThat(section.title()).isEqualTo("Imported themes"));
        assertThat(page.searchTerms()).contains("Imported themes", "Import theme");
        click("imported-themes:import");
        assertThat(reloads[0]).as("cancelling the chooser does nothing").isZero();
    }

    @Test void importingCopiesTheFileInUnderItsIdAndListsIt() throws Exception {
        page("");
        var file = download("gruvbox_dark.json", ThemeTestSupport.terminalThemeJson("Gruvbox Dark"));
        chosen.add(file);
        click("imported-themes:import");
        assertThat(Files.readAllBytes(folder().resolve("gruvbox-dark.json"))).isEqualTo(Files.readAllBytes(file));
        assertThat(reloads[0]).isEqualTo(1);
        assertThat(title("imported-themes:theme-0")).isEqualTo("Gruvbox Dark");
        assertThat(((Nodes.SettingRow) node(page, "imported-themes:theme-0")).description()).isEqualTo("Terminal scheme");
        assertThat(title("imported-themes:import-row")).isEqualTo("Jasper theme files");
        assertThat(message().text()).isEqualTo("Imported Gruvbox Dark.");
        assertThat(message().severity()).isEmpty();
        assertThat(asked).isEmpty();
    }

    @Test void aFileThatCannotBeImportedSaysWhyUnderImportAndWritesNothing() throws Exception {
        page("");
        chosen.add(download("half.json", ThemeTestSupport.terminalThemeJson("Half").replace("\"fg.bold\": \"#FBF1C7\",", "")));
        click("imported-themes:import");
        assertThat(message().text()).isEqualTo("Could not import half.json: terminal.dark: Missing colour \"fg.bold\"");
        assertThat(message().severity()).contains(Nodes.Severity.DANGER);
        assertThat(folder()).doesNotExist();
        assertThat(reloads[0]).isZero();
        chosen.add(download("steel.json", ThemeTestSupport.uiThemeJson("Slate Steel")));
        click("imported-themes:import");
        assertThat(message().text()).isEqualTo("Could not import steel.json: Slate Steel is built in and can't be replaced");
    }

    @Test void importingTheSameThemeAgainAsksBeforeReplacingIt() throws Exception {
        page("");
        chosen.add(download("one.json", ThemeTestSupport.terminalThemeJson("Gruvbox Dark")));
        click("imported-themes:import");
        var newer = download("two.json", ThemeTestSupport.terminalThemeJson("Gruvbox Dark").replace("#282828", "#1D2021"));
        agree = false;
        chosen.add(newer);
        click("imported-themes:import");
        assertThat(asked).singleElement().satisfies(question -> {
            assertThat(question.question()).isEqualTo("Replace Gruvbox Dark?");
            assertThat(question.consequence()).isEqualTo("The imported Gruvbox Dark is replaced by two.json.");
            assertThat(question.yes()).isEqualTo("Replace");
        });
        assertThat(Files.readString(folder().resolve("gruvbox-dark.json"))).as("Cancel keeps the old file").doesNotContain("#1D2021");
        agree = true;
        chosen.add(newer);
        click("imported-themes:import");
        assertThat(Files.readString(folder().resolve("gruvbox-dark.json"))).contains("#1D2021");
        assertThat(message().text()).isEqualTo("Imported Gruvbox Dark.");
    }

    @Test void removingAThemeInUseReturnsItsSavedAndStagedPicksToTheirDefaults() throws Exception {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve("ocean.json"), ThemeTestSupport.uiThemeJson("Ocean"));
        page("[ui.theme]\ndark = \"ocean\"\n[terminal]\nscheme = \"ocean/high-contrast\"\n");
        draft.stage(CoreSettings.UI_THEME_LIGHT, Optional.of("ocean"));
        draft.stage(CoreSettings.UI_FONT_SIZE, Optional.of(15f));
        click("imported-themes:remove-0");
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
        assertThat(message().text()).isEqualTo("Removed Ocean.");
        assertThat(title("imported-themes:import-row")).isEqualTo("No imported themes");
    }

    @Test void removingAThemeNotInUseOnlyDeletesItAndReloads() throws Exception {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve("zenburn.json"), ThemeTestSupport.terminalThemeJson("Zenburn"));
        page("");
        click("imported-themes:remove-0");
        assertThat(asked.getFirst().consequence()).isEqualTo("Its file, zenburn.json, is deleted from the themes folder.");
        assertThat(saved).isEmpty();
        assertThat(reloads[0]).isEqualTo(1);
        assertThat(message().text()).isEqualTo("Removed Zenburn.");
    }

    @Test void aRemovalWhoseSettingsCannotBeSavedSaysSo() throws Exception {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve("ocean.json"), ThemeTestSupport.uiThemeJson("Ocean"));
        page("[ui.theme]\ndark = \"ocean\"\n");
        failSaves = true;
        click("imported-themes:remove-0");
        assertThat(folder().resolve("ocean.json")).doesNotExist();
        assertThat(message().text()).isEqualTo("Removed Ocean. Its settings were not saved: config.toml has errors");
        assertThat(message().severity()).contains(Nodes.Severity.DANGER);
    }

    @Test void aFailedDeleteIsShownAndChangesNothing() throws Exception {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve("ocean.json"), ThemeTestSupport.uiThemeJson("Ocean"));
        page("[ui.theme]\ndark = \"ocean\"\n");
        Files.delete(folder().resolve("ocean.json"));
        click("imported-themes:remove-0");
        assertThat(message().text()).isEqualTo("Could not remove ocean.json: the file is no longer there.");
        assertThat(message().severity()).contains(Nodes.Severity.DANGER);
        assertThat(saved).isEmpty();
        assertThat(draft.modified()).isFalse();
    }
}
