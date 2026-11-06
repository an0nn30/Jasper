package dev.jasper.browser.page;

import com.jetbrains.cef.JCefAppConfig;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.dnd.InvalidDnDOperationException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowserOsrWithHandler;
import org.cef.browser.CefRequestContext;
import org.cef.callback.CefDragData;

/**
 * One page's Chromium browser, rendered off screen by its {@link PageRenderer} for its {@link PageView}. It draws 60
 * frames a second while its view shows. Closing it closes only the page, and a script that closes the page's window
 * closes the page's item. Its {@link Page} is where the client's handlers send its callbacks.
 *
 * <p>A page's {@link DevTools} are a browser of this class too, Chromium's DevTools browser, which JCEF has the page's
 * browser make ({@link #createDevToolsBrowser}): drawn off screen on the DevTools' own canvas by a renderer of its own,
 * on the page's client, whose handlers take it for no page's, as it has no {@link #owner()}. Chromium's own DevTools
 * window, a top-level one, is never made: JCEF has none for an off-screen browser.
 *
 * <p>A Markdown preview's browser is one too, on the preview client, made by its {@link PreviewPage}: it has no
 * {@link #owner()}, so nothing of a page's reaches it, not even {@code jasper-about:}, and its preview's page is where
 * the preview client's handlers send its callbacks ({@link #previewOwner()}).
 */
public final class PageBrowser extends CefBrowserOsrWithHandler {
    private static final System.Logger LOG = System.getLogger(PageBrowser.class.getName());
    // The name JCEF stores this browser's native counterpart under.
    private static final String NATIVE_BROWSER = "CefBrowser";
    // Chromium's drag operations (cef_drag_operations_mask_t) and event flags (cef_event_flags_t).
    private static final int COPY = 1, LINK = 2, MOVE = 16;
    private static final int SHIFT = 1 << 1, CONTROL = 1 << 2;
    // Chromium's tolerance for two zoom levels being the same (blink::ZoomValuesEqual).
    private static final double SAME_ZOOM = 0.001;
    private final PageView view;
    // The DevTools this browser draws, when it is Chromium's DevTools browser; null for a page's.
    private final DevTools devTools;
    // UI thread: the DevTools Chromium makes while openDevTools runs.
    private DevTools opening;
    // Set before the native browser is made, so before Chromium calls back for this browser.
    private volatile Page owner;
    // A preview's browser's page, set as its page is, before the native browser is made; null for any other browser.
    private volatile PreviewPage previewOwner;
    // Set once Java asks the browser to close: Jasper's close of its page, or JCEF's close of its client's browsers.
    private volatile boolean closeAsked;

    /**
     * A page that loads {@code url} once its native browser is made, which its view asks for once it is in a window.
     *
     * @param client the client every page is made from
     * @param url what the page loads first
     * @param context its request context, or null for the client's global one
     * @param renderer receives what Chromium renders for it
     * @param view the canvas it is drawn on, which takes its input
     */
    public PageBrowser(CefClient client, String url, CefRequestContext context, PageRenderer renderer, PageView view) {
        super(client, url, context, renderer, view, settings());
        this.view = view;
        devTools = null;
    }

    /** Chromium's DevTools browser for {@code tools}, inspecting {@code inspected}'s page at {@code at}. */
    private PageBrowser(CefClient client, String url, CefRequestContext context, PageRenderer renderer, PageView view,
                        PageBrowser inspected, Point at, DevTools tools) {
        super(client, url, context, renderer, view, inspected, at, settings());
        this.view = view;
        devTools = tools;
    }

    /** Chromium's own default is 30 frames a second. */
    private static CefBrowserSettings settings() {
        var settings = new CefBrowserSettings();
        settings.windowless_frame_rate = PageView.SHOWN_FRAME_RATE;
        return settings;
    }

    /**
     * Becomes its view's page: the view draws it, hands it the mouse, the wheel, keys, input methods and drops, and
     * tells it whether it shows. The native browser is made once the view is in a window. UI thread; once.
     */
    void attach() {
        PageView target = view;
        target.browser = this;
        target.attached(new PageView.Hooks(this::setWindowVisibility, this::setWindowlessFrameRate, this::sendKeyEvent),
            new ImeAdapter(target, this), new Drops());
    }

    /**
     * Makes the native browser, unless that has begun. The view calls this once it is in a window. UI thread. Chromium's
     * DevTools browser is made only as JCEF's {@code openDevTools} makes it, at once and once: asked again before Chromium
     * made it, Chromium would only show the DevTools, and JCEF would match the next browser Chromium makes to this one.
     */
    void create() {
        if (devTools == null && !CefClient.isNativeBrowserCreationStarted(this)) createImmediately();
    }

    /**
     * Has Chromium make {@code tools}' DevTools browser, inspecting the element at {@code at} on the page's canvas: JCEF
     * asks {@link #createDevToolsBrowser} for it, then makes it. UI thread.
     */
    void openDevTools(DevTools tools, Point at) {
        opening = Objects.requireNonNull(tools, "tools");
        try {
            openDevTools(at);
        } finally {
            opening = null;
        }
    }

