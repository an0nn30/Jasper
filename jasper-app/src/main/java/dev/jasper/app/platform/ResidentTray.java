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
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Jasper's icon outside its windows, the one owner of it. A resident Jasper shows it once start-up has settled: on
 * macOS the menu bar item with New Window, Settings… and Quit Jasper Completely; on Windows the notification-area icon
 * with Open Jasper and Quit Jasper (a windowless Windows process is otherwise invisible). On Windows the same icon
 * carries the system notifications' balloons (spec section 9): a notification sent while Jasper is away shows its
 * balloon from the resident icon, or from an icon added for the purpose and removed when Jasper is focused again. A
 * click on a balloon or on the icon activates the most recent notification, if it is still unread, since
 * {@code TrayIcon} cannot tell balloons apart; once that one is read, a click on the resident icon is Open and a click
 * on the icon added for balloons does nothing, even if an older notification is unread. macOS banners come from its own
 * bridge, so the menu bar item carries no balloons; Linux gets no icon. EDT only; menu actions arrive on the EDT.
 */
public final class ResidentTray {
    private static final System.Logger LOG = System.getLogger(ResidentTray.class.getName());
    static final String TOOLTIP = "Jasper is running in the background";
    static final String MENU_BAR_TOOLTIP = "Jasper";
    /** The tooltip of the Windows icon added only for balloons, while Jasper is not resident. */
    static final String BALLOON_TOOLTIP = "Jasper";

    /**
     * What the icon's menu does.
     *
     * @param open Windows: opens a window or raises the open one (also a double-click on the icon, unless a notification
     *     waits to be clicked)
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

    /**
     * An icon in the menu bar or the notification area, as functions.
     *
     * @param tooltip sets its tooltip
     * @param balloon shows a notification's balloon on it
     * @param remove takes it out of the menu bar or the notification area
     */
    record Icon(Consumer<String> tooltip, Consumer<SystemNotifier.Message> balloon, Runnable remove) {}

    private final boolean mac;
    /**
     * Adds an icon whose action (a click on it or on its balloon) runs the given task, with the resident menu when there
     * are actions; null where the platform gets no icon or the tray refuses one.
     */
    private final BiFunction<Runnable, Optional<Actions>, Icon> add;
    private Icon icon;
    private boolean resident;
    private Runnable open = () -> { };
    /** The most recent notification's id while it is still unread, which a click on a balloon or on the icon activates. */
    private String latest;
    private Consumer<String> clicked = id -> { };

    ResidentTray(boolean mac, BiFunction<Runnable, Optional<Actions>, Icon> add) {
        this.mac = mac;
        this.add = Objects.requireNonNull(add, "add");
    }

    /**
     * This desktop's menu bar or notification area: the real one on a macOS or Windows desktop, otherwise one that never
     * shows an icon. Cheap: nothing touches the tray until an icon is added.
     */
    public static ResidentTray forThisDesktop() {
        String os = System.getProperty("os.name");
        boolean mac = os != null && os.startsWith("Mac");
        if (!applies(os, GraphicsEnvironment.isHeadless())) return new ResidentTray(mac, (action, actions) -> null);
        return new ResidentTray(mac, (action, actions) -> addToSystemTray(mac, action, actions));
    }

    static boolean applies(String osName, boolean headless) {
        return !headless && osName != null && (osName.startsWith("Windows") || osName.startsWith("Mac"));
    }

    static List<Entry> entries(boolean mac) {
        return mac ? List.of(Entry.NEW_WINDOW, Entry.SETTINGS, Entry.SEPARATOR, Entry.QUIT_COMPLETELY)
            : List.of(Entry.OPEN, Entry.SEPARATOR, Entry.QUIT);
    }

    /**
     * Shows the resident icon and its menu; on Windows its click is Open, unless a notification waits to be clicked.
     * Returns the way to remove it, a no-op where the platform gets no icon or the tray refuses one. Never throws: the
     * icon is not worth a failed launch. Shaped for {@code JasperApplication.residentIndicator}.
     *
     * @param actions what the menu entries do
     * @return removes the icon
     */
    public Runnable install(Actions actions) {
        Objects.requireNonNull(actions, "actions");
        // A background notification's icon gives way to the resident one, which carries its balloons from now on.
        if (icon != null) icon.remove().run();
        icon = add.apply(this::action, Optional.of(actions));
        resident = icon != null;
        if (icon == null) return () -> { };
        open = actions.open();
        icon.tooltip().accept(mac ? MENU_BAR_TOOLTIP : TOOLTIP);
        Icon shown = icon;
        return () -> {
            if (icon != shown) return;
            shown.remove().run();
            icon = null;
            resident = false;
            open = () -> { };
            latest = null;
        };
    }

