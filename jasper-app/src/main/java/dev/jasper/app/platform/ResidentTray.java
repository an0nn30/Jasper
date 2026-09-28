package dev.jasper.app.platform;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.util.List;

/**
 * The Windows notification-area icon of a resident Jasper: a windowless process there is otherwise
 * invisible and has nothing to Quit from. macOS keeps its Dock icon and gets no tray icon; neither does
 * Linux. Install and remove on the EDT; menu actions arrive on the EDT.
 */
public final class ResidentTray {
    private static final System.Logger LOG = System.getLogger(ResidentTray.class.getName());
    static final String TOOLTIP = "Jasper is running in the background";

    private ResidentTray() {}

    /**
     * Shows the icon with Open and Quit; double-clicking the icon is Open. Returns the way to remove it,
     * a no-op where the platform gets no icon or the tray refuses one. Never throws: the icon is not worth
     * a failed launch.
     */
    public static Runnable install(Runnable open, Runnable quit) {
        if (!applies(System.getProperty("os.name"), GraphicsEnvironment.isHeadless()) || !SystemTray.isSupported()) {
            return () -> { };
        }
        try {
            SystemTray tray = SystemTray.getSystemTray();
            PopupMenu menu = new PopupMenu();
            MenuItem openItem = new MenuItem("Open Jasper");
            openItem.addActionListener(event -> open.run());
            MenuItem quitItem = new MenuItem("Quit Jasper");
            quitItem.addActionListener(event -> quit.run());
            menu.add(openItem);
            menu.addSeparator();
            menu.add(quitItem);
            TrayIcon icon = new TrayIcon(image(ApplicationIcon.images("windows"), tray.getTrayIconSize()), TOOLTIP, menu);
            icon.setImageAutoSize(true);
            // Windows reports a double-click on the icon, or Enter on a keyboard-focused one, as its action.
            icon.addActionListener(event -> open.run());
            tray.add(icon);
            return () -> tray.remove(icon);
        } catch (AWTException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not add the notification-area icon", failure);
            return () -> { };
        }
    }

    static boolean applies(String osName, boolean headless) {
        return !headless && osName != null && osName.startsWith("Windows");
    }

    /** The smallest image at least as large as the tray's slot, or else the largest; Windows scales it to fit. */
    static Image image(List<Image> images, Dimension slot) {
        int wanted = Math.max(slot.width, slot.height);
        return images.stream()
            .filter(image -> image.getWidth(null) >= wanted)
            .findFirst()
            .orElse(images.getLast());
    }
}
