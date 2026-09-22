package dev.jasper.app.platform;

import com.formdev.flatlaf.util.SystemInfo;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Taskbar;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Native-sized PNG representations of the same artwork used by jpackage. */
public final class ApplicationIcon {
    private static final System.Logger LOG = System.getLogger(ApplicationIcon.class.getName());

    private ApplicationIcon() {}

    /** Returns the icon sizes and framing appropriate to the current desktop. */
    public static List<Image> images() {
        return images(SystemInfo.isMacOS ? "macos" : SystemInfo.isWindows ? "windows" : "linux");
    }

    static List<Image> images(String platform) {
        return switch (platform) {
            case "macos" -> Mac.IMAGES;
            case "windows" -> Windows.IMAGES;
            case "linux" -> Linux.IMAGES;
            default -> throw new IllegalArgumentException("Unknown icon platform: " + platform);
        };
    }

    /** Also gives development launches the correct Dock icon; call on the EDT. */
    public static void installTaskbarIcon() {
        if (GraphicsEnvironment.isHeadless() || !Taskbar.isTaskbarSupported()) return;
        try {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.setIconImage(images().getLast());
            }
        } catch (UnsupportedOperationException | SecurityException failure) {
            LOG.log(System.Logger.Level.WARNING, "Desktop does not allow setting the application icon", failure);
        }
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
