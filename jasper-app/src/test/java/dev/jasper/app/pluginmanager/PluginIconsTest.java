package dev.jasper.app.pluginmanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EdtTestExtension.class)
class PluginIconsTest {
    static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 16 16\">"
        + "<rect width=\"16\" height=\"16\" rx=\"3\" fill=\"#3574F0\"/></svg>";

    static byte[] png() throws Exception {
        var image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) image.setRGB(x, y, 0xFF3574F0);
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test void svgAndPngIconsDrawAtTheAskedSizeAndABrokenOneDrawsTheDefault() throws Exception {
        var svg = TestRows.plugin("a.svg", "Svg").icon(new PluginRuntime.PluginIcon("icons/a.svg", SVG.getBytes(StandardCharsets.UTF_8))).row();
        var png = TestRows.plugin("a.png", "Png").icon(new PluginRuntime.PluginIcon("icons/a.png", png())).row();
        var broken = TestRows.plugin("a.bad", "Bad").icon(new PluginRuntime.PluginIcon("icons/a.svg", "not an image".getBytes(StandardCharsets.UTF_8))).row();
        var none = TestRows.plugin("a.none", "None").row();
        for (int size : new int[]{40, 64})
            for (var row : List.of(svg, png, broken, none)) {
                Icon icon = PluginIcons.of(row, size, false);
                assertThat(icon.getIconWidth()).as(row.id() + " at " + size).isEqualTo(UIScale.scale(size));
                assertThat(icon.getIconHeight()).as(row.id() + " at " + size).isEqualTo(UIScale.scale(size));
            }
        assertThat(PluginIcons.decode(svg.icon().orElseThrow(), 40, "a.svg")).isPresent();
        assertThat(PluginIcons.decode(png.icon().orElseThrow(), 40, "a.png")).isPresent();
        assertThat(PluginIcons.decode(broken.icon().orElseThrow(), 40, "a.bad")).as("a corrupt SVG").isEmpty();
        assertThat(PluginIcons.decode(new PluginRuntime.PluginIcon("icons/a.png", "not an image".getBytes(StandardCharsets.UTF_8)), 40, "a.bad"))
            .as("a corrupt PNG").isEmpty();
    }

    @Test void aGreyedIconKeepsItsSize() throws Exception {
        Icon svg = PluginIcons.fallback(40);
        Icon greySvg = PluginIcons.grey(svg);
        assertThat(greySvg).isNotSameAs(svg);
        assertThat(greySvg.getIconWidth()).isEqualTo(svg.getIconWidth());
        Icon image = PluginIcons.decode(new PluginRuntime.PluginIcon("icons/a.png", png()), 40, "a.png").orElseThrow();
        assertThat(image).isInstanceOf(ImageIcon.class);
        Icon greyImage = PluginIcons.grey(image);
        assertThat(greyImage).isNotSameAs(image);
        assertThat(greyImage.getIconWidth()).isEqualTo(image.getIconWidth());
    }
}
