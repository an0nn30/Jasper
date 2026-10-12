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


    /**
     * The pages the application supplies.
     *
     * @param keymap the Keymap page
     * @param plugins the Plugins page
     * @param tools pages under Tools, such as plugins' settings pages
     */
    public record Pages(Function<PageContext, SettingsPage> keymap, Function<PageContext, SettingsPage> plugins,
                        Function<PageContext, List<SettingsPage>> tools) {
        /** Keymap and Plugins only. */
        public static Pages of(Function<PageContext, SettingsPage> keymap, Function<PageContext, SettingsPage> plugins) {
            return new Pages(keymap, plugins, context -> List.of());
        }
    }

    /** Every schema page's spec, in tree order, with no detected shells. */
    public static List<SchemaPage.Spec> specs(AppearanceChoices appearance, SystemActions system) {
        return specs(appearance, system, ShellChoices.NONE);
    }

    /** Every schema page's spec, in tree order. */
    public static List<SchemaPage.Spec> specs(AppearanceChoices appearance, SystemActions system, ShellChoices shells) {
        return specs(appearance, system, shells, id -> {});
    }
    /** Schema pages whose links select other pages with {@code navigate}. */
    public static List<SchemaPage.Spec> specs(AppearanceChoices appearance, SystemActions system, ShellChoices shells, java.util.function.Consumer<String> navigate) {
        return specs(appearance, system, shells, navigate, com.formdev.flatlaf.util.SystemInfo.isMacOS);
    }

    /**
     * Schema pages; {@code glass} is whether workspace glass exists here (macOS only), which decides whether its
     * Settings rows (and their Workspace section) are built.
     */
    static List<SchemaPage.Spec> specs(AppearanceChoices appearance, SystemActions system, ShellChoices shells, java.util.function.Consumer<String> navigate, boolean glass) {
        return specs(appearance, system, shells, navigate, glass, NotificationGroupChoices.NONE);
    }

    /** As above, with the notification groups the Notifications page lists. */
    static List<SchemaPage.Spec> specs(AppearanceChoices appearance, SystemActions system, ShellChoices shells,
                                       java.util.function.Consumer<String> navigate, boolean glass, NotificationGroupChoices groups) {
        return List.of(
            SchemaPage.page("appearance", "Appearance").parent("appearance-behavior").anchor("theme")
                .section("Theme")
                .row(UI_APPEARANCE, Editors.appearance(appearance))
                .row(TERMINAL_SCHEME, Editors.choice(Map.of("match", Editors.SAME_AS_UI_THEME, "slate-steel-dark", "Slate Steel Dark",
                    "slate-steel-light", "Slate Steel Light", "high-contrast", "High Contrast"))).note("Colors for terminal panes")
                .row(UI_THEME_NAME).shownWhen(false)
                .row(UI_THEME_WORKSPACE).shownWhen(false)
                .row(UI_THEME_SOURCE).shownWhen(false)
                .row(UI_THEME_FOLLOW_SYSTEM).shownWhen(false)
                .section("Workspace")
                .row(UI_WORKSPACE_OPACITY, WorkspaceEditors.opacity(appearance)).shownWhen(glass)
                .row(UI_WORKSPACE_BLUR, WorkspaceEditors.blur(appearance)).shownWhen(glass)
                .section("Interface Font")
                .row(UI_FONT_FAMILY, Editors.interfaceFont())
                .row(UI_FONT_SIZE, PlatformFontSize.factory())
                .section("View mode")
                .row(WINDOW_VIEW_MODE)
                .section("Animations")
                .row(UI_ANIMATIONS_ENABLED),
            SchemaPage.page("window", "Window").parent("appearance-behavior")
                .section("Toolbar & Status Bar")
                .row(WINDOW_TOOLBAR, Editors.segments(Map.of("icons_and_labels", "Icons & Labels", "icons", "Icons Only", "hidden", "Hidden")))
                .row(WINDOW_TOOLBAR_HEIGHT, Editors.stepper("px"))
                .row(WINDOW_STATUS_BAR)
                .row(WINDOW_STATUS_BAR_HEIGHT, Editors.dependentHeight()).enabledWhen(draft -> draft.value(WINDOW_STATUS_BAR))
                .row(WINDOW_NATIVE_DECORATIONS).shownWhen(!com.formdev.flatlaf.util.SystemInfo.isMacOS)
                .section("Tabs & Panels")
                .row(WINDOW_TAB_HEIGHT, Editors.stepper("px"))
                .row(WINDOW_RAIL_WIDTH, Editors.stepper("px"))
                .section("New Windows")
                .row(WINDOW_COLUMNS, InitialSizeEditor.factory())
                .row(WINDOW_LINES).shownWhen(false),
            SchemaPage.page("notifications", "Notifications").parent("appearance-behavior").anchor("finished-command-notifications")
                .section("System Notifications")
                .row(NOTIFICATIONS_SYSTEM).note("Also post to the OS notification center")
                .row(NOTIFICATIONS_LONG_COMMAND_SECONDS, Editors.notificationDelay()).note("0 turns these notifications off.")
                .section("Groups")
                .row(NOTIFICATIONS_GROUPS, Editors.notificationGroups(groups))
                .note("How each kind of notification appears. A plugin's groups are listed once it has run."),
            SchemaPage.page("command-palette", "Command Palette").parent("appearance-behavior").anchor("palette")
                .row(PALETTE_VISIBLE_ROWS, Editors.stepper("")),
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
                    new SchemaPage.Button("Migrate Config\u2026", system.migrate(), system.migratable())),
            SchemaPage.page("terminal", "Terminal")
                .row(TERMINAL_SCROLLBACK, Editors.stepper("lines"))
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
                .action(() -> "Terminal colors come from the terminal scheme in Appearance.",
                    new SchemaPage.Button("Choose terminal scheme…", () -> navigate.accept("appearance"), () -> true)),
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
    public static Function<PageContext, List<SettingsPage>> pages(AppearanceChoices appearance, SystemActions system,
                                                                   Function<PageContext, SettingsPage> keymap,
                                                                   Function<PageContext, SettingsPage> plugins) {
        return pages(appearance, system, ShellChoices.NONE, keymap, plugins);
    }

    /**
     * The whole tree for one dialog, in display order: group and schema pages, with the Keymap and Plugins pages the
     * application supplies.
     */
    public static Function<PageContext, List<SettingsPage>> pages(AppearanceChoices appearance, SystemActions system, ShellChoices shells,
                                                                   Function<PageContext, SettingsPage> keymap,
                                                                   Function<PageContext, SettingsPage> plugins) {
        return pages(appearance, system, shells, keymap, plugins, context -> List.of());
    }

    /** The whole tree with no detected shells, followed by {@code tools}. */
    public static Function<PageContext, List<SettingsPage>> pages(AppearanceChoices appearance, SystemActions system,
                                                                   Function<PageContext, SettingsPage> keymap,
                                                                   Function<PageContext, SettingsPage> plugins,
                                                                   Function<PageContext, List<SettingsPage>> tools) {
        return pages(appearance, system, ShellChoices.NONE, keymap, plugins, tools);
    }

    /** As above with {@code shells}, followed by {@code tools}: pages under Tools, such as plugins' settings pages. */
    public static Function<PageContext, List<SettingsPage>> pages(AppearanceChoices appearance, SystemActions system, ShellChoices shells,
                                                                   Function<PageContext, SettingsPage> keymap,
                                                                   Function<PageContext, SettingsPage> plugins,
                                                                   Function<PageContext, List<SettingsPage>> tools) {
        return pages(appearance, system, shells, new Pages(keymap, plugins, tools));
    }

    /** The whole tree for one dialog, in display order, with the pages the application supplies and no notification groups. */
    public static Function<PageContext, List<SettingsPage>> pages(AppearanceChoices appearance,
                                                                   SystemActions system, ShellChoices shells, Pages supplied) {
        return pages(appearance, system, shells, NotificationGroupChoices.NONE, supplied);
    }

    /** The whole tree for one dialog, in display order, with the notification groups and pages the application supplies. */
    public static Function<PageContext, List<SettingsPage>> pages(AppearanceChoices appearance, SystemActions system, ShellChoices shells,
                                                                   NotificationGroupChoices groups, Pages supplied) {
        return context -> {
            var specs = specs(appearance, system, shells, context.navigate(), com.formdev.flatlaf.util.SystemInfo.isMacOS, groups);
            var all = new ArrayList<SettingsPage>();
            Function<String, Supplier<List<SettingsPage>>> childrenOf = id ->
                () -> all.stream().filter(page -> page.parent().equals(Optional.of(id))).toList();
            all.add(new GroupPage("appearance-behavior", "Appearance & Behavior", Optional.empty(), false,
                childrenOf.apply("appearance-behavior"), context));
            for (var spec : specs.subList(0, 6)) all.add(spec.build(context));
            all.add(supplied.keymap().apply(context));
            all.add(supplied.plugins().apply(context));
            for (var spec : specs.subList(6, specs.size())) all.add(spec.build(context));
            all.add(new GroupPage("tools", "Tools", Optional.empty(), true, childrenOf.apply("tools"), context));
            all.addAll(supplied.tools().apply(context));
            return List.copyOf(all);
        };
    }
}
