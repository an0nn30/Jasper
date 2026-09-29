package dev.jasper.app.pluginmanager;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.plugins.PluginRuntime;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.swing.GrayFilter;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/** Draws a plugin's icon from the bytes its row carries, or the default plugin logo. EDT only. */
final class PluginIcons {
    private static final String DEFAULT = "dev/jasper/app/icons/intellij/nodes/pluginLogo.svg";
    private static final System.Logger LOG = System.getLogger(PluginIcons.class.getName());

    private PluginIcons() { }

    /** The plugin's icon at {@code size} logical pixels, greyed on request; the default when it has none or it cannot be drawn. */
    static Icon of(PluginRuntime.Row row, int size, boolean greyed) {
        Icon icon = row.icon().flatMap(found -> decode(found, size, row.id())).orElseGet(() -> fallback(size));
        return greyed ? grey(icon) : icon;
    }

    /** IntelliJ's plugin logo; FlatLaf picks its dark variant under a dark theme. */
    static Icon fallback(int size) { return new FlatSVGIcon(DEFAULT, size, size, PluginIcons.class.getClassLoader()); }

    /** The icon drawn at {@code size}, or empty when its bytes are not a readable SVG or PNG. */
    static Optional<Icon> decode(PluginRuntime.PluginIcon icon, int size, String id) {
        try {
            if (icon.svg()) {
                var svg = new FlatSVGIcon(new ByteArrayInputStream(icon.data()));
                if (!svg.hasFound() || svg.getIconWidth() <= 0 || svg.getIconHeight() <= 0) throw new IOException("not an SVG image");
                return Optional.of(svg.derive(size, size));
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(icon.data()));
            if (image == null) throw new IOException("not a PNG image");
            int pixels = UIScale.scale(size);
            return Optional.of(new ImageIcon(image.getScaledInstance(pixels, pixels, Image.SCALE_SMOOTH)));
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.DEBUG, "Could not draw the icon of " + id + "; the default is used", failure);
            return Optional.empty();
        }
    }

    /** The same icon drawn greyed, for a plugin that is disabled or not running. */
    static Icon grey(Icon icon) {
        if (icon instanceof FlatSVGIcon svg) return svg.getDisabledIcon();
        if (icon instanceof ImageIcon image) return new ImageIcon(GrayFilter.createDisabledImage(image.getImage()));
        return icon;
    }
}
