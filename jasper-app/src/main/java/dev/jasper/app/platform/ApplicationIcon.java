package dev.jasper.app.platform;

import com.formdev.flatlaf.util.SystemInfo;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Taskbar;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.imageio.ImageIO;

/**
 * Native-sized PNG representations of the same artwork used by jpackage, or a branded look's own icons (spec 10.11),
 * which then stand in for Jasper's in every new window's icon list and on the taskbar and Dock.
 */
public final class ApplicationIcon {
    private static final System.Logger LOG = System.getLogger(ApplicationIcon.class.getName());

    /** A look's icons: its PNG files as given, and the images that decoded. */
    private record Look(List<byte[]> files, List<Image> images) { }

    private static volatile Look look = new Look(List.of(), List.of());
    /** Whether a look's icon was ever put on the taskbar, so that Jasper's goes back over it. */
    private static volatile boolean taskbarBranded;

    private ApplicationIcon() {}

    /** Returns a branded look's icons when it supplied them, else the icon sizes and framing of the current desktop. */
    public static List<Image> images() {
        List<Image> branded = look.images();
        if (!branded.isEmpty()) return branded;
        return images(SystemInfo.isMacOS ? "macos" : SystemInfo.isWindows ? "windows" : "linux");
    }

    /**
     * Jasper's packaged PNG for the current desktop, independent of a look override.
     * @param size a shipped icon size (64 and 128 are available on every platform)
     * @return the PNG bytes
     */
    public static byte[] defaultPng(int size) {
        String platform = SystemInfo.isMacOS ? "macos" : SystemInfo.isWindows ? "windows" : "linux";
        String name = "/dev/jasper/app/icons/app/" + platform + "/icon-" + size + ".png";
        try (var input = ApplicationIcon.class.getResourceAsStream(name)) {
            if (input == null) throw new IllegalArgumentException("Missing application icon: " + name);
            return input.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read application icon: " + name, failure);
        }
    }

    static List<Image> images(String platform) {
        return switch (platform) {
            case "macos" -> Mac.IMAGES;
            case "windows" -> Windows.IMAGES;
            case "linux" -> Linux.IMAGES;
            default -> throw new IllegalArgumentException("Unknown icon platform: " + platform);
        };
    }

    /**
     * Makes a branded look's icons the application's (spec 10.11): its PNG files, smallest first, become every new
     * window's icon list and, through {@link #installTaskbarIcon()}, the taskbar and Dock icon, in packaged macOS
     * launches too. An empty list gives Jasper's own icons back. A file that does not decode is left out and logged; when
     * none decodes, Jasper's icons stay. Giving the current files again changes nothing. Any thread, before the windows
     * it should reach.
     *
     * @param files the look's icon files, smallest first
     */
    public static void useLook(List<byte[]> files) {
        Objects.requireNonNull(files, "files");
        if (same(files, look.files())) return;
        var images = new ArrayList<Image>(files.size());
        var copies = new ArrayList<byte[]>(files.size());
        for (byte[] file : files) {
            copies.add(file.clone());
            try {
                Image image = ImageIO.read(new ByteArrayInputStream(file));
                if (image != null) images.add(image);
                else LOG.log(System.Logger.Level.WARNING, "A look icon is not an image Jasper can read; it is left out");
            } catch (IOException | RuntimeException unreadable) {
                LOG.log(System.Logger.Level.WARNING, "A look icon could not be read; it is left out", unreadable);
            }
        }
        look = new Look(List.copyOf(copies), List.copyOf(images));
    }

    /** Whether a branded look's icons are the application's. */
    public static boolean branded() { return !look.images().isEmpty(); }

    /** Also gives development launches the correct Dock icon, and every launch a branded look's; call on the EDT. */
    public static void installTaskbarIcon() {
        boolean branded = branded();
        // Keep the system-rendered catalog icon throughout a packaged Mac launch, unless a look's icon is or was shown.
        if (!setsTaskbarIcon(SystemInfo.isMacOS, System.getProperty("jpackage.app-path"), branded || taskbarBranded)) return;
        if (GraphicsEnvironment.isHeadless() || !Taskbar.isTaskbarSupported()) return;
        try {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.setIconImage(images().getLast());
                if (branded) taskbarBranded = true;
            }
        } catch (UnsupportedOperationException | SecurityException failure) {
            LOG.log(System.Logger.Level.WARNING, "Desktop does not allow setting the application icon", failure);
        }
    }

    static boolean usesBundleIcon(boolean macos, String launcherPath) {
        return macos && launcherPath != null && !launcherPath.isBlank();
    }

    /**
     * Whether Jasper sets the taskbar icon: always for a look's icons (or to put Jasper's back over one), else unless a
     * packaged Mac launch keeps its bundle icon.
     */
    static boolean setsTaskbarIcon(boolean macos, String launcherPath, boolean lookIcons) {
        return lookIcons || !usesBundleIcon(macos, launcherPath);
    }

    private static boolean same(List<byte[]> files, List<byte[]> current) {
        if (files.size() != current.size()) return false;
        for (int i = 0; i < files.size(); i++) if (!Arrays.equals(files.get(i), current.get(i))) return false;
        return true;
    }

    private static List<Image> load(String platform, int... sizes) {
        var images = new ArrayList<Image>(sizes.length);
        for (int size : sizes) {
            String name = "/dev/jasper/app/icons/app/" + platform + "/icon-" + size + ".png";
            try (var input = ApplicationIcon.class.getResourceAsStream(name)) {
                if (input == null) throw new IllegalStateException("Missing application icon: " + name);
                var image = ImageIO.read(input);
                if (image == null) throw new IllegalStateException("Invalid application icon: " + name);
                images.add(image);
            } catch (IOException failure) {
                throw new UncheckedIOException("Cannot read application icon: " + name, failure);
            }
        }
        return List.copyOf(images);
    }

    private static final class Mac {
        static final List<Image> IMAGES = load("macos", 16, 32, 64, 128, 256, 512, 1024);
    }

    private static final class Windows {
        static final List<Image> IMAGES = load("windows", 16, 20, 24, 30, 32, 36, 40, 48,
            60, 64, 72, 80, 96, 128, 256);
    }

    private static final class Linux {
        static final List<Image> IMAGES = load("linux", 16, 24, 32, 48, 64, 96, 128, 256, 512, 1024);
    }
}