    /** Shows a notification's balloon, adding Jasper's icon for it when none is there. Windows only. */
    void balloon(SystemNotifier.Message message) {
        if (mac) return; // macOS banners come from its own bridge; the menu bar item stays as it is.
        if (icon == null) {
            icon = add.apply(this::action, Optional.empty());
            if (icon == null) return;
            icon.tooltip().accept(BALLOON_TOOLTIP);
        }
        icon.balloon().accept(message);
        latest = message.id();
    }

    /** Jasper is focused again: no notification waits to be clicked, and an icon shown only for balloons goes. */
    void focused() {
        latest = null;
        if (icon != null && !resident) {
            icon.remove().run();
            icon = null;
        }
    }

    /** The notification was read in Jasper: a click no longer activates it. */
    void withdraw(String id) {
        if (id.equals(latest)) latest = null;
    }

    void withdrawAll() { latest = null; }

    /** Where a click on a notification's balloon goes: its id. */
    void onClick(Consumer<String> handler) { clicked = Objects.requireNonNull(handler, "handler"); }

    /**
     * A click on a balloon, or on the Windows icon: Windows reports both, a double-click on the icon and Enter on a
     * keyboard-focused one as the icon's action.
     */
    private void action() {
        String id = latest;
        if (id == null) {
            open.run();
            return;
        }
        latest = null;
        clicked.accept(id);
    }

    /** The Windows notification backend over {@code tray}: every call hops to the EDT, where the tray lives. EDT. */
    static SystemNotifier.Backend backend(ResidentTray tray, Consumer<String> clicked) {
        tray.onClick(clicked);
        return new SystemNotifier.Backend(
            message -> SwingUtilities.invokeLater(() -> tray.balloon(message)),
            id -> SwingUtilities.invokeLater(() -> tray.withdraw(id)),
            () -> SwingUtilities.invokeLater(tray::withdrawAll),
            () -> SwingUtilities.invokeLater(tray::focused),
            () -> SwingUtilities.invokeLater(tray::focused));
    }

    static TrayIcon.MessageType type(SystemNotifier.Level level) {
        return switch (level) {
            case INFO -> TrayIcon.MessageType.INFO;
            case WARNING -> TrayIcon.MessageType.WARNING;
            case ERROR -> TrayIcon.MessageType.ERROR;
        };
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

    /**
     * Adds a real icon: the macOS menu bar item, or the Windows icon, with the resident menu when there are actions.
     * Never throws, since the icon is not worth a failed launch; null where the tray is unsupported or refuses it.
     */
    private static Icon addToSystemTray(boolean mac, Runnable action, Optional<Actions> actions) {
        try {
            if (!SystemTray.isSupported()) return null;
            SystemTray tray = SystemTray.getSystemTray();
            PopupMenu menu = actions.map(chosen -> menu(entries(mac), chosen)).orElse(null);
            TrayIcon icon;
            if (mac) {
                // A template image (apple.awt.enableTemplateImages, set with the desktop properties) follows the menu bar.
                icon = new TrayIcon(menuBarGlyph(), null, menu);
            } else {
                icon = new TrayIcon(image(ApplicationIcon.images("windows"), tray.getTrayIconSize()), null, menu);
                icon.setImageAutoSize(true);
                icon.addActionListener(event -> action.run());
            }
            tray.add(icon);
            return new Icon(icon::setToolTip,
                message -> icon.displayMessage(message.title(), message.body(), type(message.level())),
                () -> tray.remove(icon));
        } catch (AWTException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not add Jasper's menu bar or notification-area icon", failure);
            return null;
        }
    }

    private static PopupMenu menu(List<Entry> entries, Actions actions) {
        PopupMenu menu = new PopupMenu();
        for (Entry entry : entries) {
            if (entry == Entry.SEPARATOR) {
                menu.addSeparator();
                continue;
            }
            MenuItem item = new MenuItem(entry.label);
            Runnable run = entry.action(actions);
            item.addActionListener(event -> run.run());
            menu.add(item);
        }
        return menu;
    }
}
