package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.internal.animation.BuddyFrame;
import dev.jasper.buddy.internal.model.BuddyDeck;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.notice.BuddyNotice;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Actual placement, card painter and sprite at 60 Hz, without creating any native windows. */
public final class BuddyMotionPreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of("build/reports/buddy-motion").toAbsolutePath();
        Files.createDirectories(output);
        SwingUtilities.invokeAndWait(() -> {
            for (boolean dark : new boolean[]{true, false}) {
                var options = BuddyTestAppearance.options(dark);
                BuddyDeck deck = new BuddyDeck();
                deck.post(new BuddyNotice(new BuddyNoticeId("preview", "one"), BuddyNotice.Kind.TASK, "Rebuild buddy notification bubbles", BuddyNotice.State.RUNNING, () -> "Thinking about the next command", () -> {}));
                BuddyColumnPanel panel = new BuddyColumnPanel(options, deck, () -> {}, () -> {});
                long[] clock = {0}; panel.setClock(() -> clock[0]); panel.refresh();
                panel.setSize(panel.getPreferredSize());
                var placement = new BuddyColumnPlacement();
                var sprite = BuddySprite.load();
                Path folder = output.resolve((dark ? "dark" : "light"));
                try { Files.createDirectories(folder); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                for (int frame = 0; frame < 420; frame++) {
                    clock[0] = 1_000_000_000L + frame * 1_000_000_000L / 60;
                    double t = frame / 60d;
                    double progress = t < 1 ? 0 : t < 3 ? (t - 1) / 2 : t < 4 ? 1 : t < 6 ? (6 - t) / 2 : 0;
                    Rectangle anchor = new Rectangle(258, (int) (540 - 490 * progress), 84, 96);
                    boolean below = placement.update(anchor, panel.getSize(), new Rectangle(0, 0, 600, 700), clock[0], frame == 0);
                    panel.setBelow(below, frame == 0);
                    Point location = placement.at(clock[0]);
                    var image = new BufferedImage(1200, 1400, BufferedImage.TYPE_INT_RGB);
                    var g = image.createGraphics();
                    try {
                        g.setColor(new Color(249, 211, 191)); g.fillRect(0, 0, 1200, 1400); g.scale(2, 2);
                        var cards = (Graphics2D) g.create();
                        try { cards.translate(location.x, location.y); panel.paint(cards); } finally { cards.dispose(); }
                        sprite.paint(g, BuddyFrame.TYPE_A, 2, anchor.x, anchor.y);
                    } finally { g.dispose(); }
                    try { ImageIO.write(image, "png", folder.resolve(String.format("%04d.png", frame)).toFile()); }
                    catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                }
            }
        });
        System.out.println(output);
    }
}
