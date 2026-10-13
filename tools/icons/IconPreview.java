import com.formdev.flatlaf.extras.FlatSVGIcon;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.platform.AppIcons;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.Properties;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Headless before/after contact sheet. Args: baseline resources directory, output PNG. */
class IconPreview {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try { render(Path.of(args[0]), Path.of(args[1])); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
    }
    private static void render(Path baseline, Path output) throws Exception {
        String[] names = {"ADD", "SPLIT", "ZOOM", "SEARCH", "SETTINGS", "TERMINAL", "FOLDER", "NETWORK", "SERVER",
            "HISTORY", "BOOKMARK", "LOCK", "UNLOCK", "KEY", "COPY", "PASTE", "DELETE", "REFRESH", "UPLOAD", "DOWNLOAD", "CLOSE"};
        int width = 820, height = 140 + names.length * 27;
        var image = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics(); graphics.scale(2, 2);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        var oldPaths = new Properties();
        try (var in = Files.newInputStream(baseline.resolve("icons.properties"))) { oldPaths.load(in); }
        try (var loader = new URLClassLoader(new java.net.URL[]{baseline.toUri().toURL()}, null)) {
            var controller = new ThemeController(false, TerminalSchemeChoice.MATCH);
            for (int panel = 0; panel < 2; panel++) {
                controller.configure(panel == 0, TerminalSchemeChoice.MATCH);
                int left = panel * width / 2;
                graphics.setColor(UIManager.getColor("Jasper.semantic.surface.app")); graphics.fillRect(left, 0, width / 2, height);
                graphics.setColor(UIManager.getColor("Jasper.semantic.text.primary"));
                graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
                graphics.drawString(panel == 0 ? "Slate Steel Dark" : "Slate Steel Light", left + 28, 40);
                graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                graphics.drawString("Same 16 px icons. Layout unchanged.", left + 28, 65);
                graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                graphics.drawString("Classic", left + 222, 102); graphics.drawString("New UI", left + 305, 102);
                for (int row = 0; row < names.length; row++) {
                    String name = names[row]; int y = 120 + row * 27;
                    graphics.setColor(UIManager.getColor("Jasper.semantic.text.primary"));
                    graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
                    graphics.drawString(name.toLowerCase().replace('_', ' '), left + 28, y + 13);
                    String path = oldPaths.getProperty(name);
                    var old = new FlatSVGIcon(path, 16, 16, loader);
                    if (path.contains("/standard/")) old.setColorFilter(new FlatSVGIcon.ColorFilter(color -> {
                        Color target = UIManager.getColor("Jasper.semantic.text.primary");
                        return new Color(target.getRed(), target.getGreen(), target.getBlue(), color.getAlpha());
                    }));
                    old.paintIcon(new JLabel(), graphics, left + 237, y);
                    AppIcons.named(name).paintIcon(new JLabel(), graphics, left + 320, y);
                }
            }
        } finally { graphics.dispose(); }
        Files.createDirectories(output.getParent()); ImageIO.write(image, "png", output.toFile());
        System.out.println(output.toAbsolutePath());
    }
}
