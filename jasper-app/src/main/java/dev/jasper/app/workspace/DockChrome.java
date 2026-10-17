package dev.jasper.app.workspace;

/**
 * How a window docks its tool windows under its look (spec 10.7), as {@link WorkspaceChrome#of} reads it from the
 * workspace projection: labelled stripes with the bottom strip or the icon rails, and flush frames with rules and
 * header strips or today's frames. Every other dock class receives these booleans, never the projection's enums.
 *
 * @param labelledStripes whether tool windows are reached by labelled stripes and the bottom strip
 * @param flushFrames whether frames are flush: divided by painted rules, with header strips
 */
record DockChrome(boolean labelledStripes, boolean flushFrames) {
    /** The default look's dock: icon rails and today's frames. */
    static final DockChrome STANDARD = new DockChrome(false, false);

    /** A new set of stripes for one window over its kit: today's icon rails. */
    StripeSet stripes(dev.jasper.ui.kit.Kit kit) { return new StripeSet.IconRails(); }
}
