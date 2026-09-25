package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.Command;
import dev.jasper.app.commands.CommandRegistry;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.config.ToolbarMode;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.Action;

/** Adapts existing window actions to searchable commands, including the shared View menu actions. */
final class WindowCommands implements AutoCloseable {
    private final WindowContent owner;
    private final List<Subscription> registrations = new ArrayList<>();
    private final Map<String, Action> views = new LinkedHashMap<>();

    WindowCommands(WindowContent owner, CommandRegistry registry) {
        this.owner = owner;
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
                case OPEN_SETTINGS -> List.of("preferences", "configuration");
                case RELOAD_CONFIG -> List.of("configuration", "refresh");
                case ZOOM_PANE -> List.of("maximize", "restore");
                case FIND, FIND_NEXT, FIND_PREVIOUS -> List.of("search");
                case CLEAR_SCROLLBACK -> List.of("history", "clear");
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
                "Toolbar: " + label, () -> owner.setToolbarMode(mode));
        }
        add(registry, "view.status_bar", "Status Bar", "Hide Status Bar", () -> owner.setStatusVisible(!owner.status().isVisible()));
        add(registry, "view.rail", "Rail", "Hide Rail", () -> owner.setRailVisible(!owner.railVisible()), List.of("panels", "sidebar"));
        add(registry, "view.buddy", "Show Jasper", "Hide Jasper", () -> { owner.onToggleBuddy.run(); owner.updateActions(); },
            List.of("jasper", "mascot", "turtle", "desk buddy"));
        for (var appearance : Appearance.values()) {
            String label = switch (appearance) { case LIGHT -> "Light"; case DARK -> "Dark"; };
            add(registry, "view.appearance." + appearance.name().toLowerCase(java.util.Locale.ROOT), label,
                "Appearance: " + label, () -> owner.selectAppearance(appearance));
        }
        for (var colors : TerminalColors.values()) {
            String label = switch (colors) { case MATCH -> "Match UI"; case LIGHT -> "Light"; case DARK -> "Dark"; };
            add(registry, "view.terminal_colors." + colors.name().toLowerCase(java.util.Locale.ROOT), "Terminal: " + label,
                "Terminal Colors: " + label, () -> owner.selectTerminalColors(colors), List.of("terminal", "colors", "palette"));
        }
        add(registry, "view.tab_height", "Tab height\u2026", "Tab Height\u2026", () -> owner.chrome().editTabHeight());
    }

    private void add(CommandRegistry registry, String id, String label, String title, Runnable callback) {
        add(registry, id, label, title, callback, List.of());
    }

    private void add(CommandRegistry registry, String id, String label, String title, Runnable callback,
                      List<String> extraKeywords) {
        Action action = new AbstractAction(label) {
            @Override public void actionPerformed(ActionEvent event) {
                if (owner.isActiveAndOpen() && !owner.commandPalette().isOpen()
                        && !dev.jasper.app.platform.WindowInput.blocked(owner)) callback.run();
            }
        };
        action.putValue(Command.TITLE, title);
        views.put(id, action);
        var keywords = new java.util.ArrayList<>(List.of(id.replace('.', ' ').replace('_', ' ')));
        keywords.addAll(extraKeywords);
        registrations.add(registry.register(new Command(id, action, keywords)));
    }

    Action view(String id) { return views.get(id); }

    void refresh() {
        owner.action(ActionId.ZOOM_PANE).putValue(Command.TITLE,
            owner.currentTab() != null && owner.currentTab().tree().zoomed() ? "Restore Pane" : "Zoom Pane");
        view("view.status_bar").putValue(Command.TITLE, owner.status().isVisible() ? "Hide Status Bar" : "Show Status Bar");
        view("view.status_bar").putValue(Action.SELECTED_KEY, owner.status().isVisible());
        view("view.rail").putValue(Command.TITLE, owner.railVisible() ? "Hide Rail" : "Show Rail");
        view("view.rail").putValue(Action.SELECTED_KEY, owner.railVisible());
        boolean buddy = owner.buddyEnabled.getAsBoolean();
        view("view.buddy").putValue(Command.TITLE, buddy ? "Hide Jasper" : "Show Jasper");
        view("view.buddy").putValue(Action.SELECTED_KEY, buddy);
        for (var mode : ToolbarMode.values())
            view("view.toolbar." + mode.name().toLowerCase(java.util.Locale.ROOT)).putValue(Action.SELECTED_KEY, owner.toolbarMode() == mode);
for (var appearance : Appearance.values()) {
    var action = view("view.appearance." + appearance.name().toLowerCase(java.util.Locale.ROOT));
    action.putValue(Action.SELECTED_KEY, owner.appearance() == appearance);
    action.setEnabled(true);
}
        for (var colors : TerminalColors.values()) {
            var action = view("view.terminal_colors." + colors.name().toLowerCase(java.util.Locale.ROOT));
            action.putValue(Action.SELECTED_KEY, owner.terminalColors() == colors);
            action.setEnabled(true);
        }
view("view.tab_height").setEnabled(true);

    }

    @Override public void close() {
        registrations.forEach(Subscription::close); registrations.clear();
        views.values().forEach(action -> action.setEnabled(false));
    }
}
