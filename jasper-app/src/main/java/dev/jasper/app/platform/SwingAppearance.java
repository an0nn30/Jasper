package dev.jasper.app.platform;

import javax.swing.UIManager;

/** Presentation predicate supplied by the installed app LAF; contains no configuration state. */
public final class SwingAppearance {
    private SwingAppearance() {}
    public static boolean retro() { return UIManager.getBoolean("Jasper.retro"); }
}
