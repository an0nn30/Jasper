package dev.jasper.sdk.contracts;

import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Test-only captures of existing owners. No native windows or component text/secret extraction. */
public final class ThemeMigrationCapture {
    private ThemeMigrationCapture() { }

    /** Runs each real owner on the EDT using pinned FlatLaf dark/light, restoring the prior LAF. */
    public static void themes(Consumer<String> scenario) throws Exception {
        if (!GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Headless capture required");
        SwingUtilities.invokeAndWait(() -> {
            var original = UIManager.getLookAndFeel();
            try {
                for (String theme : new String[]{"dark", "light"}) {
                    UIManager.setLookAndFeel("com.formdev.flatlaf.Flat" + (theme.equals("dark") ? "Dark" : "Light") + "Laf");
                    scenario.accept(theme);
                }
            } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException failure) {
                throw new IllegalStateException(failure);
            } finally {
                try { UIManager.setLookAndFeel(original); }
                catch (javax.swing.UnsupportedLookAndFeelException failure) { throw new IllegalStateException(failure); }
            }
        });
    }

    /** Saves only pixels and non-sensitive geometry/owner metadata into the module build directory. */
    public static void save(String family, String theme, String state, JComponent root, int width, int height) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("EDT capture required");
        Path output = Path.of("build/theme-migration-evidence", family, theme);
        try {
            Files.createDirectories(output);
            root.setSize(width, height);
            for (int i = 0; i < 3; i++) layout(root);
            var restoreRenderers = new java.util.ArrayList<Runnable>();
            prepareRenderers(root, restoreRenderers);
            var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try { root.printAll(graphics); } finally { graphics.dispose(); restoreRenderers.forEach(Runnable::run); }
            if (!ImageIO.write(image, "png", output.resolve(state + ".png").toFile())) throw new IOException("PNG writer unavailable");
            Files.writeString(output.resolve(state + ".json"), "{\"owner\":\"" + root.getClass().getName()
                + "\",\"theme\":\"" + theme + "\",\"state\":\"" + state + "\",\"width\":" + width
                + ",\"height\":" + height + ",\"laf\":\"" + UIManager.getLookAndFeel().getClass().getName()
                + "\",\"fontFamily\":\"" + UIManager.getFont("Label.font").getFamily() + "\",\"fontSize\":" + UIManager.getFont("Label.font").getSize() + ",\"headless\":true,\"nativeWindow\":false,\"icons\":\"fake SDK icons where used\"}\n");
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    // Without a peer Swing validation does not recurse into composite CellRendererPane children.
    // Layout the actual owner renderer's children at its assigned row bounds; retain the renderer itself.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void prepareRenderers(Container root, java.util.List<Runnable> restore) {
        if (root instanceof javax.swing.JList list) {
            javax.swing.ListCellRenderer original = list.getCellRenderer();
            if (original != null) {
                list.setCellRenderer((owner, value, index, selected, focused) -> {
                    java.awt.Component component = original.getListCellRendererComponent(owner, value, index, selected, focused);
                    component.setSize(Math.max(1, owner.getWidth()), Math.max(1, component.getPreferredSize().height));
                    if (component instanceof Container nested) for (int i = 0; i < 3; i++) layout(nested);
                    return component;
                });
                restore.add(() -> list.setCellRenderer(original));
            }
        }
        for (var child : root.getComponents()) if (child instanceof Container nested && child.isVisible()) prepareRenderers(nested, restore);
    }

    private static void layout(Container root) {
        root.doLayout();
        for (var child : root.getComponents()) if (child instanceof Container nested && child.isVisible()) layout(nested);
    }
}
