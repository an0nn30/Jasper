package dev.jasper.app;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.swing.SwingUtilities;

/** Headless 2x actual-column paint cost; deliberately excludes native WindowServer/drag scheduling. */
public final class BuddyPerformanceMeasurement {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ThemeController.install(BuiltinTheme.DARK);
            var deck = new BuddyDeck();
            for (int i = 0; i < 3; i++) deck.post(new BuddyNotice("bench", i, BuddyNotice.Kind.TASK,
                "Rebuild buddy notification bubbles " + i, BuddyNotice.State.RUNNING,
                () -> "Thinking about the next command", () -> {}));
            long[] now = {1_000_000_000L};
            var panel = new BuddyColumnPanel(deck, () -> {}, () -> {});
            panel.setClock(() -> now[0]); panel.refresh(); panel.setSize(panel.getPreferredSize());
            var image = new BufferedImage(panel.getWidth()*2, panel.getHeight()*2, BufferedImage.TYPE_INT_ARGB_PRE);
            long[] samples = new long[600];
            for (int frame = -200; frame < samples.length; frame++) {
                now[0] += 16_666_667;
                long start = System.nanoTime();
                var g = image.createGraphics();
                try {
                    g.setComposite(AlphaComposite.Clear); g.fillRect(0, 0, image.getWidth(), image.getHeight());
                    g.setComposite(AlphaComposite.SrcOver); g.scale(2, 2); panel.paint(g);
                } finally { g.dispose(); }
                if (frame >= 0) samples[frame] = System.nanoTime() - start;
            }
            Arrays.sort(samples);
            System.out.printf("3 running capsules at 2x: median %.3f ms, p95 %.3f ms, p99 %.3f ms%n",
                samples[300]/1e6, samples[570]/1e6, samples[594]/1e6);
        });
    }
}
