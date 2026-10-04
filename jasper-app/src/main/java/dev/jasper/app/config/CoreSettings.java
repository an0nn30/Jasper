package dev.jasper.app.config;

import static dev.jasper.app.settings.SettingKind.OutOfRange.CLAMP;
import static dev.jasper.app.settings.SettingKind.OutOfRange.DEFAULT;

import dev.jasper.app.settings.Applies;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingKind;
import dev.jasper.app.settings.SettingsSchema;
import dev.jasper.terminal.config.BellMode;
import dev.jasper.terminal.config.CursorStyle;
import dev.jasper.terminal.config.OptionAsMeta;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every setting config.toml holds, in the order the generated template lists them. The loader reads
 * through this schema; the template, the example file's test and the documentation test check against it.
 */
public final class CoreSettings {
    private CoreSettings() { }

    private static final String FONT_NAME = "Use a nonblank font name without NUL; using the default.";

    static boolean noNul(String text) { return text.indexOf('\0') < 0; }

    static boolean fontName(String text) { return !text.isBlank() && noNul(text); }

    public static final Setting<Integer> WINDOW_TAB_HEIGHT = Setting.of("window.tab_height", SettingKind.integer(20, 72, CLAMP), 30)
        .label("Tab height").keywords("tabs", "height", "size")
        .description("Tab height in logical pixels, 20-72; out-of-range values use the nearest limit. Updates live.").build();
    public static final Setting<Integer> WINDOW_RAIL_WIDTH = Setting.of("window.rail_width",
            SettingKind.integer(ConfigSnapshot.MIN_RAIL_WIDTH, ConfigSnapshot.MAX_RAIL_WIDTH, CLAMP), ConfigSnapshot.DEFAULT_RAIL_WIDTH)
        .label("Panel rail width").keywords("rail", "panels", "sidebar", "width")
        .description("Panel rail width in logical pixels, 28-64; out-of-range values use the nearest limit. Updates live.").build();
    public static final Setting<ToolbarMode> WINDOW_TOOLBAR = Setting.of("window.toolbar", SettingKind.choice(List.of(
            Map.entry("icons_and_labels", ToolbarMode.ICONS_AND_LABELS), Map.entry("icons", ToolbarMode.ICONS),
            Map.entry("hidden", ToolbarMode.HIDDEN))), ToolbarMode.ICONS_AND_LABELS)
        .label("Toolbar").keywords("toolbar", "buttons", "labels", "icons")
        .description("Toolbar: \"icons_and_labels\", \"icons\", or \"hidden\"; updates live.").build();
    public static final Setting<Integer> WINDOW_TOOLBAR_HEIGHT = Setting.of("window.toolbar_height",
            SettingKind.integer(ConfigSnapshot.MIN_TOOLBAR_HEIGHT, ConfigSnapshot.MAX_TOOLBAR_HEIGHT, CLAMP), ConfigSnapshot.DEFAULT_TOOLBAR_HEIGHT)
        .label("Toolbar height").keywords("toolbar", "title bar", "height")
        .description("Toolbar/title-bar height in logical pixels, 28-64; out-of-range values use the nearest limit. Updates live.").build();
    public static final Setting<Boolean> WINDOW_STATUS_BAR = Setting.of("window.status_bar", SettingKind.bool(), true)
        .label("Show status bar").keywords("status bar", "footer")
        .description("Show the status bar; updates live.").build();
    public static final Setting<Integer> WINDOW_STATUS_BAR_HEIGHT = Setting.of("window.status_bar_height",
            SettingKind.integer(ConfigSnapshot.MIN_STATUS_BAR_HEIGHT, ConfigSnapshot.MAX_STATUS_BAR_HEIGHT, CLAMP), ConfigSnapshot.DEFAULT_STATUS_BAR_HEIGHT)
        .label("Status bar height").keywords("status bar", "footer", "height")
        .description("Status bar height in logical pixels, 20-48; never shorter than its text. Updates live.").build();
    public static final Setting<Integer> WINDOW_COLUMNS = Setting.of("window.columns", SettingKind.integer(5, 500, DEFAULT), 100)
        .label("Initial columns").keywords("grid", "width", "size", "columns").applies(Applies.NEW_WINDOWS)
        .description("Initial terminal grid for new windows only; existing windows keep their size.\n"
            + "Columns: 5-500. The window is clamped to the usable screen.").build();
    public static final Setting<Integer> WINDOW_LINES = Setting.of("window.lines", SettingKind.integer(2, 200, DEFAULT), 35)
        .label("Initial lines").keywords("grid", "height", "size", "rows").applies(Applies.NEW_WINDOWS)
        .description("Initial rows for new windows only, 2-200.").build();
    public static final Setting<Boolean> WINDOW_NATIVE_DECORATIONS = Setting.of("window.native_decorations", SettingKind.bool(), false)
        .label("Use the system title bar").keywords("title bar", "decorations", "window frame", "menu bar")
        .applies(Applies.RESTART)
        .description("Windows and Linux only: use the operating system's title bar, with the menus and the toolbar in\n"
            + "rows of their own, instead of Jasper's title bar. macOS always uses its own title bar.").build();
    public static final Setting<ViewMode> WINDOW_VIEW_MODE = Setting.of("window.view_mode", SettingKind.choice(List.of(
            Map.entry("normal", ViewMode.NORMAL), Map.entry("compact", ViewMode.COMPACT))), ViewMode.NORMAL)
        .label("View mode").keywords("title bar", "compact", "normal", "command palette")
        .applies(Applies.RESTART)
        .description("View mode: \"normal\" keeps the current workspace; \"compact\" uses the native title bar, hides the\n"
            + "toolbar and title search field, and opens the command palette as a floating card. Takes effect after restart.\n"
            + "The saved toolbar preference is retained for Normal.").build();
    public static final Setting<Boolean> BUDDY_ENABLED = Setting.of("buddy.enabled", SettingKind.bool(), false)
        .label("Show Jasper").keywords("buddy", "desk buddy", "mascot")
        .description("Show the pixel-art Jasper floating above your windows while a terminal is open; off by default,\n"
            + "updates live. The Show/Hide Jasper command and a right-click on Jasper toggle him for this session only.").build();
    public static final Setting<Integer> NOTIFICATIONS_LONG_COMMAND_SECONDS = Setting.of("notifications.long_command_seconds",
            SettingKind.integer(0, 3600, DEFAULT), 10)
        .label("Notify after (seconds)").keywords("notification", "long command", "finished", "alert")
        .description("Live. Notify when a command that ran at least this long finishes in a pane you are not\n"
            + "looking at. Needs shell integration: the duration comes from its command marks.\n"
            + "Zero disables notifications. Range 0-3600.").build();
    /**
     * Residency is on by default where a windowless Jasper stays reachable: macOS (its menu bar item and Dock) and
     * Windows (its notification-area icon). Linux has no tray, so it stays off there.
     */
    static boolean runsInBackgroundByDefault(String osName) {
        return osName != null && (osName.startsWith("Mac") || osName.startsWith("Windows"));
    }

