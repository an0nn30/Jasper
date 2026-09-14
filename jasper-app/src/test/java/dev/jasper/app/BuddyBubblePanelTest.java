package dev.jasper.app;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuddyBubblePanelTest {
    private static final String TITLE = "Hide Jasper";

    private static Icon glyph() {
        return new ImageIcon(new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB));
    }

    private static BufferedImage paint(BuddyBubblePanel panel) {
        Dimension size = panel.getPreferredSize();
        panel.setSize(size);
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { panel.paint(g); } finally { g.dispose(); }
        return image;
    }

    @Test void aTitleOnlyBubbleIsTheTextPlusPadding() {
        BuddyBubblePanel panel = new BuddyBubblePanel();
        panel.setContent(BuddyBubbleContent.menu(TITLE));
        FontMetrics metrics = panel.getFontMetrics(BuddyBubblePanel.titleFont());
        int expected = Math.max(120, Math.min(320, metrics.stringWidth(TITLE) + 36));
        assertThat(panel.getPreferredSize()).isEqualTo(new Dimension(expected, metrics.getHeight() + 28));
    }

    @Test void aDetailLineAddsItsHeightAndAFourPixelGap() {
        BuddyBubblePanel titleOnly = new BuddyBubblePanel();
        titleOnly.setContent(BuddyBubbleContent.menu("Blocked"));
        BuddyBubblePanel withDetail = new BuddyBubblePanel();
        withDetail.setContent(new BuddyBubbleContent("Blocked", "Waiting", null));
        FontMetrics detail = withDetail.getFontMetrics(BuddyBubblePanel.detailFont());
        assertThat(withDetail.getPreferredSize().height)
            .isEqualTo(titleOnly.getPreferredSize().height + detail.getHeight() + 4);
    }

    @Test void aGlyphAddsItsWidthAndAGap() {
        String wide = "Hide Jasper for now"; // long enough that neither size hits the min or max clamp
        BuddyBubblePanel plain = new BuddyBubblePanel();
        plain.setContent(BuddyBubbleContent.menu(wide));
        BuddyBubblePanel decorated = new BuddyBubblePanel();
        decorated.setContent(new BuddyBubbleContent(wide, null, glyph()));
        assertThat(decorated.getPreferredSize().width).isEqualTo(plain.getPreferredSize().width + 18 + 16);
    }

    @Test void theBubbleIsNeverNarrowerThanTheMinimumOrWiderThanTheMaximum() {
        BuddyBubblePanel narrow = new BuddyBubblePanel();
        narrow.setContent(BuddyBubbleContent.menu("Hi"));
        assertThat(narrow.getPreferredSize().width).isEqualTo(120);
        BuddyBubblePanel wide = new BuddyBubblePanel();
        wide.setContent(BuddyBubbleContent.menu(TITLE.repeat(20)));
        assertThat(wide.getPreferredSize().width).isEqualTo(320);
    }

    @Test void theCornersAreTransparentAndTheFillIsDarkAndTranslucent() {
        BuddyBubblePanel panel = new BuddyBubblePanel();
        panel.setContent(BuddyBubbleContent.menu(TITLE));
        BufferedImage image = paint(panel);
        assertThat(new Color(image.getRGB(0, 0), true).getAlpha()).isZero();
        assertThat(new Color(image.getRGB(image.getWidth() - 1, image.getHeight() - 1), true).getAlpha()).isZero();
        Color fill = new Color(image.getRGB(image.getWidth() - 6, image.getHeight() / 2), true);
        assertThat(fill.getAlpha()).isBetween(200, 245);
        assertThat(fill.getRed()).isLessThan(60);
        assertThat(fill.getGreen()).isLessThan(60);
        assertThat(fill.getBlue()).isLessThan(60);
    }

    @Test void highlightingLightensTheFill() {
        BuddyBubblePanel panel = new BuddyBubblePanel();
        panel.setContent(BuddyBubbleContent.menu(TITLE));
        Color normal = new Color(paint(panel).getRGB(panel.getWidth() - 6, panel.getHeight() / 2), true);
        panel.setHighlighted(true);
        Color lit = new Color(paint(panel).getRGB(panel.getWidth() - 6, panel.getHeight() / 2), true);
        assertThat(lit.getRed()).isGreaterThan(normal.getRed());
        assertThat(lit.getGreen()).isGreaterThan(normal.getGreen());
        assertThat(lit.getBlue()).isGreaterThan(normal.getBlue());
        assertThat(lit.getAlpha()).isBetween(200, 250);
    }

    @Test void theTitleIsPaintedInWhite() {
        BuddyBubblePanel panel = new BuddyBubblePanel();
        panel.setContent(BuddyBubbleContent.menu(TITLE));
        BufferedImage image = paint(panel);
        boolean bright = false;
        for (int y = 0; y < image.getHeight() && !bright; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixel = new Color(image.getRGB(x, y), true);
                if (pixel.getRed() > 200 && pixel.getGreen() > 200 && pixel.getBlue() > 200 && pixel.getAlpha() > 200) {
                    bright = true; break;
                }
            }
        }
        assertThat(bright).as("the title is drawn in white on the dark fill").isTrue();
    }

    @Test void aVeryLongTitleIsClampedAndStillPaints() {
        BuddyBubblePanel panel = new BuddyBubblePanel();
        panel.setContent(new BuddyBubbleContent(TITLE.repeat(40), "a detail line that is also far too long to fit", glyph()));
        assertThat(panel.getPreferredSize().width).isEqualTo(320);
        BufferedImage image = paint(panel);
        assertThat(image.getWidth()).isEqualTo(320);
    }

    @Test void theBubbleIsNotOpaque() {
        assertThat(new BuddyBubblePanel().isOpaque()).isFalse();
    }
}
