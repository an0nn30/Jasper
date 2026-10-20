package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.ListItem;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One plugin in the list as a kit row: the plugin tile, its name, version and vendor, a badge when it needs attention,
 * and its enable switch, with the plugin's own context menu. The row is a descriptor; the list renders it. EDT only.
 */
final class PluginCard {
    static final String TOGGLE = "toggle:";
    static final String SETTINGS = "settings:", FOLDER = "folder:", DATA = "data:", REMOVE = "remove:", DISCARD = "discard:";

    private PluginCard() { }

    /** The row; kit text is literal, so plugin text is never markup. {@code busy} disables the switch and the menu. */
    static ListItem item(PluginRuntime.Row row, boolean busy) {
        return item(row, busy, "plugin");
    }

    static ListItem item(PluginRuntime.Row row, boolean busy, String artwork) { return item(row, busy, artwork, PluginShape.ROWS); }

    /** The row in {@code shape}: under checkbox rows, the checkbox leads the name and there is no icon (spec 12.2). */
    static ListItem item(PluginRuntime.Row row, boolean busy, String artwork, PluginShape shape) {
        String id = row.id();
        var subtitle = new ArrayList<Nodes.Node>();
        subtitle.add(new Nodes.Text("meta:" + id, ControlState.READY, meta(row), Nodes.TextRole.CAPTION));
        String badge = badge(row);
        if (!badge.isEmpty()) {
            if (warning(row)) {
                subtitle.add(new Nodes.StatusDot("badge-dot:" + id, ControlState.READY, Nodes.StatusKind.DANGER));
                subtitle.add(new Nodes.Text("badge:" + id, ControlState.READY, badge, Nodes.TextRole.CAPTION));
            } else subtitle.add(new Nodes.Chip("badge:" + id, ControlState.READY, badge, Nodes.Hue.BLUE));
        }
        var toggle = new Nodes.Toggle(TOGGLE + id, new ControlState(true, !busy && toggleable(row), false, ""), "Enable " + row.name(),
            checked(row), TOGGLE + id, true, toggleable(row) ? "" : why(row));
        var icon = new Nodes.Icon("icon:" + id, new ControlState(true, !greyed(row), false, ""), artwork, Nodes.Hue.NEUTRAL,
            Nodes.IconSize.RAIL, row.name());
        if (shape.checkboxRows())
            return new ListItem(id, Optional.of(toggle), List.of(new Nodes.Text("name:" + id, ControlState.READY, row.name(), Nodes.TextRole.BODY)),
                List.of(new Nodes.Stack("second:" + id, ControlState.READY, Nodes.LayoutRole.INLINE, subtitle)), List.of(), false,
                Optional.of(menu(row, busy)));
        return new ListItem(id, Optional.of(icon), List.of(new Nodes.Text("name:" + id, ControlState.READY, row.name(), Nodes.TextRole.BODY_STRONG)),
            List.of(new Nodes.Stack("second:" + id, ControlState.READY, Nodes.LayoutRole.INLINE, subtitle)), List.of(toggle), false,
            Optional.of(menu(row, busy)));
    }

    /** The version, and the vendor when there is one. */
    static String meta(PluginRuntime.Row row) { return row.vendor().isBlank() ? row.version() : row.version() + " · " + row.vendor(); }

    /** Why the switch cannot act: what is pending, the reason, or a plain explanation. */
    static String why(PluginRuntime.Row row) {
        return !row.pending().isEmpty() ? row.pending() : !row.reason().isEmpty() ? row.reason() : "This plugin cannot be enabled or disabled here.";
    }

    /**
     * The plugin's other actions: the three openers, then Remove…, Keep or Discard Install when it has one. The same items
     * are the row's context menu and the details pane's More actions menu; each action names the plugin.
     */
    static List<MenuItem> menu(PluginRuntime.Row row, boolean busy) {
        String id = row.id();
        var items = new ArrayList<MenuItem>(List.of(
            new MenuItem("open-settings", "Open Settings", "", SETTINGS + id, !busy, false),
            new MenuItem("open-folder", "Open Plugin Folder", "", FOLDER + id, !busy, false),
            new MenuItem("open-data", "Open Data Folder", "", DATA + id, !busy, false)));
        String removal = PluginDetails.removal(row);
        if (!removal.isEmpty()) {
            items.add(new MenuItem("removal-separator", "", "", "", false, true));
            items.add(new MenuItem("removal", removal, "", (row.pendingInstall() ? DISCARD : REMOVE) + id, !busy, false));
        }
        return List.copyOf(items);
    }

    /** The card as a line: name, version and badge. */
    static String text(PluginRuntime.Row row) { return row.name() + "  " + row.version() + (badge(row).isEmpty() ? "" : " — " + badge(row)); }

    /** The first badge that applies, or empty. */
    static String badge(PluginRuntime.Row row) {
        if (row.pendingRemoval()) return "Remove at restart";
        if (row.pendingInstall()) return "Install at restart";
        if (!row.pending().isEmpty()) return "Restart to apply";
        if (row.needsConsent()) return "Needs review";
        return switch (row.state()) {
            case "FAILED" -> "Failed";
            case "SKIPPED" -> "Skipped";
            default -> "";
        };
    }

    /** Whether the badge is a warning (failed or skipped) rather than a pending change. */
    static boolean warning(PluginRuntime.Row row) {
        String badge = badge(row);
        return badge.equals("Failed") || badge.equals("Skipped");
    }

    /** Disabled, or not running because it needs review, failed or was skipped. */
    static boolean greyed(PluginRuntime.Row row) {
        return !row.enabled() || row.needsConsent() || row.state().equals("FAILED") || row.state().equals("SKIPPED");
    }

    /** Whether the switch is on: enabled, and not waiting for a review before it can run. */
    static boolean checked(PluginRuntime.Row row) { return row.enabled() && !row.needsConsent(); }

    /** Whether the switch can act: toggle it, or review it first. */
    static boolean toggleable(PluginRuntime.Row row) { return row.canToggle() || row.needsConsent(); }
}
