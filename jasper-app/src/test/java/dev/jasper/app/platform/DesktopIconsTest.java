package dev.jasper.app.platform;

import dev.jasper.app.appearance.GtkTestThemes;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class DesktopIconsTest {
    private static final String SVG = "dev/jasper/app/icons/search.svg";
    @TempDir Path root;

    @AfterEach void restore() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> { UIManager.put(DesktopIcons.SOURCE_KEY, null); new ThemeController(); });
    }

    @Test void appIconsComeFromTheThemeAtChromeAndToolbarSizes() throws Exception {
        GtkTestThemes.themes(GtkTestThemes.LIGHT);
        install();
        Icon compact = AppIcons.icon("square-plus");
        assertThat(compact.getIconWidth()).isEqualTo(16);
        assertThat(pixel(compact, 8)).isEqualTo(Color.RED.getRGB());
        Icon toolbar = AppIcons.toolbarIcon("square-plus");
        assertThat(toolbar.getIconWidth()).isEqualTo(24);
        assertThat(pixel(toolbar, 12)).isEqualTo(Color.GREEN.getRGB());
    }

    @Test void namesTheThemeLacksUseBundledArtwork() throws Exception {
        GtkTestThemes.themes(GtkTestThemes.LIGHT);
        install();
        assertThat(pixels(AppIcons.icon("search"), 16)).isEqualTo(pixels(GnomeIcons.icon("search", 16), 16));
        var lock = AppIcons.skin(getClass().getClassLoader(), SVG, "LOCK");
        assertThat(pixels(lock, 16)).isEqualTo(pixels(OldGnomeCatalog.icon("LOCK", 16), 16));
        assertThat(AppIcons.forToolbar(lock).getIconWidth()).isEqualTo(24);
        assertThat(AppIcons.toolbarIcon("search").getIconWidth()).isEqualTo(24);
    }

    @Test void symbolicSkinIconsTakeTheDarkChromeForeground() throws Exception {
        GtkTestThemes.themes(GtkTestThemes.DARK);
        install();
        var copy = AppIcons.skin(getClass().getClassLoader(), SVG, "COPY");
        assertThat(copy.getIconWidth()).isEqualTo(16);
        assertThat(pixel(copy, 8) & 0xffffff).isEqualTo(0xeeeeec);
        assertThat(AppIcons.forToolbar(copy).getIconWidth()).isEqualTo(24);
        assertThat(pixel(AppIcons.named("COPY"), 8) & 0xffffff).isEqualTo(0xeeeeec);
    }

    @Test void validationIsUnchangedInGtk() {
        GtkTestThemes.themes(GtkTestThemes.LIGHT);
        assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.skin(getClass().getClassLoader(), SVG, "invalid"));
        assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.skin(getClass().getClassLoader(), "missing.svg", "LOCK"));
        assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.icon("nope"));
        assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.toolbarIcon("nope"));
    }

    private void install() throws Exception {
        Path theme = Files.createDirectories(root.resolve("Test"));
        Files.writeString(theme.resolve("index.theme"), "[Icon Theme]\nName=Test\nDirectories=16x16/actions,24x24/actions,scalable/actions\n\n"
            + "[16x16/actions]\nSize=16\nType=Fixed\n[24x24/actions]\nSize=24\nType=Fixed\n"
            + "[scalable/actions]\nSize=16\nType=Scalable\nMinSize=8\nMaxSize=512\n");
        png(theme.resolve("16x16/actions/tab-new.png"), 16, Color.RED);
        png(theme.resolve("24x24/actions/tab-new.png"), 24, Color.GREEN);
        Files.createDirectories(theme.resolve("scalable/actions"));
        Files.writeString(theme.resolve("scalable/actions/edit-copy-symbolic.svg"),
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\"><rect width=\"16\" height=\"16\" fill=\"#000000\"/></svg>");
        UIManager.put(DesktopIcons.SOURCE_KEY, new FreedesktopIcons("Test", List.of(root), List.of()));
    }
    private static void png(Path file, int size, Color color) throws Exception {
        Files.createDirectories(file.getParent());
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { g.setColor(color); g.fillRect(0, 0, size, size); } finally { g.dispose(); }
        ImageIO.write(image, "png", file.toFile());
    }
    private static int pixel(Icon icon, int at) { return pixels(icon, icon.getIconWidth())[at * icon.getIconWidth() + at]; }
    private static int[] pixels(Icon icon, int size) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { icon.paintIcon(new JLabel(), g, 0, 0); } finally { g.dispose(); }
        return image.getRGB(0, 0, size, size, null, 0, size);
    }
}