    public static final Setting<Boolean> SYSTEM_RUN_IN_BACKGROUND = Setting.of("system.run_in_background", SettingKind.bool(),
            runsInBackgroundByDefault(System.getProperty("os.name")))
        .alias("background.enabled").label("Run in background").keywords("resident", "login", "autostart", "background", "menu bar")
        .applies(Applies.RESTART)
        .description("Keep Jasper running with no windows so a new window opens instantly, and start it in the background\n"
            + "at login. On by default on macOS and Windows, off on Linux. On macOS, Quit (Cmd+Q) closes every window and\n"
            + "keeps Jasper in the menu bar; Quit Jasper Completely there ends it. Residency applies from the next start;\n"
            + "the login item updates as soon as you save. Autostart needs an installed Jasper, not a development run.").build();
    public static final Setting<Integer> PALETTE_VISIBLE_ROWS = Setting.of("palette.visible_rows",
            SettingKind.integer(PaletteSettings.MIN_VISIBLE_ROWS, PaletteSettings.MAX_VISIBLE_ROWS, DEFAULT), PaletteSettings.DEFAULT_VISIBLE_ROWS)
        .alias("palette.max_results").label("Visible rows").keywords("palette", "search", "results", "rows", "height")
        .description("Result rows the palette list shows before it scrolls, 1-20; updates live.").build();
    public static final Setting<Integer> TERMINAL_SCROLLBACK = Setting.of("terminal.scrollback", SettingKind.integer(0, 1_000_000, DEFAULT), 10_000)
        .label("Scrollback lines").keywords("history", "buffer", "scrollback").applies(Applies.NEW_PANES)
        .description("Scrollback lines, 0-1000000; new panes only. Existing sessions keep their capacity.").build();
    public static final Setting<OptionAsMeta> TERMINAL_OPTION_AS_META = Setting.of("terminal.option_as_meta", SettingKind.choice(List.of(
            Map.entry("left", OptionAsMeta.LEFT), Map.entry("right", OptionAsMeta.RIGHT),
            Map.entry("both", OptionAsMeta.BOTH), Map.entry("none", OptionAsMeta.NONE))), OptionAsMeta.LEFT)
        .label("Option key as Meta").keywords("option", "alt", "meta", "keyboard")
        .description("Option key sending Meta: \"left\", \"right\", \"both\", or \"none\"; updates live.").build();
    public static final Setting<Float> TERMINAL_DIM_INACTIVE_PANES = Setting.of("terminal.dim_inactive_panes", SettingKind.number(0, 1), .3f)
        .label("Dim inactive panes").keywords("dim", "split", "inactive", "focus")
        .description("Inactive pane dimming, 0.0-1.0; updates live.").build();
    public static final Setting<Boolean> TERMINAL_COPY_ON_SELECT = Setting.of("terminal.copy_on_select", SettingKind.bool(), false)
        .label("Copy on select").keywords("clipboard", "selection", "copy")
        .description("Copy a completed selection to the clipboard; updates live.").build();
    public static final Setting<BellMode> TERMINAL_BELL = Setting.of("terminal.bell", SettingKind.choice(List.of(
            Map.entry("visual", BellMode.VISUAL), Map.entry("sound", BellMode.SOUND), Map.entry("none", BellMode.NONE))), BellMode.VISUAL)
        .label("Bell").keywords("bell", "beep", "sound", "flash")
        .description("Bell: \"visual\", \"sound\", or \"none\"; updates live.").build();
    public static final Setting<ShellExitBehavior> TERMINAL_ON_EXIT = Setting.of("terminal.on_exit", SettingKind.choice(List.of(
            Map.entry("keep_open", ShellExitBehavior.KEEP_OPEN), Map.entry("close_on_success", ShellExitBehavior.CLOSE_ON_SUCCESS),
            Map.entry("close", ShellExitBehavior.CLOSE))), ShellExitBehavior.KEEP_OPEN)
        .label("When the shell exits").keywords("exit", "close", "keep open")
        .description("Shell process exit: \"keep_open\", \"close_on_success\" (code 0), or \"close\" (any exit).\n"
            + "Only the exited pane closes; its tab/window closes when empty. Live for future exits.\n"
            + "\"keep_open\" retains output from the stopped shell; reload never closes retained output.").build();
    public static final Setting<ShellIntegrationMode> TERMINAL_SHELL_INTEGRATION = Setting.of("terminal.shell_integration", SettingKind.choice(List.of(
            Map.entry("auto", ShellIntegrationMode.AUTO), Map.entry("manual", ShellIntegrationMode.MANUAL),
            Map.entry("off", ShellIntegrationMode.OFF))), ShellIntegrationMode.AUTO)
        .label("Shell integration").keywords("integration", "prompt marks", "osc 133", "zsh", "bash", "fish").applies(Applies.NEW_PANES)
        .description("Shell integration: \"auto\" loads Jasper's zsh/bash/fish script after your own startup files (new panes only),\n"
            + "\"manual\" only exports JASPER_SHELL_INTEGRATION so you can source it yourself, \"off\" does neither.").build();
    public static final Setting<String> UI_THEME_WORKSPACE = Setting.of("ui.theme.workspace",
            SettingKind.string(text -> !text.isBlank(), "Use a workspace scheme name or match; using match.",
                text -> WorkspaceThemeChoice.parse(text.isBlank() ? "match" : text).name()), "match")
        .alias("terminal.colors").alias("ui.theme.terminal").label("Workspace scheme")
        .keywords("palette", "colours", "color scheme", "scheme", "theme", "alacritty", "workspace")
        .description("Colour scheme for terminal panes and their workspace controls. \"match\" follows the UI theme;\n"
            + "otherwise name a native theme. Choose it in Settings > Appearance, whose theme gear imports theme files. Changes update the workspace live.").build();
    /** Source compatibility for transitional pages; the schema registers this setting only once. */
    @Deprecated public static final Setting<String> TERMINAL_COLORS = UI_THEME_WORKSPACE;
    public static final Setting<String> TERMINAL_FONT_FAMILY = Setting.of("terminal.font.family",
            SettingKind.string(CoreSettings::fontName, FONT_NAME), FontConfig.defaults().family())
        .alias("font.family").label("Font").keywords("font", "typeface", "family")
        .description("Font family and ordered fallbacks update live. Missing fonts use JBR/system fallback.").build();
    public static final Setting<Float> TERMINAL_FONT_SIZE = Setting.of("terminal.font.size", SettingKind.number(6, 72), FontConfig.defaults().size())
        .alias("font.size").label("Font size").keywords("font", "size", "points", "zoom")
        .description("Font size in points, 6-72; changes update all panes live.\n"
            + "Reset Font Size restores this saved default.\n"
            + "A pane's own size (Cmd+= and Cmd+-) survives reloads until this saved size changes.").build();
    public static final Setting<List<String>> TERMINAL_FONT_FALLBACK = Setting.of("terminal.font.fallback",
            SettingKind.stringList(CoreSettings::fontName), FontConfig.defaults().fallback())
        .alias("font.fallback").label("Fallback fonts").keywords("font", "fallback", "emoji", "nerd font")
        .description("Fallback names must be nonblank; an empty fallback list is allowed.").build();
    public static final Setting<Boolean> TERMINAL_FONT_LIGATURES = Setting.of("terminal.font.ligatures", SettingKind.bool(), true)
        .alias("font.ligatures").label("Ligatures").keywords("font", "ligatures")
        .description("Render programming ligatures; updates live.").build();
    public static final Setting<Float> TERMINAL_FONT_LINE_HEIGHT = Setting.of("terminal.font.line_height", SettingKind.number(.5, 3), 1f)
        .alias("font.line_height").label("Line height").keywords("font", "line height", "spacing", "rows")
        .description("Line height multiplier, 0.5-3.0; updates live. Below 1.0 rows are tighter and glyphs may overlap.").build();
    public static final Setting<String> TERMINAL_SHELL_PROFILE = Setting.of("terminal.shell.profile",
            SettingKind.string(text -> text.isEmpty() || text.matches("[a-z][a-z0-9]*(:[A-Za-z0-9._-]+)?"),
                "Use a shell profile id such as \"powershell\", \"cmd\" or \"wsl:Ubuntu\"; using the default."), "")
        .label("Shell profile").keywords("shell", "profile", "default shell", "wsl", "powershell", "cmd", "command prompt")
        .applies(Applies.NEW_PANES)
        .description("New panes only. The shell new tabs start: \"powershell\", \"pwsh\" (PowerShell 7) or \"cmd\" on Windows,\n"
            + "\"wsl:<name>\" for a WSL distribution, \"login\" on macOS and Linux, or \"custom\" (program and args).\n"
            + "Empty means \"custom\" when program or args are set, otherwise the platform default. A split starts\n"
            + "the shell of the pane it splits. A profile that is not installed starts the default with a warning.").build();
    public static final Setting<String> TERMINAL_SHELL_PROGRAM = Setting.of("terminal.shell.program",
            SettingKind.string(text -> (text.isEmpty() || !text.isBlank()) && noNul(text),
                "Use an empty or nonblank executable name without NUL; using the default."), "")
        .label("Shell program").keywords("shell", "zsh", "bash", "fish", "command").applies(Applies.NEW_PANES)
        .description("New panes only, for the \"custom\" profile. Empty program resolves the default shell and appends\n"
            + "configured args. An explicit program gets only configured args, with no implicit login flag.").build();
    public static final Setting<List<String>> TERMINAL_SHELL_ARGS = Setting.of("terminal.shell.args",
            SettingKind.stringList(CoreSettings::noNul), List.<String>of())
        .label("Shell arguments").keywords("shell", "arguments", "flags").applies(Applies.NEW_PANES)
        .description("Arguments are exact strings, without shell parsing; preserve each argument separately.\n"
            + "Program and arguments must not contain NUL; a nonempty program cannot be blank.").build();
    public static final Setting<CursorStyle> TERMINAL_CURSOR_SHAPE = Setting.of("terminal.cursor.shape", SettingKind.choice(List.of(
            Map.entry("block", CursorStyle.BLOCK), Map.entry("beam", CursorStyle.BEAM), Map.entry("underline", CursorStyle.UNDERLINE))),
            CursorStyle.BLOCK)
        .label("Cursor shape").keywords("cursor", "caret", "block", "beam")
        .description("Live fallback when the running program has not set its own cursor style.\n"
            + "Shape: \"block\", \"beam\", or \"underline\".").build();
    public static final Setting<Boolean> TERMINAL_CURSOR_BLINK = Setting.of("terminal.cursor.blink", SettingKind.bool(), true)
        .label("Blinking cursor").keywords("cursor", "blink", "caret")
        .description("Blink the fallback cursor; updates live.").build();
    public static final Setting<Map<String, String>> TERMINAL_ENV = Setting.of("terminal.env", SettingKind.stringMap(new SettingKind.MapRules(
            TerminalConfig::validEnvName, "Use a portable environment name; entry omitted.",
            Set.of("TERM", "COLORTERM"), "Jasper sets this environment variable; entry omitted.",
            CoreSettings::noNul, "Environment values must contain no NUL; entry omitted.", "a string environment value")), Map.<String, String>of())
        .label("Environment variables").keywords("environment", "env", "variables", "PATH").applies(Applies.NEW_PANES)
        .description("New panes only: add string values to the inherited environment.\n"
            + "Names use letters, digits and underscore, starting with a letter or underscore.\n"
            + "Values must not contain NUL. Desktop launches remove inherited TERM_PROGRAM,\n"
            + "TERM_PROGRAM_VERSION, TERM_SESSION_ID, TMUX, TMUX_PANE, and ITERM_* variables,\n"
            + "then apply this overlay.\n"
            + "Explicit configured values are intentional overrides. On macOS, LANG defaults to\n"
            + "en_US.UTF-8 only when absent or blank; LC_* values are preserved. TERM and COLORTERM\n"
            + "remain reserved and are always forced to xterm-256color and truecolor.\n"
            + "This table does not change which default login shell is selected.").build();
    public static final Setting<String> UI_FONT_FAMILY = Setting.of("ui.font.family",
            SettingKind.string(CoreSettings::fontName, FONT_NAME), UiFontConfig.defaults().family())
        .label("UI font").keywords("font", "interface", "chrome", "typeface")
        .description("UI font for app chrome and plugin controls; updates live, independently of the terminal font.\n"
            + "\"system\" uses the platform font on macOS, Linux and Windows.\n"
            + "A missing named font falls back to that platform font.").build();
    public static final Setting<Float> UI_FONT_SIZE = Setting.of("ui.font.size", SettingKind.number(8, 32), UiFontConfig.defaults().size())
        .example(14f).label("UI font size").keywords("font", "interface", "size", "points")
        .description("Omit size to retain platform sizing. Use 8-32 points, including fractions, when set.").build();
    public static final Setting<String> UI_THEME_NAME = Setting.of("ui.theme.name",
            SettingKind.string(text -> true, "", String::strip), ConfigSnapshot.DEFAULT_THEME)
        .alias("ui.theme.variant").label("Theme").keywords("theme", "dark", "light", "appearance", "look")
        .description("The UI theme by name: \"Jasper Dark\" (the default), \"Jasper Light\", \"Nord\",\n"
            + "\"Clear Dark\", \"Clear Light\", or a native TOML theme in the themes folder beside this file. Choose it in Settings > Appearance, whose theme gear imports theme files. Switches live across windows.").build();
    public static final Setting<Float> UI_WORKSPACE_OPACITY = Setting.of("ui.workspace.opacity", SettingKind.number(10, 100),
            GlassSettings.UNSET_OPACITY)
        .example(95f).label("Opacity").keywords("opacity", "transparency", "translucent", "glass", "workspace", "background")
        .description("macOS only (ignored on other platforms), live: the terminal and editor background opacity, 10-100 percent.\n"
            + "Omit to use the workspace theme's value (opaque unless the theme sets one).").build();
    public static final Setting<WorkspaceBlur> UI_WORKSPACE_BLUR = Setting.of("ui.workspace.blur", SettingKind.choice(List.of(
            Map.entry("theme", WorkspaceBlur.THEME), Map.entry("on", WorkspaceBlur.ON), Map.entry("off", WorkspaceBlur.OFF))),
            WorkspaceBlur.THEME)
        .label("Blur").keywords("blur", "frosted", "glass", "workspace", "background")
        .description("macOS only (ignored on other platforms), live: blur the desktop behind a translucent workspace. \"theme\" follows the workspace theme.").build();
    public static final SettingsSchema SCHEMA = SettingsSchema.of(List.<Setting<?>>of(
            WINDOW_TAB_HEIGHT, WINDOW_RAIL_WIDTH, WINDOW_TOOLBAR, WINDOW_TOOLBAR_HEIGHT, WINDOW_STATUS_BAR, WINDOW_STATUS_BAR_HEIGHT,
            WINDOW_COLUMNS, WINDOW_LINES, WINDOW_NATIVE_DECORATIONS, WINDOW_VIEW_MODE, BUDDY_ENABLED, NOTIFICATIONS_LONG_COMMAND_SECONDS, SYSTEM_RUN_IN_BACKGROUND,
            PALETTE_VISIBLE_ROWS, TERMINAL_SCROLLBACK, TERMINAL_OPTION_AS_META, TERMINAL_DIM_INACTIVE_PANES, TERMINAL_COPY_ON_SELECT,
            TERMINAL_BELL, TERMINAL_ON_EXIT, TERMINAL_SHELL_INTEGRATION, TERMINAL_FONT_FAMILY, TERMINAL_FONT_SIZE,
            TERMINAL_FONT_FALLBACK, TERMINAL_FONT_LIGATURES, TERMINAL_FONT_LINE_HEIGHT, TERMINAL_SHELL_PROFILE, TERMINAL_SHELL_PROGRAM, TERMINAL_SHELL_ARGS,
            TERMINAL_CURSOR_SHAPE, TERMINAL_CURSOR_BLINK, TERMINAL_ENV, UI_FONT_FAMILY, UI_FONT_SIZE, UI_THEME_NAME, UI_THEME_WORKSPACE,
            UI_WORKSPACE_OPACITY, UI_WORKSPACE_BLUR),
        Set.of(List.of("keybindings"), List.of("plugins"), List.of("palette", "scopes")),
        Map.ofEntries(
            Map.entry(List.of("window"), "Workspace chrome. Heights and widths are logical pixels."),
            Map.entry(List.of("buddy"), "The desk buddy."),
            Map.entry(List.of("notifications"), "Desktop notifications for finished commands."),
            Map.entry(List.of("system"), "How Jasper runs when no window is open."),
            Map.entry(List.of("palette"), "The command palette."),
            Map.entry(List.of("terminal"), "Terminal behaviour shared by every pane."),
            Map.entry(List.of("terminal", "font"), "The terminal font; the interface font is [ui.font]."),
            Map.entry(List.of("terminal", "shell"), "The shell new panes start."),
            Map.entry(List.of("terminal", "cursor"), "The cursor a program sees until it chooses its own."),
            Map.entry(List.of("ui", "font"), "Interface typography, independent of the terminal font."),
            Map.entry(List.of("ui", "theme"), "The UI theme and workspace scheme."),
            Map.entry(List.of("ui", "workspace"), "Translucent terminal and editor backgrounds on macOS; omitted values follow the workspace theme.")));
}
