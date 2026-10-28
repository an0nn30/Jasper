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
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.browser.CefBrowserOsrWithHandler;
import org.cef.browser.CefRequestContext;
import org.cef.callback.CefDragData;

/**
 * One page's Chromium browser, rendered off screen by its {@link PageRenderer} for its {@link PageView}. It draws 60
 * frames a second while its view shows. Closing it closes only the page. Its {@link Page} is where the client's handlers
 * send its callbacks.
 */
public final class PageBrowser extends CefBrowserOsrWithHandler {
    private static final System.Logger LOG = System.getLogger(PageBrowser.class.getName());
    // The name JCEF stores this browser's native counterpart under.
    private static final String NATIVE_BROWSER = "CefBrowser";
    // Chromium's drag operations (cef_drag_operations_mask_t) and event flags (cef_event_flags_t).
    private static final int COPY = 1, LINK = 2, MOVE = 16;
    private static final int SHIFT = 1 << 1, CONTROL = 1 << 2;
    private final PageView view;
    // Set before the native browser is made, so before Chromium calls back for this browser.
    private volatile Page owner;

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

    /** Makes the native browser, unless that has begun. The view calls this once it is in a window. UI thread. */
    void create() {
        if (!CefClient.isNativeBrowserCreationStarted(this)) createImmediately();
    }

    /** Device pixels per view pixel on {@code view}'s screen. */
    double deviceScale(PageView view) {
        return JCefAppConfig.getDeviceScaleFactor(view);
    }

    /** The page this browser shows, or null before it has one. Any thread. */
    Page owner() {
        return owner;
    }

    /** Makes {@code page} the one the client's handlers send this browser's callbacks to. Before the native browser exists. */
    void owner(Page page) {
        owner = page;
    }

    /**
     * JCEF's native code stores the native browser here once it exists. Only from then on do calls such as
     * {@code setWindowVisibility}, {@code setWindowlessFrameRate} and {@code loadURL} take effect, because JCEF drops
     * the ones made earlier. So the view tells the page again whether it shows, and the page loads what it was asked
     * to meanwhile, on the UI thread.
     */
    @Override public void setNativeRef(String identifier, long nativeRef) {
        super.setNativeRef(identifier, nativeRef);
        PageView target = view;
        if (nativeRef != 0 && NATIVE_BROWSER.equals(identifier) && target != null) EventQueue.invokeLater(() -> {
            target.browserCreated();
            Page page = owner;
            if (page != null) page.created(this);
        });
    }

    /**
     * Lets Chromium close this browser and nothing else. JCEF's own answer cancels a close not first allowed with
     * {@code setCloseAllowed}, and lets an allowed one go ahead only after asking the view's window, Jasper's, to close.
     *
     * @return false, always
     */
    @Override public boolean doClose() {
        return false;
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
