package dev.jasper.app.dock;

/**
 * The six places a tool window docks: IntelliJ's anchor plus split. On a side, the first slot takes the upper half and
 * the split slot the lower half; in the bottom area, the first slot takes the left half and the split slot the right.
 * A slot alone on its side, or alone in the bottom area, takes all of it.
 */
public enum Slot {
    LEFT_TOP(Side.LEFT, false, Side.LEFT, "Left Top"),
    LEFT_BOTTOM(Side.LEFT, true, Side.LEFT, "Left Bottom"),
    BOTTOM_LEFT(Side.BOTTOM, false, Side.LEFT, "Bottom Left"),
    RIGHT_TOP(Side.RIGHT, false, Side.RIGHT, "Right Top"),
    RIGHT_BOTTOM(Side.RIGHT, true, Side.RIGHT, "Right Bottom"),
    BOTTOM_RIGHT(Side.BOTTOM, true, Side.RIGHT, "Bottom Right");

    /** A slot's group on its stripe, top to bottom: the top group, the group under the separator, the foot. */
    public enum Group { TOP, SECOND, FOOT }

    private final Side side;
    private final boolean split;
    private final Side stripe;
    private final String displayName;

    Slot(Side side, boolean split, Side stripe, String displayName) {
        this.side = side; this.split = split; this.stripe = stripe; this.displayName = displayName;
    }

    /** Where the slot docks. */
    public Side side() { return side; }

    /** Whether this is its side's secondary half (the lower half of a side, the right half of the bottom area). */
    public boolean split() { return split; }

    /** The stripe that shows the slot's icons: {@link Side#LEFT} or {@link Side#RIGHT}, never {@link Side#BOTTOM}. */
    public Side stripe() { return stripe; }

    /** The slot's group on its stripe. */
    public Group group() { return side == Side.BOTTOM ? Group.FOOT : split ? Group.SECOND : Group.TOP; }

    /** The other slot on the same side. */
    public Slot partner() { return of(side, !split); }

    /** The name menus show, such as "Left Top". */
    public String displayName() { return displayName; }

    /** The slot for a side and split flag. */
    public static Slot of(Side side, boolean split) {
        return switch (side) {
            case LEFT -> split ? LEFT_BOTTOM : LEFT_TOP;
            case RIGHT -> split ? RIGHT_BOTTOM : RIGHT_TOP;
            case BOTTOM -> split ? BOTTOM_RIGHT : BOTTOM_LEFT;
        };
    }
}
