package dev.jasper.app.persistence;

import dev.jasper.app.dock.Slot;
import dev.jasper.app.dock.ToolWindowMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.tomlj.TomlTable;

/**
 * One window's tool windows as {@code workspace.toml} saves them (full windows spec 6.1): whether its stripes show, Zen
 * mode, and per look it has been shown under its dock's sizes and ratios and each tool window's slot, order, visibility,
 * mode and own window's bounds. Immutable; {@link ToolWindowState} is the window's live copy.
 *
 * @param railVisible whether the stripes show
 * @param zenMode whether the window is in Zen
 * @param looks each look's dock by its key: the providing plugin's id, {@code ""} for the default look; a look missing
 *     here starts from its built-in dock
 */
public record ToolWindows(boolean railVisible, boolean zenMode, Map<String, Look> looks) {
    /**
     * One look's dock.
     *
     * @param panels each tool window's remembered place, by its id
     * @param dock the dock's sizes and ratios
     */
    public record Look(Map<String, UiState.Panel> panels, UiState.Dock dock) {
        /** Copies the panels. */
        public Look {
            panels = Map.copyOf(panels);
            Objects.requireNonNull(dock, "dock");
            for (String id : panels.keySet()) if (!ID.matcher(id).matches()) throw new IllegalArgumentException("Not a tool window id: " + id);
        }
    }

    /** A fresh window's: the stripes show, Zen is off, and every look starts from its built-in dock. */
    public static final ToolWindows BUILT_IN = new ToolWindows(true, false, Map.of());

    /** A tool window's id and a look's key (other than the default look's {@code ""}): no TOML escaping needed. */
    static final Pattern ID = Pattern.compile("[a-z][a-z0-9_.-]{0,127}");

    /** Copies the looks and checks their keys. */
    public ToolWindows {
        looks = Map.copyOf(looks);
        for (String key : looks.keySet()) if (!lookKey(key)) throw new IllegalArgumentException("Not a look key: " + key);
    }

    /** Whether {@code key} names a look: empty for the default look, else a plugin id. */
    static boolean lookKey(String key) { return key.isEmpty() || ID.matcher(key).matches(); }

    /**
     * The tool windows in {@code table} (a window's {@code tool_windows}): {@code rail_visible}, {@code zen_mode}, the
     * default look's {@code dock} and {@code panels}, and each other look's under {@code look."<id>"}. A bad key, panel
     * or look falls back alone.
     */
    static ToolWindows read(TomlTable table) {
        boolean rail = !(table.get(List.of("rail_visible")) instanceof Boolean visible) || visible;
        boolean zen = Boolean.TRUE.equals(table.get(List.of("zen_mode")));
        var looks = new TreeMap<String, Look>();
        readLook(table).ifPresent(look -> looks.put("", look));
        if (table.get(List.of("look")) instanceof TomlTable others)
            for (String key : others.keySet())
                if (ID.matcher(key).matches() && others.get(List.of(key)) instanceof TomlTable entry) readLook(entry).ifPresent(look -> looks.put(key, look));
        return new ToolWindows(rail, zen, looks);
    }

