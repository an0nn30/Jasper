package dev.jasper.app.dock;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where everything goes: a pure function from bounds and a {@link DockState} to the rectangle of each showing slot,
 * the terminal column and each divider. The bottom area spans the full width under the sides and the terminal
 * column. When the bounds are too small, the bottom area gives way first, then the right side, then the left, before
 * the terminal column goes under its minimum; the bottom area also gives way before the halves of a side that shows
 * both go under theirs. The sizes in the state are never changed by that squeeze.
 */
public final class DockGeometry {
    private DockGeometry() { }

    /**
     * Unscaled metrics, and the UI scale that turns them and the state's sizes into pixels.
     *
     * @param scale the UI scale factor, 1 for unscaled
     * @param divider the gap between two areas, which is also the divider's width
     * @param toolWindowMinimum the smallest a tool window gets before the terminal column gives way
     * @param terminalMinimumWidth the terminal column's width that the side panels give way to
     * @param terminalMinimumHeight the terminal column's height that the bottom area gives way to
     */
    public record Metrics(double scale, int divider, int toolWindowMinimum, int terminalMinimumWidth, int terminalMinimumHeight) {
        public static final Metrics DEFAULT = at(1);

        public Metrics {
            if (!(scale > 0)) throw new IllegalArgumentException("The scale must be positive: " + scale);
        }

        /** Jasper's metrics at a UI scale: 6px dividers, 120px tool windows, a 200 x 120 px terminal column. */
        public static Metrics at(double scale) { return new Metrics(scale, 6, 120, 200, 120); }

        public int scaled(int unscaled) { return scale == 1 ? unscaled : (int) Math.round(unscaled * scale); }

        public int unscaled(int pixels) { return scale == 1 ? pixels : (int) Math.round(pixels / scale); }
    }

    /** What dragging a divider changes. */
    public enum Handle {
        LEFT_WIDTH, RIGHT_WIDTH, BOTTOM_HEIGHT, LEFT_RATIO, RIGHT_RATIO, BOTTOM_RATIO;

        /** Whether the divider is a vertical bar, dragged left and right. */
        public boolean vertical() { return this == LEFT_WIDTH || this == RIGHT_WIDTH || this == BOTTOM_RATIO; }
    }

    /**
     * One divider.
     *
     * @param bounds where the divider is
     * @param span what it resizes: the area beside or under it, or the column or row it splits
     * @param minimum the smallest position of its leading edge (x for a vertical bar, y otherwise)
     * @param maximum the largest position of its leading edge
     */
    public record Divider(Handle handle, Rectangle bounds, Rectangle span, int minimum, int maximum) {
        public Divider {
            bounds = new Rectangle(bounds);
            span = new Rectangle(span);
            if (minimum > maximum) throw new IllegalArgumentException("Divider range is empty");
        }

        @Override public Rectangle bounds() { return new Rectangle(bounds); }

        @Override public Rectangle span() { return new Rectangle(span); }

        /** The leading edge's position now. */
        public int position() { return handle.vertical() ? bounds.x : bounds.y; }
    }

    /** One computed layout. Rectangles are copies; changing them changes nothing. */
    public record Layout(Rectangle terminal, Map<Slot, Rectangle> slots, List<Divider> dividers) {
        public Layout {
            terminal = new Rectangle(terminal);
            var copies = new EnumMap<Slot, Rectangle>(Slot.class);
            slots.forEach((slot, bounds) -> copies.put(slot, new Rectangle(bounds)));
            slots = Collections.unmodifiableMap(copies);
            dividers = List.copyOf(dividers);
        }

        @Override public Rectangle terminal() { return new Rectangle(terminal); }

        @Override public Map<Slot, Rectangle> slots() {
            var copies = new EnumMap<Slot, Rectangle>(Slot.class);
            slots.forEach((slot, bounds) -> copies.put(slot, new Rectangle(bounds)));
            return Collections.unmodifiableMap(copies);
        }

        /** Where a showing slot is. */
        public Optional<Rectangle> slot(Slot slot) { return Optional.ofNullable(slots.get(slot)).map(Rectangle::new); }

        public Optional<Divider> divider(Handle handle) {
            return dividers.stream().filter(divider -> divider.handle() == handle).findFirst();
        }
    }

    /**
     * The least height, in scaled pixels, that a side showing both halves needs: two tool window minimums and the
     * divider between them. It is 0 while neither the left nor the right side shows both halves.
     */
    public static int splitMinimum(DockState state, Metrics metrics) {
        int gap = metrics.scaled(metrics.divider()), floor = metrics.scaled(metrics.toolWindowMinimum());
        return halves(state, Slot.LEFT_TOP) || halves(state, Slot.RIGHT_TOP) ? 2 * floor + gap : 0;
    }

