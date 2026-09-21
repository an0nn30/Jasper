package dev.jasper.app;

import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.notice.BuddyNotice;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BuddyShimmerTest {
    private BufferedImage paint(BuddyNotice.State state, long time) {
        var image = new BufferedImage(330, 56, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            BuddyCard.paint(graphics, new JPanel(), new BuddyNotice(new BuddyNoticeId("test", "one"), BuddyNotice.Kind.TASK, "Title stays still", state, () -> "Thinking about the next command", () -> {}),
                new Rectangle(0, 0, 330, 56), 0, 1, false, 0, time);
        } finally { graphics.dispose(); }
        return image;
    }

    @Test void highlightTravelsAcrossDetailOnlyAndStopsWhenTheCommandFinishes() throws Exception {
        DesktopTestSupport.edt(() -> {
            for (BuiltinTheme theme : new BuiltinTheme[]{BuiltinTheme.LIGHT, BuiltinTheme.DARK}) {
                ThemeController.install(theme);
                BufferedImage rest = paint(BuddyNotice.State.RUNNING, 2_100_000_000L);
                BufferedImage early = paint(BuddyNotice.State.RUNNING, 400_000_000L);
                BufferedImage late = paint(BuddyNotice.State.RUNNING, 1_100_000_000L);
                double firstX = differences(rest, early), lastX = differences(rest, late);
                assertThat(lastX).isGreaterThan(firstX + 40);
                assertThat(differences(paint(BuddyNotice.State.DONE, 400_000_000L),
                    paint(BuddyNotice.State.DONE, 1_100_000_000L))).isEqualTo(-1);
                assertThat(differences(rest, paint(BuddyNotice.State.RUNNING, 4_500_000_000L))).isEqualTo(-1);
            }
        });
    }

    private double differences(BufferedImage first, BufferedImage second) {
        int count = 0; long sum = 0;
        for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++) {
            if (first.getRGB(x, y) != second.getRGB(x, y)) {
                assertThat(y).as("only subtext glyphs change").isBetween(28, 45);
                count++; sum += x;
            }
        }
        return count == 0 ? -1 : (double) sum / count;
    }

    @Test void onlyAnActivelyRunningTaskShimmersAndRequestsFrames() {
        var deck = new BuddyDeck();
        var panel = new BuddyColumnPanel(deck, () -> {}, () -> {});
        panel.setClock(() -> 0);
        var task = new BuddyNotice(new BuddyNoticeId("test", "one"), BuddyNotice.Kind.TASK, "title", BuddyNotice.State.RUNNING, () -> "Thinking", () -> {});
        deck.post(task); panel.refresh(); panel.setClock(() -> 1_000_000_000L);
        assertThat(panel.needsAnimationFrames()).isTrue();
        deck.orphan("test", "one"); panel.refresh();
        assertThat(panel.needsAnimationFrames()).isFalse();
        assertThat(BuddyCard.shimmers(new BuddyNotice(new BuddyNoticeId("test", "two"), BuddyNotice.Kind.TASK, "title", BuddyNotice.State.NEEDS_INPUT, () -> "Waiting", () -> {}))).isFalse();
    }
}
