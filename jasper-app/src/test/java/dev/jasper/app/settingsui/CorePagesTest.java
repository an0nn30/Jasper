package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settings.Setting;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CorePagesTest {
    private final List<String> calls = new ArrayList<>();
    private final CorePages.SystemActions system = new CorePages.SystemActions(() -> Path.of("/home/me/.config/jasper/config.toml"),
        () -> calls.add("open"), () -> calls.add("reveal"), () -> calls.add("reload"), () -> calls.add("migrate"), () -> true);
    private static final ThemeChoices JASPER_DARK = ThemeChoices.of(() -> List.of("Jasper Dark"));
    private final PageContext context = new PageContext(new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values()),
        id -> { }, Runnable::run, (title, initial) -> Optional.empty(), () -> { });

    @Test void everyCoreSettingIsOnExactlyOnePage() {
        var shown = new ArrayList<Setting<?>>();
        for (var spec : CorePages.specs(JASPER_DARK, system)) shown.addAll(spec.settings());
        assertThat(shown).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(CoreSettings.SCHEMA.settings());
    }

    @Test void appearanceHasAWorkspaceSectionForOpacityAndBlur() {
        var appearance = CorePages.specs(JASPER_DARK, system).getFirst();
        assertThat(appearance.id()).isEqualTo("appearance");
        assertThat(appearance.settings()).containsSubsequence(CoreSettings.UI_WORKSPACE_OPACITY, CoreSettings.UI_WORKSPACE_BLUR);
    }

    private static SchemaPage.Spec appearance(boolean glass) {
        return CorePages.specs(JASPER_DARK, system(), ShellChoices.NONE, id -> { }, glass).getFirst();
    }

    private static CorePages.SystemActions system() {
        return new CorePages.SystemActions(() -> Path.of("config.toml"), () -> { }, () -> { }, () -> { }, () -> { }, () -> true);
    }

    private static List<Setting<?>> builtSettings(SchemaPage page) {
        return page.rows().stream().<Setting<?>>map(SchemaPage.Row::setting).toList();
    }

    @Test void offMacOsTheGlassRowsAreHiddenButStillOnThePage() {
        var spec = appearance(false);
        assertThat(spec.settings()).contains(CoreSettings.UI_WORKSPACE_OPACITY, CoreSettings.UI_WORKSPACE_BLUR);
        var page = spec.build(context);
        page.component();
        assertThat(builtSettings(page)).doesNotContain(CoreSettings.UI_WORKSPACE_OPACITY, CoreSettings.UI_WORKSPACE_BLUR)
            .contains(CoreSettings.UI_FONT_FAMILY, CoreSettings.UI_FONT_SIZE);
        assertThat(page.searchTerms()).doesNotContain(CoreSettings.UI_WORKSPACE_OPACITY.label(), CoreSettings.UI_WORKSPACE_BLUR.label());
        assertThat(page.searchTerms()).doesNotContain("Workspace");
        assertThat(SchemaPageTest.sectionTitles(page.component())).containsExactly("Theme", "Interface font", "View mode");
        page.dispose();
    }

    @Test void onMacOsTheGlassRowsAreShownAndEnabledWithoutANote() {
        var page = appearance(true).build(context);
        page.component();
        assertThat(builtSettings(page)).contains(CoreSettings.UI_WORKSPACE_OPACITY, CoreSettings.UI_WORKSPACE_BLUR);
        for (var row : page.rows()) {
            if (row.setting() != CoreSettings.UI_WORKSPACE_OPACITY && row.setting() != CoreSettings.UI_WORKSPACE_BLUR) continue;
            assertThat(row.editor().component().isEnabled()).as(row.setting().label()).isTrue();
        }
        assertThat(page.searchTerms()).contains(CoreSettings.UI_WORKSPACE_OPACITY.label(), CoreSettings.UI_WORKSPACE_BLUR.label());
        page.dispose();
    }

    @Test void theWorkspaceSectionFollowsTheThemeSectionOnlyOnMacOs() {
        assertThat(sectionTitles(appearance(true))).containsExactly("Theme", "Workspace", "Interface font", "View mode");
        assertThat(sectionTitles(appearance(false))).containsExactly("Theme", "Interface font", "View mode");
        assertThat(appearance(true).settings()).containsSubsequence(CoreSettings.UI_THEME_NAME, CoreSettings.UI_THEME_WORKSPACE,
            CoreSettings.UI_WORKSPACE_OPACITY, CoreSettings.UI_WORKSPACE_BLUR, CoreSettings.UI_FONT_FAMILY);
    }

    private static List<String> sectionTitles(SchemaPage.Spec spec) {
        var page = spec.build(new PageContext(new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values()),
            id -> { }, Runnable::run, (title, initial) -> Optional.empty(), () -> { }));
        var titles = SchemaPageTest.sectionTitles(page.component());
        page.dispose();
        return titles;
    }

    @Test void theBlurRowNamesTheThemeChoiceLikeOpacity() {
        var spec = appearance(true);
        var page = spec.build(context);
        page.component();
        int index = spec.settings().indexOf(CoreSettings.UI_WORKSPACE_BLUR);
        assertThat(page.rows()).hasSameSizeAs(spec.settings());
        @SuppressWarnings("unchecked") var box = (javax.swing.JComboBox<String>) page.rows().get(index).editor().component();
        var items = new ArrayList<String>();
        for (int i = 0; i < box.getItemCount(); i++) items.add(box.getItemAt(i));
        assertThat(items).containsExactly("Use theme's value", "On", "Off");
    }

    @Test void theTreeIsTheApprovedOne() {
        var pages = CorePages.pages(JASPER_DARK, system, CorePagesTest::keymap, CorePagesTest::plugins).apply(context);
        assertThat(pages).extracting(SettingsPage::id).containsExactly("appearance-behavior", "appearance", "window", "notifications",
            "command-palette", "desk-buddy", "system", "keymap", "terminal", "terminal.font",
            "terminal.cursor", "terminal.shell", "plugins", "tools");
        assertThat(pages).extracting(page -> page.parent().orElse("")).containsExactly("", "appearance-behavior", "appearance-behavior",
            "appearance-behavior", "appearance-behavior", "appearance-behavior", "appearance-behavior", "", "", "terminal",
            "terminal", "terminal", "", "");
        assertThat(pages).extracting(SettingsPage::title).contains("Appearance & Behavior", "System Settings", "Font & Colors", "Tools");
        assertThat(pages.getLast().hiddenWhenChildless()).isTrue();
        assertThat(CorePages.DEFAULT_PAGE).isEqualTo("appearance");
    }


    @Test void toolsPagesComeLastUnderTools() {
        var extra = new GroupPage("plugin.dev.example", "Example", Optional.of("tools"), false, List::of, context);
        var pages = CorePages.pages(JASPER_DARK, system, CorePagesTest::keymap, CorePagesTest::plugins,
            page -> List.of(extra)).apply(context);
        assertThat(pages.getLast()).isSameAs(extra);
        assertThat(pages.get(pages.size() - 2).id()).isEqualTo("tools");
    }

    @Test void systemSettingsOffersTheConfigurationFileActions() {
        var pages = CorePages.pages(JASPER_DARK, system, CorePagesTest::keymap, CorePagesTest::plugins).apply(context);
        var systemPage = (SchemaPage) pages.stream().filter(page -> page.id().equals("system")).findFirst().orElseThrow();
        systemPage.component();
        assertThat(systemPage.actionButtons()).extracting(javax.swing.JButton::getText)
            .containsExactly("Open in Editor", "Reveal", "Reload from Disk", "Migrate Config\u2026");
        systemPage.actionButtons().forEach(javax.swing.JButton::doClick);
        assertThat(calls).containsExactly("open", "reveal", "reload", "migrate");
    }

    @Test void fontAndColorsLinksToTheWorkspaceSchemeInAppearance() {
        var navigation = new ArrayList<String>();
        for (var spec : CorePages.specs(ThemeChoices.of(List::of), system, ShellChoices.NONE, navigation::add)) {
            var page = spec.build(context); page.component(); page.actionButtons().forEach(javax.swing.JButton::doClick); page.dispose();
        }
        assertThat(navigation).containsExactly("appearance");
    }
    private static SettingsPage keymap(PageContext context) {
        return new GroupPage("keymap", "Keymap", Optional.empty(), false, List::of, context);
    }

    private static SettingsPage plugins(PageContext context) {
        return new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, context);
    }

    @Test void theShellPageOffersProfilesAndEnablesProgramAndArgsOnlyForCustom() {
        var shells = new ShellChoices(() -> List.of(new ShellChoices.Choice("powershell", "PowerShell"), new ShellChoices.Choice("custom", "Custom")),
            () -> { }, listener -> new Subscription(() -> { }));
        var spec = CorePages.specs(JASPER_DARK, system, shells).stream()
            .filter(candidate -> candidate.id().equals("terminal.shell")).findFirst().orElseThrow();
        assertThat(spec.settings()).startsWith(CoreSettings.TERMINAL_SHELL_PROFILE, CoreSettings.TERMINAL_SHELL_PROGRAM, CoreSettings.TERMINAL_SHELL_ARGS);
        var page = spec.build(context);
        page.component();
        assertThat(page.rows().getFirst().editor()).isInstanceOf(Editors.Profile.class);
        var program = page.rows().get(1).editor().component();
        var args = page.rows().get(2).editor().component();
        assertThat(program.isEnabled()).isFalse();
        assertThat(args.isEnabled()).isFalse();
        context.draft().stage(CoreSettings.TERMINAL_SHELL_PROFILE, Optional.of("custom"));
        assertThat(program.isEnabled()).isTrue();
        assertThat(args.isEnabled()).isTrue();
        context.draft().stage(CoreSettings.TERMINAL_SHELL_PROFILE, Optional.of("powershell"));
        assertThat(program.isEnabled()).isFalse();
        context.draft().stage(CoreSettings.TERMINAL_SHELL_PROFILE, Optional.empty());
        context.draft().stage(CoreSettings.TERMINAL_SHELL_PROGRAM, Optional.of("/bin/fish"));
        assertThat(program.isEnabled()).as("an old program setting is the Custom profile").isTrue();
        page.dispose();
    }

}
