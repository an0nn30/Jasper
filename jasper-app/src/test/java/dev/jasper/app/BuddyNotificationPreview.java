package dev.jasper.app;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Actual components at the reference recording's 2x density; no window, shell or desktop capture. */
public final class BuddyNotificationPreview {
    private BuddyNotificationPreview() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "build/reports/buddy-notifications" : args[0]);
        Files.createDirectories(output);
        SwingUtilities.invokeAndWait(() -> {
            for (BuiltinTheme theme : new BuiltinTheme[] {BuiltinTheme.LIGHT, BuiltinTheme.DARK}) {
                ThemeController.install(theme);
                String name = theme.name().toLowerCase(java.util.Locale.ROOT);
                BuddyDeck deck = new BuddyDeck();
                BuddyColumnPanel panel = new BuddyColumnPanel(deck, () -> { }, () -> { });
                long[] now = {0};
                panel.setClock(() -> now[0]);
                deck.post(new BuddyNotice("preview", "one", BuddyNotice.Kind.TASK,
                    theme == BuiltinTheme.LIGHT ? "New chat" : "Inspect release tracker CLI",
                    BuddyNotice.State.RUNNING, () -> "Thinking", () -> { }));
                panel.refresh();
                panel.setSize(panel.getPreferredSize());
                BuddySprite sprite = BuddySprite.load();
                for (int frame = 0; frame <= 90; frame++) {
                    now[0] = frame * 1_000_000_000L / 60;
                    BufferedImage image = new BufferedImage(842, 510, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = image.createGraphics();
                    try {
                        g.setColor(new Color(253, 253, 253));
                        g.fillRect(0, 0, image.getWidth(), image.getHeight());
                        g.scale(2, 2);
                        sprite.paint(g, BuddyFrame.TYPE_A, 2, 249, 108);
                        g.translate(79 - BuddyColumnPanel.MARGIN,
                            22 - BuddyColumnPanel.MARGIN - BuddyColumnPanel.ARRIVAL_ROOM);
                        panel.paint(g);
                    } finally { g.dispose(); }
                    try {
                        Path folder = output.resolve(name);
                        Files.createDirectories(folder);
                        ImageIO.write(image, "png", folder.resolve(String.format("%04d.png", frame)).toFile());
                        if (frame == 60) ImageIO.write(image, "png", output.resolve(name + ".png").toFile());
                    } catch (IOException failure) { throw new UncheckedIOException(failure); }
                }
            }
        });
    }
}
