package dev.jasper.browser.page;

import dev.jasper.sdk.Subscription;
import java.awt.Point;
import java.awt.event.HierarchyEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.swing.JComponent;
import org.cef.browser.CefFrame;

/**
 * Chromium's DevTools for one page, inspecting the element at a point on the page's canvas, as Inspect Element opens
 * them (spec section 10), in plain types: a canvas of their own, which their {@link Page} makes, and Chromium's DevTools
 * browser, drawn on it off screen as a page's browser is drawn on the page's. Chromium makes that browser through the
 * page's ({@code PageBrowser.createDevToolsBrowser}) once the canvas is in a window and the DevTools made for the page
 * before these have closed: Chromium keeps one DevTools per page, and asked again while they are open it only shows
 * them, which would leave JCEF matching the next browser Chromium makes to the one it was asked for. The wait is
 * transitive (P44): DevTools closed while they wait end only once those before them have, so DevTools after them wait for
 * every earlier one. The client's handlers take the DevTools browser for no page's. Closing the DevTools closes that
 * browser, and so does the page's close; those who asked {@link #onEnd} are told once Chromium has closed it, or, for
 * DevTools Chromium was never asked for, once they are closed and the DevTools before them have ended. UI thread.
 */
public final class DevTools implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(DevTools.class.getName());
    private final Page page;
    private final PageView view;
    private final Point at;
    private final List<Runnable> ends = new CopyOnWriteArrayList<>();
    // The page's DevTools before these, until they have ended.
    private DevTools previous;
    // The DevTools browser Chromium made, and what has Chromium close it; null before Chromium is asked and after the end.
    private PageBrowser browser;
    private Runnable closer;
    private boolean asked, closed, ended;

    /**
     * DevTools for {@code page} at {@code at}, drawn on {@code view}, which wait for {@code previous} to end. Only
     * {@link Page} makes them, as it makes every canvas.
     */
    DevTools(Page page, PageView view, Point at, DevTools previous) {
        this.page = Objects.requireNonNull(page, "page");
        this.view = Objects.requireNonNull(view, "view");
        this.at = new Point(Objects.requireNonNull(at, "at"));
        view.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) open();
        });
        if (previous != null && !previous.ended) {
            this.previous = previous;
            previous.onEnd(() -> {
                this.previous = null;
                if (closed) ended();
                else open();
            });
        }
    }

    /**
     * The canvas the DevTools are drawn on, for their item's custom content.
     *
     * @return the canvas
     */
    public JComponent view() {
        return view;
    }

    /** Gives the DevTools the keyboard focus. */
    public void focus() {
        view.requestFocusInWindow();
    }

    /** Copies the selection of the DevTools' focused frame, as Jasper's Copy does in them. */
    public void copy() {
        onFocusedFrame(CefFrame::copy);
    }

    /** Pastes the clipboard into the DevTools' focused frame, as Jasper's Paste does in them. */
    public void paste() {
        onFocusedFrame(CefFrame::paste);
    }

    /**
     * Tells {@code listener} once, when the DevTools end: once Chromium has closed their browser, after their close, the
     * page's or Chromium's own; at their close, for DevTools Chromium never made; at once when they have ended.
     *
     * @param listener told on the UI thread
     * @return the registration
     */
    public Subscription onEnd(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (ended) {
            listener.run();
            return () -> { };
        }
        ends.add(listener);
        return () -> ends.remove(listener);
    }

    /**
     * Closes the DevTools, once: Chromium closes their browser, and they end once it has. DevTools Chromium was never asked
     * for end now, or while they wait, once the DevTools before them have ended.
     */
    @Override public void close() {
        if (closed) return;
        closed = true;
        if (asked) {
            if (closer != null) closer.run(); // else open() runs it once Chromium has been asked
        } else if (previous == null) {
            ended();
        }
    }

    /** The page the DevTools inspect. */
    Page page() {
        return page;
    }

    /** The canvas, for the DevTools browser Chromium makes for these. */
    PageView canvas() {
        return view;
    }

    /** The DevTools browser Chromium made for these, through the page's browser, as their {@link #open} asked. */
    void made(PageBrowser devTools) {
        browser = Objects.requireNonNull(devTools, "devTools");
    }

    /**
     * Chromium has closed the DevTools browser, or was never asked for it: those who asked {@link #onEnd} are told, and the
     * DevTools let go of Chromium's browser. Once.
     */
    void ended() {
        if (ended) return;
        ended = true;
        closed = true;
        browser = null;
        closer = null;
        previous = null;
        List<Runnable> told = List.copyOf(ends);
        ends.clear();
        for (Runnable listener : told) listener.run();
    }

    /**
     * Has Chromium make the DevTools browser through the page's ({@code Page.openDevTools}), once the canvas is in a window
     * and the page's DevTools before have ended, while the page's browser is ready. Once; DevTools Chromium could not make
     * end.
     */
    private void open() {
        if (asked || closed || previous != null || !view.isDisplayable()) return;
        asked = true;
        Optional<Runnable> opened;
        try {
            opened = page.openDevTools(this, at);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Chromium could not open a page's DevTools", failure);
            ended();
            return;
        }
        if (opened.isEmpty()) { // the page's browser is not ready: nothing was asked
            asked = false;
            if (closed) ended();
            return;
        }
        closer = opened.get();
        if (closed) closer.run(); // closed while Chromium was asked
    }

    /** Runs {@code command} on the DevTools' focused frame, then releases only this handle to it; nothing before Chromium made them. */
    private void onFocusedFrame(Consumer<CefFrame> command) {
        PageBrowser target = closed ? null : browser;
        CefFrame frame = target == null ? null : target.getFocusedFrame();
        if (frame == null) return;
        try {
            command.accept(frame);
        } finally {
            frame.dispose();
        }
    }
}