    /**
     * The least height, in scaled pixels, of the area above the bottom row: the larger of the terminal column's
     * minimum height and {@link #splitMinimum}. The layout keeps that much above the bottom row.
     */
    public static int upperMinimum(DockState state, Metrics metrics) {
        return Math.max(metrics.scaled(metrics.terminalMinimumHeight()), splitMinimum(state, metrics));
    }

    public static Layout compute(Rectangle bounds, DockState state, Metrics metrics) {
        int x = bounds.x, y = bounds.y, width = Math.max(0, bounds.width), height = Math.max(0, bounds.height);
        int gap = metrics.scaled(metrics.divider()), floor = metrics.scaled(metrics.toolWindowMinimum());
        boolean left = state.showing(Side.LEFT), right = state.showing(Side.RIGHT), bottom = state.showing(Side.BOTTOM);
        var slots = new EnumMap<Slot, Rectangle>(Slot.class);
        var dividers = new ArrayList<Divider>();

        int[] bottomHeight = {bottom ? Math.max(floor, metrics.scaled(state.size(Side.BOTTOM))) : 0};
        int upperFloor = upperMinimum(state, metrics);
        int upper = squeeze(height - (bottom ? gap : 0), upperFloor, floor, bottomHeight);
        int[] sides = {right ? Math.max(floor, metrics.scaled(state.size(Side.RIGHT))) : 0,
                       left ? Math.max(floor, metrics.scaled(state.size(Side.LEFT))) : 0};
        int terminalWidth = squeeze(width - (left ? gap : 0) - (right ? gap : 0),
            metrics.scaled(metrics.terminalMinimumWidth()), floor, sides);
        int rightWidth = sides[0], leftWidth = sides[1];
        int surplusWidth = Math.max(0, terminalWidth - metrics.scaled(metrics.terminalMinimumWidth()));
        var upperArea = new Rectangle(x, y, width, upper);

        if (left) {
            pair(new Rectangle(x, y, leftWidth, upper), state, Slot.LEFT_TOP, Handle.LEFT_RATIO, gap, floor, slots, dividers);
            int bar = x + leftWidth;
            dividers.add(divider(Handle.LEFT_WIDTH, new Rectangle(bar, y, gap, upper), upperArea, x + floor, bar + surplusWidth));
        }
        if (right) {
            int column = x + width - rightWidth, bar = column - gap;
            pair(new Rectangle(column, y, rightWidth, upper), state, Slot.RIGHT_TOP, Handle.RIGHT_RATIO, gap, floor, slots, dividers);
            dividers.add(divider(Handle.RIGHT_WIDTH, new Rectangle(bar, y, gap, upper), upperArea,
                bar - surplusWidth, x + width - floor - gap));
        }
        if (bottom) {
            int bar = y + upper;
            int surplusHeight = Math.max(0, upper - upperFloor);
            dividers.add(divider(Handle.BOTTOM_HEIGHT, new Rectangle(x, bar, width, gap), new Rectangle(x, y, width, height),
                bar - surplusHeight, y + height - floor - gap));
            pair(new Rectangle(x, bar + gap, width, bottomHeight[0]), state, Slot.BOTTOM_LEFT, Handle.BOTTOM_RATIO,
                gap, floor, slots, dividers);
        }
        int terminalX = x + leftWidth + (left ? gap : 0);
        return new Layout(new Rectangle(terminalX, y, terminalWidth, upper), slots, dividers);
    }

    /**
     * The stand-in window that fills a pair's first slot in {@link #landing}'s copy of the state. It is no real
     * window's id: panel ids are lowercase dotted names, and this one has spaces.
     */
    private static final String GUIDE_PARTNER = "dock guide partner";

    /**
     * Where a window would land if it moved to the target slot and showed there: the drop preview, the guide. It lays
     * out a copy of the state, so the real layout does not change, and it uses {@link #compute}, so preview and layout
     * agree, with one exception on purpose: the guide for a pair's second slot (Left Bottom, Right Bottom, Bottom
     * Right) is always that slot's half, as if its partner held a window. A window dropped there while the partner
     * shows nothing really fills the whole side or bottom area, but the guide shows the slot's place. The first slots
     * keep the true landing. A window already in the target slot keeps its place there. Empty when the window is not
     * docked.
     */
    public static Optional<Rectangle> landing(Rectangle bounds, DockState state, String id, Slot target, Metrics metrics) {
        DockState whatIf = state.copy();
        if (whatIf.slot(id).isEmpty()) return Optional.empty();
        whatIf.moveAndShow(id, target, whatIf.dropIndex(id, target));
        if (target.split() && whatIf.active(target.partner()).isEmpty()) {
            whatIf.add(GUIDE_PARTNER, target.partner());
            whatIf.show(GUIDE_PARTNER);
        }
        return compute(bounds, whatIf, metrics).slot(target);
    }

