package dev.jasper.app.testsupport;

import javax.swing.Icon;
import java.awt.image.BufferedImage;

/** Exact pixels of a rendered glyph, without surrounding layout or surface paint. */
public final class IconPixels {
    private IconPixels() {}
    public static int[] of(Icon icon) {
        var image=new BufferedImage(icon.getIconWidth(),icon.getIconHeight(),BufferedImage.TYPE_INT_ARGB);
        var graphics=image.createGraphics();
        try {icon.paintIcon(null,graphics,0,0);} finally {graphics.dispose();}
        return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
    }
}
