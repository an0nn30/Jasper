package dev.jasper.app.config;

import dev.jasper.app.config.PaletteSettings;
import dev.jasper.app.config.ToolbarMode;

import dev.jasper.app.settings.SettingValues;
import dev.jasper.terminal.config.Palette;
import dev.jasper.terminal.config.TerminalOptions;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validated saved defaults; only a pane's own font size is kept separately, by the pane. */
public record ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusBar,
                      FontConfig font, String theme, Map<String, String> keybindings,
                      int columns, int lines, TerminalConfig terminal, boolean buddyEnabled,
                      int visibleRows, int longCommandSeconds, boolean backgroundEnabled,
                      Map<String, Map<String, Object>> plugins, UiFontConfig uiFont, TerminalColors terminalColors,
                      int railWidth, int toolbarHeight, int statusBarHeight, boolean nativeDecorations,
                      WorkspaceThemeChoice workspaceTheme, GlassSettings glass) {
    /** The theme a configuration without {@code ui.theme.name} uses. */
    public static final String DEFAULT_THEME = "Jasper Dark";
    /** The rail's width in logical pixels without {@code window.rail_width}, and its limits. */
    public static final int DEFAULT_RAIL_WIDTH = 28, MIN_RAIL_WIDTH = 28, MAX_RAIL_WIDTH = 64;
    /** The toolbar (and unified macOS title bar) height in logical pixels without {@code window.toolbar_height}, and its limits. */
    public static final int DEFAULT_TOOLBAR_HEIGHT = 30, MIN_TOOLBAR_HEIGHT = 28, MAX_TOOLBAR_HEIGHT = 64;
    /** The status bar's height in logical pixels without {@code window.status_bar_height}, and its limits. */
    public static final int DEFAULT_STATUS_BAR_HEIGHT = 24, MIN_STATUS_BAR_HEIGHT = 20, MAX_STATUS_BAR_HEIGHT = 48;

    public ConfigSnapshot {
        if (longCommandSeconds < 0 || longCommandSeconds > 3600)
            throw new IllegalArgumentException("Long-command seconds must be 0\u20133600.");
        if (visibleRows < PaletteSettings.MIN_VISIBLE_ROWS || visibleRows > PaletteSettings.MAX_VISIBLE_ROWS)
            throw new IllegalArgumentException("Visible rows must be 1\u201320.");
        if (tabHeight < 20 || tabHeight > 72) throw new IllegalArgumentException("Tab height must be 20–72.");
        if (railWidth < MIN_RAIL_WIDTH || railWidth > MAX_RAIL_WIDTH) throw new IllegalArgumentException("Rail width must be 28–64.");
        if (toolbarHeight < MIN_TOOLBAR_HEIGHT || toolbarHeight > MAX_TOOLBAR_HEIGHT)
            throw new IllegalArgumentException("Toolbar height must be 28–64.");
        if (statusBarHeight < MIN_STATUS_BAR_HEIGHT || statusBarHeight > MAX_STATUS_BAR_HEIGHT)
            throw new IllegalArgumentException("Status bar height must be 20–48.");
        if (columns < 5 || columns > 500) throw new IllegalArgumentException("Columns must be 5–500.");
        if (lines < 2 || lines > 200) throw new IllegalArgumentException("Lines must be 2–200.");
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(uiFont, "uiFont");
        Objects.requireNonNull(terminal, "terminal");
        Objects.requireNonNull(toolbar, "toolbar");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(terminalColors, "terminalColors");
        Objects.requireNonNull(workspaceTheme, "workspaceTheme");
        Objects.requireNonNull(glass, "glass");
        keybindings = Map.copyOf(keybindings);
        // Plugin tables arrive deeply immutable from the loader; only the outer map is copied here.
        plugins = Map.copyOf(plugins);
        // A snapshot has no platform. At least one platform must accept its complete map;
        // the loader validates against the actual platform before constructing a snapshot.
        try {
            KeyBindings.withOverrides(true, keybindings);
        } catch (IllegalArgumentException macFailure) {
            try {
                KeyBindings.withOverrides(false, keybindings);
            } catch (IllegalArgumentException otherFailure) {
                throw new IllegalArgumentException("Keybindings must name known actions and valid, noncolliding shortcuts.");
            }
        }
    }

    /** Every field but the workspace glass, which defers to the theme. */
    public ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusBar,
                          FontConfig font, String theme, Map<String, String> keybindings,
                          int columns, int lines, TerminalConfig terminal, boolean buddyEnabled,
                          int visibleRows, int longCommandSeconds, boolean backgroundEnabled,
                          Map<String, Map<String, Object>> plugins, UiFontConfig uiFont, TerminalColors terminalColors,
                          int railWidth, int toolbarHeight, int statusBarHeight, boolean nativeDecorations,
                          WorkspaceThemeChoice workspaceTheme) {
        this(tabHeight, toolbar, statusBar, font, theme, keybindings, columns, lines, terminal, buddyEnabled,
            visibleRows, longCommandSeconds, backgroundEnabled, plugins, uiFont, terminalColors, railWidth,
            toolbarHeight, statusBarHeight, nativeDecorations, workspaceTheme, GlassSettings.UNSET);
    }

    /** Compatibility constructor for callers that still supply the terminal palette choice. */
    public ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusBar, FontConfig font, String theme,
                          Map<String, String> keybindings, int columns, int lines, TerminalConfig terminal,
                          boolean buddyEnabled, int visibleRows, int longCommandSeconds, boolean backgroundEnabled,
                          Map<String, Map<String, Object>> plugins, UiFontConfig uiFont, TerminalColors terminalColors,
                          int railWidth, int toolbarHeight, int statusBarHeight, boolean nativeDecorations) {
        this(tabHeight, toolbar, statusBar, font, theme, keybindings, columns, lines, terminal, buddyEnabled,
            visibleRows, longCommandSeconds, backgroundEnabled, plugins, uiFont, terminalColors, railWidth,
            toolbarHeight, statusBarHeight, nativeDecorations, workspaceFromTerminal(terminalColors));
    }

    private static WorkspaceThemeChoice workspaceFromTerminal(TerminalColors choice) {
        return switch (choice.name()) {
            case "light" -> WorkspaceThemeChoice.parse("Jasper Light");
            case "dark" -> WorkspaceThemeChoice.parse("Jasper Dark");
            default -> WorkspaceThemeChoice.parse(choice.name());
        };
    }

    private static TerminalColors terminalFromWorkspace(WorkspaceThemeChoice choice) {
        return switch (choice.name().toLowerCase(java.util.Locale.ROOT)) {
            case "jasper light" -> TerminalColors.LIGHT;
            case "jasper dark" -> TerminalColors.DARK;
            default -> TerminalColors.parse(choice.name());
        };
    }

    /** Every field but the rail width and bar heights, which are the defaults. */
    public ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusBar,
                          FontConfig font, String theme, Map<String, String> keybindings,
                          int columns, int lines, TerminalConfig terminal, boolean buddyEnabled,
                          int visibleRows, int longCommandSeconds, boolean backgroundEnabled,
                          Map<String, Map<String, Object>> plugins, UiFontConfig uiFont, TerminalColors terminalColors) {
        this(tabHeight, toolbar, statusBar, font, theme, keybindings, columns, lines, terminal, buddyEnabled,
            visibleRows, longCommandSeconds, backgroundEnabled, plugins, uiFont, terminalColors, DEFAULT_RAIL_WIDTH,
            DEFAULT_TOOLBAR_HEIGHT, DEFAULT_STATUS_BAR_HEIGHT, false);
    }

    public ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusBar,
                          FontConfig font, String theme, Map<String, String> keybindings,
                          int columns, int lines, TerminalConfig terminal, boolean buddyEnabled,
                          int visibleRows, int longCommandSeconds, boolean backgroundEnabled,
                          Map<String, Map<String, Object>> plugins) {
        this(tabHeight, toolbar, statusBar, font, theme, keybindings, columns, lines,
            terminal, buddyEnabled, visibleRows, longCommandSeconds, backgroundEnabled,
            plugins, UiFontConfig.defaults(), TerminalColors.MATCH);
    }


    /** Compatibility constructor for independently introduced appearance settings. */
    public ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusBar,
                          FontConfig font, String theme, Map<String, String> keybindings,
                          int columns, int lines, TerminalConfig terminal, boolean buddyEnabled,
                          int visibleRows, int longCommandSeconds, boolean backgroundEnabled,
                          Map<String, Map<String, Object>> plugins, UiFontConfig uiFont) {
        this(tabHeight, toolbar, statusBar, font, theme, keybindings, columns, lines,
            terminal, buddyEnabled, visibleRows, longCommandSeconds, backgroundEnabled,
            plugins, uiFont, TerminalColors.MATCH);
    }

    /** Every constructor that predates plugin tables: no plugin settings. */
    public ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusBar,
                   FontConfig font, String theme, Map<String, String> keybindings,
                   int columns, int lines, TerminalConfig terminal, boolean buddyEnabled,
                   int visibleRows, int longCommandSeconds, boolean backgroundEnabled) {
        this(tabHeight, toolbar, statusBar, font, theme, keybindings, columns, lines, terminal, buddyEnabled,
            visibleRows, longCommandSeconds, backgroundEnabled, Map.of());
    }

    /**
     * Every constructor that predates long-command notifications and background residency: the
     * default notification threshold, and never resident, because residency is opt-in.
     */
    public ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusBar,
                   FontConfig font, String theme, Map<String, String> keybindings,
                   int columns, int lines, TerminalConfig terminal, boolean buddyEnabled, int visibleRows) {
        this(tabHeight, toolbar, statusBar, font, theme, keybindings, columns, lines, terminal,
            buddyEnabled, visibleRows, 10, false);
    }

    public ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusBar,
                   FontConfig font, String theme, Map<String, String> keybindings,
                   int columns, int lines, TerminalConfig terminal) {
        this(tabHeight, toolbar, statusBar, font, theme, keybindings, columns, lines, terminal, false);
    }

    public ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusBar,
                   FontConfig font, String theme, Map<String, String> keybindings,
                   int columns, int lines, TerminalConfig terminal, boolean buddyEnabled) {
        this(tabHeight, toolbar, statusBar, font, theme, keybindings, columns, lines, terminal, buddyEnabled,
            PaletteSettings.DEFAULT_VISIBLE_ROWS);
    }

    public float fontSize() {
        return font.size();
    }

    public TerminalOptions viewOptions(float effectiveSize, Palette effectivePalette) {
        return new TerminalOptions(font.family(), effectiveSize, font.fallback(), font.ligatures(),
            effectivePalette, terminal.cursorShape(), terminal.cursorBlink(), terminal.optionAsMeta(),
            terminal.scrollback(), terminal.copyOnSelect(), font.lineHeight(), terminal.bell());
    }

    public static ConfigSnapshot defaults() {
        return new ConfigSnapshot(30, ToolbarMode.ICONS_AND_LABELS, true,
            FontConfig.defaults(), DEFAULT_THEME, Map.of(), 100, 35, TerminalConfig.defaults());
    }

    /** The snapshot for loaded settings plus the keybindings and legacy plugin tables their own readers produced. */
    public static ConfigSnapshot from(SettingValues values, Map<String, String> keybindings, Map<String, Map<String, Object>> plugins) {
        return new ConfigSnapshot(values.get(CoreSettings.WINDOW_TAB_HEIGHT), values.get(CoreSettings.WINDOW_TOOLBAR),
            values.get(CoreSettings.WINDOW_STATUS_BAR),
            new FontConfig(values.get(CoreSettings.TERMINAL_FONT_FAMILY), values.get(CoreSettings.TERMINAL_FONT_SIZE),
                values.get(CoreSettings.TERMINAL_FONT_FALLBACK), values.get(CoreSettings.TERMINAL_FONT_LIGATURES),
                values.get(CoreSettings.TERMINAL_FONT_LINE_HEIGHT)),
            values.get(CoreSettings.UI_THEME_NAME), keybindings, values.get(CoreSettings.WINDOW_COLUMNS), values.get(CoreSettings.WINDOW_LINES),
            new TerminalConfig(new TerminalConfig.Shell(values.get(CoreSettings.TERMINAL_SHELL_PROGRAM), values.get(CoreSettings.TERMINAL_SHELL_ARGS), values.get(CoreSettings.TERMINAL_SHELL_PROFILE)),
                values.get(CoreSettings.TERMINAL_ENV), values.get(CoreSettings.TERMINAL_SCROLLBACK), values.get(CoreSettings.TERMINAL_OPTION_AS_META),
                values.get(CoreSettings.TERMINAL_CURSOR_SHAPE), values.get(CoreSettings.TERMINAL_CURSOR_BLINK),
                values.get(CoreSettings.TERMINAL_DIM_INACTIVE_PANES), values.get(CoreSettings.TERMINAL_COPY_ON_SELECT),
                values.get(CoreSettings.TERMINAL_BELL), values.get(CoreSettings.TERMINAL_ON_EXIT), values.get(CoreSettings.TERMINAL_SHELL_INTEGRATION)),
            values.get(CoreSettings.BUDDY_ENABLED), values.get(CoreSettings.PALETTE_VISIBLE_ROWS),
            values.get(CoreSettings.NOTIFICATIONS_LONG_COMMAND_SECONDS), values.get(CoreSettings.SYSTEM_RUN_IN_BACKGROUND), plugins,
            new UiFontConfig(values.get(CoreSettings.UI_FONT_FAMILY), values.get(CoreSettings.UI_FONT_SIZE)),
            terminalFromWorkspace(WorkspaceThemeChoice.parse(values.get(CoreSettings.UI_THEME_WORKSPACE))), values.get(CoreSettings.WINDOW_RAIL_WIDTH),
            values.get(CoreSettings.WINDOW_TOOLBAR_HEIGHT), values.get(CoreSettings.WINDOW_STATUS_BAR_HEIGHT),
            values.get(CoreSettings.WINDOW_NATIVE_DECORATIONS),
            WorkspaceThemeChoice.parse(values.get(CoreSettings.UI_THEME_WORKSPACE)),
            new GlassSettings(values.get(CoreSettings.UI_WORKSPACE_OPACITY), values.get(CoreSettings.UI_WORKSPACE_BLUR)));
    }

    public KeyBindings bindings(boolean macOs) {
        return KeyBindings.withOverrides(macOs, keybindings);
    }

    public static Builder builder() { return new Builder(defaults()); }
    /** Copies every canonical field; subsequent changes retain unrelated saved settings. */
    public Builder toBuilder() { return new Builder(this); }
    /** Caller-confined fluent construction; build delegates to canonical validation. */
    public static final class Builder {
        private int tabHeight;
        private ToolbarMode toolbar;
        private boolean statusBar;
        private FontConfig font;
        private UiFontConfig uiFont;
        private String theme;
        private Map<String, String> keybindings;
        private int columns;
        private int lines;
        private TerminalConfig terminal;
        private boolean buddyEnabled;
        private int visibleRows;
        private int longCommandSeconds;
        private boolean backgroundEnabled;
        private Map<String, Map<String, Object>> plugins;
        private TerminalColors terminalColors;
        private WorkspaceThemeChoice workspaceTheme;
        private GlassSettings glass;
        private int railWidth;
        private int toolbarHeight;
        private int statusBarHeight;
        private boolean nativeDecorations;
        private Builder(ConfigSnapshot source) {
            tabHeight = source.tabHeight();
            toolbar = source.toolbar();
            statusBar = source.statusBar();
            font = source.font();
            uiFont = source.uiFont();
            theme = source.theme();
            keybindings = source.keybindings();
            columns = source.columns();
            lines = source.lines();
            terminal = source.terminal();
            buddyEnabled = source.buddyEnabled();
            visibleRows = source.visibleRows();
            longCommandSeconds = source.longCommandSeconds();
            backgroundEnabled = source.backgroundEnabled();
            plugins = source.plugins();
            terminalColors = source.terminalColors();
            workspaceTheme = source.workspaceTheme();
            glass = source.glass();
            railWidth = source.railWidth();
            toolbarHeight = source.toolbarHeight();
            statusBarHeight = source.statusBarHeight();
            nativeDecorations = source.nativeDecorations();
        }
        public Builder tabHeight(int value) { tabHeight = value; return this; }
        public Builder railWidth(int value) { railWidth = value; return this; }
        public Builder toolbarHeight(int value) { toolbarHeight = value; return this; }
        public Builder statusBarHeight(int value) { statusBarHeight = value; return this; }
        public Builder nativeDecorations(boolean value) { nativeDecorations = value; return this; }
        public Builder toolbar(ToolbarMode value) { toolbar = value; return this; }
        public Builder statusBar(boolean value) { statusBar = value; return this; }
        public Builder uiFont(UiFontConfig value) { uiFont = value; return this; }
        public Builder font(FontConfig value) { font = value; return this; }
        public Builder theme(String value) { theme = value; return this; }
        public Builder keybindings(Map<String, String> value) { keybindings = Map.copyOf(value); return this; }
        public Builder columns(int value) { columns = value; return this; }
        public Builder lines(int value) { lines = value; return this; }
        public Builder terminal(TerminalConfig value) { terminal = value; return this; }
        public Builder buddyEnabled(boolean value) { buddyEnabled = value; return this; }
        public Builder visibleRows(int value) { visibleRows = value; return this; }
        public Builder longCommandSeconds(int value) { longCommandSeconds = value; return this; }
        public Builder backgroundEnabled(boolean value) { backgroundEnabled = value; return this; }
        public Builder terminalColors(TerminalColors value) { terminalColors = value; workspaceTheme = workspaceFromTerminal(value); return this; }
        public Builder glass(GlassSettings value) { glass = value; return this; }
        public Builder workspaceTheme(WorkspaceThemeChoice value) { workspaceTheme = value; terminalColors = terminalFromWorkspace(value); return this; }
        public Builder plugins(Map<String, Map<String, Object>> value) { plugins = Map.copyOf(value); return this; }
        public ConfigSnapshot build() {
            return new ConfigSnapshot(tabHeight, toolbar, statusBar, font, theme, keybindings, columns, lines, terminal, buddyEnabled, visibleRows, longCommandSeconds, backgroundEnabled, plugins, uiFont, terminalColors, railWidth, toolbarHeight, statusBarHeight, nativeDecorations, workspaceTheme, glass);
        }
    }
}
