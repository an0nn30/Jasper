package dev.jasper.app.platform;

import javax.swing.UIManager;

/** Presentation predicates supplied by the installed app LAF; contains no configuration state. */
public final class SwingAppearance {
    private SwingAppearance() {}
    /** Metal-specific paint and typography. */
    public static boolean retro() { return UIManager.getBoolean("Jasper.retro"); }
    /** Retro and GTK: standard Swing tabs, toolbar and title instead of Jasper's custom-painted chrome. */
    public static boolean nativeChrome() { return UIManager.getBoolean("Jasper.nativeChrome"); }
    /** Chrome and icons follow the desktop's GTK and icon themes. */
    public static boolean gtk() { return UIManager.getBoolean("Jasper.gtk"); }
}
