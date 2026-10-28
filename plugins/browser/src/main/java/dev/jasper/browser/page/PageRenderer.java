// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// Adapted for Jasper from JetBrains/jcef java_tests/tests (JBCefOsrHandler.java) at 9df0879.
package dev.jasper.browser.page;

import com.jetbrains.cef.JCefAppConfig;
import com.jetbrains.cef.SharedMemory;
import com.jetbrains.cef.SharedMemoryCache;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.cef.browser.CefBrowser;
import org.cef.callback.CefDragData;
import org.cef.handler.CefAcceleratedPaintInfo;
import org.cef.handler.CefNativeRenderHandler;
import org.cef.handler.CefScreenInfo;
import org.cef.misc.CefRange;

/**
 * What Chromium renders off screen for one page, handed to its {@link PageView}: each frame, copied into an image the
 * view draws, the pointer's cursor, and where the page's composition sits for the input method. It draws nothing
 * itself. Chromium calls it on its own threads.
 */
public final class PageRenderer implements CefNativeRenderHandler {
    private static final System.Logger LOG = System.getLogger(PageRenderer.class.getName());
    private static final boolean MAC_OS = System.getProperty("os.name", "").startsWith("Mac");
    private static final Rectangle NO_POPUP = new Rectangle();
    private final PageView view;
    private final SharedMemoryCache sharedMemory = new SharedMemoryCache();
    // getLocationOnScreen is costly, so it is read when the view shows and on each press, not for every query.
    private final AtomicReference<Point> onScreen = new AtomicReference<>(new Point());
    // Chromium's paint thread only.
    private BufferedImage image;
    private Rectangle popupBounds = NO_POPUP;
    private boolean popupShown;

