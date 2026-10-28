package dev.jasper.browser.page;

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
import org.cef.CefClient;
import org.cef.browser.CefBrowserOsrWithHandler;
import org.cef.browser.CefRequestContext;
import org.cef.callback.CefDragData;

/**
 * One page's Chromium browser, rendered off screen by its {@link PageRenderer} for its {@link PageView}. Closing it
 * closes only the page.
 */
public final class PageBrowser extends CefBrowserOsrWithHandler {
    private static final System.Logger LOG = System.getLogger(PageBrowser.class.getName());
    // Chromium's drag operations (cef_drag_operations_mask_t) and event flags (cef_event_flags_t).
    private static final int COPY = 1, LINK = 2, MOVE = 16;
    private static final int SHIFT = 1 << 1, CONTROL = 1 << 2;

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
        super(client, url, context, renderer, view);
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

    /** Hands file and text drags over the view to the page, as JCEF's own (package-private) drop listener does. */
    DropTargetListener drops() {
        return new Drops();
    }

    // Adapted from JetBrains/jcef (java/org/cef/browser/CefDropTargetListener.java) at 9df0879, under JCEF's BSD-3-Clause licence.
    /** JCEF's own drop listener, {@code org.cef.browser.CefDropTargetListener}, which a plugin cannot construct. UI thread. */
    private final class Drops implements DropTargetListener {
        private int operations = COPY, modifiers, accepted = DnDConstants.ACTION_COPY;
        private boolean over;

        @Override public void dragEnter(DropTargetDragEvent event) {
            actionChanged(event.getDropAction());
            dragTargetDragEnter(dragData(event.getTransferable()), event.getLocation(), modifiers, operations);
            over = true;
        }

        @Override public void dragOver(DropTargetDragEvent event) {
            if (over) dragTargetDragOver(event.getLocation(), modifiers, operations);
        }

        @Override public void dropActionChanged(DropTargetDragEvent event) {
            actionChanged(event.getDropAction());
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
            dragTargetDrop(event.getLocation(), modifiers);
            event.acceptDrop(accepted);
            event.dropComplete(true);
        }

        /** The drag's action as Chromium's operation and the keys that ask for it, as JCEF maps them. */
        private void actionChanged(int action) {
            accepted = action;
            switch (action) {
                case DnDConstants.ACTION_NONE -> { operations = COPY; modifiers = 0; accepted = DnDConstants.ACTION_COPY; }
                case DnDConstants.ACTION_COPY -> { operations = COPY; modifiers = CONTROL; }
                case DnDConstants.ACTION_MOVE -> { operations = MOVE; modifiers = SHIFT; }
                case DnDConstants.ACTION_LINK -> { operations = LINK; modifiers = CONTROL | SHIFT; }
                default -> { }
            }
        }
    }

    /** The dragged files, or else the dragged text, for the page. */
    private static CefDragData dragData(Transferable dragged) {
        CefDragData data = CefDragData.create();
        try {
            if (dragged.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                for (Object file : (List<?>) dragged.getTransferData(DataFlavor.javaFileListFlavor))
                    if (file instanceof File each) data.addFile(each.getPath(), each.getName());
            } else if (dragged.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                data.setFragmentText((String) dragged.getTransferData(DataFlavor.stringFlavor));
            }
        } catch (UnsupportedFlavorException | IOException | InvalidDnDOperationException unreadable) {
            LOG.log(System.Logger.Level.DEBUG, "A drag's data could not be read", unreadable);
        }
        return data;
    }
}
