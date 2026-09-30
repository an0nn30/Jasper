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
    private final PageContext context = new PageContext(new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values()),
        id -> { }, Runnable::run, (title, initial) -> Optional.empty(), () -> { });

    @Test void everyCoreSettingIsOnExactlyOnePage() {
        var shown = new ArrayList<Setting<?>>();
        for (var spec : CorePages.specs(() -> List.of("Jasper Dark"), system)) shown.addAll(spec.settings());
        assertThat(shown).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(CoreSettings.SCHEMA.settings());
    }

    @Test void theTreeIsTheApprovedOne() {
        var pages = CorePages.pages(() -> List.of("Jasper Dark"), system, CorePagesTest::keymap, CorePagesTest::plugins).apply(context);
        assertThat(pages).extracting(SettingsPage::id).containsExactly("appearance-behavior", "appearance", "window", "notifications",
            "command-palette", "desk-buddy", "system", "keymap", "themes", "themes.intellij", "terminal", "terminal.font",
            "terminal.cursor", "terminal.shell", "plugins", "tools");
        assertThat(pages).extracting(page -> page.parent().orElse("")).containsExactly("", "appearance-behavior", "appearance-behavior",
            "appearance-behavior", "appearance-behavior", "appearance-behavior", "appearance-behavior", "", "", "themes", "", "terminal",
            "terminal", "terminal", "", "");
        assertThat(pages).extracting(SettingsPage::title).contains("Appearance & Behavior", "System Settings", "Font & Colors", "Tools",
            "Themes", "IntelliJ Themes");
        assertThat(pages.getLast().hiddenWhenChildless()).isTrue();
        assertThat(CorePages.DEFAULT_PAGE).isEqualTo("appearance");
    }

    @Test void theIntellijThemesPageWrapsItsSchemaHeaderUnderThemes() {
        var wrapped = new GroupPage("themes.intellij", "IntelliJ Themes", Optional.of("themes"), false, List::of, context);
        var headers = new ArrayList<SettingsPage>();
        var pages = CorePages.pages(() -> List.of("Jasper Dark"), system, ShellChoices.NONE, CorePagesTest::keymap, CorePagesTest::plugins,
            page -> List.of(), (page, header) -> { headers.add(header); return wrapped; }).apply(context);
        assertThat(pages).contains(wrapped);
        assertThat(headers).singleElement().satisfies(header -> {
            assertThat(header.id()).isEqualTo(CorePages.INTELLIJ_THEMES);
            assertThat(header.parent()).contains(CorePages.THEMES);
            assertThat(header.helpAnchor()).isEqualTo("intellij-themes");
            assertThat(header.searchTerms()).anyMatch(term -> term.contains("Marketplace"));
        });
    }

    @Test void toolsPagesComeLastUnderTools() {
        var extra = new GroupPage("plugin.dev.example", "Example", Optional.of("tools"), false, List::of, context);
        var pages = CorePages.pages(() -> List.of("Jasper Dark"), system, CorePagesTest::keymap, CorePagesTest::plugins,
            page -> List.of(extra)).apply(context);
        assertThat(pages.getLast()).isSameAs(extra);
        assertThat(pages.get(pages.size() - 2).id()).isEqualTo("tools");
    }

    @Test void systemSettingsOffersTheConfigurationFileActions() {
        var pages = CorePages.pages(() -> List.of("Jasper Dark"), system, CorePagesTest::keymap, CorePagesTest::plugins).apply(context);
        var systemPage = (SchemaPage) pages.stream().filter(page -> page.id().equals("system")).findFirst().orElseThrow();
        systemPage.component();
        assertThat(systemPage.actionButtons()).extracting(javax.swing.JButton::getText)
            .containsExactly("Open in Editor", "Reveal", "Reload from Disk", "Migrate Config\u2026");
        systemPage.actionButtons().forEach(javax.swing.JButton::doClick);
        assertThat(calls).containsExactly("open", "reveal", "reload", "migrate");
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
        var spec = CorePages.specs(() -> List.of("Jasper Dark"), system, shells).stream()
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

    @Test void theAlacrittyThemesPageFollowsIntellijThemesUnderThemes() {
        var alacritty = new GroupPage("themes.alacritty", "Alacritty Themes", Optional.of("themes"), false, List::of, context);
        var pages = CorePages.pages(() -> List.of("Jasper Dark"), () -> List.of("Dracula"), system, ShellChoices.NONE,
            new CorePages.Pages(CorePagesTest::keymap, CorePagesTest::plugins, page -> List.of(), (page, header) -> header,
                page -> Optional.of(alacritty))).apply(context);
        var ids = pages.stream().map(SettingsPage::id).toList();
        assertThat(ids.indexOf("themes.alacritty")).isEqualTo(ids.indexOf("themes.intellij") + 1);
        assertThat(CorePages.ALACRITTY_THEMES).isEqualTo("themes.alacritty");
        var withoutIt = CorePages.pages(() -> List.of("Jasper Dark"), system, CorePagesTest::keymap, CorePagesTest::plugins).apply(context);
        assertThat(withoutIt).extracting(SettingsPage::id).doesNotContain("themes.alacritty");
    }
}