    /**
     * Renders for {@code view}.
     *
     * @param view the canvas the frames go to
     */
    public PageRenderer(PageView view) {
        this.view = Objects.requireNonNull(view, "view");
        view.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent event) { locate(); }
        });
        view.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { locate(); }
        });
    }

    @Override public synchronized void disposeNativeResources() { }

    /** The view's size, never empty: Chromium requires a view rectangle with an area. */
    @Override public Rectangle getViewRect(CefBrowser browser) {
        return new Rectangle(0, 0, Math.max(1, view.getWidth()), Math.max(1, view.getHeight()));
    }

    @Override public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
        Rectangle screen = screenBounds(view.isShowing() ? view.getGraphicsConfiguration() : null);
        screenInfo.Set(getDeviceScaleFactor(browser), 32, 4, false, screen, screen);
        return true;
    }

    /** A point of the view on screen; macOS counts screen rows from the bottom of the main display. */
    @Override public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
        Point at = onScreen.get();
        if (!MAC_OS) return new Point(at.x + viewPoint.x, at.y + viewPoint.y);
        return new Point(at.x + viewPoint.x, screenBounds(null).height - at.y - viewPoint.y);
    }

    @Override public double getDeviceScaleFactor(CefBrowser browser) {
        return JCefAppConfig.getDeviceScaleFactor(view);
    }

    @Override public void onPopupShow(CefBrowser browser, boolean show) {
        popupShown = show;
    }

    @Override public void onPopupSize(CefBrowser browser, Rectangle size) {
        double scale = view.scale();
        popupBounds = new Rectangle((int) Math.floor(size.x * scale), (int) Math.floor(size.y * scale),
            (int) Math.ceil(size.width * scale), (int) Math.ceil(size.height * scale));
    }

    @Override public void onPaintWithSharedMem(CefBrowser browser, boolean popup, int dirtyRectsCount, String sharedMemName,
                                               long sharedMemHandle, int width, int height) {
        // A popup widget (a select's list) does not draw on this path: JetBrains' handler drew its frame as the page's,
        // at the popup's size, so the spike skipped it, and so does this.
        if (popup) return;
        SharedMemory.WithRaster memory = sharedMemory.get(sharedMemName, sharedMemHandle);
        memory.setWidth(width);
        memory.setHeight(height);
        memory.setDirtyRectsCount(dirtyRectsCount);
        BufferedImage target = image;
        if (target == null || target.getWidth() != width || target.getHeight() != height) {
            target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            memory.setDirtyRectsCount(0); // a new image loads the whole raster
        }
        Rectangle dirty = load(target, memory);
        image = target;
        view.painted(target, dirty);
    }

    @Override public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
        BufferedImage target = image;
        if (!popup && (target == null || target.getWidth() != width || target.getHeight() != height)) {
            target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            dirtyRects = new Rectangle[] {new Rectangle(0, 0, width, height)};
        }
        if (target == null) return; // a popup before the page's first frame
        int imageWidth = target.getWidth(), imageHeight = target.getHeight();
        // The first page frame after a popup closes also repaints where the popup was.
        if (!popup && !popupShown && popupBounds != NO_POPUP) {
            Rectangle[] widened = Arrays.copyOf(dirtyRects, dirtyRects.length + 1);
            widened[dirtyRects.length] = popupBounds;
            dirtyRects = new Rectangle[] {outer(widened).intersection(new Rectangle(0, 0, width, height))};
            popupBounds = NO_POPUP;
        }
        Point at = popup ? popupBounds.getLocation() : new Point();
        int[] pixels = ((DataBufferInt) target.getRaster().getDataBuffer()).getData();
        IntBuffer source = buffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        Rectangle bounds = new Rectangle(0, 0, imageWidth, imageHeight);
        for (Rectangle rect : dirtyRects) {
            Rectangle to = new Rectangle(rect.x + at.x, rect.y + at.y, rect.width, rect.height).intersection(bounds);
            if (to.isEmpty()) continue;
            if (to.x == 0 && to.width == imageWidth && width == imageWidth && at.x == 0) { // whole rows: one copy
                source.position((to.y - at.y) * width).get(pixels, to.y * imageWidth, to.height * imageWidth);
                continue;
            }
            for (int line = to.y; line < to.y + to.height; line++)
                source.position((line - at.y) * width + (to.x - at.x)).get(pixels, line * imageWidth + to.x, to.width);
        }
        Rectangle dirty = outer(dirtyRects);
        dirty.translate(at.x, at.y);
        image = target;
        view.painted(target, dirty.intersection(bounds));
    }

    @Override public void onAcceleratedPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, CefAcceleratedPaintInfo info) { }

    @Override public boolean onCursorChange(CefBrowser browser, int cursorType) {
        EventQueue.invokeLater(() -> view.setCursor(cursor(cursorType)));
        return true;
    }

    @Override public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        return false;
    }

    @Override public void updateDragCursor(CefBrowser browser, int operation) { }

    @Override public void OnImeCompositionRangeChanged(CefBrowser browser, CefRange selectionRange, Rectangle[] characterBounds) {
        view.ime().compositionChanged(characterBounds);
    }

    @Override public void OnTextSelectionChanged(CefBrowser browser, String selectedText, CefRange selectionRange) {
        view.ime().selectionChanged(selectedText);
    }

    private void locate() {
        if (view.isShowing()) onScreen.set(view.getLocationOnScreen());
    }

    /** The bounds of {@code screen}'s display, or of the main display; empty headless. */
    private static Rectangle screenBounds(GraphicsConfiguration screen) {
        if (GraphicsEnvironment.isHeadless()) return new Rectangle();
        try {
            var device = screen != null ? screen.getDevice() : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            return device.getDefaultConfiguration().getBounds();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.DEBUG, "The page's screen is unknown", failure);
            return new Rectangle();
        }
    }

    private static Cursor cursor(int type) {
        try {
            return Cursor.getPredefinedCursor(type);
        } catch (IllegalArgumentException unknown) {
            return Cursor.getDefaultCursor();
        }
    }

    private static Rectangle outer(Rectangle[] rects) {
        if (rects.length == 0) return new Rectangle();
        Rectangle outer = new Rectangle(rects[0]);
        for (Rectangle rect : rects) outer.add(rect);
        return outer;
    }

    /** Copies the shared raster's dirty rectangles, or all of it when it names none, into {@code image}; returns their union. */
    static Rectangle load(BufferedImage image, SharedMemory.WithRaster memory) {
        int width = memory.getWidth(), height = memory.getHeight(), count = memory.getDirtyRectsCount();
        IntBuffer source = memory.wrapRaster().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        Rectangle[] dirty = {new Rectangle(0, 0, width, height)};
        if (count > 0) {
            IntBuffer rects = memory.wrapRects().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
            dirty = new Rectangle[count];
            for (int c = 0; c < count; c++)
                dirty[c] = new Rectangle(rects.get(c * 4), rects.get(c * 4 + 1), rects.get(c * 4 + 2), rects.get(c * 4 + 3));
        }
        for (Rectangle rect : dirty) {
            if (rect.width < width) {
                for (int line = rect.y; line < rect.y + rect.height; line++) {
                    int offset = line * width + rect.x;
                    source.position(offset).get(pixels, offset, rect.width);
                }
            } else { // whole rows: one copy
                int offset = rect.y * width;
                source.position(offset).get(pixels, offset, width * rect.height);
            }
        }
        return outer(dirty);
    }
}
