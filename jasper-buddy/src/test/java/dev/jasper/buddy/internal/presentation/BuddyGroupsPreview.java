package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.internal.model.BuddyDeck;
import dev.jasper.buddy.notice.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Production summary and grouped drawer at 2x density, without native windows. */
public final class BuddyGroupsPreview {
    private BuddyGroupsPreview() { }
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length == 0 ? "build/reports/buddy-groups" : args[0]);
        Files.createDirectories(directory);
        SwingUtilities.invokeAndWait(() -> {
            try {
                for (boolean dark : new boolean[]{false, true}) {
                    var options = BuddyTestAppearance.options(dark);
                    var image = new BufferedImage(1840, 1500, BufferedImage.TYPE_INT_RGB);
                    var g = image.createGraphics();
                    g.setColor(dark ? new Color(72, 74, 78) : new Color(232, 233, 235));
                    g.fillRect(0, 0, image.getWidth(), image.getHeight()); g.scale(2, 2);
                    for (int scene = 0; scene < 5; scene++) {
                        var deck = new BuddyDeck();
                        var drawer = new BuddyDeckPanel(options, deck, () -> {});
                        int tasks = scene == 0 ? 1 : 3;
                        for (int pane = 0; pane < tasks; pane++) {
                            deck.post(new BuddyNotice(new BuddyNoticeId("preview", "pane" + pane), BuddyNotice.Kind.TASK,
                                pane < 2 ? "Claude CLI" : "Build release", BuddyNotice.State.RUNNING,
                                () -> "Running · 2m 12s", () -> {}, new BuddyNotice.Group(new BuddyNoticeId("tabs", "release"), "Release workspace")));
                            drawer.refresh();
                        }
                        if (scene > 0) {
                            deck.post(new BuddyNotice(new BuddyNoticeId("preview", "ssh"), BuddyNotice.Kind.CONNECTION,
                                "Production tunnel", BuddyNotice.State.UP, () -> "Connected · staging:5432", () -> {}));
                            drawer.refresh();
                        }
                        if (scene == 2) deck.post(new BuddyNotice(new BuddyNoticeId("preview", "input"), BuddyNotice.Kind.TASK,
                            "Deploy production", BuddyNotice.State.NEEDS_INPUT, () -> "Confirm deployment", () -> {}));
                        JComponent panel;
                        if (scene >= 3) {
                            panel = drawer;
                            if (scene == 4) {
                                drawer.setSize(drawer.getPreferredSize()); drawer.handleClick(new Point(60, 45));
                            }
                        } else {
                            var summary = new BuddyColumnPanel(options, deck, () -> {}, () -> {});
                            summary.refresh(); panel = summary;
                        }
                        panel.setSize(panel.getPreferredSize());
                        var child = (Graphics2D) g.create();
                        child.translate(scene < 3 ? 30 : 470, scene < 3 ? 20 + scene * 160 : 20 + (scene - 3) * 420);
                        child.setColor(dark ? Color.WHITE : Color.DARK_GRAY);
                        child.drawString(new String[]{"One task", "Mixed activity", "Needs input", "Grouped activity", "Collapsed group"}[scene], 32, 12);
                        child.translate(0, 15); panel.paint(child); child.dispose();
                    }
                    g.dispose();
                    ImageIO.write(image, "png", directory.resolve(dark ? "dark.png" : "light.png").toFile());
                }
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }
}
