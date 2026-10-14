package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.ui.model.Nodes;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settings.Setting;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(dev.jasper.app.testsupport.EdtTestExtension.class)
class CorePagesTest {
    private final List<String> calls = new ArrayList<>();
    private final CorePages.SystemActions system = new CorePages.SystemActions(() -> Path.of("/home/me/.config/jasper/config.toml"),
        () -> calls.add("open"), () -> calls.add("reveal"), () -> calls.add("reload"), () -> calls.add("migrate"), () -> true, Optional.empty());
    private static final AppearanceChoices APPEARANCE = AppearanceFixtures.CHOICES;
    private final PageContext context = PageKit.context(new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values()),
        id -> { }, () -> { });

    @Test void everyCoreSettingIsOnExactlyOnePage() {
        var shown = new ArrayList<Setting<?>>();
        for (var spec : CorePages.specs(APPEARANCE, system)) shown.addAll(spec.settings());
        assertThat(shown).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(CoreSettings.SCHEMA.settings());
    }

    @Test void appearanceHasAWorkspaceSectionForOpacityAndBlur() {
        var appearance = CorePages.specs(APPEARANCE, system).getFirst();
        assertThat(appearance.id()).isEqualTo("appearance");
        assertThat(appearance.settings()).containsSubsequence(CoreSettings.UI_WORKSPACE_OPACITY, CoreSettings.UI_WORKSPACE_BLUR);
    }

    private static SchemaPage.Spec appearance(boolean glass) {
        return CorePages.specs(APPEARANCE, system(), ShellChoices.NONE, id -> { }, glass).getFirst();
    }

    private static CorePages.SystemActions system() {
        return new CorePages.SystemActions(() -> Path.of("config.toml"), () -> { }, () -> { }, () -> { }, () -> { }, () -> true, Optional.empty());
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
        assertThat(PageKit.sectionTitles(page)).containsExactly("Theme", "Interface Font", "View mode", "Animations");
        page.dispose();
    }

    @Test void onMacOsTheGlassRowsAreShownAndEnabledWithoutANote() {
        var page = appearance(true).build(context);
        page.component();
        assertThat(builtSettings(page)).contains(CoreSettings.UI_WORKSPACE_OPACITY, CoreSettings.UI_WORKSPACE_BLUR);
        for (var row : page.rows()) {
            if (row.setting() != CoreSettings.UI_WORKSPACE_OPACITY && row.setting() != CoreSettings.UI_WORKSPACE_BLUR) continue;
            var control = (dev.jasper.ui.model.Nodes.Stack) ((dev.jasper.ui.model.Nodes.SettingRow) PageKit.node(page, "row:" + row.setting().name())).control();
            assertThat(control.children().getFirst().state().enabled()).as(row.setting().label()).isEqualTo(row.setting()!=CoreSettings.UI_WORKSPACE_OPACITY);
            if(row.setting()==CoreSettings.UI_WORKSPACE_OPACITY)assertThat(PageKit.node(page,"ui.workspace.opacity:default").state().enabled()).isTrue();
        }
        var enable = (dev.jasper.ui.model.Nodes.Toggle) PageKit.node(page, "ui.workspace.opacity:default");
        assertThat(enable.label()).isEqualTo("Enable opacity");
        assertThat(enable.checked()).as("off while the theme's value applies").isFalse();
        PageKit.kit(page, "ui.workspace.opacity:default", javax.swing.JButton.class).doClick(0);
        assertThat(context.draft().value(CoreSettings.UI_WORKSPACE_OPACITY)).isEqualTo(100f);
        assertThat(((dev.jasper.ui.model.Nodes.Toggle) PageKit.node(page, "ui.workspace.opacity:default")).checked()).isTrue();
        PageKit.kit(page, "ui.workspace.opacity:default", javax.swing.JButton.class).doClick(0);
        assertThat(context.draft().value(CoreSettings.UI_WORKSPACE_OPACITY)).isEqualTo(dev.jasper.app.config.GlassSettings.UNSET_OPACITY);
        assertThat(page.searchTerms()).contains(CoreSettings.UI_WORKSPACE_OPACITY.label(), CoreSettings.UI_WORKSPACE_BLUR.label());
        page.dispose();
    }

    @Test void theWorkspaceSectionFollowsTheThemeSectionOnlyOnMacOs() {
        assertThat(sectionTitles(appearance(true))).containsExactly("Theme", "Workspace", "Interface Font", "View mode", "Animations");
        assertThat(sectionTitles(appearance(false))).containsExactly("Theme", "Interface Font", "View mode", "Animations");
        assertThat(appearance(true).settings()).containsSubsequence(CoreSettings.UI_APPEARANCE, CoreSettings.TERMINAL_SCHEME,
            CoreSettings.UI_WORKSPACE_OPACITY, CoreSettings.UI_WORKSPACE_BLUR, CoreSettings.UI_FONT_FAMILY);
    }

    private static List<String> sectionTitles(SchemaPage.Spec spec) {
        var page = spec.build(PageKit.context(new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values()),
            id -> { }, () -> { }));
        var titles = PageKit.sectionTitles(page);
        page.dispose();
        return titles;
    }

    @Test void theBlurRowNamesTheThemeChoiceLikeOpacity() {
        var spec = appearance(true);
        var page = spec.build(context);
        page.component();
        var toggle=PageKit.kit(page,"ui.workspace.blur",javax.swing.JButton.class);
        assertThat(toggle.getAccessibleContext().getAccessibleName()).isEqualTo("Use theme's value");
        // The row's own Reset is the way back to the theme's value: hidden while inheriting, shown once overridden.
        assertThat(PageKit.node(page,"ui.workspace.blur:reset").state().visible()).isFalse();
        toggle.doClick(0);
        assertThat(context.draft().value(CoreSettings.UI_WORKSPACE_BLUR)).isNotEqualTo(dev.jasper.app.config.WorkspaceBlur.THEME);
        assertThat(PageKit.node(page,"ui.workspace.blur:reset").state().visible()).isTrue();
        PageKit.kit(page,"ui.workspace.blur:reset",javax.swing.JButton.class).doClick(0);
        assertThat(context.draft().value(CoreSettings.UI_WORKSPACE_BLUR)).isEqualTo(dev.jasper.app.config.WorkspaceBlur.THEME);
        assertThat(PageKit.kit(page,"ui.workspace.blur",javax.swing.JButton.class).getAccessibleContext().getAccessibleName()).isEqualTo("Use theme's value");
        assertThat(page.rows()).allSatisfy(row -> assertThat(row.setting().name()).doesNotStartWith("ui.theme."));
    }

    @Test void theTreeIsTheApprovedOne() {
        var pages = CorePages.pages(APPEARANCE, system, CorePagesTest::keymap, CorePagesTest::plugins).apply(context);
        assertThat(pages).extracting(SettingsPage::id).containsExactly("appearance-behavior", "appearance", "window", "notifications",
            "command-palette", "desk-buddy", "system", "keymap", "plugins", "terminal", "terminal.font",
            "terminal.cursor", "terminal.shell", "tools");
        assertThat(pages).extracting(page -> page.parent().orElse("")).containsExactly("", "appearance-behavior", "appearance-behavior",
            "appearance-behavior", "appearance-behavior", "appearance-behavior", "appearance-behavior", "", "", "",
            "terminal", "terminal", "terminal", "");
        assertThat(pages).extracting(SettingsPage::title).contains("Appearance & Behavior", "System Settings", "Font & Colors", "Tools");
        assertThat(pages.getLast().hiddenWhenChildless()).isTrue();
        assertThat(CorePages.DEFAULT_PAGE).isEqualTo("appearance");
    }


    @Test void toolsPagesComeLastUnderTools() {
        var extra = new GroupPage("plugin.dev.example", "Example", Optional.of("tools"), false, List::of, context);
        var pages = CorePages.pages(APPEARANCE, system, CorePagesTest::keymap, CorePagesTest::plugins,
            page -> List.of(extra)).apply(context);
        assertThat(pages.getLast()).isSameAs(extra);
        assertThat(pages.get(pages.size() - 2).id()).isEqualTo("tools");
    }

    @Test void systemSettingsOffersTheConfigurationFileActions() {
        var pages = CorePages.pages(APPEARANCE, system, CorePagesTest::keymap, CorePagesTest::plugins).apply(context);
        var systemPage = (SchemaPage) pages.stream().filter(page -> page.id().equals("system")).findFirst().orElseThrow();
        systemPage.component();
        var buttons = systemPage.actionButtons().stream().map(id -> PageKit.kit(systemPage, id, javax.swing.JButton.class)).toList();
        assertThat(buttons).extracting(javax.swing.JButton::getText)
            .containsExactly("Open in Editor", "Reveal", "Reload", "Migrate\u2026");
        buttons.forEach(javax.swing.JButton::doClick);
        assertThat(calls).containsExactly("open", "reveal", "reload", "migrate");
        // One card of settings rows: title and description left, the buttons at each row's end.
        var titles = new ArrayList<String>();
        walk(systemPage.viewSpec().root(), node -> { if (node instanceof Nodes.SettingRow row) titles.add(row.title()); });
        assertThat(titles).containsSubsequence("Location", "Reload from disk", "Migrate old settings");
    }

    @Test void systemSettingsOffersTheJasperCommandWhenTheApplicationDoes() {
        boolean[] installed = {false};
        var commandLine = new CorePages.CommandLineActions(
            () -> installed[0] ? "Installed at /usr/local/bin/jasper" : "Not installed",
            () -> { calls.add("install"); installed[0] = true; }, () -> !installed[0],
            () -> { calls.add("uninstall"); installed[0] = false; }, () -> installed[0]);
        var withCommand = new CorePages.SystemActions(() -> Path.of("config.toml"), () -> { }, () -> { }, () -> { }, () -> { },
            () -> true, Optional.empty(), Optional.of(commandLine));
        var pages = CorePages.pages(APPEARANCE, withCommand, CorePagesTest::keymap, CorePagesTest::plugins).apply(context);
        var systemPage = (SchemaPage) pages.stream().filter(page -> page.id().equals("system")).findFirst().orElseThrow();
        systemPage.component();
        var buttons = systemPage.actionButtons().stream().map(id -> PageKit.kit(systemPage, id, javax.swing.JButton.class)).toList();
        assertThat(buttons).extracting(javax.swing.JButton::getText).startsWith("Install", "Uninstall");
        assertThat(buttons.get(0).isEnabled()).isTrue();
        assertThat(buttons.get(1).isEnabled()).isFalse();
        buttons.get(0).doClick();
        assertThat(calls).containsExactly("install");
        var rows = new ArrayList<Nodes.SettingRow>();
        walk(systemPage.viewSpec().root(), node -> { if (node instanceof Nodes.SettingRow row) rows.add(row); });
        assertThat(rows).extracting(Nodes.SettingRow::title).containsSubsequence("jasper command", "Location");
        assertThat(rows).filteredOn(row -> row.title().equals("jasper command")).singleElement()
            .satisfies(row -> assertThat(row.description()).isEqualTo("Installed at /usr/local/bin/jasper"));
    }

    @Test void systemSettingsHasTheJavaVirtualMachineSectionWhenTheApplicationSuppliesIt() {
        int[] restarted = {0};
        var vm = new VmOptionsHooks(new SchemaPage.External("vm.heap", "Maximum heap size", "MiB", List.of("memory"),
                new Nodes.Numeric(512, 1_048_576, 256, 0), () -> 2048, () -> 2048, value -> "", value -> "Current: 2048 MiB.",
                value -> CompletableFuture.completedFuture(null), () -> false),
            () -> "Applies to the installed Jasper app.", () -> { }, () -> { }, () -> false,
            () -> Optional.of(new SchemaPage.Notice("Applies to the installed app", "Custom VM options apply to the installed Jasper app.")),
            Optional::empty,
            () -> Optional.of(new SchemaPage.Notice("Restart required", "Restart Jasper to apply your changes.")),
            () -> restarted[0]++, listener -> new Subscription(() -> { }));
        var system = new CorePages.SystemActions(() -> Path.of("/fixture/config.toml"), () -> { }, () -> { }, () -> { }, () -> { }, () -> false,
            Optional.of(vm));
        var pageContext = PageKit.context(new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values()), id -> { }, () -> { });
        var page = CorePages.specs(AppearanceFixtures.CHOICES, system).stream().filter(spec -> spec.id().equals("system"))
            .findFirst().orElseThrow().build(pageContext);
        assertThat(PageKit.sectionTitles(page)).containsSubsequence("Java Virtual Machine", "Configuration file");
        assertThat(((Nodes.Field) PageKit.node(page, "vm.heap")).state().enabled()).isFalse();
        assertThat(((Nodes.SettingRow) PageKit.node(page, "notice-vm.state")).description())
            .isEqualTo("Custom VM options apply to the installed Jasper app.");
        assertThat(((Nodes.SettingRow) PageKit.node(page, "notice-vm.findings")).state().visible()).isFalse();
        PageKit.kit(page, "notice-vm.restart-0", JButton.class).doClick();
        assertThat(restarted[0]).isEqualTo(1);
    }

    /** Every node below {@code node}: stacks, cards, sections and a settings row's control. */
    static void walk(Nodes.Node node, java.util.function.Consumer<Nodes.Node> visit) {
        visit.accept(node);
        if (node instanceof Nodes.Stack stack) stack.children().forEach(child -> walk(child, visit));
        if (node instanceof Nodes.Card card) card.children().forEach(child -> walk(child, visit));
        if (node instanceof Nodes.Section section) walk(section.body(), visit);
        if (node instanceof Nodes.SettingRow row) walk(row.control(), visit);
    }

    @Test void decimalSettingsAreSteppers() {
        var pages = CorePages.pages(APPEARANCE, system, CorePagesTest::keymap, CorePagesTest::plugins).apply(context);
        var font = (SchemaPage) pages.stream().filter(page -> page.id().equals("terminal.font")).findFirst().orElseThrow();
        font.component();
        var fields = new java.util.HashMap<String, Nodes.Field>();
        walk(font.viewSpec().root(), node -> { if (node instanceof Nodes.Field field) fields.put(field.id(), field); });
        var size = fields.get(CoreSettings.TERMINAL_FONT_SIZE.name());
        assertThat(size.presentation()).isEqualTo(Nodes.FieldPresentation.STEPPER);
        assertThat(size.numeric().orElseThrow().step()).isEqualTo(0.5);
        assertThat(size.hint()).isEqualTo("pt");
        var line = fields.get(CoreSettings.TERMINAL_FONT_LINE_HEIGHT.name());
        assertThat(line.presentation()).isEqualTo(Nodes.FieldPresentation.STEPPER);
        assertThat(line.numeric().orElseThrow().step()).isEqualTo(0.1);
        assertThat(line.numeric().orElseThrow().decimals()).isEqualTo(2);
    }

    @Test void fontAndColorsLinksToTheTerminalSchemeInAppearance() {
        var navigation = new ArrayList<String>();
        for (var spec : CorePages.specs(AppearanceFixtures.CHOICES, system, ShellChoices.NONE, navigation::add)) {
            var page = spec.build(context); page.component();
            page.actionButtons().forEach(id -> PageKit.kit(page, id, javax.swing.JButton.class).doClick()); page.dispose();
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
        var spec = CorePages.specs(APPEARANCE, system, shells).stream()
            .filter(candidate -> candidate.id().equals("terminal.shell")).findFirst().orElseThrow();
        assertThat(spec.settings()).startsWith(CoreSettings.TERMINAL_SHELL_PROFILE, CoreSettings.TERMINAL_SHELL_PROGRAM, CoreSettings.TERMINAL_SHELL_ARGS);
        var page = spec.build(context);
        page.component();
        assertThat(page.rows().getFirst().editor()).isInstanceOf(Editors.Profile.class);
        var program = PageKit.kit(page, "terminal.shell.program.input", javax.swing.JTextField.class);
        var args = PageKit.kit(page, "terminal.shell.args:new.input", javax.swing.JTextField.class);
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

    @Test void theNotificationsPageListsEveryGroupInItsOwnSection() {
        var groups = new NotificationGroupChoices(() -> List.of(
            new NotificationGroupChoices.Group("jasper.commands", "Long-running commands", "Jasper", "card")));
        var spec = CorePages.specs(APPEARANCE, system(), ShellChoices.NONE, id -> { }, false, groups).stream()
            .filter(page -> page.settings().contains(CoreSettings.NOTIFICATIONS_GROUPS)).findFirst().orElseThrow();
        var draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values());
        var page = spec.build(PageKit.context(draft, id -> { }, () -> { }));
        assertThat(PageKit.sectionTitles(page)).containsExactly("System Notifications", "Groups");
        var row = page.rows().stream().filter(candidate -> candidate.setting() == CoreSettings.NOTIFICATIONS_GROUPS)
            .findFirst().orElseThrow();
        assertThat(row.editor()).isInstanceOf(NotificationGroupsEditor.class);
        pick(page, "history");
        assertThat(draft.value(CoreSettings.NOTIFICATIONS_GROUPS)).isEqualTo(Map.of("jasper.commands", "history"));
        pick(page, "card");
        assertThat(draft.modified(CoreSettings.NOTIFICATIONS_GROUPS)).as("back at the default, nothing to save").isFalse();
        page.dispose();
    }

    /** A config.toml Jasper made holds the template's empty [notifications.groups]; going back to a default is no change. */
    @Test void aGroupBackAtItsDefaultIsNoChangeInAFileMadeFromTheTemplate() {
        var groups = new NotificationGroupChoices(() -> List.of(
            new NotificationGroupChoices.Group("jasper.commands", "Long-running commands", "Jasper", "card")));
        var spec = CorePages.specs(APPEARANCE, system(), ShellChoices.NONE, id -> { }, false, groups).stream()
            .filter(page -> page.settings().contains(CoreSettings.NOTIFICATIONS_GROUPS)).findFirst().orElseThrow();
        var draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), dev.jasper.app.config.ConfigTemplate.text(true), true).values());
        var page = spec.build(PageKit.context(draft, id -> { }, () -> { }));
        page.component();
        pick(page, "history");
        assertThat(draft.modified(CoreSettings.NOTIFICATIONS_GROUPS)).isTrue();
        pick(page, "card");
        assertThat(draft.modified(CoreSettings.NOTIFICATIONS_GROUPS)).as("back at the default, nothing to save").isFalse();
        pick(page, "off");
        var reset = PageKit.kit(page, NotificationGroupsEditor.resetId(CoreSettings.NOTIFICATIONS_GROUPS.name(), 0), javax.swing.JButton.class);
        assertThat(reset.isEnabled()).isTrue();
        reset.doClick();
        assertThat(draft.modified(CoreSettings.NOTIFICATIONS_GROUPS)).as("reset, nothing to save").isFalse();
        page.dispose();
    }

    /** Chooses {@code token} in the first group's mounted kit dropdown, as a user does. */
    private static void pick(SchemaPage page, String token) {
        var combo = PageKit.kit(page, NotificationGroupsEditor.modeId(CoreSettings.NOTIFICATIONS_GROUPS.name(), 0) + ".input",
            javax.swing.JComboBox.class);
        for (int i = 0; i < combo.getItemCount(); i++)
            if (combo.getItemAt(i) instanceof dev.jasper.ui.model.Option option && option.id().equals(token)) {
                combo.setSelectedIndex(i);
                return;
            }
        throw new AssertionError("No mode " + token);
    }
}
