package dev.jasper.app.workspace;

import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.ui.icons.ChromeArtwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A window's tool window stripes (spec 10.7): the default look's two icon rails, or the classic look's two labelled
 * stripes and the bottom strip. {@link DockChrome#stripes} builds one per window.
 */
sealed interface StripeSet permits StripeSet.IconRails, StripeSet.LabelledStripes {
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

    /**
     * The classic look's stripes: the side slots' windows on their side's labelled stripe, the bottom slots' on the
     * bottom strip.
     *
     * @param left the left labelled stripe
     * @param right the right labelled stripe
     * @param bottomStrip the bottom strip
     */
    record LabelledStripes(LabelledStripe left, LabelledStripe right, BottomStrip bottomStrip) implements StripeSet {
        /** Three new stripes over the window's kit, their labels' icons in the look's {@code artwork}. */
        LabelledStripes(dev.jasper.ui.kit.Kit kit, ChromeArtwork artwork) {
            this(new LabelledStripe(Side.LEFT, kit, artwork), new LabelledStripe(Side.RIGHT, kit, artwork), new BottomStrip(kit, artwork));
        }

        @Override public Optional<DockStripe> bottom() { return Optional.of(bottomStrip); }

        @Override public DockStripe of(Slot slot) {
            return switch (slot.side()) {
                case LEFT -> left;
                case RIGHT -> right;
                case BOTTOM -> bottomStrip;
            };
        }
    }
}
