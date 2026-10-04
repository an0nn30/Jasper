package dev.jasper.remote.ui;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConnectionDotsTest {
    @Test void rendersOneDotThenThreeBouncingDotsAndReturnsSeamlessly() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var time = new AtomicLong();
            var dots = new ConnectionDots(time::get);
            var host = new JPanel(); host.add(dots); host.addNotify();
            try {
                dots.setSize(88, 36); dots.setWorking(true);
                var single = render(dots);
                assertThat(columnGroups(single)).isEqualTo(1);
                time.set(800_000_000L);
                var separated = render(dots);
                assertThat(columnGroups(separated)).isEqualTo(3);
                time.set(880_000_000L);
                assertThat(pixels(render(dots))).as("dots bounce between separated frames").isNotEqualTo(pixels(separated));
                time.set(1_600_000_000L);
                assertThat(pixels(render(dots))).as("one full cycle joins back into the original dot").isEqualTo(pixels(single));
            } finally { host.removeNotify(); }
        });
    }

    @Test void timerRunsOnlyWhileWorkingAndShowingIncludingAncestorHidesAndRemoval() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var dots = new ConnectionDots();
            var host = new JPanel(); host.add(dots);
            dots.setWorking(true);
            assertThat(dots.timer.isRunning()).isFalse();
            host.addNotify();
            try {
                assertThat(dots.timer.isRunning()).isTrue();
                host.setVisible(false);
                assertThat(dots.timer.isRunning()).isFalse();
                host.setVisible(true);
                assertThat(dots.timer.isRunning()).isTrue();
                dots.setWorking(false);
                assertThat(dots.timer.isRunning()).isFalse();
                dots.setWorking(true);
                host.remove(dots);
                assertThat(dots.timer.isRunning()).isFalse();
                host.add(dots);
                assertThat(dots.timer.isRunning()).isTrue();
            } finally { host.removeNotify(); }
            assertThat(dots.timer.isRunning()).isFalse();
        });
    }

    @Test void liveThemeAndFontChangesReachTheIndicator() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Object oldAccent = UIManager.get("ProgressBar.foreground"), oldFont = UIManager.get("Label.font");
            var dots = new ConnectionDots(); dots.setSize(88, 36);
            try {
                UIManager.put("ProgressBar.foreground", Color.BLUE);
                assertThat(render(dots).getRGB(44, 18)).isEqualTo(Color.BLUE.getRGB());
                UIManager.put("ProgressBar.foreground", Color.GREEN);
                assertThat(render(dots).getRGB(44, 18)).isEqualTo(Color.GREEN.getRGB());
                UIManager.put("Label.font", new java.awt.Font("Dialog", java.awt.Font.PLAIN, 13));
                var normal = dots.getPreferredSize();
                UIManager.put("Label.font", new java.awt.Font("Dialog", java.awt.Font.PLAIN, 26));
                var large = dots.getPreferredSize();
                assertThat(large.width).isEqualTo(normal.width * 2);
                assertThat(large.height).isEqualTo(normal.height * 2);
            } finally { UIManager.put("ProgressBar.foreground", oldAccent); UIManager.put("Label.font", oldFont); }
        });
    }

    private static BufferedImage render(ConnectionDots dots) {
        var image = new BufferedImage(dots.getWidth(), dots.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { dots.paint(g); } finally { g.dispose(); }
        return image;
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static int columnGroups(BufferedImage image) {
        int groups = 0;
        boolean previous = false;
        for (int x = 0; x < image.getWidth(); x++) {
            boolean filled = false;
            for (int y = 0; y < image.getHeight(); y++) filled |= (image.getRGB(x, y) >>> 24) > 128;
            if (filled && !previous) groups++;
            previous = filled;
        }
        return groups;
    }
}
