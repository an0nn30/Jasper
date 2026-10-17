package dev.jasper.app.workspace;

import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A window's tool window stripes (spec 10.7): the default look's two icon rails, or the classic look's two labelled
 * stripes and the bottom strip. {@link DockChrome#stripes} builds one per window.
 */
sealed interface StripeSet permits StripeSet.IconRails {
    /** The stripe on the window's left edge. */
    DockStripe left();

    /** The stripe on the window's right edge. */
    DockStripe right();

    /** The bottom strip, when the set has one. */
    Optional<DockStripe> bottom();

    /** The stripe that shows a window docked in {@code slot}. */
    DockStripe of(Slot slot);

    /** Every stripe: left, right, then the bottom strip. */
    default List<DockStripe> all() {
        var all = new ArrayList<DockStripe>(List.of(left(), right()));
        bottom().ifPresent(all::add);
        return List.copyOf(all);
    }

    /**
     * The default look's rails: a bottom slot's window shows in its side's foot group.
     *
     * @param left the left rail
     * @param right the right rail
     */
    record IconRails(ToolStripe left, ToolStripe right) implements StripeSet {
        /** Two new rails. */
        IconRails() { this(new ToolStripe(Side.LEFT), new ToolStripe(Side.RIGHT)); }

        @Override public Optional<DockStripe> bottom() { return Optional.empty(); }

        @Override public DockStripe of(Slot slot) { return slot.stripe() == Side.LEFT ? left : right; }
    }
}
