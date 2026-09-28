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
            "command-palette", "desk-buddy", "system", "keymap", "terminal", "terminal.font", "terminal.cursor", "terminal.shell",
            "plugins", "tools");
        assertThat(pages).extracting(page -> page.parent().orElse("")).containsExactly("", "appearance-behavior", "appearance-behavior",
            "appearance-behavior", "appearance-behavior", "appearance-behavior", "appearance-behavior", "", "", "terminal", "terminal",
            "terminal", "", "");
        assertThat(pages).extracting(SettingsPage::title).contains("Appearance & Behavior", "System Settings", "Font & Colors", "Tools");
        assertThat(pages.getLast().hiddenWhenChildless()).isTrue();
        assertThat(CorePages.DEFAULT_PAGE).isEqualTo("appearance");
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
            .containsExactly("Open in Editor", "Reveal", "Reload from Disk", "Migrate Config…");
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
}
