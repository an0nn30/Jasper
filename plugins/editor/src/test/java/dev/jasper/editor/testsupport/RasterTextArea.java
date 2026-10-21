package dev.jasper.editor.testsupport;

import dev.jasper.editor.vim.VimTextArea;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

/** Supplies RSTA's first-paint metric refresh with a real raster graphics context, without a native peer. */
public final class RasterTextArea extends VimTextArea implements AutoCloseable {
    private final java.awt.Graphics2D metrics = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB).createGraphics();
    @Override public Graphics getGraphics() { return metrics; }
    @Override public void close() { metrics.dispose(); }
}
