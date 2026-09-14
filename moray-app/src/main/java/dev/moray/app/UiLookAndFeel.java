package dev.moray.app;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultEditorKit;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.metal.MetalLookAndFeel;

/** The fixed set of look and feels shipped by Java runtimes. */
enum UiLookAndFeel {
    METAL("metal", "javax.swing.plaf.metal.MetalLookAndFeel"),
    NIMBUS("nimbus", "javax.swing.plaf.nimbus.NimbusLookAndFeel"),
    MOTIF("motif", "com.sun.java.swing.plaf.motif.MotifLookAndFeel"),
    SYSTEM("system", null),
    AQUA("aqua", "com.apple.laf.AquaLookAndFeel"),
    WINDOWS("windows", "com.sun.java.swing.plaf.windows.WindowsLookAndFeel"),
    WINDOWS_CLASSIC("windows-classic", "com.sun.java.swing.plaf.windows.WindowsClassicLookAndFeel"),
    GTK("gtk", "com.sun.java.swing.plaf.gtk.GTKLookAndFeel");

    private final String id;
    private final String className;

    UiLookAndFeel(String id, String className) {
        this.id = id;
        this.className = className;
    }

    String id() { return id; }

    /** Installs on the EDT; unavailable platform implementations use Metal and return a warning. */
    String install() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Look and feel installation requires the EDT.");
        }
        try {
            UIManager.setLookAndFeel(this == SYSTEM ? UIManager.getSystemLookAndFeelClassName() : className);
            installMacEditingShortcuts();
            return "";
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException | RuntimeException | LinkageError failure) {
            try {
                UIManager.setLookAndFeel(new MetalLookAndFeel());
                installMacEditingShortcuts();
            } catch (UnsupportedLookAndFeelException impossible) {
                throw new IllegalStateException("The Java runtime cannot install Metal.", impossible);
            }
            return "Look and feel '" + id + "' is unavailable on this platform; using Metal.";
        }
    }
    private static void installMacEditingShortcuts() {
        if (!System.getProperty("os.name", "").startsWith("Mac")) return;
        for (String component : new String[]{"TextField", "FormattedTextField", "PasswordField",
                "TextArea", "TextPane", "EditorPane"}) {
            Object value = UIManager.getLookAndFeelDefaults().get(component + ".focusInputMap");
            if (value instanceof InputMap input) {
                input.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_DOWN_MASK), DefaultEditorKit.copyAction);
                input.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.META_DOWN_MASK), DefaultEditorKit.pasteAction);
                input.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.META_DOWN_MASK), DefaultEditorKit.cutAction);
                input.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.META_DOWN_MASK), DefaultEditorKit.selectAllAction);
            }
        }
    }
}