    /**
     * The DevTools browser for the DevTools {@link #openDevTools(DevTools, Point)} opens, which JCEF then makes: drawn on
     * their canvas by a renderer of its own, as a page's browser is on the page's, and on {@code client}, the page's, as
     * Chromium makes DevTools on their page's client. UI thread.
     *
     * @throws IllegalStateException when asked other than through {@link #openDevTools(DevTools, Point)}
     */
    @Override protected CefBrowser createDevToolsBrowser(CefClient client, String url, CefRequestContext context, CefBrowser parent,
                                                         Point inspectAt) {
        DevTools tools = opening;
        if (tools == null) throw new IllegalStateException("A page's DevTools open only through its Inspect Element");
        PageView canvas = tools.canvas();
        var made = new PageBrowser(client, url, context, new PageRenderer(canvas), canvas, this, inspectAt, tools);
        made.attach();
        tools.made(made);
        return made;
    }

    /** Device pixels per view pixel on {@code view}'s screen. */
    double deviceScale(PageView view) {
        return JCefAppConfig.getDeviceScaleFactor(view);
    }

    /**
     * The page this browser shows, where the client's handlers send its callbacks. Any thread.
     *
     * @return the page, or null before it has one
     */
    public Page owner() {
        return owner;
    }

    /**
     * Whether the browser's page keeps its data in memory, as a /dev/null page's does, so its context is one Chromium's
     * {@code jasper-about:} scheme handler factory does not serve: as the page's store says, for a page's DevTools the
     * inspected page's, and never for a browser of no page. Not JCEF's context object, which for a persistent page's
     * DevTools is the global context's. Any thread.
     *
     * @return true for a browser of an in-memory context
     */
    public boolean inMemory() {
        Page page = owner;
        if (page == null) page = inspected();
        return page != null && page.inMemory();
    }

    /**
     * The page whose DevTools this browser shows, when it is Chromium's DevTools browser: where the links the DevTools open
     * go ({@link Page#openFromDevTools}). Any thread.
     *
     * @return the page, or null for a page's own browser
     */
    public Page inspected() {
        DevTools tools = devTools;
        return tools == null ? null : tools.page();
    }

    /**
     * The canvas the page is drawn on, which records the press and the key that decide whether and where its links and
     * popups open. Any thread.
     *
     * @return the canvas
     */
    public PageView view() {
        return view;
    }

    /**
     * Shows the page at its own zoom again as its main frame starts loading a document, which Chromium shows at the zoom
     * it keeps for the document's host: at once on Chromium's thread, the thread its handlers are told on, so the
     * document draws at the page's zoom from its first frame. A page already at its zoom leaves its host's zoom, and so
     * the other pages on that host, alone.
     */
    void keepZoom() {
        Page page = owner;
        if (page == null) return;
        double level = page.zoomLevel();
        if (Math.abs(getZoomLevel() - level) > SAME_ZOOM) setZoomLevel(level);
    }

    /** Makes {@code page} the one the client's handlers send this browser's callbacks to. Before the native browser exists. */
    void owner(Page page) {
        owner = page;
    }

    /**
     * The Markdown preview's page this browser shows, where the preview client's handlers send its callbacks. Any thread.
     *
     * @return the preview's page, or null for a browser that is no preview's
     */
    PreviewPage previewOwner() {
        return previewOwner;
    }

    /**
     * Makes {@code page} the preview's page the preview client's handlers send this browser's callbacks to. Before the
     * native browser exists.
     */
    void previewOwner(PreviewPage page) {
        previewOwner = page;
    }

    /**
     * JCEF's native code stores the native browser here once it exists. Only from then on do calls such as
     * {@code setWindowVisibility}, {@code setWindowlessFrameRate} and {@code loadURL} take effect, because JCEF drops
     * the ones made earlier. So the view tells the page again whether it shows, and the page loads what it was asked
     * to meanwhile, on the UI thread; a preview's page is told too.
     */
    @Override public void setNativeRef(String identifier, long nativeRef) {
        super.setNativeRef(identifier, nativeRef);
        PageView target = view;
        if (nativeRef != 0 && NATIVE_BROWSER.equals(identifier) && target != null) EventQueue.invokeLater(() -> {
            target.browserCreated();
            Page page = owner;
            if (page != null) page.created(this);
            PreviewPage preview = previewOwner;
            if (preview != null) preview.created(this);
        });
    }

    /**
     * Closes the browser, as Jasper's close of its page and JCEF's close of its client's browsers ask: Chromium's
     * {@link #doClose()} then lets the close go ahead.
     *
     * @param force whether the page's {@code beforeunload} is skipped
     */
    @Override public void close(boolean force) {
        closeAsked = true;
        super.close(force);
    }

    /**
     * Chromium's question, on its own thread, before it destroys this browser: true holds the close back, false lets
     * it go ahead, and an off-screen browser is then destroyed at once. A close {@link #close(boolean)} asked for goes
     * ahead. Any other, a script's {@code window.close()}, closes the page's item instead, as Chrome closes the tab, and
     * the item's close then closes the browser. JCEF's own answer is never asked: it holds back a close not first
     * allowed with {@code setCloseAllowed}, and lets an allowed one go ahead only after asking the view's window,
     * Jasper's, to close. Chromium's DevTools browser closes as Chromium closes the DevTools, as their close or their
     * page's asks: it goes ahead.
     *
     * @return whether Chromium holds the close back
     */
    @Override public boolean doClose() {
        return devTools == null && Page.holdsClose(closeAsked, owner, EventQueue::invokeLater);
    }

