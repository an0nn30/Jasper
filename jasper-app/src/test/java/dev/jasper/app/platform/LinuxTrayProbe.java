package dev.jasper.app.platform;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public final class LinuxTrayProbe {
    public static void main(String[] args) throws Exception {
        if (!System.getProperty("os.name", "").equalsIgnoreCase("Linux") || GraphicsEnvironment.isHeadless())
            throw new IllegalStateException("Opt-in Linux graphical-session probe only");
        SwingUtilities.invokeAndWait(() -> {
            TrayIcon[] current = new TrayIcon[1];
            SystemTray[] owner = new SystemTray[1];
            Runnable remove = () -> {
                if (current[0] != null) {
                    owner[0].remove(current[0]);
                    current[0] = null;
                    System.out.println("removed");
                }
            };
            Timer poll = new Timer(2000, event -> {
                try {
                    if (!SystemTray.isSupported()) { remove.run(); System.out.println("no host"); return; }
                    if (current[0] != null) return;
                    BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
                    var g = image.createGraphics();
                    try { g.setColor(Color.ORANGE); g.fillOval(4, 4, 24, 24); }
                    finally { g.dispose(); }
                    PopupMenu menu = new PopupMenu();
                    MenuItem item = new MenuItem("Record click");
                    item.addActionListener(click -> System.out.println("menu action EDT=" + SwingUtilities.isEventDispatchThread()));
                    menu.add(item);
                    TrayIcon icon = new TrayIcon(image, "Jasper legacy tray probe", menu);
                    icon.setImageAutoSize(true);
                    icon.addActionListener(click -> System.out.println("activate"));
                    owner[0] = SystemTray.getSystemTray();
                    owner[0].add(icon);
                    current[0] = icon;
                    System.out.println("added slot=" + SystemTray.getSystemTray().getTrayIconSize());
                } catch (Exception failure) { System.out.println(failure); }
            });
            poll.setInitialDelay(0);
            poll.start();
            Timer end = new Timer(180_000, event -> { poll.stop(); remove.run(); System.exit(0); });
            end.setRepeats(false);
            end.start();
        });
        // A Swing timer alone cannot keep AWT alive while no tray host exists.
        new CountDownLatch(1).await(185, TimeUnit.SECONDS);
        System.exit(1); // Bounded fallback if the EDT cannot deliver the three-minute timer.
    }
}
