package dev.jasper.app.platform;

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

    @Test void packagedMacKeepsItsNativeIconWhileDevelopmentCanSetOne() {
        assertThat(ApplicationIcon.usesBundleIcon(true, "/Applications/Jasper.app/Contents/MacOS/Jasper")).isTrue();
        assertThat(ApplicationIcon.usesBundleIcon(true, null)).isFalse();
        assertThat(ApplicationIcon.usesBundleIcon(true, "")).isFalse();
        assertThat(ApplicationIcon.usesBundleIcon(false, "/opt/jasper/bin/Jasper")).isFalse();
    }

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
            if (type == 0x69633034 || type == 0x69633035) {
                // Native small-size ARGB chunks use associated (premultiplied) alpha.
                // Straight RGB here becomes a bright fringe when macOS decodes it.
                ByteBuffer payload = data.slice(data.position(), length - 8);
                assertPremultipliedArgb(payload, type == 0x69633034 ? 16 : 32);
            }
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

    @Test void macFaceFillsTheNativeShapeWithoutTransparentOrSilverMargins() throws Exception {
        BufferedImage mac = read("macos", 256);
        // The terminal face reaches the native edge; no transparent inset or silver tile.
        int surround = mac.getRGB(35, 128);
        assertThat(surround >>> 24).isEqualTo(255);
        assertThat((surround >>> 16) & 255).isLessThan(64);
        assertThat((surround >>> 8) & 255).isLessThan(64);
        assertThat(surround & 255).isLessThan(64);
    }

    @Test void loadsAllPlatformImageSetsWithoutInitializingTheDesktop() {
        assertThat(ApplicationIcon.images("macos")).extracting(image -> image.getWidth(null))
            .contains(16, 32, 64, 128, 256, 512, 1024);
        assertThat(ApplicationIcon.images("windows")).extracting(image -> image.getWidth(null))
            .contains(16, 20, 24, 32, 40, 48, 64, 128, 256);
        assertThat(ApplicationIcon.images("linux")).extracting(image -> image.getWidth(null))
            .containsExactly(16, 24, 32, 48, 64, 96, 128, 256, 512, 1024);
        assertThat(ApplicationIcon.images()).isNotEmpty();
        ApplicationIcon.installTaskbarIcon(); // No native calls in headless test mode.
    }

    @Test void everyMacSizeContainsTheFaceAndBrightPromptIncludingRetina() throws Exception {
        for (int size : new int[] {16, 32, 64, 128, 256, 512, 1024}) {
            BufferedImage image = read("macos", size);
            int opaque = 0, bright = 0;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int pixel = image.getRGB(x, y);
                    if ((pixel >>> 24) >= 230) {
                        opaque++;
                        if (((pixel >>> 16) & 255) > 153 && ((pixel >>> 8) & 255) > 153
                                && (pixel & 255) > 153) bright++;
                    }
                }
            }
            assertThat(opaque).as("%spx face", size).isGreaterThan(size * size / 3);
            assertThat(bright).as("%spx prompt", size).isGreaterThan(size * size / 100);
        }
    }

    @Test void linuxPackageAndDesktopIconsMatchTheRuntimeArtwork() throws Exception {
        for (int size : new int[] {16, 24, 32, 48, 64, 96, 128, 256, 512, 1024}) {
            Path installed = ICONS.resolve("linux/hicolor/" + size + "x" + size + "/apps/jasper.png");
            try (var runtime = getClass().getResourceAsStream("/dev/jasper/app/icons/app/linux/icon-" + size + ".png")) {
                assertThat(runtime).isNotNull();
                assertThat(Files.readAllBytes(installed)).isEqualTo(runtime.readAllBytes());
            }
        }
        assertThat(Files.readAllBytes(ICONS.resolve("Jasper.png")))
            .isEqualTo(Files.readAllBytes(ICONS.resolve("linux/hicolor/512x512/apps/jasper.png")));
        BufferedImage linux = read("linux", 256);
        assertThat(linux.getColorModel().hasAlpha()).isTrue();
        assertThat(linux.getRGB(0, 0) >>> 24).isZero();
        assertThat(firstOpaquePixel(linux, 128)).isBetween(12, 15);
    }

    private static BufferedImage read(String platform, int size) throws Exception {
        var resource = ApplicationIconTest.class.getResource("/dev/jasper/app/icons/app/" + platform + "/icon-" + size + ".png");
        assertThat(resource).as(platform + " " + size).isNotNull();
        return ImageIO.read(resource);
    }

    private static void assertPremultipliedArgb(ByteBuffer payload, int size) {
        assertThat(payload.getInt()).isEqualTo(0x41524742); // ARGB
        byte[] planes = new byte[size * size * 4];
        int position = 0;
        while (payload.hasRemaining()) {
            int tag = Byte.toUnsignedInt(payload.get());
            int count = tag < 128 ? tag + 1 : tag - 125;
            assertThat(position + count).isLessThanOrEqualTo(planes.length);
            if (tag < 128) {
                payload.get(planes, position, count);
            } else {
                java.util.Arrays.fill(planes, position, position + count, payload.get());
            }
            position += count;
        }
        assertThat(position).isEqualTo(planes.length);
        int pixels = size * size;
        for (int i = 0; i < pixels; i++) {
            int alpha = Byte.toUnsignedInt(planes[i]);
            for (int channel = 1; channel < 4; channel++) {
                assertThat(Byte.toUnsignedInt(planes[channel * pixels + i]))
                    .as("%spx ICNS pixel %s channel %s must be premultiplied", size, i, channel)
                    .isLessThanOrEqualTo(alpha);
            }
        }
    }

    private static int firstOpaquePixel(BufferedImage image, int row) {
        for (int x = 0; x < image.getWidth(); x++) {
            if ((image.getRGB(x, row) >>> 24) >= 240) return x;
        }
        return -1;
    }

    /** Spec 10.11: a branded look's icons stand in for Jasper's; one that does not decode is left out (Review Focus 2). */
    @Test void aBrandedLooksIconsStandInForJaspersAndAFileThatDoesNotDecodeIsLeftOut() throws Exception {
        byte[] truncated = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R', 0, 0, 0, 48, 0, 0, 0, 48};
        try {
            ApplicationIcon.useLook(java.util.List.of(png(16), truncated, png(32)));
            assertThat(ApplicationIcon.branded()).isTrue();
            assertThat(ApplicationIcon.images()).extracting(image -> image.getWidth(null)).containsExactly(16, 32);
            var first = ApplicationIcon.images();
            ApplicationIcon.useLook(java.util.List.of(png(16), truncated, png(32)));
            assertThat(ApplicationIcon.images()).as("the same files change nothing").isSameAs(first);
            ApplicationIcon.useLook(java.util.List.of(truncated));
            assertThat(ApplicationIcon.branded()).as("nothing decoded: Jasper's icons stay").isFalse();
            assertThat(ApplicationIcon.images()).extracting(image -> image.getWidth(null)).contains(16, 32, 256);
            ApplicationIcon.useLook(java.util.List.of(png(16)));
            ApplicationIcon.useLook(java.util.List.of());
            assertThat(ApplicationIcon.branded()).as("an empty list is Jasper's icons again").isFalse();
            ApplicationIcon.installTaskbarIcon(); // No native calls in headless test mode.
        } finally {
            ApplicationIcon.useLook(java.util.List.of());
        }
    }

    /** Spec 10.11: a look's icon reaches the Dock in a packaged macOS launch too, and Jasper's goes back over it. */
    @Test void aLooksIconIsSetOnTheTaskbarEvenInAPackagedMacLaunch() {
        String packaged = "/Applications/Jasper.app/Contents/MacOS/Jasper";
        assertThat(ApplicationIcon.setsTaskbarIcon(true, packaged, true)).isTrue();
        assertThat(ApplicationIcon.setsTaskbarIcon(true, packaged, false)).isFalse();
        assertThat(ApplicationIcon.setsTaskbarIcon(true, null, false)).isTrue();
        assertThat(ApplicationIcon.setsTaskbarIcon(false, "/opt/jasper/bin/Jasper", false)).isTrue();
    }

    private static byte[] png(int size) throws java.io.IOException {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var bytes = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}
