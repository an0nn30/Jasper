package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.ViewMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;

class AppearancePageTest {
    private final List<String> names = new ArrayList<>(List.of("Darcula", "Jasper Dark", "Jasper Light"));
    private final List<String> calls = new ArrayList<>();
    private final ThemeChoices themes = new ThemeChoices(() -> List.copyOf(names),
        context -> new ThemeChoices.Import(() -> calls.add("import"), () -> calls.add("closed")));
    private final SettingsDraft draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values());
    private final PageContext context = new PageContext(draft, id -> { }, Runnable::run, (title, initial) -> Optional.empty(), () -> { });
    private final CorePages.SystemActions system = new CorePages.SystemActions(() -> Path.of("config.toml"),
        () -> { }, () -> { }, () -> { }, () -> { }, () -> false);
    private final SchemaPage.Spec spec = CorePages.specs(themes, system, ShellChoices.NONE, id -> { }).stream()
        .filter(spec -> spec.id().equals("appearance")).findFirst().orElseThrow();
    private final SchemaPage page = spec.build(context);

    private Editors.ThemePick editor(int row) { page.component(); return (Editors.ThemePick) page.rows().get(row).editor(); }
    private static List<String> items(JComboBox<String> box) {
        var items = new ArrayList<String>();
        for (int i = 0; i < box.getItemCount(); i++) items.add(box.getItemAt(i));
        return items;
    }

    @Test void viewModeOffersNormalAndCompact() {
        page.component();
        var setting = spec.settings().stream().filter(item -> item.name().equals("window.view_mode"))
            .findFirst().orElseThrow();
        var row = page.rows().get(spec.settings().indexOf(setting));
        @SuppressWarnings("unchecked")
        var box = (JComboBox<String>) row.editor().component();
        assertThat(items(box)).containsExactly("Normal", "Compact");
        assertThat(box.getSelectedItem()).isEqualTo("Normal");
        assertThat(String.join(" ", page.searchTerms()).toLowerCase(java.util.Locale.ROOT)).contains("compact");
    }

    @Test void choosingCompactStagesItForApplyAndResetRestoresNormal() {
        page.component();
        var setting = spec.settings().stream().filter(item -> item.name().equals("window.view_mode"))
            .findFirst().orElseThrow();
        var row = page.rows().get(spec.settings().indexOf(setting));
        var box = (JComboBox<?>) row.editor().component();
        assertThat(row.comment().getText()).contains("Takes effect after restart");
        assertThat(draft.modified()).isFalse();
        box.setSelectedItem("Compact");
        assertThat(draft.value(CoreSettings.WINDOW_VIEW_MODE)).isEqualTo(ViewMode.COMPACT);
        assertThat(draft.edit().changes()).containsEntry(CoreSettings.WINDOW_VIEW_MODE, Optional.of(ViewMode.COMPACT));
        assertThat(page.modified()).isTrue();
        assertThat(row.reset().isVisible()).isTrue();
        row.reset().doClick();
        assertThat(draft.value(CoreSettings.WINDOW_VIEW_MODE)).isEqualTo(ViewMode.NORMAL);
        assertThat(box.getSelectedItem()).isEqualTo("Normal");
        assertThat(draft.modified()).isFalse();
        assertThat(row.reset().isVisible()).isFalse();
    }

    @Test void resettingASavedCompactModeStagesRemovalOfTheKey() {
        draft.rebase(ConfigLoader.parse(Path.of("config.toml"), "window.view_mode = 'compact'", true).values());
        page.component();
        var setting = spec.settings().stream().filter(item -> item.name().equals("window.view_mode"))
            .findFirst().orElseThrow();
        var row = page.rows().get(spec.settings().indexOf(setting));
        var box = (JComboBox<?>) row.editor().component();
        assertThat(box.getSelectedItem()).isEqualTo("Compact");
        assertThat(draft.modified()).isFalse();
        row.reset().doClick();
        assertThat(box.getSelectedItem()).isEqualTo("Normal");
        assertThat(draft.edit().changes()).containsEntry(CoreSettings.WINDOW_VIEW_MODE, Optional.empty());
        assertThat(draft.edit().expected()).containsEntry(CoreSettings.WINDOW_VIEW_MODE, Optional.of(ViewMode.COMPACT));
        assertThat(draft.modified()).isTrue();
    }

    @Test void theThemeAndWorkspaceSchemeAreDropdownsOfEveryThemeName() {
        var theme = editor(0);
        var workspace = editor(1);
        assertThat(List.of(CoreSettings.UI_THEME_NAME.label(), CoreSettings.UI_THEME_WORKSPACE.label())).containsExactly("Theme", "Workspace scheme");
        assertThat(items(theme.box)).containsExactly("Darcula", "Jasper Dark", "Jasper Light");
        assertThat(theme.box.getSelectedItem()).isEqualTo("Jasper Dark");
        assertThat(items(workspace.box)).containsExactly("Same as UI theme", "Darcula", "Jasper Dark", "Jasper Light");
        assertThat(workspace.box.getSelectedItem()).isEqualTo("Same as UI theme");
        assertThat(workspace.gear).as("only the theme has the gear").isNull();
        assertThat(page.searchTerms()).contains("Workspace scheme", "Same as UI theme", "Import theme…");
    }

    @Test void choosingStagesTheChoiceForApply() {
        var theme = editor(0);
        var workspace = editor(1);
        theme.box.setSelectedItem("Darcula");
        assertThat(draft.value(CoreSettings.UI_THEME_NAME)).isEqualTo("Darcula");
        workspace.box.setSelectedItem("Jasper Light");
        assertThat(draft.value(CoreSettings.UI_THEME_WORKSPACE)).isEqualTo("Jasper Light");
        workspace.box.setSelectedItem("Same as UI theme");
        assertThat(draft.value(CoreSettings.UI_THEME_WORKSPACE)).isEqualTo("match");
        assertThat(page.modified()).isTrue();
    }

    @Test void bothDropdownsSearchAndEnterStagesTheMatch() {
        var theme = editor(0);
        var keys = ((SearchCombo) theme.box).searchPopup().getKeyListener();
        for (char c : "darc".toCharArray())
            keys.keyTyped(new java.awt.event.KeyEvent(theme.box, java.awt.event.KeyEvent.KEY_TYPED, 0, 0, java.awt.event.KeyEvent.VK_UNDEFINED, c));
        assertThat(((SearchCombo) theme.box).searchPopup().shown()).containsExactly("Darcula");
        keys.keyPressed(new java.awt.event.KeyEvent(theme.box, java.awt.event.KeyEvent.KEY_PRESSED, 0, 0, java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.CHAR_UNDEFINED));
        assertThat(draft.value(CoreSettings.UI_THEME_NAME)).isEqualTo("Darcula");
        var workspace = ((SearchCombo) editor(1).box).searchPopup();
        workspace.getKeyListener().keyTyped(new java.awt.event.KeyEvent(editor(1).box, java.awt.event.KeyEvent.KEY_TYPED, 0, 0, java.awt.event.KeyEvent.VK_UNDEFINED, 'l'));
        assertThat(workspace.shown()).as("Same as UI theme stays pinned").containsExactly("Same as UI theme", "Darcula", "Jasper Light");
    }

    @Test void pickingTheEntryAlreadyShownIsNoEdit() {
        var theme = editor(0);
        theme.box.setSelectedItem("Jasper Dark");
        assertThat(draft.modified()).as("the default stays unsaved").isFalse();
        theme.box.setSelectedItem("Darcula");
        theme.box.setSelectedItem("Darcula");
        assertThat(draft.value(CoreSettings.UI_THEME_NAME)).isEqualTo("Darcula");
    }

    @Test void theListsFollowTheCatalogAndKeepAChoiceItNoLongerHas() {
        var theme = editor(0);
        draft.stage(CoreSettings.UI_THEME_NAME, Optional.of("Gone"));
        assertThat(theme.box.getSelectedItem()).isEqualTo("Gone (not found)");
        assertThat(theme.value()).isEqualTo("Gone");
        names.add("Nord");
        page.refresh();
        assertThat(items(theme.box)).containsExactly("Darcula", "Jasper Dark", "Jasper Light", "Nord", "Gone (not found)");
        assertThat(theme.value()).as("a refresh keeps the choice").isEqualTo("Gone");
        theme.box.setSelectedItem("Nord");
        assertThat(items(theme.box)).as("only the shown missing choice is listed").doesNotContain("Gone (not found)");
        draft.stage(CoreSettings.UI_THEME_NAME, Optional.of(" darcula "));
        assertThat(theme.box.getSelectedItem()).as("names match as the catalog does").isEqualTo("Darcula");
        assertThat(theme.value()).as("and are not rewritten").isEqualTo(" darcula ");
    }

    @Test void theGearOffersImportThemeAndThePageClosesIt() {
        var theme = editor(0);
        assertThat(theme.gear.getAccessibleContext().getAccessibleName()).isEqualTo("Theme actions");
        assertThat(theme.menu.getComponents()).extracting(item -> ((JMenuItem) item).getText()).containsExactly("Import theme…");
        ((JMenuItem) theme.menu.getComponent(0)).doClick();
        assertThat(calls).containsExactly("import");
        page.dispose();
        assertThat(calls).containsExactly("import", "closed");
    }
}
