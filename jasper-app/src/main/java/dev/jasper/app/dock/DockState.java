package dev.jasper.app.dock;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One window's tool window layout: each slot's windows in stripe order with at most one of them active, a size per
 * side in unscaled pixels and a split ratio per side. A tool window is docked in its slot or detached into a window of
 * its own (a {@link ToolWindowMode}), keeping its slot for its stripe button; a detached one is never its slot's active
 * window, and shows or hides on its own. Mutable and confined to its owner's thread (the EDT). Every mutation that
 * changes something tells each listener once, with what changed; one that changes nothing is silent.
 */
public final class DockState {
    /** Default and limits of a side's size, in unscaled pixels. */
    public static final int DEFAULT_SIDE = 260, DEFAULT_BOTTOM = 200, MIN_SIZE = 120, MAX_SIZE = 4000;
    /** Default and limits of a side's split ratio: the share of its first slot. */
    public static final double DEFAULT_RATIO = 0.5, MIN_RATIO = 0.1, MAX_RATIO = 0.9;

    /**
     * What one mutation changed.
     *
     * @param layout the showing windows, a size or a ratio changed, so the area lays out again
     * @param stripes a window was added, removed or moved, or a slot's active window changed, so the stripes render again
     * @param hidden windows that stopped showing, in order
     * @param shown windows that started showing, in order
     * @param placed windows whose slot or mode changed, in order; a window that moved between slots, or was detached or
     *     docked, or went from Float to Window or back, whether it showed or not
     */
    public record Change(boolean layout, boolean stripes, List<String> hidden, List<String> shown, List<String> placed) {
        public Change {
            hidden = List.copyOf(hidden);
            shown = List.copyOf(shown);
            placed = List.copyOf(placed);
        }

        /** A change that placed nothing. */
        public Change(boolean layout, boolean stripes, List<String> hidden, List<String> shown) {
            this(layout, stripes, hidden, shown, List.of());
        }
    }

    private static final Change ORDER = new Change(false, true, List.of(), List.of());
    private static final Change LAYOUT = new Change(true, false, List.of(), List.of());

    private final EnumMap<Slot, List<String>> order = new EnumMap<>(Slot.class);
    /** The docked window each slot shows; a detached window is never in it. */
    private final EnumMap<Slot, String> active = new EnumMap<>(Slot.class);
    /** The mode of each detached window; a docked window has no entry. */
    private final Map<String, ToolWindowMode> modes = new HashMap<>();
    /** Where each window's own window was last, kept after it docks again; a window never detached may have none. */
    private final Map<String, Rectangle> windowBounds = new HashMap<>();
    /** The detached windows whose window shows. */
    private final Set<String> shownDetached = new HashSet<>();
    private final EnumMap<Side, Integer> sizes = new EnumMap<>(Side.class);
    private final EnumMap<Side, Double> ratios = new EnumMap<>(Side.class);
    private final List<Consumer<Change>> listeners = new ArrayList<>();

    public DockState() {
        for (Slot slot : Slot.values()) order.put(slot, new ArrayList<>());
        sizes.put(Side.LEFT, DEFAULT_SIDE);
        sizes.put(Side.RIGHT, DEFAULT_SIDE);
        sizes.put(Side.BOTTOM, DEFAULT_BOTTOM);
        for (Side side : Side.values()) ratios.put(side, DEFAULT_RATIO);
    }

    /** An independent copy without listeners, for laying out a change before making it. */
    public DockState copy() {
        var copy = new DockState();
        for (Slot slot : Slot.values()) copy.order.get(slot).addAll(order.get(slot));
        copy.active.putAll(active);
        copy.modes.putAll(modes);
        copy.windowBounds.putAll(windowBounds);
        copy.shownDetached.addAll(shownDetached);
        copy.sizes.putAll(sizes);
        copy.ratios.putAll(ratios);
        return copy;
    }

    public void addListener(Consumer<Change> listener) { listeners.add(Objects.requireNonNull(listener, "listener")); }

    public void removeListener(Consumer<Change> listener) { listeners.remove(listener); }

    /** The slot holding the window, if it is docked. */
    public Optional<Slot> slot(String id) {
        for (Slot slot : Slot.values()) if (order.get(slot).contains(id)) return Optional.of(slot);
        return Optional.empty();
    }

    /** The slot's windows in stripe order. */
    public List<String> ids(Slot slot) { return List.copyOf(order.get(slot)); }

    /**
     * Where a workspace drop puts the window in the target slot's group: its own place if it is already there, else
     * the end.
     */
    public int dropIndex(String id, Slot target) {
        List<String> ids = ids(target);
        return ids.contains(id) ? ids.indexOf(id) : ids.size();
    }

    /** The docked window the slot shows, if any; a detached window is never a slot's active window. */
    public Optional<String> active(Slot slot) { return Optional.ofNullable(active.get(slot)); }

    /** Whether the window is showing: it is its slot's active window, or it is detached and its window shows. */
    public boolean visible(String id) { return active.containsValue(id) || shownDetached.contains(id); }

