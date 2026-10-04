package dev.jasper.app.platform;

import com.formdev.flatlaf.extras.FlatSVGUtils;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BaseMultiResolutionImage;
import java.net.URL;
import java.util.List;
import java.util.Objects;

/**
 * A resident Jasper's icon outside its windows: on macOS the menu bar item, present whenever residency is on, with New
 * Window, Settings… and Quit Jasper Completely; on Windows the notification-area icon with Open Jasper and Quit Jasper
 * (a windowless Windows process is otherwise invisible). Linux gets none. Install and remove on the EDT; menu actions
 * arrive on the EDT.
 */
public final class ResidentTray {
    private static final System.Logger LOG = System.getLogger(ResidentTray.class.getName());
    static final String TOOLTIP = "Jasper is running in the background";
    static final String MENU_BAR_TOOLTIP = "Jasper";

    /**
     * What the icon's menu does.
     *
     * @param open Windows: opens a window or raises the open one (also a double-click on the icon)
     * @param newWindow macOS: always opens a new window
     * @param settings macOS: opens Settings
     * @param quit quits completely, through the normal quit path
     */
    public record Actions(Runnable open, Runnable newWindow, Runnable settings, Runnable quit) {
        /** Rejects nulls. */
        public Actions {
            Objects.requireNonNull(open, "open");
            Objects.requireNonNull(newWindow, "newWindow");
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(quit, "quit");
        }
    }

    /** One menu entry; a null label is a separator. */
    enum Entry {
        OPEN("Open Jasper"), NEW_WINDOW("New Window"), SETTINGS("Settings…"), SEPARATOR(null),
        QUIT("Quit Jasper"), QUIT_COMPLETELY("Quit Jasper Completely");

        final String label;

        Entry(String label) { this.label = label; }

        Runnable action(Actions actions) {
            return switch (this) {
                case OPEN -> actions.open();
                case NEW_WINDOW -> actions.newWindow();
                case SETTINGS -> actions.settings();
                case QUIT, QUIT_COMPLETELY -> actions.quit();
                case SEPARATOR -> () -> { };
            };
        }
    }

    private ResidentTray() {}

    /**
     * Shows the icon and its menu. Returns the way to remove it, a no-op where the platform gets no icon or the tray
     * refuses one. Never throws: the icon is not worth a failed launch.
     *
     * @param actions what the menu entries do
     * @return removes the icon
     */
    public static Runnable install(Actions actions) {
        Objects.requireNonNull(actions, "actions");
        String os = System.getProperty("os.name");
        if (!applies(os, GraphicsEnvironment.isHeadless()) || !SystemTray.isSupported()) return () -> { };
        boolean mac = os.startsWith("Mac");
        try {
            SystemTray tray = SystemTray.getSystemTray();
            PopupMenu menu = new PopupMenu();
            for (Entry entry : entries(mac)) {
                if (entry == Entry.SEPARATOR) {
                    menu.addSeparator();
                    continue;
                }
                MenuItem item = new MenuItem(entry.label);
                Runnable action = entry.action(actions);
                item.addActionListener(event -> action.run());
                menu.add(item);
            }
            TrayIcon icon;
            if (mac) {
                // A template image (apple.awt.enableTemplateImages, set with the desktop properties) follows the menu bar.
                icon = new TrayIcon(menuBarGlyph(), MENU_BAR_TOOLTIP, menu);
            } else {
                icon = new TrayIcon(image(ApplicationIcon.images("windows"), tray.getTrayIconSize()), TOOLTIP, menu);
                icon.setImageAutoSize(true);
                // Windows reports a double-click on the icon, or Enter on a keyboard-focused one, as its action.
                icon.addActionListener(event -> actions.open().run());
            }
            tray.add(icon);
            return () -> tray.remove(icon);
        } catch (AWTException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not add Jasper's menu bar or notification-area icon", failure);
            return () -> { };
        }
    }

    static boolean applies(String osName, boolean headless) {
        return !headless && osName != null && (osName.startsWith("Windows") || osName.startsWith("Mac"));
    }

    static List<Entry> entries(boolean mac) {
        return mac ? List.of(Entry.NEW_WINDOW, Entry.SETTINGS, Entry.SEPARATOR, Entry.QUIT_COMPLETELY)
            : List.of(Entry.OPEN, Entry.SEPARATOR, Entry.QUIT);
    }

    /** Jasper's terminal glyph at 1x and 2x (16 and 32 pixels); as a template image only its shape counts. */
    static Image menuBarGlyph() {
        URL artwork = AppIcons.artwork("TERMINAL");
        return new BaseMultiResolutionImage(FlatSVGUtils.svg2image(artwork, 16, 16), FlatSVGUtils.svg2image(artwork, 32, 32));
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
