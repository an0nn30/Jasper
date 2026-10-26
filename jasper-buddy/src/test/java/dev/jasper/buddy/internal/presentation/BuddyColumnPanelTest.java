package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.internal.model.BuddyDeck;
import dev.jasper.buddy.notice.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BuddyColumnPanelTest {
    @Test void emptySummaryNeedsNoRoomAndSingleCapsuleNeverMovesOrGrows() {
        var deck = new BuddyDeck();
        var panel = new BuddyColumnPanel(BuddyTestAppearance.options(), deck, () -> {}, () -> {});
        assertThat(panel.getPreferredSize()).isEqualTo(new Dimension());
        for (int i = 0; i < 9; i++) {
            deck.post(new BuddyNotice(new BuddyNoticeId("test", "n" + i), BuddyNotice.Kind.TASK,
                "Task " + i, BuddyNotice.State.RUNNING, () -> "elapsed", () -> {}));
            panel.refresh();
            assertThat(panel.getPreferredSize()).isEqualTo(new Dimension(394, 120));
            var bounds = panel.cardBounds(0);
            panel.setBelow(true); panel.setClock(() -> 999_000_000L);
            assertThat(panel.cardBounds(0)).isEqualTo(bounds);
            assertThat(panel.animating()).isFalse();
        }
        panel.setSize(panel.getPreferredSize());
        panel.handleMove(new Point(60, 60)); assertThat(panel.hovered()).isZero();
        var image = new BufferedImage(394, 120, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { panel.paint(g); } finally { g.dispose(); }
        panel.handleExit(); assertThat(panel.hovered()).isEqualTo(-1);
    }
}