    /**
     * Applies dragging a divider: its leading edge moves to position, kept within the divider's range. A width or
     * height is stored unscaled; a ratio is the first slot's share of the split.
     */
    public static void drag(DockState state, Divider divider, int position, Metrics metrics) {
        int at = Math.clamp(position, divider.minimum(), divider.maximum());
        Rectangle span = divider.span();
        int gap = metrics.scaled(metrics.divider());
        switch (divider.handle()) {
            case LEFT_WIDTH -> state.resize(Side.LEFT, metrics.unscaled(at - span.x));
            case RIGHT_WIDTH -> state.resize(Side.RIGHT, metrics.unscaled(span.x + span.width - at - gap));
            case BOTTOM_HEIGHT -> state.resize(Side.BOTTOM, metrics.unscaled(span.y + span.height - at - gap));
            case LEFT_RATIO -> ratio(state, Side.LEFT, at - span.y, span.height - gap);
            case RIGHT_RATIO -> ratio(state, Side.RIGHT, at - span.y, span.height - gap);
            case BOTTOM_RATIO -> ratio(state, Side.BOTTOM, at - span.x, span.width - gap);
        }
    }

    private static void ratio(DockState state, Side side, int first, int total) {
        if (total > 0) state.setRatio(side, (double) first / total);
    }

    /**
     * Lays out a side's column or the bottom row: both slots split at the side's ratio around a divider, or the one
     * showing slot alone. The first slot is the upper one of a column and the left one of the row.
     */
    private static void pair(Rectangle area, DockState state, Slot first, Handle handle, int gap, int floor,
                             Map<Slot, Rectangle> slots, List<Divider> dividers) {
        Slot second = first.partner();
        boolean one = state.active(first).isPresent(), two = state.active(second).isPresent();
        if (!(one && two)) {
            slots.put(one ? first : second, new Rectangle(area));
            return;
        }
        boolean row = handle.vertical();
        int total = Math.max(0, (row ? area.width : area.height) - gap);
        int part = split(total, state.ratio(first.side()), floor);
        int start = row ? area.x : area.y, bar = start + part;
        int minimum, maximum;
        if (total >= 2 * floor) {
            minimum = Math.max(start + floor, start + (int) Math.round(total * DockState.MIN_RATIO));
            maximum = Math.min(start + total - floor, start + (int) Math.round(total * DockState.MAX_RATIO));
        } else {
            minimum = bar;
            maximum = bar;
        }
        if (row) {
            slots.put(first, new Rectangle(area.x, area.y, part, area.height));
            slots.put(second, new Rectangle(bar + gap, area.y, total - part, area.height));
            dividers.add(divider(handle, new Rectangle(bar, area.y, gap, area.height), area, minimum, maximum));
        } else {
            slots.put(first, new Rectangle(area.x, area.y, area.width, part));
            slots.put(second, new Rectangle(area.x, bar + gap, area.width, total - part));
            dividers.add(divider(handle, new Rectangle(area.x, bar, area.width, gap), area, minimum, maximum));
        }
    }

    /** Whether both slots of the first slot's side show, so the side splits. */
    private static boolean halves(DockState state, Slot first) {
        return state.active(first).isPresent() && state.active(first.partner()).isPresent();
    }

    /** A divider whose range always contains where it is now, however squeezed the layout. */
    private static Divider divider(Handle handle, Rectangle bounds, Rectangle span, int minimum, int maximum) {
        int now = handle.vertical() ? bounds.x : bounds.y;
        return new Divider(handle, bounds, span, Math.min(minimum, now), Math.max(maximum, now));
    }

    /** The first part of total at ratio, at least floor on both sides of the split when there is room for that. */
    private static int split(int total, double ratio, int floor) {
        int first = (int) Math.round(total * ratio);
        return total >= 2 * floor ? Math.clamp(first, floor, total - floor) : first;
    }

    /**
     * Fits panels and a flexible part into the available length and returns the flexible part's length. The panels
     * shrink, in order, toward floor until the flexible part has its minimum; then the flexible part gives way; then
     * the panels shrink below floor, in the same order. The array holds the panels' lengths and is updated.
     */
    private static int squeeze(int available, int flexibleMinimum, int floor, int[] panels) {
        int room = Math.max(0, available);
        int excess = sum(panels) + flexibleMinimum - room;
        for (int i = 0; i < panels.length && excess > 0; i++) {
            int cut = Math.min(excess, Math.max(0, panels[i] - floor));
            panels[i] -= cut;
            excess -= cut;
        }
        excess = sum(panels) - room;
        for (int i = 0; i < panels.length && excess > 0; i++) {
            int cut = Math.min(excess, panels[i]);
            panels[i] -= cut;
            excess -= cut;
        }
        return room - sum(panels);
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) total += value;
        return total;
    }
}
