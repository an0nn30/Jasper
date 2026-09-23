package dev.jasper.app.shortcuthelp;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.ActionEntry;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.swing.KeyStroke;

/** Resolves shortcuts through the dispatch engine; contextual rows document explicit UI handlers. */
final class ShortcutCatalog {
    private ShortcutCatalog() { }

    static List<ShortcutEntry> rows(KeyBindings base, List<ActionEntry> actions, Map<String, String> plugins, boolean macOs) {
        var resolved = base.withExtensions(actions.stream().filter(a -> KeyBindings.extensionId(a.id()))
            .map(a -> new KeyBindings.Extension(a.id(), a.defaultBinding())).toList());
        var rows = new ArrayList<ShortcutEntry>();
        for (ActionId id : ActionId.values()) rows.add(new ShortcutEntry(id.id(), category(id), id.label(),
            resolved.bindings().strokeFor(id).orElse(null), "Terminal window", id.id().replace('_', ' ')));
        for (ActionEntry action : actions) {
            String context = resolved.problems().stream().filter(problem -> problem.actionId().equals(action.id()))
                .map(KeyBindings.Problem::message).findFirst().orElse("Terminal window");
            rows.add(new ShortcutEntry(action.id(), pluginGroup(action.id(), plugins), action.title(),
                resolved.bindings().strokeFor(action.id()).orElse(null), context, String.join(" ", action.keywords())));
        }
        contextual(rows, actions, plugins, macOs);
        rows.sort(Comparator.comparing(ShortcutEntry::group, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ShortcutEntry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(ShortcutEntry::id));
        return List.copyOf(rows);
    }

    private static String pluginGroup(String id, Map<String, String> plugins) {
        if (id.equals("plugins.manage") || id.equals("app.shortcuts")) return "Application";
        return plugins.keySet().stream().filter(plugin -> id.startsWith(plugin + "."))
            .max(Comparator.comparingInt(String::length)).map(plugins::get)
            .orElseGet(() -> id.contains(".") ? id.substring(0, id.lastIndexOf('.')) : "Application");
    }

    private static String category(ActionId id) {
        return switch (id) {
            case NEW_TAB, CLOSE_TAB, NEXT_TAB, PREVIOUS_TAB, RENAME_TAB, SELECT_TAB_1, SELECT_TAB_2,
                SELECT_TAB_3, SELECT_TAB_4, SELECT_TAB_5, SELECT_TAB_6, SELECT_TAB_7, SELECT_TAB_8, SELECT_TAB_9 -> "Tabs";
            case SPLIT_RIGHT, SPLIT_DOWN, CLOSE_PANE, ZOOM_PANE, FOCUS_PANE_LEFT, FOCUS_PANE_RIGHT,
                FOCUS_PANE_UP, FOCUS_PANE_DOWN -> "Panes";
            case FIND, FIND_NEXT, FIND_PREVIOUS -> "Search";
            case COPY, PASTE -> "Clipboard";
            case FONT_BIGGER, FONT_SMALLER, FONT_RESET -> "Appearance";
            case PREVIOUS_PROMPT, NEXT_PROMPT, CLEAR_SCROLLBACK -> "Terminal";
            case COMMAND_PALETTE -> "Command Palette";
            default -> "Application";
        };
    }

    private static void contextual(List<ShortcutEntry> rows, List<ActionEntry> actions, Map<String, String> plugins, boolean macOs) {
        int primary = macOs ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
        add(rows, "auxiliary.close", "Application", "Close auxiliary window", KeyEvent.VK_W, primary, "Plugin and help windows");
        add(rows, "plugins.menu", "Application", "Open selected plugin menu", KeyEvent.VK_CONTEXT_MENU, 0, "Plugins manager list");
        add(rows, "plugins.menu.shift", "Application", "Open selected plugin menu", KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK, "Plugins manager list");
        add(rows, "find.next", "Search", "Next match", KeyEvent.VK_ENTER, 0, "Find field");
        add(rows, "find.previous", "Search", "Previous match", KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK, "Find field");
        add(rows, "find.close", "Search", "Close find bar", KeyEvent.VK_ESCAPE, 0, "Find bar");
        add(rows, "palette.primary", "Command Palette", "Run primary action", KeyEvent.VK_ENTER, 0, "Command palette");
        add(rows, "palette.secondary", "Command Palette", "Run secondary action", KeyEvent.VK_ENTER, primary, "Command palette");
        add(rows, "palette.tertiary", "Command Palette", "Run third action", KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK, "Command palette");
        add(rows, "palette.next", "Command Palette", "Next result", KeyEvent.VK_DOWN, 0, "Command palette");
        add(rows, "palette.previous", "Command Palette", "Previous result", KeyEvent.VK_UP, 0, "Command palette");
        add(rows, "palette.tab", "Command Palette", "Accept scope or next field", KeyEvent.VK_TAB, 0, "Palette scope picker or step form");
        add(rows, "palette.backtab", "Command Palette", "Accept scope or previous field", KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK, "Palette scope picker or step form");
        add(rows, "palette.escape", "Command Palette", "Back or dismiss", KeyEvent.VK_ESCAPE, 0, "Command palette");
        for (int n = 1; n <= 5; n++) add(rows, "palette.result" + n, "Command Palette", "Run result " + n,
            KeyEvent.VK_1 + n - 1, primary, "Command palette");
        // These bundled controls have local handlers, not SDK action registrations. Keep in step with
        // HostsPanel.activate and ManagerPanel.editSelected; never instantiate plugin UI to inspect it.
        if (actions.stream().anyMatch(a -> a.id().startsWith("dev.jasper.remote."))) {
            String group = plugins.getOrDefault("dev.jasper.remote", "Remote");
            add(rows, "remote.activate", group, "Activate host or expand/collapse group", KeyEvent.VK_ENTER, 0, "SSH Hosts list");
            add(rows, "remote.next", group, "Next host or group", KeyEvent.VK_DOWN, 0, "SSH Hosts list");
            add(rows, "remote.previous", group, "Previous host or group", KeyEvent.VK_UP, 0, "SSH Hosts list");
        }
        if (actions.stream().anyMatch(a -> a.id().startsWith("dev.jasper.vault."))) {
            String group = plugins.getOrDefault("dev.jasper.vault", "Credential Vault");
            add(rows, "vault.edit", group, "Edit selected credential", KeyEvent.VK_ENTER, 0, "Vault entries list");
            add(rows, "vault.next", group, "Next credential", KeyEvent.VK_DOWN, 0, "Vault entries list");
            add(rows, "vault.previous", group, "Previous credential", KeyEvent.VK_UP, 0, "Vault entries list");
        }
    }

    private static void add(List<ShortcutEntry> rows, String id, String group, String title, int key, int modifiers, String context) {
        rows.add(new ShortcutEntry(id, group, title, KeyStroke.getKeyStroke(key, modifiers), context, ""));
    }
}
