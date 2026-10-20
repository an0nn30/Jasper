package dev.jasper.app.plugins;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Device-scale-2 captures of actual staged Editor and plugin owners (Plan 3 Task 8): the owner is laid out at a logical
 * size and painted through a real Java2D scale(2,2) transform with printAll. Output goes to
 * build/theme-migration-evidence/{@value #FAMILY}/&lt;theme&gt;/&lt;state&gt;.png beside a .txt of the named anchors' logical
 * bounds; no text values beyond component names are recorded. Headless pixels are evidence, not native acceptance.
 */
final class EditorEvidence {
    static final String FAMILY = "editor-hosted-2x";
    private EditorEvidence() { }

    /** Captures {@code root} at {@code width}×{@code height} logical pixels and records the anchors' bounds; EDT. */
    static BufferedImage capture(String theme, String state, JComponent root, int width, int height, String... anchors) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("EDT capture required");
        root.setSize(width, height);
        for (int i = 0; i < 3; i++) layout(root);
        measureDocuments(root);
        var image = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { graphics.scale(2, 2); root.printAll(graphics); } finally { graphics.dispose(); }
        var metadata = new StringBuilder("provenance=real staged plugin/headless Java2D\ndeviceScale=2\nlogical=" + width + "x" + height + "\n");
        for (String anchor : anchors) {
            var component = WorkspaceContentRouteTest.named(root, anchor, Component.class);
            metadata.append(anchor).append('=').append(component == null || !visible(root, component) ? "absent" : bounds(root, component)).append('\n');
        }
        Path output = Path.of("build/theme-migration-evidence", FAMILY, theme);
        try {
            Files.createDirectories(output);
            if (!ImageIO.write(image, "png", output.resolve(state + ".png").toFile())) throw new IOException("PNG writer unavailable");
            Files.writeString(output.resolve(state + ".txt"), metadata);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
        return image;
    }

    /** {@code component}'s bounds in {@code root}'s coordinates. */
    static Rectangle bounds(Component root, Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), root);
    }

    /** The visible, enabled, focusable components under {@code root}, in container (traversal) order. */
    static List<Component> focusable(Container root) {
        var result = new ArrayList<Component>();
        for (Component child : root.getComponents()) {
            if (!child.isVisible()) continue;
            if (child.isFocusable() && child.isEnabled() && (child instanceof javax.swing.AbstractButton || child instanceof javax.swing.text.JTextComponent)) result.add(child);
            if (child instanceof Container nested) result.addAll(focusable(nested));
        }
        return result;
    }

    /** Initializes every RSyntaxTextArea document canvas under {@code root} for headless painting. */
    static void measureDocuments(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof javax.swing.JTextArea area && area.getClass().getSuperclass().getName().equals("org.fife.ui.rsyntaxtextarea.RSyntaxTextArea")) measure(area);
            if (child instanceof Container nested) measureDocuments(nested);
        }
    }

    /** The pinned vendor's headless font-metric initialization (as EditorDocumentPreservationTest); no native peer. */
    static void measure(javax.swing.JTextArea area) {
        var graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            var type = area.getClass().getSuperclass(); var refresh = type.getDeclaredMethod("refreshFontMetrics", java.awt.Graphics2D.class);
            refresh.setAccessible(true); refresh.invoke(area, graphics);
            var flag = type.getDeclaredField("metricsNeverRefreshed"); flag.setAccessible(true); flag.setBoolean(area, false);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); } finally { graphics.dispose(); }
    }

    /** Whether {@code component} and every ancestor below {@code root} is visible. */
    static boolean visible(Component root, Component component) {
        for (Component at = component; at != null && at != root; at = at.getParent()) if (!at.isVisible()) return false;
        return true;
    }

    static void layout(Container root) {
        root.doLayout();
        for (var child : root.getComponents()) if (child instanceof Container nested && child.isVisible()) layout(nested);
    }
}
