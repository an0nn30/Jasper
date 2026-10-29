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

    @Test void enterpriseKeepsTheSameApplicationIconAsNormalMode() {
        ApplicationIcon.useLook(java.util.List.of());
        var normal = ApplicationIcon.images();
        try {
            var branding = dev.jasper.app.testsupport.EnterpriseResources.facts().parsed().orElseThrow().branding();
            assertThat(branding).isSameAs(dev.jasper.app.presentation.Branding.NONE);
            assertThat(branding.iconFiles()).isEmpty();
            ApplicationIcon.useLook(branding.iconFiles());
            assertThat(ApplicationIcon.images()).isSameAs(normal);
            assertThat(ApplicationIcon.branded()).isFalse();
            assertThat(ApplicationIcon.setsTaskbarIcon(true, "/Applications/Jasper.app", ApplicationIcon.branded())).isFalse();
        } finally {
            ApplicationIcon.useLook(java.util.List.of());
        }
    }

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
            assertThat(image.getRGB(0, 0) >>> 24).isLessThan(32);
            assertThat(sizes.add(size)).isTrue();
        }
        assertThat(sizes).contains(16, 20, 24, 32, 40, 48, 64, 256);
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
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes, data.position(), length - 8));
            assertThat(image).as("PNG ICNS representation %s", Integer.toHexString(type)).isNotNull();
            assertThat(image.getColorModel().hasAlpha()).isTrue();
            data.position(data.position() + length - 8);
        }
        assertThat(types).contains(0x69637034, 0x69637035, 0x69637036,
            0x69633037, 0x69633038, 0x69633039, 0x69633130,
            0x69633131, 0x69633132, 0x69633133, 0x69633134);
    }

    @Test void suppliedNativeContainersAndPlatformPngsArePreserved() throws Exception {
        Path source = ICONS.resolve("protean-source");
        assertThat(Files.readAllBytes(ICONS.resolve("Jasper.ico")))
            .isEqualTo(Files.readAllBytes(source.resolve("Windows/Protean.ico")));
        assertThat(Files.readAllBytes(ICONS.resolve("Jasper.icns")))
            .isEqualTo(Files.readAllBytes(source.resolve("macOS/Protean.icns")));
        for (int size : new int[] {16, 20, 24, 32, 40, 48, 64, 256}) {
            assertRuntimeMatches("windows", size, source.resolve("Windows/PNG/Protean-" + size + ".png"));
        }
        for (int size : new int[] {16, 24, 32, 48, 64, 128, 256, 512}) {
            assertRuntimeMatches("linux", size, source.resolve("Linux/hicolor/" + size + "x" + size + "/apps/protean.png"));
        }
        for (int size : new int[] {16, 32, 64, 128, 256, 512, 1024}) {
            int points = size == 64 || size == 1024 ? size / 2 : size;
            String suffix = size == 64 || size == 1024 ? "-2x" : "";
            assertRuntimeMatches("macos", size, source.resolve("macOS/Protean.iconset/icon_" + points + "x" + points + suffix + ".png"));
        }
    }

    private void assertRuntimeMatches(String platform, int size, Path source) throws Exception {
        try (var runtime = getClass().getResourceAsStream("/dev/jasper/app/icons/app/" + platform + "/icon-" + size + ".png")) {
            assertThat(runtime).isNotNull();
            assertThat(runtime.readAllBytes()).isEqualTo(Files.readAllBytes(source));
        }
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
    }

    @Test void websiteIconMatchesTheShippedApplicationIcon() throws Exception {
        Path website = ICONS.getParent().getParent().resolve("website/assets/jasper.png");
        assertThat(Files.readAllBytes(website))
            .isEqualTo(Files.readAllBytes(ICONS.resolve("linux/hicolor/256x256/apps/jasper.png")));
    }

    private static BufferedImage read(String platform, int size) throws Exception {
        var resource = ApplicationIconTest.class.getResource("/dev/jasper/app/icons/app/" + platform + "/icon-" + size + ".png");
        assertThat(resource).as(platform + " " + size).isNotNull();
        return ImageIO.read(resource);
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
