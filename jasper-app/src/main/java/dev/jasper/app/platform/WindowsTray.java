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
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Jasper's one Windows notification-area icon (spec section 9). A resident Jasper with no window shows it with Open and
 * Quit, since a windowless process is otherwise invisible and has nothing to quit from. A notification sent while Jasper
 * is in the background shows its balloon from that icon, or from one added for the purpose and removed when Jasper is
 * focused again. A click on a balloon or on the icon activates the most recent notification still unread, since
 * {@code TrayIcon} cannot tell balloons apart; with none, a click on the resident icon is Open. macOS keeps its Dock
 * icon and gets no tray icon; neither does Linux. EDT only.
 */
public final class WindowsTray {
    private static final System.Logger LOG = System.getLogger(WindowsTray.class.getName());
    static final String RESIDENT_TOOLTIP = "Jasper is running in the background";
    static final String TOOLTIP = "Jasper";

    /**
     * An icon in the notification area, as functions.
     *
     * @param tooltip sets its tooltip
     * @param balloon shows a notification's balloon on it
     * @param remove takes it out of the notification area
     */
    record Icon(Consumer<String> tooltip, Consumer<SystemNotifier.Message> balloon, Runnable remove) {}

    /** What the resident icon's menu does. */
    record Menu(Runnable open, Runnable quit) {}

    /** Adds an icon whose action runs the given task, with the menu when there is one; null when the tray refuses. */
    private final BiFunction<Runnable, Optional<Menu>, Icon> add;
    private Icon icon;
    private boolean resident;
    private Runnable open = () -> { };
    /** The most recent notification still unread, which a click on a balloon or on the icon activates. */
    private String latest;
    private Consumer<String> clicked = id -> { };

    WindowsTray(BiFunction<Runnable, Optional<Menu>, Icon> add) {
        this.add = Objects.requireNonNull(add, "add");
    }

    /** This desktop's notification area: the real one on a Windows desktop, otherwise one that never shows an icon. */
    public static WindowsTray forThisDesktop() {
        if (!applies(System.getProperty("os.name"), GraphicsEnvironment.isHeadless()) || !SystemTray.isSupported())
            return new WindowsTray((action, menu) -> null);
        return new WindowsTray(WindowsTray::addToSystemTray);
    }

    static boolean applies(String osName, boolean headless) {
        return !headless && osName != null && osName.startsWith("Windows");
    }

    /**
     * Shows the resident icon with Open and Quit; its click is Open, unless a notification waits to be clicked. Returns
     * the way to remove it, a no-op where the tray refuses an icon. Shaped for {@code JasperApplication.residentIndicator}.
     */
    public Runnable resident(Runnable open, Runnable quit) {
        Objects.requireNonNull(open, "open");
        Objects.requireNonNull(quit, "quit");
        // A background notification's icon gives way to the resident one, which carries its balloons from now on.
        if (icon != null) icon.remove().run();
        icon = add.apply(this::action, Optional.of(new Menu(open, quit)));
        resident = icon != null;
        if (icon == null) return () -> { };
        this.open = open;
        icon.tooltip().accept(RESIDENT_TOOLTIP);
        Icon shown = icon;
        return () -> {
            if (icon != shown) return;
            shown.remove().run();
            icon = null;
            resident = false;
            this.open = () -> { };
            latest = null;
        };
    }

    /** Shows a notification's balloon, adding Jasper's icon for it when none is there. */
    void balloon(SystemNotifier.Message message) {
        if (icon == null) {
            icon = add.apply(this::action, Optional.empty());
            if (icon == null) return;
            icon.tooltip().accept(TOOLTIP);
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

    /** A click on a balloon, or on the icon: Windows reports both, and Enter on a focused icon, as the icon's action. */
    private void action() {
        String id = latest;
        if (id == null) {
            open.run();
            return;
        }
        latest = null;
        clicked.accept(id);
    }

    /** The backend over {@code tray}: every call hops to the EDT, where the tray lives. Call on the EDT. */
    static SystemNotifier.Backend backend(WindowsTray tray, Consumer<String> clicked) {
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

    /** The smallest image at least as large as the tray's slot, or else the largest; Windows scales it to fit. */
    static Image image(List<Image> images, Dimension slot) {
        int wanted = Math.max(slot.width, slot.height);
        return images.stream()
            .filter(image -> image.getWidth(null) >= wanted)
            .findFirst()
            .orElse(images.getLast());
    }

    /** Adds a real icon; never throws, since the icon is not worth a failed launch. */
    private static Icon addToSystemTray(Runnable action, Optional<Menu> menu) {
        try {
            SystemTray tray = SystemTray.getSystemTray();
            PopupMenu popup = null;
            if (menu.isPresent()) {
                popup = new PopupMenu();
                MenuItem openItem = new MenuItem("Open Jasper");
                openItem.addActionListener(event -> menu.get().open().run());
                MenuItem quitItem = new MenuItem("Quit Jasper");
                quitItem.addActionListener(event -> menu.get().quit().run());
                popup.add(openItem);
                popup.addSeparator();
                popup.add(quitItem);
            }
            TrayIcon icon = new TrayIcon(image(ApplicationIcon.images("windows"), tray.getTrayIconSize()), TOOLTIP, popup);
            icon.setImageAutoSize(true);
            icon.addActionListener(event -> action.run());
            tray.add(icon);
            return new Icon(icon::setToolTip,
                message -> icon.displayMessage(message.title(), message.body(), type(message.level())),
                () -> tray.remove(icon));
        } catch (AWTException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not add the notification-area icon", failure);
            return null;
        }
    }
}
