package dev.jasper.app.files;

import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class FileChooserScrollTest {
    @TempDir Path dir;

    @Test void scrollbarOverlaysWithoutReflowAndHidesAfterScrollingAndRemoval() throws Exception {
        for (int i = 0; i < 80; i++) Files.createFile(dir.resolve("file-" + i));
        var panel = new AtomicReference<FileChooserPanel>();
        var scroll = new AtomicReference<JScrollPane>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                var request = new ChooserRequest("Open", ChooserRequest.Mode.OPEN, false, Location.local(dir),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                var model = new ChooserModel(request, List.of(new LocalSource(new LocalBrowser(dir))),
                    Runnable::run, Runnable::run, false);
                panel.set(new FileChooserPanel(dev.jasper.app.testsupport.KitTestSupport.context(), model, new ChooserPrefs(UiState.inMemory()), dir,
                    locations -> {}, location -> true, Optional::empty));
                model.start();
                // The listing is the kit table under the OVERLAY scroll policy.
                var pane = dev.jasper.app.testsupport.KitTestSupport.require(panel.get(), FileChooserPanel.FILES, JScrollPane.class);
                scroll.set(pane);
                pane.setSize(500, 180);
                panel.get().addNotify();
                LayoutTestSupport.layoutTree(pane);
                var bar = pane.getVerticalScrollBar();
                assertThat(pane.isShowing()).isTrue();
                assertThat(paintedPixels(bar)).as("idle scrollbar is hidden").isZero();
                assertThat(bar.contains(2, 20)).as("hidden scrollbar does not intercept file clicks").isFalse();
                int width = pane.getViewport().getWidth();
                assertThat(width).as("the list uses the scrollbar's space").isEqualTo(pane.getWidth());
                bar.setValue(50);
                LayoutTestSupport.layoutTree(pane);
                assertThat(paintedPixels(bar)).as("scrolling reveals the thumb").isPositive();
                assertThat(pane.getViewport().getWidth()).isEqualTo(width);
                bar.setValueIsAdjusting(true);
            });
            // Let the real idle timer fire while a thumb drag is still held.
            Thread.sleep(1300);
            SwingUtilities.invokeAndWait(() -> {
                var bar = scroll.get().getVerticalScrollBar();
                assertThat(paintedPixels(bar)).as("a held drag stays visible").isPositive();
                bar.setValueIsAdjusting(false);
            });
            Thread.sleep(1300);
            SwingUtilities.invokeAndWait(() -> {
                var pane = scroll.get();
                var bar = pane.getVerticalScrollBar();
                assertThat(paintedPixels(bar)).as("idle after scrolling").isZero();
                bar.setValue(100);
                assertThat(paintedPixels(bar)).isPositive();
                pane.removeNotify();
                assertThat(paintedPixels(bar)).as("removal stops and hides the scrolling affordance").isZero();
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (panel.get() != null && panel.get().isDisplayable()) panel.get().removeNotify();
                if (panel.get() != null) panel.get().close();
            });
        }
    }

    private static long paintedPixels(JScrollBar bar) {
        var image = new BufferedImage(bar.getWidth(), bar.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { bar.paint(g); } finally { g.dispose(); }
        return java.util.Arrays.stream(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()))
            .filter(pixel -> pixel >>> 24 != 0).count();
    }
}
