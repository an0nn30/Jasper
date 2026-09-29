package dev.jasper.app.settingsui;

import static dev.jasper.app.config.CoreSettings.*;

import dev.jasper.app.config.TerminalConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/** The Settings tree for the core settings, as the settings dialog spec lays it out. */
public final class CorePages {
    /** The page shown when Settings opens for the first time. */
    public static final String DEFAULT_PAGE = "appearance";

    private CorePages() { }

    /**
     * What the System Settings page's buttons do.
     *
     * @param file the configuration file's path
     * @param openInEditor opens it in the user's editor
     * @param reveal shows it in the file manager
     * @param reload reads it again
     * @param migrate runs Migrate Config
     * @param migratable whether Migrate Config has work
     */
    public record SystemActions(Supplier<Path> file, Runnable openInEditor, Runnable reveal, Runnable reload, Runnable migrate,
                                BooleanSupplier migratable) { }

    /** Every schema page's spec, in tree order, with no detected shells. */
    public static List<SchemaPage.Spec> specs(Supplier<List<String>> themeNames, SystemActions system) {
        return specs(themeNames, system, ShellChoices.NONE);
    }

    /** Every schema page's spec, in tree order. */
    public static List<SchemaPage.Spec> specs(Supplier<List<String>> themeNames, SystemActions system, ShellChoices shells) {
        return List.of(
            SchemaPage.page("appearance", "Appearance").parent("appearance-behavior").anchor("theme")
                .section("Theme")
                .row(UI_THEME_NAME, Editors.theme(themeNames))
                .section("Interface font")
                .row(UI_FONT_FAMILY, Editors.font(false))
                .row(UI_FONT_SIZE, Editors.optionalNumber("Use platform size", 0.5, "pt")),
            SchemaPage.page("window", "Window").parent("appearance-behavior")
                .section("Toolbar and status bar")
                .row(WINDOW_TOOLBAR)
                .row(WINDOW_TOOLBAR_HEIGHT, Editors.integer("px"))
                .row(WINDOW_STATUS_BAR)
                .row(WINDOW_STATUS_BAR_HEIGHT, Editors.integer("px"))
                .row(WINDOW_NATIVE_DECORATIONS).shownWhen(!com.formdev.flatlaf.util.SystemInfo.isMacOS)
                .section("Tabs and panels")
                .row(WINDOW_TAB_HEIGHT, Editors.integer("px"))
                .row(WINDOW_RAIL_WIDTH, Editors.integer("px"))
                .section("New windows")
                .row(WINDOW_COLUMNS, Editors.integer("columns"))
                .row(WINDOW_LINES, Editors.integer("lines")),
            SchemaPage.page("notifications", "Notifications").parent("appearance-behavior").anchor("finished-command-notifications")
                .row(NOTIFICATIONS_LONG_COMMAND_SECONDS, Editors.integer("s")).note("0 turns these notifications off."),
            SchemaPage.page("command-palette", "Command Palette").parent("appearance-behavior").anchor("palette")
                .row(PALETTE_MAX_RESULTS, Editors.integer("")),
            SchemaPage.page("desk-buddy", "Desk Buddy").parent("appearance-behavior").anchor("desk-buddy")
                .row(BUDDY_ENABLED),
            SchemaPage.page("system", "System Settings").parent("appearance-behavior").anchor("background-residency")
                .row(SYSTEM_RUN_IN_BACKGROUND)
                .section("Configuration file")
                .action(() -> system.file().get().toString(),
                    new SchemaPage.Button("Open in Editor", system.openInEditor(), () -> true),
                    new SchemaPage.Button("Reveal", system.reveal(), () -> true))
                .action(() -> "Read the file again after editing it elsewhere.",
                    new SchemaPage.Button("Reload from Disk", system.reload(), () -> true))
                .action(() -> "Rewrite old setting names to the current layout; the original is backed up first.",
                    new SchemaPage.Button("Migrate Config…", system.migrate(), system.migratable())),
            SchemaPage.page("terminal", "Terminal")
                .row(TERMINAL_SCROLLBACK, Editors.integer("lines"))
                .row(TERMINAL_BELL)
                .row(TERMINAL_ON_EXIT, Editors.choice(Map.of("keep_open", "Keep the pane open",
                    "close_on_success", "Close when it succeeds", "close", "Close")))
                .row(TERMINAL_COPY_ON_SELECT)
                .row(TERMINAL_OPTION_AS_META, Editors.choice(Map.of("left", "Left Option", "right", "Right Option",
                    "both", "Both", "none", "Neither")))
                .row(TERMINAL_DIM_INACTIVE_PANES, Editors.number(0.05, "")),
            SchemaPage.page("terminal.font", "Font & Colors").parent("terminal").anchor("terminal-colors")
                .section("Font")
                .row(TERMINAL_FONT_FAMILY, Editors.font(true))
                .row(TERMINAL_FONT_SIZE, Editors.number(0.5, "pt"))
                .row(TERMINAL_FONT_FALLBACK, Editors.fontList(false))
                .row(TERMINAL_FONT_LIGATURES)
                .row(TERMINAL_FONT_LINE_HEIGHT, Editors.number(0.05, "×"))
                .section("Colors")
                .row(TERMINAL_COLORS, Editors.choice(Map.of("match", "Match the theme", "light", "Light", "dark", "Dark"))),
            SchemaPage.page("terminal.cursor", "Cursor").parent("terminal")
                .row(TERMINAL_CURSOR_SHAPE)
                .row(TERMINAL_CURSOR_BLINK),
            SchemaPage.page("terminal.shell", "Shell").parent("terminal").anchor("shell-environment-and-history")
                .row(TERMINAL_SHELL_PROFILE, Editors.profile(shells)).note("New tabs start this shell; a split starts the shell of its pane.")
                .row(TERMINAL_SHELL_PROGRAM, Editors.executable()).note("For Custom. Leave empty for your login shell.")
                .enabledWhen(CorePages::customShell)
                .row(TERMINAL_SHELL_ARGS, Editors.textList()).enabledWhen(CorePages::customShell)
                .row(TERMINAL_ENV, Editors.table())
                .row(TERMINAL_SHELL_INTEGRATION, Editors.choice(Map.of("auto", "Automatic", "manual", "Manual", "off", "Off"))));
    }

