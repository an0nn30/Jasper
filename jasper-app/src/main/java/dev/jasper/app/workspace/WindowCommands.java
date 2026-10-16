package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.Command;
import dev.jasper.app.commands.CommandRegistry;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.config.ToolbarMode;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.Action;

/**
 * Adapts existing window actions to searchable commands, including the shared View menu actions. The "Terminal: …"
 * commands follow the installed schemes: they are rebuilt whenever the theme library changes.
 */
final class WindowCommands implements AutoCloseable {
    private final WindowContent owner;
    private final CommandRegistry registry;
    private final List<Subscription> registrations = new ArrayList<>();
    private final Map<String, Action> views = new LinkedHashMap<>();
    private final Map<String, Subscription> schemeCommands = new LinkedHashMap<>();
    private List<TerminalSchemeChoice> schemes = List.of();
    private ThemeLibrary schemesFrom;
    private final Subscription themeListening;

    WindowCommands(WindowContent owner, CommandRegistry registry) {
        this.owner = owner;
        this.registry = registry;
        owner.action(ActionId.SPLIT_RIGHT).putValue(Command.TITLE, "Split Right \u00b7 Vertical");
        owner.action(ActionId.SPLIT_DOWN).putValue(Command.TITLE, "Split Down \u00b7 Horizontal");
        for (ActionId id : ActionId.values()) {
            if (id == ActionId.COMMAND_PALETTE) continue;
            String icon = switch (id) {
                case NEW_TAB -> "square-plus"; case NEW_WINDOW -> "app-window";
                case SPLIT_RIGHT, SPLIT_DOWN -> "columns-2"; case ZOOM_PANE -> "maximize";
                case FIND, FIND_NEXT, FIND_PREVIOUS -> "search"; case OPEN_SETTINGS -> "settings";
                case RELOAD_CONFIG -> "refresh"; default -> null;
            };
            if (icon != null) owner.action(id).putValue(Command.ICON, AppIcons.icon(icon));
            if (id == ActionId.SPLIT_RIGHT || id == ActionId.SPLIT_DOWN) continue;
            var keywords = new ArrayList<String>(); keywords.add(id.id().replace('_', ' '));
            keywords.addAll(switch (id) {
                case OPEN_FILE -> List.of("open", "editor", "browse");
                case OPEN_SETTINGS -> List.of("preferences", "configuration");
                case RELOAD_CONFIG -> List.of("configuration", "refresh");
                case ZOOM_PANE -> List.of("maximize", "restore");
                case TOGGLE_ZEN_MODE -> List.of("view mode", "focus", "distraction free", "hide tools", "restore layout");
                case FIND, FIND_NEXT, FIND_PREVIOUS -> List.of("search");
                case CLEAR_SCROLLBACK -> List.of("history", "clear");
                case CLEAR_TERMINAL -> List.of("clear", "screen");
                case FONT_BIGGER, FONT_SMALLER, FONT_RESET -> List.of("text", "size");
                default -> List.of();
            });
            registrations.add(registry.register(new Command(id.id(), owner.action(id), keywords)));
        }
        registrations.add(registry.register(new Command("split_right", owner.action(ActionId.SPLIT_RIGHT),
            List.of("split", "vertical", "right", "side by side", "pane"))));
        registrations.add(registry.register(new Command("split_down", owner.action(ActionId.SPLIT_DOWN),
            List.of("split", "horizontal", "down", "above below", "pane"))));
        for (var mode : ToolbarMode.values()) {
            String label = switch (mode) { case ICONS_AND_LABELS -> "Icons and Labels"; case ICONS -> "Icons Only"; case HIDDEN -> "Hidden"; };
            add(registry, "view.toolbar." + mode.name().toLowerCase(java.util.Locale.ROOT), label,
                "Toolbar: " + label, () -> owner.chooseToolbarMode(mode));
        }
        add(registry, "view.status_bar", "Status Bar", "Hide Status Bar", () -> owner.chooseStatusVisible(!owner.status().isVisible()));
        add(registry, "view.rail", "Rail", "Hide Rail", () -> owner.setRailVisible(!owner.railVisible()), List.of("panels", "sidebar"));
        add(registry, "view.buddy", "Show Jasper", "Hide Jasper", () -> { owner.onToggleBuddy.run(); owner.updateActions(); },
            List.of("jasper", "mascot", "turtle", "desk buddy"));
        themeListening = owner.themes().subscribe((theme, chrome) -> syncSchemes());
        add(registry, "view.themes", "Themes…", "Themes…", owner::openThemesSettings, List.of("theme", "appearance", "dark", "light", "terminal scheme"));
        add(registry, "view.window_settings", "Window Settings\u2026", "Window Settings\u2026", owner::openWindowSettings,
            List.of("tab height", "rail width", "toolbar height", "status bar height", "size"));
    }

