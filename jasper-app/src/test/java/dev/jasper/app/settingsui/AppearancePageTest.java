package dev.jasper.app.settingsui;

import static dev.jasper.app.settingsui.PageKit.kit;
import static dev.jasper.app.settingsui.PageKit.node;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.ViewMode;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.plaf.basic.ComboPopup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EdtTestExtension.class)
class AppearancePageTest {
    private final List<String> names = new ArrayList<>(List.of("Darcula", "Jasper Dark", "Jasper Light"));
    private final List<String> calls = new ArrayList<>();
    private final ThemeChoices themes = new ThemeChoices(() -> List.copyOf(names),
        context -> new ThemeChoices.Import(() -> calls.add("import"), () -> calls.add("closed")));
    private final SettingsDraft draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values());
    private final PageContext context = PageKit.context(draft, id -> { }, () -> { });
    private final CorePages.SystemActions system = new CorePages.SystemActions(() -> Path.of("config.toml"),
        () -> { }, () -> { }, () -> { }, () -> { }, () -> false);
    private final SchemaPage.Spec spec = CorePages.specs(themes, system, ShellChoices.NONE, id -> { }).stream()
        .filter(spec -> spec.id().equals("appearance")).findFirst().orElseThrow();
    private final SchemaPage page = spec.build(context);

    private Editors.ThemePick editor(int row) { page.component(); return (Editors.ThemePick) page.rows().get(row).editor(); }
    private JComboBox<?> combo(String setting) { return kit(page, setting + ".input", JComboBox.class); }
    private SchemaPage.Row<?> row(String name) {
        page.component();
        return page.rows().stream().filter(row -> row.setting().name().equals(name)).findFirst().orElseThrow();
    }
    private static List<String> items(JComboBox<?> box) {
        var items = new ArrayList<String>();
        for (int i = 0; i < box.getItemCount(); i++) items.add(((dev.jasper.ui.model.Option) box.getItemAt(i)).label());
        return items;
    }
    private static String selected(JComboBox<?> box) { return ((dev.jasper.ui.model.Option) box.getSelectedItem()).label(); }
    private static void pick(JComboBox<?> box, String label) { box.setSelectedIndex(items(box).indexOf(label)); }

    @Test void viewModeOffersNormalAndCompact() {
        var box = combo("window.view_mode");
        assertThat(node(page, "window.view_mode")).isInstanceOfSatisfying(Nodes.Choice.class,
            choice -> assertThat(choice.kind()).isEqualTo(Nodes.ChoiceKind.DROPDOWN));
        assertThat(items(box)).containsExactly("Normal", "Compact");
        assertThat(selected(box)).isEqualTo("Normal");
        assertThat(String.join(" ", page.searchTerms()).toLowerCase(java.util.Locale.ROOT)).contains("compact");
    }

    @Test void choosingCompactStagesItForApplyAndResetRestoresNormal() {
        var row = row("window.view_mode");
        var box = combo("window.view_mode");
        assertThat(row.comment()).contains("Takes effect after restart");
        assertThat(draft.modified()).isFalse();
        pick(box, "Compact");
        assertThat(draft.value(CoreSettings.WINDOW_VIEW_MODE)).isEqualTo(ViewMode.COMPACT);
        assertThat(draft.edit().changes()).containsEntry(CoreSettings.WINDOW_VIEW_MODE, Optional.of(ViewMode.COMPACT));
        assertThat(page.modified()).isTrue();
        var reset = kit(page, "window.view_mode:reset", JButton.class);
        assertThat(reset.isVisible()).isTrue();
        reset.doClick();
        assertThat(draft.value(CoreSettings.WINDOW_VIEW_MODE)).isEqualTo(ViewMode.NORMAL);
        assertThat(selected(box)).isEqualTo("Normal");
        assertThat(draft.modified()).isFalse();
        assertThat(reset.isVisible()).isFalse();
    }

    @Test void resettingASavedCompactModeStagesRemovalOfTheKey() {
        draft.rebase(ConfigLoader.parse(Path.of("config.toml"), "window.view_mode = 'compact'", true).values());
        var box = combo("window.view_mode");
        assertThat(selected(box)).isEqualTo("Compact");
        assertThat(draft.modified()).isFalse();
        kit(page, "window.view_mode:reset", JButton.class).doClick();
        assertThat(selected(box)).isEqualTo("Normal");
        assertThat(draft.edit().changes()).containsEntry(CoreSettings.WINDOW_VIEW_MODE, Optional.empty());
        assertThat(draft.edit().expected()).containsEntry(CoreSettings.WINDOW_VIEW_MODE, Optional.of(ViewMode.COMPACT));
        assertThat(draft.modified()).isTrue();
    }

    @Test void theThemeAndWorkspaceSchemeAreSearchableDropdownsOfEveryThemeName() {
        var theme = editor(0);
        var workspace = editor(1);
        assertThat(List.of(CoreSettings.UI_THEME_NAME.label(), CoreSettings.UI_THEME_WORKSPACE.label())).containsExactly("Theme", "Workspace scheme");
        assertThat(((Nodes.Choice) node(page, "ui.theme.name")).kind()).isEqualTo(Nodes.ChoiceKind.SEARCHABLE);
        assertThat(((Nodes.Choice) node(page, "ui.theme.name")).search().orElseThrow().pinned()).isZero();
        assertThat(((Nodes.Choice) node(page, "ui.theme.workspace")).search().orElseThrow().pinned()).as("Same as UI theme stays pinned").isEqualTo(1);
        assertThat(items(combo("ui.theme.name"))).containsExactly("Darcula", "Jasper Dark", "Jasper Light");
        assertThat(theme.labels()).containsExactly("Darcula", "Jasper Dark", "Jasper Light");
        assertThat(selected(combo("ui.theme.name"))).isEqualTo("Jasper Dark");
        assertThat(items(combo("ui.theme.workspace"))).containsExactly("Same as UI theme", "Darcula", "Jasper Dark", "Jasper Light");
        assertThat(selected(combo("ui.theme.workspace"))).isEqualTo("Same as UI theme");
        assertThat(workspace.hasActions()).as("only the theme has the gear").isFalse();
        assertThat(PageKit.kit(page, "ui.theme.name:actions.main", JButton.class)).isNotNull();
        assertThat(page.searchTerms()).contains("Workspace scheme", "Same as UI theme", "Import theme…");
    }

    @Test void choosingStagesTheChoiceForApply() {
        pick(combo("ui.theme.name"), "Darcula");
        assertThat(draft.value(CoreSettings.UI_THEME_NAME)).isEqualTo("Darcula");
        pick(combo("ui.theme.workspace"), "Jasper Light");
        assertThat(draft.value(CoreSettings.UI_THEME_WORKSPACE)).isEqualTo("Jasper Light");
        pick(combo("ui.theme.workspace"), "Same as UI theme");
        assertThat(draft.value(CoreSettings.UI_THEME_WORKSPACE)).isEqualTo("match");
        assertThat(page.modified()).isTrue();
    }

    @Test void animationSectionOnlyStagesTheSharedPolicyAndHasNoDurationEditors() {
        page.component();
        var animationRows = page.rows().stream().filter(row -> row.setting().table().equals(List.of("ui", "animations"))).toList();
        assertThat(animationRows).extracting(row -> row.setting().name()).containsExactly("ui.animations.enabled");
        assertThat(node(page, "ui.animations.enabled")).isInstanceOf(Nodes.Toggle.class);
        kit(page, "ui.animations.enabled", JButton.class).doClick();
        assertThat(draft.value(CoreSettings.UI_ANIMATIONS_ENABLED)).isFalse();
        assertThat(page.modified()).isTrue();
        assertThat(page.searchTerms()).doesNotContain("Open tab duration", "Close tab duration", "Switch tab duration");
    }

    private static List<String> shown(JComboBox<?> box) {
        var list = ((ComboPopup) box.getUI().getAccessibleChild(box, 0)).getList();
        var shown = new ArrayList<String>();
        for (int i = 0; i < list.getModel().getSize(); i++) shown.add(String.valueOf(list.getModel().getElementAt(i)));
        return shown;
    }

    private static void type(JComboBox<?> box, String text) {
        // The dropdown's own key listeners, as Swing delivers a typed key to the focused dropdown.
        for (char c : text.toCharArray()) {
            var typed = new KeyEvent(box, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, c);
            for (var listener : box.getKeyListeners()) listener.keyTyped(typed);
        }
    }

    @Test void bothDropdownsSearchAndEnterStagesTheMatch() {
        var theme = combo("ui.theme.name");
        type(theme, "darc");
        assertThat(shown(theme)).containsExactly("Darcula");
        var enter = new KeyEvent(theme, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED);
        for (var listener : theme.getKeyListeners()) listener.keyPressed(enter);
        assertThat(enter.isConsumed()).as("Enter picks and never reaches the dialog's OK").isTrue();
        assertThat(draft.value(CoreSettings.UI_THEME_NAME)).isEqualTo("Darcula");
        var workspace = combo("ui.theme.workspace");
        type(workspace, "l");
        assertThat(shown(workspace)).as("Same as UI theme stays pinned").containsExactly("Same as UI theme", "Darcula", "Jasper Light");
    }

    @Test void pickingTheEntryAlreadyShownIsNoEdit() {
        pick(combo("ui.theme.name"), "Jasper Dark");
        assertThat(draft.modified()).as("the default stays unsaved").isFalse();
        pick(combo("ui.theme.name"), "Darcula");
        pick(combo("ui.theme.name"), "Darcula");
        assertThat(draft.value(CoreSettings.UI_THEME_NAME)).isEqualTo("Darcula");
    }

    @Test void theListsFollowTheCatalogAndKeepAChoiceItNoLongerHas() {
        var theme = editor(0);
        draft.stage(CoreSettings.UI_THEME_NAME, Optional.of("Gone"));
        assertThat(selected(combo("ui.theme.name"))).isEqualTo("Gone (not found)");
        assertThat(theme.value()).isEqualTo("Gone");
        names.add("Nord");
        page.refresh();
        assertThat(items(combo("ui.theme.name"))).containsExactly("Darcula", "Jasper Dark", "Jasper Light", "Nord", "Gone (not found)");
        assertThat(theme.value()).as("a refresh keeps the choice").isEqualTo("Gone");
        pick(combo("ui.theme.name"), "Nord");
        assertThat(items(combo("ui.theme.name"))).as("only the shown missing choice is listed").doesNotContain("Gone (not found)");
        draft.stage(CoreSettings.UI_THEME_NAME, Optional.of(" darcula "));
        assertThat(selected(combo("ui.theme.name"))).as("names match as the catalog does").isEqualTo("Darcula");
        assertThat(theme.value()).as("and are not rewritten").isEqualTo(" darcula ");
    }

    @Test void theGearOffersImportThemeAndThePageClosesIt() {
        page.component();
        var gear = (Nodes.MenuButton) node(page, "ui.theme.name:actions");
        assertThat(gear.tooltip()).isEqualTo("Theme actions");
        assertThat(kit(page, "ui.theme.name:actions.main", JButton.class).getAccessibleContext().getAccessibleName()).isEqualTo("Theme actions");
        assertThat(gear.items()).extracting(dev.jasper.ui.model.MenuItem::label).containsExactly("Import theme…", "Reset to defaults");
        // Press the gear menu's own item, as the user does (opening a native popup needs a screen, so the test
        // presses the mounted item directly).
        var menu = kit(page, "ui.theme.name:actions", javax.swing.JComponent.class).getComponentPopupMenu();
        var items = java.util.Arrays.stream(menu.getComponents()).filter(javax.swing.AbstractButton.class::isInstance)
            .map(javax.swing.AbstractButton.class::cast).toList();
        assertThat(items).extracting(javax.swing.AbstractButton::getText).containsExactly("Import theme…", "Reset to defaults");
        items.getFirst().doClick();
        assertThat(calls).containsExactly("import");
        page.dispose();
        assertThat(calls).containsExactly("import", "closed");
    }
}