    /** Program and Args apply only to the Custom profile, which an old program or args setting also selects. */
    static boolean customShell(SettingsDraft draft) {
        return TerminalConfig.Shell.custom(draft.value(TERMINAL_SHELL_PROGRAM), draft.value(TERMINAL_SHELL_ARGS), draft.value(TERMINAL_SHELL_PROFILE));
    }

    /** The whole tree with no detected shells. */
    public static Function<PageContext, List<SettingsPage>> pages(Supplier<List<String>> themeNames, SystemActions system,
                                                                   Function<PageContext, SettingsPage> keymap,
                                                                   Function<PageContext, SettingsPage> plugins) {
        return pages(themeNames, system, ShellChoices.NONE, keymap, plugins);
    }

    /**
     * The whole tree for one dialog, in display order: group and schema pages, with the Keymap and Plugins pages the
     * application supplies.
     */
    public static Function<PageContext, List<SettingsPage>> pages(Supplier<List<String>> themeNames, SystemActions system, ShellChoices shells,
                                                                   Function<PageContext, SettingsPage> keymap,
                                                                   Function<PageContext, SettingsPage> plugins) {
        return pages(themeNames, system, shells, keymap, plugins, context -> List.of());
    }

    /** The whole tree with no detected shells, followed by {@code tools}. */
    public static Function<PageContext, List<SettingsPage>> pages(Supplier<List<String>> themeNames, SystemActions system,
                                                                   Function<PageContext, SettingsPage> keymap,
                                                                   Function<PageContext, SettingsPage> plugins,
                                                                   Function<PageContext, List<SettingsPage>> tools) {
        return pages(themeNames, system, ShellChoices.NONE, keymap, plugins, tools);
    }

    /** As above with {@code shells}, followed by {@code tools}: pages under Tools, such as plugins' settings pages. */
    public static Function<PageContext, List<SettingsPage>> pages(Supplier<List<String>> themeNames, SystemActions system, ShellChoices shells,
                                                                   Function<PageContext, SettingsPage> keymap,
                                                                   Function<PageContext, SettingsPage> plugins,
                                                                   Function<PageContext, List<SettingsPage>> tools) {
        return context -> {
            var specs = specs(themeNames, system, shells);
            var all = new ArrayList<SettingsPage>();
            Function<String, Supplier<List<SettingsPage>>> childrenOf = id ->
                () -> all.stream().filter(page -> page.parent().equals(Optional.of(id))).toList();
            all.add(new GroupPage("appearance-behavior", "Appearance & Behavior", Optional.empty(), false,
                childrenOf.apply("appearance-behavior"), context));
            for (var spec : specs.subList(0, 6)) all.add(spec.build(context));
            all.add(keymap.apply(context));
            for (var spec : specs.subList(6, specs.size())) all.add(spec.build(context));
            all.add(plugins.apply(context));
            all.add(new GroupPage("tools", "Tools", Optional.empty(), true, childrenOf.apply("tools"), context));
            all.addAll(tools.apply(context));
            return List.copyOf(all);
        };
    }
}
