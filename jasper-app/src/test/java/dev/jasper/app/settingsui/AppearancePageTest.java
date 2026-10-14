package dev.jasper.app.settingsui;

import static dev.jasper.app.settingsui.PageKit.kit;
import static dev.jasper.app.settingsui.PageKit.node;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.ViewMode;
import dev.jasper.app.lifecycle.Subscription;
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
    private final AppearanceChoices appearance = AppearanceFixtures.CHOICES;
    private final SettingsDraft draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values());
    private final PageContext context = PageKit.context(draft, id -> { }, () -> { });
    private final CorePages.SystemActions system = new CorePages.SystemActions(() -> Path.of("config.toml"),
        () -> { }, () -> { }, () -> { }, () -> { }, () -> false);
    private final SchemaPage.Spec spec = CorePages.specs(appearance, system, ShellChoices.NONE, id -> { }).stream()
        .filter(spec -> spec.id().equals("appearance")).findFirst().orElseThrow();
    private final SchemaPage page = spec.build(context);

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
    /** Slate Steel, with Ocean as a second Dark theme and Dawn as a second Light theme, so a pick other than Slate Steel is installed. */
    private static final InstalledThemes TWO_EACH = new InstalledThemes(
        List.of(new InstalledThemes.ThemeOption("slate-steel", "Slate Steel", AppearanceFixtures.preview(true)),
            new InstalledThemes.ThemeOption("ocean", "Ocean", AppearanceFixtures.preview(true))),
        List.of(new InstalledThemes.ThemeOption("slate-steel", "Slate Steel", AppearanceFixtures.preview(false)),
            new InstalledThemes.ThemeOption("dawn", "Dawn", AppearanceFixtures.preview(false))),
        InstalledThemes.slateSteel(AppearanceFixtures.preview(true), AppearanceFixtures.preview(false)).schemes(),
        java.util.Map.of("slate-steel", "Slate Steel", "ocean", "Ocean", "dawn", "Dawn"));
    /** The Appearance page over {@code themes}, built on the test's draft. */
    private SchemaPage pageWith(InstalledThemes themes) {
        var choices = new AppearanceChoices(100f, false, Optional::empty, ignored -> new Subscription(() -> { }), () -> themes, Optional.empty());
        var appearanceSpec = CorePages.specs(choices, system, ShellChoices.NONE, id -> { }).stream()
            .filter(spec -> spec.id().equals("appearance")).findFirst().orElseThrow();
        return appearanceSpec.build(context);
    }

    /** {@code target}'s left edge within {@code page}'s body, laid out at a Settings window's width. */
    private static int left(SchemaPage page, String target) {
        var body = page.component();
        var viewport = new javax.swing.JViewport();
        viewport.setSize(900, 2400);
        viewport.setView(body);
        layOut(viewport);
        return javax.swing.SwingUtilities.convertPoint(kit(page, target, java.awt.Component.class), 0, 0, body).x;
    }
    private static void layOut(java.awt.Component component) {
        if (!(component instanceof java.awt.Container container)) return;
        container.doLayout();
        for (var child : container.getComponents()) layOut(child);
    }

    @Test void theDarkLightAndTerminalDropdownsLineUp() {
        int dark = left(page, "ui.appearance:dark-theme.input");
        assertThat(List.of(left(page, "ui.appearance:light-theme.input"), left(page, "terminal.scheme.input")))
            .as("the Light row keeps the Reset lane the other rows have").containsOnly(dark);
    }

    @Test void withImportsEachThemeDropdownHasAGearAndTheyLineUp() {
        var imports = new ThemeImports(new dev.jasper.app.appearance.ThemeFolder(Path.of("themes")), surface -> Optional.empty(),
            (surface, question) -> { }, (surface, notice) -> { }, edit -> java.util.concurrent.CompletableFuture.completedFuture(null),
            () -> java.util.concurrent.CompletableFuture.completedFuture(null), Runnable::run, Runnable::run);
        var withGears = CorePages.specs(new AppearanceChoices(100f, false, Optional::empty, ignored -> new Subscription(() -> { }), () -> TWO_EACH,
                Optional.of(imports)), system, ShellChoices.NONE, id -> { }).stream()
            .filter(spec -> spec.id().equals("appearance")).findFirst().orElseThrow().build(context);
        int dark = left(withGears, "ui.appearance:dark-theme.input");
        assertThat(List.of(left(withGears, "ui.appearance:light-theme.input"), left(withGears, "terminal.scheme.input"))).containsOnly(dark);
        int gear = left(withGears, "ui.appearance:dark-gear.main");
        assertThat(gear).as("the gear follows its dropdown").isGreaterThan(dark);
        assertThat(List.of(left(withGears, "ui.appearance:light-gear.main"), left(withGears, "terminal.scheme:gear.main"))).containsOnly(gear);
        assertThatThrownBy(() -> kit(page, "ui.appearance:dark-gear.main", java.awt.Component.class))
            .as("pages built without an application have no gears").isInstanceOf(Throwable.class);
    }

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

    @Test void choosingTheLightTileStagesTheAppearance() {
        page.component();
        kit(page, "ui.appearance:policy.option.light", JButton.class).doClick(0);
        assertThat(draft.value(CoreSettings.UI_APPEARANCE)).isEqualTo(dev.jasper.app.config.AppearanceMode.LIGHT);
        assertThat(page.modified()).isTrue();
    }

    @Test void pickingADarkThemeStagesItForApplyAndResetReturnsToSlateSteel() {
        page.component();
        var box = kit(page, "ui.appearance:dark-theme.input", JComboBox.class);
        assertThat(items(box)).containsExactly("Slate Steel");
        assertThat(selected(box)).isEqualTo("Slate Steel");
        draft.rebase(ConfigLoader.parse(Path.of("config.toml"), "[ui.theme]\ndark = 'ocean'\n", true).values());
        page.refresh();
        assertThat(items(kit(page, "ui.appearance:dark-theme.input", JComboBox.class))).containsExactly("Slate Steel", "ocean (not installed)");
        pick(kit(page, "ui.appearance:dark-theme.input", JComboBox.class), "Slate Steel");
        assertThat(draft.value(CoreSettings.UI_THEME_DARK)).isEqualTo("slate-steel");
        assertThat(draft.edit().changes()).containsEntry(CoreSettings.UI_THEME_DARK, Optional.empty());
        assertThat(page.modified()).isTrue();
    }

    @Test void pickingOnlyADarkThemeStagesOnlyThatPick() {
        var page = pageWith(TWO_EACH);
        page.component();
        pick(kit(page, "ui.appearance:dark-theme.input", JComboBox.class), "Ocean");
        assertThat(draft.value(CoreSettings.UI_THEME_DARK)).isEqualTo("ocean");
        assertThat(draft.edit().changes()).containsOnlyKeys(CoreSettings.UI_THEME_DARK);
        assertThat(draft.modified(CoreSettings.UI_APPEARANCE)).isFalse();
    }

    @Test void resetReturnsTheTileAndBothThemePicksToTheirDefaults() {
        var page = pageWith(TWO_EACH);
        page.component();
        pick(kit(page, "ui.appearance:dark-theme.input", JComboBox.class), "Ocean");
        pick(kit(page, "ui.appearance:light-theme.input", JComboBox.class), "Dawn");
        kit(page, "ui.appearance:policy.option.light", JButton.class).doClick(0);
        assertThat(draft.edit().changes()).containsOnlyKeys(CoreSettings.UI_APPEARANCE, CoreSettings.UI_THEME_DARK, CoreSettings.UI_THEME_LIGHT);
        var reset = kit(page, "ui.appearance:reset", JButton.class);
        assertThat(reset.isVisible()).isTrue();
        reset.doClick();
        assertThat(draft.value(CoreSettings.UI_APPEARANCE)).isEqualTo(CoreSettings.UI_APPEARANCE.defaultValue());
        assertThat(draft.value(CoreSettings.UI_THEME_DARK)).isEqualTo("slate-steel");
        assertThat(draft.value(CoreSettings.UI_THEME_LIGHT)).isEqualTo("slate-steel");
        assertThat(draft.modified()).isFalse();
        assertThat(selected(kit(page, "ui.appearance:dark-theme.input", JComboBox.class))).isEqualTo("Slate Steel");
        assertThat(selected(kit(page, "ui.appearance:light-theme.input", JComboBox.class))).isEqualTo("Slate Steel");
    }

    @Test void theTerminalSchemeIsADropdownOfTheFourSchemes() {
        var box = combo("terminal.scheme");
        assertThat(items(box)).containsExactly("Same as UI theme", "Slate Steel Dark", "Slate Steel Light", "High Contrast");
        assertThat(selected(box)).isEqualTo("Same as UI theme");
        pick(box, "High Contrast");
        assertThat(draft.value(CoreSettings.TERMINAL_SCHEME)).isEqualTo("slate-steel/high-contrast");
        assertThat(page.searchTerms()).contains("Terminal scheme", "Same as UI theme", "Slate Steel", "Match System").doesNotContain("Import theme…");
    }
}