    /** One look's {@code dock} table and one entry per panel; empty when it has neither. A bad key or entry falls back alone. */
    private static Optional<Look> readLook(TomlTable toml) {
        boolean hasDock = toml.get(List.of("dock")) instanceof TomlTable;
        boolean hasPanels = toml.get(List.of("panels")) instanceof TomlTable;
        if (!hasDock && !hasPanels) return Optional.empty();
        UiState.Dock dock = UiState.Dock.DEFAULT;
        if (toml.get(List.of("dock")) instanceof TomlTable table)
            dock = new UiState.Dock(size(table, "left_width", dock.leftWidth()), size(table, "right_width", dock.rightWidth()),
                size(table, "bottom_height", dock.bottomHeight()), ratio(table, "left_ratio", dock.leftRatio()),
                ratio(table, "right_ratio", dock.rightRatio()), ratio(table, "bottom_ratio", dock.bottomRatio()));
        var panels = new TreeMap<String, UiState.Panel>();
        if (toml.get(List.of("panels")) instanceof TomlTable table)
            for (String id : table.keySet()) {
                if (!ID.matcher(id).matches() || !(table.get(List.of(id)) instanceof TomlTable entry)) continue;
                Optional<Slot> slot = slot(entry.get(List.of("slot")));
                if (slot.isEmpty()) continue;
                int order = entry.get(List.of("order")) instanceof Long value && value >= 0 && value <= UiState.Panel.LAST
                    ? value.intValue() : UiState.Panel.LAST;
                Optional<UiState.Bounds> window = entry.get(List.of("window")) instanceof TomlTable own ? UiState.bounds(own) : Optional.empty();
                panels.put(id, new UiState.Panel(slot.get(), order, Boolean.TRUE.equals(entry.get(List.of("visible"))), mode(entry.get(List.of("mode"))), window));
            }
        return Optional.of(new Look(panels, dock));
    }

    /** The detached mode a file's {@code mode} names (lower case, as saved); anything else, or none, is docked. */
    private static ToolWindowMode mode(Object value) {
        for (ToolWindowMode detached : ToolWindowMode.values()) if (detached.detached() && detached.name().toLowerCase(Locale.ROOT).equals(value)) return detached;
        return ToolWindowMode.DOCKED;
    }

    private static Optional<Slot> slot(Object value) {
        for (Slot slot : Slot.values()) if (slot.name().equals(value)) return Optional.of(slot);
        return Optional.empty();
    }

    private static int size(TomlTable table, String key, int fallback) {
        return table.get(List.of(key)) instanceof Long value && UiState.Dock.size(value) ? value.intValue() : fallback;
    }

    private static double ratio(TomlTable table, String key, double fallback) {
        return table.get(List.of(key)) instanceof Double value && UiState.Dock.ratio(value) ? value : fallback;
    }

    /**
     * Writes these tool windows as the table {@code table} ({@code window.tool_windows}): its keys, then each look's
     * {@code dock} table and panel entries, the default look's directly under it and each other look's under
     * {@code look."<id>"}, in key order.
     */
    void append(StringBuilder text, String table) {
        text.append("\n[").append(table).append("]\nrail_visible = ").append(railVisible).append("\nzen_mode = ").append(zenMode).append('\n');
        new TreeMap<>(looks).forEach((key, look) ->
            appendLook(text, table + "." + (key.isEmpty() ? "" : "look.\"" + key + "\"."), look));
    }

    /** One look's {@code dock} table and panel entries, their table names under {@code prefix}. */
    private static void appendLook(StringBuilder text, String prefix, Look look) {
        UiState.Dock dock = look.dock();
        text.append("\n[").append(prefix).append("dock]\nleft_width = ").append(dock.leftWidth()).append("\nright_width = ").append(dock.rightWidth())
            .append("\nbottom_height = ").append(dock.bottomHeight()).append("\nleft_ratio = ").append(dock.leftRatio())
            .append("\nright_ratio = ").append(dock.rightRatio()).append("\nbottom_ratio = ").append(dock.bottomRatio()).append('\n');
        new TreeMap<>(look.panels()).forEach((id, panel) -> {
            text.append("\n[").append(prefix).append("panels.\"").append(id).append("\"]\nslot = \"")
                .append(panel.slot().name()).append("\"\norder = ").append(panel.order()).append("\nvisible = ").append(panel.visible()).append('\n');
            if (panel.mode().detached()) text.append("mode = \"").append(panel.mode().name().toLowerCase(Locale.ROOT)).append("\"\n");
            panel.window().ifPresent(bounds -> text.append("window = { x = ").append(bounds.x()).append(", y = ").append(bounds.y())
                .append(", width = ").append(bounds.width()).append(", height = ").append(bounds.height()).append(" }\n"));
        });
    }
}