    /** Chromium has destroyed the browser: for its DevTools browser, the DevTools end, on the UI thread. Chromium's thread. */
    @Override public void onBeforeClose() {
        super.onBeforeClose();
        DevTools tools = devTools;
        if (tools != null) EventQueue.invokeLater(tools::ended);
    }

    // Adapted from JetBrains/jcef (java/org/cef/browser/CefDropTargetListener.java) at 9df0879, under JCEF's BSD-3-Clause licence.
    /**
     * JCEF's own drop listener, {@code org.cef.browser.CefDropTargetListener}, which a plugin cannot construct. A drag
     * from outside Jasper cannot be read before its drop is accepted, so until then the page sees only what kind of
     * drag it is. The drop accepts, reads the data, and hands the page the drag again with it. UI thread.
     */
    private final class Drops implements DropTargetListener {
        private int operations = COPY, modifiers, accepted = DnDConstants.ACTION_COPY;
        private boolean over;

        @Override public void dragEnter(DropTargetDragEvent event) {
            actionChanged(event.getDropAction(), event.getSourceActions());
            dragTargetDragEnter(offered(event), event.getLocation(), modifiers, operations);
            over = true;
        }

        @Override public void dragOver(DropTargetDragEvent event) {
            if (over) dragTargetDragOver(event.getLocation(), modifiers, operations);
        }

        @Override public void dropActionChanged(DropTargetDragEvent event) {
            actionChanged(event.getDropAction(), event.getSourceActions());
        }

        @Override public void dragExit(DropTargetEvent event) {
            if (over) dragTargetDragLeave();
            over = false;
        }

        @Override public void drop(DropTargetDropEvent event) {
            if (!over) {
                event.rejectDrop();
                return;
            }
            over = false;
            actionChanged(event.getDropAction(), event.getSourceActions());
            event.acceptDrop(accepted); // only now can a drag from outside the JVM be read
            CefDragData data = dropped(event.getTransferable());
            Point at = event.getLocation();
            dragTargetDragLeave();
            if (data == null) {
                event.dropComplete(false);
                return;
            }
            dragTargetDragEnter(data, at, modifiers, operations);
            dragTargetDragOver(at, modifiers, operations);
            dragTargetDrop(at, modifiers);
            event.dropComplete(true);
        }

        /**
         * Chromium is offered every action the source allows. The chosen action sets the keys that ask for it and the
         * action accepted, as JCEF maps them.
         */
        private void actionChanged(int action, int sourceActions) {
            operations = operations(sourceActions);
            accepted = action;
            switch (action) {
                case DnDConstants.ACTION_NONE -> { modifiers = 0; accepted = DnDConstants.ACTION_COPY; }
                case DnDConstants.ACTION_COPY -> modifiers = CONTROL;
                case DnDConstants.ACTION_MOVE -> modifiers = SHIFT;
                case DnDConstants.ACTION_LINK -> modifiers = CONTROL | SHIFT;
                default -> { }
            }
        }
    }

    /** AWT's drag actions as Chromium's operations; a source that names none is offered a copy. */
    private static int operations(int actions) {
        int mask = 0;
        if ((actions & DnDConstants.ACTION_COPY) != 0) mask |= COPY;
        if ((actions & DnDConstants.ACTION_MOVE) != 0) mask |= MOVE;
        if ((actions & DnDConstants.ACTION_LINK) != 0) mask |= LINK;
        return mask == 0 ? COPY : mask;
    }

    /**
     * What the drag holds, by kind only: a file entry with no path, or empty text. Pages see a drag's kinds, never its
     * data, until the drop.
     */
    private static CefDragData offered(DropTargetDragEvent event) {
        CefDragData data = CefDragData.create();
        if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) data.addFile("", "");
        else if (event.isDataFlavorSupported(DataFlavor.stringFlavor)) data.setFragmentText("");
        return data;
    }

    /** The dropped files, or else the dropped text; null when there is neither or it cannot be read. */
    private static CefDragData dropped(Transferable dropped) {
        try {
            if (dropped.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                List<?> files = (List<?>) dropped.getTransferData(DataFlavor.javaFileListFlavor);
                CefDragData data = CefDragData.create();
                for (Object file : files)
                    if (file instanceof File each) data.addFile(each.getPath(), each.getName());
                return data;
            }
            if (dropped.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = (String) dropped.getTransferData(DataFlavor.stringFlavor);
                CefDragData data = CefDragData.create();
                data.setFragmentText(text);
                return data;
            }
        } catch (UnsupportedFlavorException | IOException | InvalidDnDOperationException | ClassCastException unreadable) {
            LOG.log(System.Logger.Level.WARNING, "A drop's data could not be read", unreadable);
        }
        return null;
    }
}
