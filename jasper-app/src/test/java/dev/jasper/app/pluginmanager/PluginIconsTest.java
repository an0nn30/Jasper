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

    static int[] pixels(Icon icon) {
        var image=new BufferedImage(icon.getIconWidth(),icon.getIconHeight(),BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try{icon.paintIcon(null,g,0,0);}finally{g.dispose();}return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
    }
    static byte[] png() throws Exception {
        var image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) image.setRGB(x, y, 0xFF3574F0);
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test void declaredSvgPngAndBrokenImagesUseTheSameSharedReceivingRoute() throws Exception {
        var svg = TestRows.plugin("a.svg", "Svg").icon(new PluginRuntime.PluginIcon("icons/a.svg", SVG.getBytes(StandardCharsets.UTF_8))).row();
        var png = TestRows.plugin("a.png", "Png").icon(new PluginRuntime.PluginIcon("icons/a.png", png())).row();
        var broken = TestRows.plugin("a.bad", "Bad").icon(new PluginRuntime.PluginIcon("icons/a.svg", "not an image".getBytes(StandardCharsets.UTF_8), dev.jasper.ui.model.Nodes.Hue.BLUE)).row();
        var brokenPng = TestRows.plugin("a.badpng", "Bad PNG").icon(new PluginRuntime.PluginIcon("icons/a.png",
            "not a PNG".getBytes(StandardCharsets.UTF_8), dev.jasper.ui.model.Nodes.Hue.GREEN)).row();
        var none = TestRows.plugin("a.none", "None").row();
        var context=dev.jasper.app.testsupport.KitTestSupport.context();
        try(var registry=new dev.jasper.ui.icons.IconRegistry();var receiver=new dev.jasper.ui.icons.ReceivingArtwork(registry)) {
            for(var row:List.of(svg,png)) {
                String name=PluginIcons.name(receiver,row);assertThat(name).isNotEqualTo("plugin");
                var icon=registry.icon(name,context.theme().semantic(),dev.jasper.ui.model.Nodes.Hue.NEUTRAL,40);
                assertThat(icon.getIconWidth()).isEqualTo(40);
            }
            assertThat(PluginIcons.name(receiver,broken)).isEqualTo("plugin");
            assertThat(PluginIcons.name(receiver,brokenPng)).isEqualTo("plugin");
            assertThat(PluginIcons.name(receiver,none)).isEqualTo("plugin");
        }
    }
}
