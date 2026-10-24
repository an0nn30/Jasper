package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.internal.model.BuddyDeck;
import dev.jasper.buddy.notice.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Headless grouped column and filtered drawer contact sheets at 2x density. */
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
                    for (int scene = 0; scene < 4; scene++) {
                        var deck = new BuddyDeck();
                        int groups = scene == 3 ? 5 : scene == 1 ? 3 : 1;
                        for (int group = groups - 1; group >= 0; group--)
                            for (int pane = (scene == 0 ? 0 : group == 0 ? 3 : 1); pane >= 0; pane--)
                                deck.post(new BuddyNotice(new BuddyNoticeId("preview", group + "/" + pane),
                                    BuddyNotice.Kind.TASK, pane < 2 ? "Claude CLI" : "Build release " + pane,
                                    BuddyNotice.State.RUNNING, () -> "Running · 2m 12s", () -> {},
                                    new BuddyNotice.Group(new BuddyNoticeId("tabs", "tab" + group), "Release workspace " + (group + 1))));
                        JComponent panel;
                        if (scene == 2) {
                            var drawer = new BuddyDeckPanel(options, deck, () -> {});
                            drawer.setGroupFilter(new BuddyNoticeId("tabs", "tab0")); panel = drawer;
                        } else {
                            var column = new BuddyColumnPanel(options, deck, () -> {}, () -> {});
                            column.setClock(() -> 0); column.refresh(); column.setClock(() -> 1_000_000_000L); panel = column;
                        }
                        panel.setSize(panel.getPreferredSize());
                        var child = (Graphics2D) g.create();
                        child.translate(30 + (scene % 2) * 440, 20 + (scene / 2) * 350);
                        child.setColor(dark ? Color.WHITE : Color.DARK_GRAY);
                        child.drawString(new String[]{"Single pane", "Three tab stacks", "Filtered tab drawer", "Five groups: column overflow"}[scene], 32, 12);
                        child.translate(0, 15); panel.paint(child); child.dispose();
                    }
                    g.dispose();
                    ImageIO.write(image, "png", directory.resolve(dark ? "dark.png" : "light.png").toFile());
                }
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }
}