    /** How the window is placed; {@link ToolWindowMode#DOCKED} for a docked or unknown window. */
    public ToolWindowMode mode(String id) { return modes.getOrDefault(id, ToolWindowMode.DOCKED); }

    /**
     * Where the window's own window was last, in screen coordinates: a copy, kept after the window docks again so the
     * next detach can reopen it there; empty if it never had one or the window is unknown.
     */
    public Optional<Rectangle> bounds(String id) { return Optional.ofNullable(windowBounds.get(id)).map(Rectangle::new); }

    /** The detached windows whose window shows: the slots in {@link Slot#values()} order, each slot's in stripe order. */
    public List<String> detachedVisible() {
        List<String> ids = new ArrayList<>();
        for (Slot slot : Slot.values()) for (String id : order.get(slot)) if (shownDetached.contains(id)) ids.add(id);
        return ids;
    }

    /** Whether any docked window shows on that side; a detached window does not take a side's space. */
    public boolean showing(Side side) {
        for (Slot slot : active.keySet()) if (slot.side() == side) return true;
        return false;
    }

    /** A side's size in unscaled pixels: the width of LEFT or RIGHT, the height of BOTTOM. */
    public int size(Side side) { return sizes.get(side); }

    /** The share of the side's first slot when both of its slots show. */
    public double ratio(Side side) { return ratios.get(side); }

    /** Appends a hidden window to the slot's group. */
    public void add(String id, Slot slot) { add(id, slot, Integer.MAX_VALUE); }

