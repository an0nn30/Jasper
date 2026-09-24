package dev.jasper.app.platform;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class FreedesktopIconsTest {
    @TempDir Path root;
    Path base() { return root.resolve("icons"); }

    @Test void chainFollowsInheritsDepthFirstIgnoringCyclesThenHicolor() throws Exception {
        theme("Child", "Parent,Other", "16x16/actions", "[16x16/actions]\nSize=16\nType=Fixed");
        theme("Parent", "Child", "16x16/actions", "[16x16/actions]\nSize=16\nType=Fixed");
        theme("Other", "", "16x16/actions", "[16x16/actions]\nSize=16\nType=Fixed");
        theme("hicolor", "", "16x16/apps", "[16x16/apps]\nSize=16\nType=Threshold");
        assertThat(icons("Child").chain()).containsExactly("Child", "Parent", "Other", "hicolor");
        assertThat(icons(null).chain()).containsExactly("hicolor");
        assertThat(icons("../Child").chain()).containsExactly("hicolor");
    }

    @Test void findsInThemeThenAncestorsThenHicolorThenPixmaps() throws Exception {
        theme("Child", "Parent", "16x16/actions", "[16x16/actions]\nSize=16\nType=Fixed");
        theme("Parent", "", "16x16/actions", "[16x16/actions]\nSize=16\nType=Fixed");
        theme("hicolor", "", "16x16/apps", "[16x16/apps]\nSize=16\nType=Threshold");
        png("Child/16x16/actions/edit-copy.png", 16, Color.RED);
        png("Parent/16x16/actions/edit-paste.png", 16, Color.GREEN);
        png("Parent/16x16/actions/edit-copy.png", 16, Color.BLUE);
        png("hicolor/16x16/apps/jasper.png", 16, Color.BLACK);
        Path pixmaps = Files.createDirectories(root.resolve("pixmaps"));
        write(pixmaps.resolve("folder.png"), 16, Color.WHITE);
        var icons = new FreedesktopIcons("Child", List.of(base()), List.of(pixmaps));
        assertThat(icons.find(List.of("edit-copy"), 16, 1)).hasValue(base().resolve("Child/16x16/actions/edit-copy.png"));
        assertThat(icons.find(List.of("edit-paste"), 16, 1)).hasValue(base().resolve("Parent/16x16/actions/edit-paste.png"));
        assertThat(icons.find(List.of("jasper"), 16, 1)).hasValue(base().resolve("hicolor/16x16/apps/jasper.png"));
        assertThat(icons.find(List.of("folder"), 16, 1)).hasValue(pixmaps.resolve("folder.png"));
        assertThat(icons.find(List.of("missing"), 16, 1)).isEmpty();
        // Candidates are tried per theme: the child's second name beats the parent's first.
        assertThat(icons.find(List.of("edit-paste", "edit-copy"), 16, 1)).hasValue(base().resolve("Child/16x16/actions/edit-copy.png"));
    }

    @Test void directoryTypesAndClosestSizeFollowTheSpecification() throws Exception {
        theme("T", "", "16x16/a,32x32/a,24x24/t,scalable/a,16x16@2/a",
            "[16x16/a]\nSize=16\nType=Fixed\n[32x32/a]\nSize=32\nType=Fixed\n[24x24/t]\nSize=24\nType=Threshold\nThreshold=2\n"
                + "[scalable/a]\nSize=48\nType=Scalable\nMinSize=64\nMaxSize=512\n[16x16@2/a]\nSize=16\nScale=2\nType=Fixed");
        png("T/16x16/a/fixed.png", 16, Color.RED); png("T/32x32/a/fixed.png", 32, Color.RED);
        png("T/24x24/t/thresh.png", 24, Color.RED);
        png("T/scalable/a/vector.png", 64, Color.RED);
        png("T/16x16@2/a/hidpi.png", 32, Color.RED); png("T/16x16/a/hidpi.png", 16, Color.RED);
        var icons = icons("T");
        assertThat(icons.find(List.of("fixed"), 22, 1)).hasValue(base().resolve("T/16x16/a/fixed.png"));
        assertThat(icons.find(List.of("fixed"), 30, 1)).hasValue(base().resolve("T/32x32/a/fixed.png"));
        assertThat(icons.find(List.of("thresh"), 22, 1)).hasValue(base().resolve("T/24x24/t/thresh.png"));
        assertThat(icons.find(List.of("vector"), 100, 1)).hasValue(base().resolve("T/scalable/a/vector.png"));
        assertThat(icons.find(List.of("hidpi"), 16, 2)).hasValue(base().resolve("T/16x16@2/a/hidpi.png"));
        assertThat(icons.find(List.of("hidpi"), 16, 1)).hasValue(base().resolve("T/16x16/a/hidpi.png"));
    }

    @Test void malformedDirectoriesAndMissingIndexesAreSkipped() throws Exception {
        theme("T", "NoIndex", "bad/a,16x16/a", "[bad/a]\nSize=abc\n[16x16/a]\nSize=16\nType=Fixed");
        Files.createDirectories(base().resolve("NoIndex/16x16/a"));
        png("NoIndex/16x16/a/only.png", 16, Color.RED);
        png("T/bad/a/x.png", 16, Color.RED); png("T/16x16/a/x.png", 16, Color.RED);
        var icons = icons("T");
        assertThat(icons.chain()).containsExactly("T", "NoIndex", "hicolor");
        assertThat(icons.find(List.of("x"), 16, 1)).hasValue(base().resolve("T/16x16/a/x.png"));
        assertThat(icons.find(List.of("only"), 16, 1)).isEmpty();
    }

    @Test void imagesCarryA2xVariantAndSymbolicSvgsTakeTheGivenColor() throws Exception {
        theme("T", "", "16x16/a,32x32/a,scalable/a",
            "[16x16/a]\nSize=16\nType=Fixed\n[32x32/a]\nSize=32\nType=Fixed\n[scalable/a]\nSize=16\nType=Scalable\nMinSize=8\nMaxSize=512");
        png("T/16x16/a/pic.png", 16, Color.RED); png("T/32x32/a/pic.png", 32, Color.BLUE);
        Files.writeString(base().resolve("T/scalable/a/sym-symbolic.svg"),
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\"><rect width=\"16\" height=\"16\" fill=\"#000000\"/></svg>");
        var icons = icons("T");
        Image pic = icons.image(List.of("pic"), 16, Color.BLACK).orElseThrow();
        assertThat(pic).isInstanceOf(MultiResolutionImage.class);
        var variant = (BufferedImage) ((MultiResolutionImage) pic).getResolutionVariant(32, 32);
        assertThat(variant.getWidth()).isEqualTo(32);
        assertThat(variant.getRGB(16, 16)).isEqualTo(Color.BLUE.getRGB());
        var symbolic = (BufferedImage) ((MultiResolutionImage) icons.image(List.of("sym-symbolic"), 16, new Color(0x123456)).orElseThrow())
            .getResolutionVariant(16, 16);
        assertThat(symbolic.getWidth()).isEqualTo(16);
        assertThat(symbolic.getRGB(8, 8) & 0xffffff).isEqualTo(0x123456);
    }

    @Test void unreadableFilesAreMissesEveryTime() throws Exception {
        theme("T", "", "16x16/a", "[16x16/a]\nSize=16\nType=Fixed");
        Files.writeString(base().resolve("T/16x16/a/broken.png"), "not a png");
        var icons = icons("T");
        assertThat(icons.image(List.of("broken"), 16, Color.BLACK)).isEmpty();
        assertThat(icons.image(List.of("broken"), 16, Color.BLACK)).isEmpty();
    }

    @Test void environmentSelectsStandardBaseDirectories() {
        Path home = Path.of("/home/u");
        assertThat(FreedesktopIcons.fromEnvironment(null, Map.of(), home).bases()).containsExactly(
            Path.of("/home/u/.local/share/icons"), Path.of("/home/u/.icons"),
            Path.of("/usr/local/share/icons"), Path.of("/usr/share/icons"));
        assertThat(FreedesktopIcons.fromEnvironment(null, Map.of("XDG_DATA_HOME", "/d", "XDG_DATA_DIRS", "/a::/b"), home).bases())
            .containsExactly(Path.of("/d/icons"), Path.of("/home/u/.icons"), Path.of("/a/icons"), Path.of("/b/icons"));
    }

    private FreedesktopIcons icons(String theme) { return new FreedesktopIcons(theme, List.of(base()), List.of()); }
    private void theme(String name, String inherits, String directories, String sections) throws Exception {
        Path dir = Files.createDirectories(base().resolve(name));
        for (String d : directories.split(",")) Files.createDirectories(dir.resolve(d));
        Files.writeString(dir.resolve("index.theme"), "[Icon Theme]\nName=" + name + "\nInherits=" + inherits
            + "\nDirectories=" + directories + "\n\n" + sections + "\n");
    }
    private void png(String relative, int size, Color color) throws Exception { write(base().resolve(relative), size, color); }
    private static void write(Path file, int size, Color color) throws Exception {
        Files.createDirectories(file.getParent());
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { g.setColor(color); g.fillRect(0, 0, size, size); } finally { g.dispose(); }
        ImageIO.write(image, "png", file.toFile());
    }
}
