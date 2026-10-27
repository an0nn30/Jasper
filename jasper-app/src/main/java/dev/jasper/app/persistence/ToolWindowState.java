package dev.jasper.app.persistence;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One window's live tool-window state (full windows spec 6.1): whether its stripes show, Zen mode, and per look its
 * dock's sizes and ratios and where each tool window docks. It reads and writes the dock of the look it was made under;
 * a look it has none for starts from that look's built-in dock, and the other looks' docks are kept as they came.
 * {@link #changed()} tells its listener, the window manager, that {@code workspace.toml} has something new to save.
 * Confined to the Event Dispatch Thread.
 */
public final class ToolWindowState {
    /** One look's dock: where each panel docks, and the dock's sizes and ratios. */
    private static final class Layout {
        final Map<String, UiState.Panel> panels = new TreeMap<>();
        UiState.Dock dock = UiState.Dock.DEFAULT;

        Layout() { }

        Layout(ToolWindows.Look saved) {
            panels.putAll(saved.panels());
            dock = saved.dock();
        }
    }

    /** The look whose dock this state reads and writes: empty for the default look. */
    private final String look;
    /** Each look's dock by its key, the default look's under the empty key; only the looks saved or written so far. */
    private final Map<String, Layout> layouts = new TreeMap<>();
    private boolean railVisible;
    private boolean zenMode;
    private Runnable listener = () -> { };

    /**
     * A window's state as {@code saved} has it, under the look {@code look}.
     *
     * @param saved the saved tool windows; {@link ToolWindows#BUILT_IN} for a fresh window
     * @param look the look's key: empty for the default look, else the providing plugin's id
     */
    public ToolWindowState(ToolWindows saved, String look) {
        Objects.requireNonNull(saved, "saved");
        this.look = Objects.requireNonNull(look, "look");
        if (!ToolWindows.lookKey(look)) throw new IllegalArgumentException("Not a look key: " + look);
        railVisible = saved.railVisible();
        zenMode = saved.zenMode();
        saved.looks().forEach((key, layout) -> layouts.put(key, new Layout(layout)));
    }

    /** @return the look whose dock this state reads and writes: empty for the default look */
    public String look() { return look; }

    /**
     * @param id a tool window's id
     * @return the look's remembered place of that tool window
     */
    public Optional<UiState.Panel> panel(String id) {
        Layout layout = layouts.get(look);
        return layout == null ? Optional.empty() : Optional.ofNullable(layout.panels.get(id));
    }

    /** @return every tool window the look remembers, by id, sorted by id */
    public Map<String, UiState.Panel> panels() {
        Layout layout = layouts.get(look);
        return layout == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(layout.panels));
    }

    /**
     * Remembers a tool window's place under the look; an id TOML would need to escape is ignored.
     *
     * @param id the tool window's id
     * @param value its place
     */
    public void putPanel(String id, UiState.Panel value) {
        Objects.requireNonNull(value, "value");
        if (ToolWindows.ID.matcher(id).matches()) layout().panels.put(id, value);
    }

    /** @return the look's dock sizes and ratios */
    public UiState.Dock dock() {
        Layout layout = layouts.get(look);
        return layout == null ? UiState.Dock.DEFAULT : layout.dock;
    }

    /** @param value the look's dock sizes and ratios */
    public void setDock(UiState.Dock value) { layout().dock = Objects.requireNonNull(value, "value"); }

    /** @return whether the stripes show */
    public boolean railVisible() { return railVisible; }

    /** @param value whether the stripes show */
    public void setRailVisible(boolean value) { railVisible = value; }

    /** @return whether the window is in Zen */
    public boolean zenMode() { return zenMode; }

    /** @param value whether the window is in Zen */
    public void setZenMode(boolean value) { zenMode = value; }

    /** Tells the listener that this state changed: {@code workspace.toml} has something new to save. */
    public void changed() { listener.run(); }

    /** @param value what {@link #changed()} runs, replacing the one before */
    public void onChanged(Runnable value) { listener = Objects.requireNonNull(value, "value"); }

    /** @return every look's layout as it is now, not only the current look's */
    public ToolWindows snapshot() {
        var looks = new TreeMap<String, ToolWindows.Look>();
        layouts.forEach((key, layout) -> looks.put(key, new ToolWindows.Look(layout.panels, layout.dock)));
        return new ToolWindows(railVisible, zenMode, looks);
    }

    /**
     * A new window's state, opened while this one's window is open (full windows plan 1 Controller Ruling 10): a copy out
     * of Zen whose tool windows saved showing in Float or Window mode are saved hidden, keeping their mode and bounds, as
     * this window shows them already. Its listener is not copied. This state is untouched.
     *
     * @return the copy
     */
    public ToolWindowState forNewWindow() {
        var copy = detachedHidden();
        copy.zenMode = false;
        return copy;
    }

    /**
     * As {@link #forNewWindow()}, keeping Zen: a layout applied while another window is open (full windows plan 4 Ruling
     * 4), which is deliberate. Its listener is not copied. This state is untouched.
     *
     * @return the copy
     */
    public ToolWindowState detachedHidden() {
        var copy = new ToolWindowState(snapshot(), look);
        for (Layout layout : copy.layouts.values())
            layout.panels.replaceAll((id, panel) -> panel.visible() && panel.mode().detached()
                ? new UiState.Panel(panel.slot(), panel.order(), false, panel.mode(), panel.window()) : panel);
        return copy;
    }

    /** The look's dock, made from its built-in dock the first time it is written. */
    private Layout layout() { return layouts.computeIfAbsent(look, ignored -> new Layout()); }
}
