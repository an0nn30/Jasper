package dev.jasper.app;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ApplicationIconTest {
    private static final Path ICONS = Path.of(System.getProperty("jasper.projectDir"), "packaging", "icons");

    @Test void windowsContainerProvidesDecodableAlphaImagesForCommonDpiSizes() throws Exception {
        Path file = ICONS.resolve("Jasper.ico");
        assertThat(file).exists();
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer directory = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(directory.getShort()).isZero();
        assertThat(directory.getShort()).isEqualTo((short) 1);
        int count = Short.toUnsignedInt(directory.getShort());
        var sizes = new HashSet<Integer>();
        for (int i = 0; i < count; i++) {
            int size = Byte.toUnsignedInt(directory.get());
            if (size == 0) size = 256;
            int height = Byte.toUnsignedInt(directory.get());
            assertThat(height == 0 ? 256 : height).isEqualTo(size);
            directory.getShort();
            assertThat(directory.getShort()).isEqualTo((short) 1);
            assertThat(directory.getShort()).isEqualTo((short) 32);
            int length = directory.getInt(), offset = directory.getInt();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes, offset, length));
            assertThat(image).isNotNull();
            assertThat(image.getWidth()).isEqualTo(size);
            assertThat(image.getHeight()).isEqualTo(size);
            assertThat(image.getColorModel().hasAlpha()).isTrue();
            // At 16px the half-pixel margin leaves a faint antialiased corner.
            assertThat(image.getRGB(0, 0) >>> 24).isLessThan(32);
            assertThat(sizes.add(size)).isTrue();
        }
        assertThat(sizes).contains(16, 20, 24, 30, 32, 36, 40, 48, 60, 64, 72, 80, 96, 128, 256);
    }

    @Test void macContainerIncludesStandardAndRetinaRepresentations() throws Exception {
        Path file = ICONS.resolve("Jasper.icns");
        assertThat(file).exists();
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer data = ByteBuffer.wrap(bytes);
        assertThat(data.getInt()).isEqualTo(0x69636e73); // icns
        assertThat(data.getInt()).isEqualTo(bytes.length);
        var types = new HashSet<Integer>();
        while (data.remaining() > 0) {
            int type = data.getInt(), length = data.getInt();
            assertThat(length).isGreaterThanOrEqualTo(8);
            assertThat(length - 8).isLessThanOrEqualTo(data.remaining());
            types.add(type);
            data.position(data.position() + length - 8);
        }
        assertThat(types).contains(0x69633034, 0x69633035,
            0x69633037, 0x69633038, 0x69633039, 0x69633130,
            0x69633131, 0x69633132, 0x69633133, 0x69633134);
    }

    @Test void platformPaddingIsBakedOnceAndWindowsArtworkUsesMoreOfTheCanvas() throws Exception {
        BufferedImage mac = read("macos", 256), windows = read("windows", 256);
        // The opaque tile starts near 10% on Mac and 3% on Windows; no extra nested margin.
        assertThat(firstOpaquePixel(mac, 128)).isBetween(24, 27);
        assertThat(firstOpaquePixel(windows, 128)).isBetween(7, 9);
        assertThat(mac.getRGB(0, 0) >>> 24).isZero();
        assertThat(windows.getRGB(0, 0) >>> 24).isZero();
    }

    @Test void loadsBothPlatformImageSetsWithoutInitializingTheDesktop() {
        assertThat(ApplicationIcon.images(true)).extracting(image -> image.getWidth(null))
            .contains(16, 32, 64, 128, 256, 512, 1024);
        assertThat(ApplicationIcon.images(false)).extracting(image -> image.getWidth(null))
            .contains(16, 20, 24, 32, 40, 48, 64, 128, 256);
        ApplicationIcon.installTaskbarIcon(); // No native calls in headless test mode.
    }

    private static BufferedImage read(String platform, int size) throws Exception {
        var resource = ApplicationIconTest.class.getResource("icons/app/" + platform + "/icon-" + size + ".png");
        assertThat(resource).as(platform + " " + size).isNotNull();
        return ImageIO.read(resource);
    }

    private static int firstOpaquePixel(BufferedImage image, int row) {
        for (int x = 0; x < image.getWidth(); x++) {
            if ((image.getRGB(x, row) >>> 24) >= 240) return x;
        }
        return -1;
    }
}
