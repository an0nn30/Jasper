package dev.jasper.app.dock;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One window's tool window layout: each slot's windows in stripe order with at most one of them active, a size per
 * side in unscaled pixels and a split ratio per side. Mutable and confined to its owner's thread (the EDT). Every
 * mutation that changes something tells each listener once, with what changed; one that changes nothing is silent.
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
     */
    public record Change(boolean layout, boolean stripes, List<String> hidden, List<String> shown) {
        public Change {
            hidden = List.copyOf(hidden);
            shown = List.copyOf(shown);
        }
    }

    private static final Change ORDER = new Change(false, true, List.of(), List.of());
    private static final Change LAYOUT = new Change(true, false, List.of(), List.of());

    private final EnumMap<Slot, List<String>> order = new EnumMap<>(Slot.class);
    private final EnumMap<Slot, String> active = new EnumMap<>(Slot.class);
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

    /** The window the slot shows, if any. */
    public Optional<String> active(Slot slot) { return Optional.ofNullable(active.get(slot)); }

    /** Whether the window is showing. */
    public boolean visible(String id) { return active.containsValue(id); }

    /** Whether any window shows on that side. */
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

    /** Removes the window; an unknown id changes nothing. */
    public void remove(String id) {
        Optional<Slot> slot = slot(id);
        if (slot.isEmpty()) return;
        order.get(slot.get()).remove(id);
        boolean wasActive = active.remove(slot.get(), id);
        fire(new Change(wasActive, true, wasActive ? List.of(id) : List.of(), List.of()));
    }

    /** Shows the window, hiding whichever other window its slot was showing. */
    public void show(String id) {
        Optional<Slot> slot = slot(id);
        if (slot.isEmpty() || id.equals(active.get(slot.get()))) return;
        String previous = active.put(slot.get(), id);
        fire(new Change(true, true, previous == null ? List.of() : List.of(previous), List.of(id)));
    }

    /** Hides the window if it shows. */
    public void hide(String id) {
        Optional<Slot> slot = slot(id);
        if (slot.isEmpty() || !active.remove(slot.get(), id)) return;
        fire(new Change(true, true, List.of(id), List.of()));
    }

    public void toggle(String id) {
        if (visible(id)) hide(id); else show(id);
    }

    /**
     * Moves the window and keeps its visibility: a showing window shows in the new slot and hides the window that
     * slot was showing.
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
        if (sameSlot || !active.remove(from.get(), id)) { fire(ORDER); return; }
        String previous = active.put(target, id);
        fire(new Change(true, true, previous == null ? List.of() : List.of(previous), List.of()));
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
