package dev.jasper.app.platform;

import com.formdev.flatlaf.FlatSystemProperties;
import javax.swing.JDialog;
import javax.swing.JFrame;

/** Which title bar windows get; chosen once at startup, before any window exists. */
public enum TitleBarStyle {
    /** macOS: the native title row with the traffic lights, hosting the workspace header ({@link MacTitleBar}). */
    MAC_UNIFIED,
    /** Windows and Linux: FlatLaf's title pane holding the app icon, menus, header and window buttons. */
    CUSTOM,
    /** Windows and Linux with {@code window.native_decorations}: the system title bar, then the menu and header rows. */
    NATIVE;

    public static TitleBarStyle choose(boolean macOs, boolean nativeDecorations) {
        return macOs ? MAC_UNIFIED : nativeDecorations ? NATIVE : CUSTOM;
    }

    /** Sets the look-and-feel decoration defaults; FlatLaf decorates Windows frames unless told not to. */
    public void configure() {
        if (this == MAC_UNIFIED) return;
        boolean custom = this == CUSTOM;
        System.setProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS, Boolean.toString(custom));
        JFrame.setDefaultLookAndFeelDecorated(custom);
        JDialog.setDefaultLookAndFeelDecorated(custom);
    }
}