    private void add(CommandRegistry registry, String id, String label, String title, Runnable callback) {
        add(registry, id, label, title, callback, List.of());
    }

    private void add(CommandRegistry registry, String id, String label, String title, Runnable callback,
                      List<String> extraKeywords) {
        registrations.add(register(registry, id, label, title, callback, extraKeywords));
    }

    private Subscription register(CommandRegistry registry, String id, String label, String title, Runnable callback,
                                  List<String> extraKeywords) {
        Action action = new AbstractAction(label) {
            @Override public void actionPerformed(ActionEvent event) {
                if (isEnabled() && owner.isActiveAndOpen() && !owner.commandPalette().isOpen()
                        && !dev.jasper.app.platform.WindowInput.blocked(owner)) callback.run();
            }
        };
        action.putValue(Command.TITLE, title);
        views.put(id, action);
        var keywords = new java.util.ArrayList<>(List.of(id.replace('.', ' ').replace('_', ' ')));
        keywords.addAll(extraKeywords);
        return registry.register(new Command(id, action, keywords));
    }

    Action view(String id) { return views.get(id); }

    /** "Terminal: Same as UI theme", then one command per installed scheme; rebuilt only when the library changed. */
    private void syncSchemes() {
        var library = owner.themes().library();
        if (library == schemesFrom) return;
        schemesFrom = library;
        schemeCommands.values().forEach(Subscription::close);
        schemeCommands.keySet().forEach(id -> views.remove(id).setEnabled(false));
        schemeCommands.clear();
        var choices = new ArrayList<TerminalSchemeChoice>(List.of(TerminalSchemeChoice.MATCH));
        for (var scheme : library.schemes()) choices.add(TerminalSchemeChoice.of(scheme.id()));
        for (var choice : choices) {
            String label = choice.scheme().flatMap(library::scheme).map(library::label).orElse("Same as UI theme");
            String id = schemeCommandId(choice);
            schemeCommands.put(id, register(registry, id, "Terminal: " + label, "Terminal Scheme: " + label,
                () -> owner.selectTerminalScheme(choice), List.of("terminal", "scheme", "colors", "palette")));
        }
        schemes = List.copyOf(choices);
    }

    /** The command that chooses {@code choice}: {@code view.terminal_scheme.match} or {@code view.terminal_scheme.<theme>.<key>}. */
    static String schemeCommandId(TerminalSchemeChoice choice) {
        return "view.terminal_scheme." + choice.scheme().map(id -> id.theme() + "." + id.key()).orElse("match");
    }

    void refresh() {
        boolean zen = owner.zenMode();
        owner.action(ActionId.TOGGLE_ZEN_MODE).putValue(Action.SELECTED_KEY, zen);
        owner.action(ActionId.TOGGLE_ZEN_MODE).putValue(Command.TITLE, zen ? "Exit Zen Mode" : "Enter Zen Mode");
        owner.action(ActionId.ZOOM_PANE).putValue(Command.TITLE,
            owner.currentTab() != null && owner.currentTab().tree().zoomed() ? "Restore Pane" : "Zoom Pane");
        view("view.status_bar").putValue(Command.TITLE, owner.status().isVisible() ? "Hide Status Bar" : "Show Status Bar");
        view("view.status_bar").putValue(Action.SELECTED_KEY, owner.status().isVisible());
        view("view.status_bar").setEnabled(!zen);
        view("view.rail").putValue(Command.TITLE, !zen && owner.railVisible() ? "Hide Rail" : "Show Rail");
        view("view.rail").putValue(Action.SELECTED_KEY, !zen && owner.railVisible());
        view("view.rail").setEnabled(!zen);
        boolean buddy = owner.buddyEnabled.getAsBoolean();
        view("view.buddy").putValue(Command.TITLE, buddy ? "Hide Jasper" : "Show Jasper");
        view("view.buddy").putValue(Action.SELECTED_KEY, buddy);
        for (var mode : ToolbarMode.values()) {
            var action = view("view.toolbar." + mode.name().toLowerCase(java.util.Locale.ROOT));
            action.putValue(Action.SELECTED_KEY, owner.toolbarMode() == mode);
        }
        syncSchemes();
        for (var scheme : schemes) {
            var action = view(schemeCommandId(scheme));
            action.putValue(Action.SELECTED_KEY, owner.terminalSchemeSelected(scheme));
            action.setEnabled(true);
        }
        view("view.themes").setEnabled(owner.settingsConnected());
        view("view.window_settings").setEnabled(owner.settingsConnected());
    }

    @Override public void close() {
        themeListening.close();
        registrations.forEach(Subscription::close); registrations.clear();
        schemeCommands.values().forEach(Subscription::close); schemeCommands.clear();
        views.values().forEach(action -> action.setEnabled(false));
    }
}
