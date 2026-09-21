package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.config.BuddyOptions;
import java.awt.Font;

final class BuddyTestAppearance {
    static BuddyOptions options() { return options(true); }
    static BuddyOptions options(boolean dark) {
        return BuddyOptions.builder(new Font("Dialog", Font.PLAIN, 13)).dark(dark).build();
    }
    static void edt(Runnable action) {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) { action.run(); return; }
        try { javax.swing.SwingUtilities.invokeAndWait(action); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
}