    /**
     * Inserts a hidden window into the slot's group.
     *
     * @param index position among the slot's windows, clamped to the group
     * @throws IllegalArgumentException when the window is already docked
     */
    public void add(String id, Slot slot, int index) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(slot, "slot");
        if (slot(id).isPresent()) throw new IllegalArgumentException("Already docked: " + id);
        List<String> ids = order.get(slot);
        ids.add(Math.clamp(index, 0, ids.size()), id);
        fire(ORDER);
    }

    /** Removes the window and forgets its mode and bounds; an unknown id changes nothing. */
    public void remove(String id) {
        Optional<Slot> slot = slot(id);
        if (slot.isEmpty()) return;
        order.get(slot.get()).remove(id);
        boolean wasShowing = visible(id);
        active.remove(slot.get(), id);
        shownDetached.remove(id);
        modes.remove(id);
        windowBounds.remove(id);
        fire(new Change(wasShowing, true, wasShowing ? List.of(id) : List.of(), List.of()));
    }

    /**
     * Shows the window. A docked window hides whichever other window its slot was showing; a detached window shows in
     * its own window and leaves its slot alone.
     */
    public void show(String id) {
        Optional<Slot> slot = slot(id);
        if (slot.isEmpty()) return;
        if (mode(id).detached()) {
            if (shownDetached.add(id)) fire(new Change(true, true, List.of(), List.of(id)));
            return;
        }
        if (id.equals(active.get(slot.get()))) return;
        String previous = active.put(slot.get(), id);
        fire(new Change(true, true, previous == null ? List.of() : List.of(previous), List.of(id)));
    }

    /** Hides the window if it shows: a detached window's own window hides, and it keeps its mode, slot and bounds. */
    public void hide(String id) {
        Optional<Slot> slot = slot(id);
        if (slot.isEmpty() || !visible(id)) return;
        active.remove(slot.get(), id);
        shownDetached.remove(id);
        fire(new Change(true, true, List.of(id), List.of()));
    }

    public void toggle(String id) {
        if (visible(id)) hide(id); else show(id);
    }

    /**
     * Sets how the window is placed and keeps its visibility, and its bounds as they are. Detaching a showing docked
     * window leaves it showing, in its own window, and empties its slot's frame; docking a showing detached window
     * shows it in its slot and hides the window that slot was showing. The mode it already has changes nothing.
     */
    public void setMode(String id, ToolWindowMode mode) {
        Objects.requireNonNull(mode, "mode");
        Optional<Slot> slot = slot(id);
        if (slot.isEmpty() || mode(id) == mode) return;
        boolean wasShowing = visible(id);
        List<String> hidden = List.of();
        if (mode.detached()) {
            modes.put(id, mode);
            if (active.remove(slot.get(), id)) shownDetached.add(id);
        } else {
            modes.remove(id);
            if (shownDetached.remove(id)) {
                String previous = active.put(slot.get(), id);
                if (previous != null) hidden = List.of(previous);
            }
        }
        fire(new Change(wasShowing, true, hidden, List.of(), List.of(id)));
    }

    /**
     * Detaches the window into its own window at the bounds and shows it, in one change: a drop outside the dock. A
     * window that already shows in that mode at those bounds changes nothing; at other bounds it reports a change that
     * places nothing, so its window moves.
     *
     * @param mode {@link ToolWindowMode#FLOAT} or {@link ToolWindowMode#WINDOW}
     * @param bounds the window's place, in screen coordinates, copied
     * @throws IllegalArgumentException when the mode is {@link ToolWindowMode#DOCKED} or the bounds are empty
     */
    public void detach(String id, ToolWindowMode mode, Rectangle bounds) {
        if (Objects.requireNonNull(mode, "mode") == ToolWindowMode.DOCKED) throw new IllegalArgumentException("Not detached: " + mode);
        Rectangle at = checkedBounds(bounds);
        Optional<Slot> slot = slot(id);
        if (slot.isEmpty()) return;
        boolean wasShowing = visible(id), sameMode = mode(id) == mode;
        if (wasShowing && sameMode && at.equals(windowBounds.get(id))) return;
        active.remove(slot.get(), id);
        modes.put(id, mode);
        windowBounds.put(id, at);
        shownDetached.add(id);
        fire(new Change(true, true, List.of(), wasShowing ? List.of() : List.of(id), sameMode ? List.of() : List.of(id)));
    }

    /**
     * Records where the window's own window is, or was, so the next detach can reopen it there. Fires nothing: a moved
     * or resized window is saved with the next save. An unknown id is ignored.
     *
     * @param bounds the window's place, in screen coordinates, copied
     * @throws IllegalArgumentException when the bounds are empty
     */
    public void setBounds(String id, Rectangle bounds) {
        Rectangle at = checkedBounds(bounds);
        if (slot(id).isPresent()) windowBounds.put(id, at);
    }

    private static Rectangle checkedBounds(Rectangle bounds) {
        Objects.requireNonNull(bounds, "bounds");
        if (bounds.width <= 0 || bounds.height <= 0) throw new IllegalArgumentException("Empty bounds: " + bounds);
        return new Rectangle(bounds);
    }

    /**
     * Moves the window and keeps its visibility: a showing docked window shows in the new slot and hides the window
     * that slot was showing. A detached window stays detached and shows or hides as before: only its stripe button
     * moves, and no slot's active window changes. A move to another slot places the window.
     *
     * @param index position among the target slot's other windows, clamped; within one slot, its new position
     */
    public void move(String id, Slot target, int index) {
        Objects.requireNonNull(target, "target");
        Optional<Slot> from = slot(id);
        if (from.isEmpty()) return;
        List<String> source = order.get(from.get());
        List<String> ids = order.get(target);
        boolean sameSlot = from.get() == target;
        int clamped = Math.clamp(index, 0, sameSlot ? ids.size() - 1 : ids.size());
        if (sameSlot && clamped == source.indexOf(id)) return;
        source.remove(id);
        ids.add(clamped, id);
        List<String> placed = sameSlot ? List.of() : List.of(id);
        if (sameSlot || !active.remove(from.get(), id)) { fire(new Change(false, true, List.of(), List.of(), placed)); return; }
        String previous = active.put(target, id);
        fire(new Change(true, true, previous == null ? List.of() : List.of(previous), List.of(), placed));
    }

    /**
     * Moves the window and shows it in its slot, in one change: a drop on the workspace. A detached window docks. The
     * window the target slot was showing hides but stays on its stripe. A docked window that already shows in that
     * place changes nothing.
     *
     * @param index position among the target slot's other windows, clamped; within one slot, its new position
     */
    public void moveAndShow(String id, Slot target, int index) {
        Objects.requireNonNull(target, "target");
        Optional<Slot> from = slot(id);
        if (from.isEmpty()) return;
        boolean wasVisible = visible(id), detached = mode(id).detached();
        List<String> source = order.get(from.get());
        List<String> ids = order.get(target);
        boolean sameSlot = from.get() == target;
        int clamped = Math.clamp(index, 0, sameSlot ? ids.size() - 1 : ids.size());
        boolean moved = !(sameSlot && clamped == source.indexOf(id));
        if (!moved && wasVisible && !detached) return;
        if (moved) {
            source.remove(id);
            ids.add(clamped, id);
        }
        if (detached) {
            modes.remove(id);
            shownDetached.remove(id);
        } else if (wasVisible) {
            active.remove(from.get(), id);
        }
        String previous = active.put(target, id);
        fire(new Change(true, true, previous == null || previous.equals(id) ? List.of() : List.of(previous),
            wasVisible ? List.of() : List.of(id), sameSlot && !detached ? List.of() : List.of(id)));
    }

    /** Sets a side's size, clamped to {@link #MIN_SIZE}..{@link #MAX_SIZE} unscaled pixels. */
    public void resize(Side side, int pixels) {
        int value = Math.clamp(pixels, MIN_SIZE, MAX_SIZE);
        int previous = sizes.put(Objects.requireNonNull(side, "side"), value);
        if (previous != value) fire(LAYOUT);
    }

    /**
     * Sets the share of a side's first slot, clamped to {@link #MIN_RATIO}..{@link #MAX_RATIO}.
     *
     * @throws IllegalArgumentException when the ratio is not a number
     */
    public void setRatio(Side side, double ratio) {
        if (Double.isNaN(ratio)) throw new IllegalArgumentException("Ratio is not a number");
        double value = Math.clamp(ratio, MIN_RATIO, MAX_RATIO);
        double previous = ratios.put(Objects.requireNonNull(side, "side"), value);
        if (previous != value) fire(LAYOUT);
    }

    private void fire(Change change) {
        for (Consumer<Change> listener : List.copyOf(listeners)) listener.accept(change);
    }
}
