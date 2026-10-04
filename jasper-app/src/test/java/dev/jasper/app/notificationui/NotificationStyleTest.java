package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationStyleTest {
    private static Icon square(int size, Color color) {
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) { g.setColor(color); g.fillRect(x, y, size, size); }
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    private static BufferedImage painted(Icon icon, int size) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { icon.paintIcon(null, g, 0, 0); } finally { g.dispose(); }
        return image;
    }

    @Test void aPostersIconIsFittedToTheSixteenPixelBox() {
        int box = UIScale.scale(16);
        Icon big = NotificationStyle.fitted(square(64, Color.RED));
        assertThat(big.getIconWidth()).isEqualTo(box);
        assertThat(big.getIconHeight()).isEqualTo(box);
        BufferedImage bigImage = painted(big, box * 2);
        assertThat(new Color(bigImage.getRGB(box / 2, box / 2), true)).isEqualTo(Color.RED);
        assertThat(bigImage.getRGB(box + box / 2, box + box / 2)).as("a large icon is scaled into the box").isZero();
        Icon small = NotificationStyle.fitted(square(box / 2, Color.BLUE));
        assertThat(small.getIconWidth()).isEqualTo(box);
        BufferedImage smallImage = painted(small, box);
        assertThat(new Color(smallImage.getRGB(box / 2, box / 2), true)).as("a small icon is centred").isEqualTo(Color.BLUE);
        assertThat(smallImage.getRGB(0, 0)).isZero();
        Icon exact = square(box, Color.GREEN);
        assertThat(NotificationStyle.fitted(exact)).isSameAs(exact);
    }
}
