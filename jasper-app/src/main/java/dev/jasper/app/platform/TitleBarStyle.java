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
    /** The system title bar; Normal keeps menu/header rows, Compact keeps only platform menus. */
    NATIVE;

    public static TitleBarStyle choose(boolean macOs, boolean nativeDecorations) {
        return macOs ? MAC_UNIFIED : nativeDecorations ? NATIVE : CUSTOM;
    }

    /** Compact keeps the operating system title bar on every platform. */
    public static TitleBarStyle choose(boolean macOs, boolean nativeDecorations, boolean compact) {
        return compact ? NATIVE : choose(macOs, nativeDecorations);
    }

    /** A look with a native title ({@code nativeTitle}) gives every platform the operating system title bar. */
    public static TitleBarStyle choose(boolean macOs, boolean nativeDecorations, boolean compact, boolean nativeTitle) {
        return nativeTitle ? NATIVE : choose(macOs, nativeDecorations, compact);
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
