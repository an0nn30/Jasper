package dev.jasper.browser.page;

import dev.jasper.browser.engine.Chromium;
import dev.jasper.sdk.ui.TabPlacement;
import java.awt.EventQueue;
import java.awt.Point;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.callback.CefRunContextMenuCallback;
import org.cef.handler.CefContextMenuHandlerAdapter;

/**
 * The page's right-click menu (spec section 11): the client's one context-menu handler. Chromium draws no menu off
 * screen, so Jasper shows its own, {@link PageMenuItems} for what is under the pointer, over the page's item's tab with
 * Jasper's pane items between its groups ({@code ContentTab.showContextMenu}), at the point Chromium gives on the page's
 * canvas. On macOS a Control-click reaches Chromium as the canvas got it, and Chromium asks for the menu as for a
 * right-click, as Chrome does there. Chromium asks on its own UI thread, where what is under the pointer is read, as its
 * parameters last only for the call; the menu shows on Swing's.
 *
 * <p>Every item is Jasper's to run: Back, Forward and Reload through the page; the links' and images' through the
 * page's item, which opens them in the tab next to its own, only at addresses the address field loads (R36); Cut,
 * Copy, Paste and Select All as the page's Edit menu runs them, on its focused frame, as Chrome's menu does; Search with
 * ⟨engine⟩ in the next tab with the engine the settings choose; Print… with Chromium's print dialog; and the copies with
 * the plugin's clipboard. View Source and Inspect Element show and do nothing yet: plan 4b's Tasks 9 and 10 give them
 * their pages. So Chromium's callback is always cancelled, exactly once: as the menu closes, before the item chosen runs,
 * whether one of the page's items is chosen, one of Jasper's runs or the menu is dismissed, or at once when no menu can
 * show. A page that closes while its menu shows closes with its item's tab, which dismisses the menu.
 *
 * <p>JCEF keeps a client's first handler of each kind, so this is the only one.
 */
final class PageMenu {
    private static final Consumer<CefClient> INSTALL = PageMenu::install;
    private static final System.Logger LOG = System.getLogger(PageMenu.class.getName());

    private PageMenu() { }

    /**
     * What Chromium reported under the pointer, in plain types.
     *
     * @param at where the menu was asked for, on the page's canvas
     * @param link the link's address as Chromium checked it, for opening; empty for none
     * @param linkAsWritten the link's address as the page wrote it, for copying, as Chrome copies it; empty for none
     * @param image the image's address; empty for none
     * @param selection the selected text; empty for none
     * @param editable whether the pointer is in an editable field
     */
    record Pointed(Point at, Optional<String> link, Optional<String> linkAsWritten, Optional<String> image,
                   Optional<String> selection, boolean editable) {
        /** Copies the point; rejects nulls. */
        Pointed {
            at = new Point(Objects.requireNonNull(at, "at"));
            Objects.requireNonNull(link, "link");
            Objects.requireNonNull(linkAsWritten, "linkAsWritten");
            Objects.requireNonNull(image, "image");
            Objects.requireNonNull(selection, "selection");
        }

        /** The point, copied: a {@code Point} can be changed. */
        @Override public Point at() {
            return new Point(at);
        }

        /**
         * What {@code params} report under the pointer: the link only when there is one, the address the page wrote
         * falling back to the checked one, and the image only for an image. Chromium's thread, while it asks.
         */
        static Pointed of(CefContextMenuParams params) {
            boolean linked = (params.getTypeFlags() & CefContextMenuParams.TypeFlags.CM_TYPEFLAG_LINK) != 0;
            Optional<String> link = linked ? text(params.getLinkUrl()) : Optional.empty();
            Optional<String> written = linked ? text(params.getUnfilteredLinkUrl()).or(() -> link) : Optional.empty();
            Optional<String> image = params.getMediaType() == CefContextMenuParams.MediaType.CM_MEDIATYPE_IMAGE
                ? text(params.getSourceUrl()) : Optional.empty();
            return new Pointed(new Point(params.getXCoord(), params.getYCoord()), link, written, image,
                text(params.getSelectionText()), params.isEditable());
        }

        private static Optional<String> text(String value) {
            return value == null || value.isEmpty() ? Optional.empty() : Optional.of(value);
        }
    }

    /**
     * Has Chromium install the handler on its client before any page is made on it; once, however often asked. A page's
     * start asks: this class names Chromium's types.
     */
    static void prepare(Chromium chromium) {
        chromium.onClient(INSTALL);
    }

    private static void install(CefClient client) {
        client.addContextMenuHandler(new Handler());
    }

    /** Chromium asks for a page's menu: Jasper shows its own, and Chromium shows none. */
    static final class Handler extends CefContextMenuHandlerAdapter {
        /**
         * Reads what is under the pointer and shows the menu on Swing's thread; a browser that is no page's gets none.
         * Chromium's own model is not read: Jasper's items stand in for all of it.
         *
         * @return true: Chromium shows nothing itself, and {@code callback} is ended, now or as the menu closes
         */
        @Override public boolean runContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model,
                                                CefRunContextMenuCallback callback) {
            Runnable end = ending(callback);
            Page page = browser instanceof PageBrowser shown ? shown.owner() : null;
            if (page == null) {
                end.run();
                return true;
            }
            Pointed pointed;
            try {
                pointed = Pointed.of(params);
            } catch (RuntimeException unreadable) {
                LOG.log(System.Logger.Level.WARNING, "A page's right-click menu could not be read", unreadable);
                end.run();
                return true;
            }
            EventQueue.invokeLater(() -> show(page, pointed, end));
            return true;
        }
    }

    /** What ends Chromium's menu, once however often it runs: {@code callback} cancelled. Any thread. */
    static Runnable ending(CefRunContextMenuCallback callback) {
        Objects.requireNonNull(callback, "callback");
        var ended = new AtomicBoolean();
        return () -> {
            if (ended.compareAndSet(false, true)) callback.cancel();
        };
    }

    /**
     * Shows the menu for {@code pointed} over the page's item's tab, and runs the page's item chosen. {@code end} runs
     * once, as the menu closes and before that item runs, or at once when the page has no place for a menu: closed, or
     * before its item gave it a tab. UI thread.
     *
     * @param page the page the menu is for
     * @param pointed what is under the pointer
     * @param end ends Chromium's menu
     */
    static void show(Page page, Pointed pointed, Runnable end) {
        Optional<MenuPlace> found = page.menuPlace();
        if (found.isEmpty()) {
            end.run();
            return;
        }
        MenuPlace place = found.get();
        try {
            PageMenuItems items = PageMenuItems.of(new PageMenuItems.Context(page.canGoBack(), page.canGoForward(), pointed.link(),
                pointed.image(), pointed.selection(), pointed.editable(), place.engineName().get()));
            Point at = pointed.at();
            SwingUtilities.convertPointToScreen(at, page.view());
            place.tab().showContextMenu(at, items.leading(), items.trailing(), chosen -> {
                end.run();
                chosen.ifPresent(id -> run(page, place, pointed, id));
            });
        } catch (RuntimeException | Error failure) {
            end.run();
            throw failure;
        }
    }

    /** Runs the page's item {@code id}, chosen from the menu for {@code pointed}. UI thread. */
    private static void run(Page page, MenuPlace place, Pointed pointed, String id) {
        switch (id) {
            case PageMenuItems.BACK -> page.back();
            case PageMenuItems.FORWARD -> page.forward();
            case PageMenuItems.RELOAD -> page.reload();
            case PageMenuItems.OPEN_LINK -> openInNextTab(page, pointed.link());
            case PageMenuItems.COPY_LINK -> pointed.linkAsWritten().ifPresent(place.clipboard());
            case PageMenuItems.OPEN_IMAGE -> openInNextTab(page, pointed.image());
            case PageMenuItems.COPY_IMAGE_ADDRESS -> pointed.image().ifPresent(place.clipboard());
            case PageMenuItems.CUT -> page.cut();
            case PageMenuItems.COPY -> page.copy();
            case PageMenuItems.PASTE -> page.paste();
            case PageMenuItems.SEARCH -> pointed.selection().map(PageMenuItems::searchTerms).filter(terms -> !terms.isEmpty())
                .ifPresent(terms -> page.openItem(place.search().apply(terms).toString(), TabPlacement.NEXT_TAB));
            case PageMenuItems.SELECT_ALL -> page.selectAll();
            case PageMenuItems.PRINT -> page.print();
            case PageMenuItems.COPY_TITLE -> {
                if (!page.title().isEmpty()) place.clipboard().accept(page.title());
            }
            case PageMenuItems.VIEW_SOURCE -> viewSource(page);
            case PageMenuItems.INSPECT_ELEMENT -> inspectElement(page, pointed.at());
            default -> { }
        }
    }

    /** Opens {@code address} in the tab next to the page's, when the address field loads it (R36). */
    private static void openInNextTab(Page page, Optional<String> address) {
        address.filter(PageMenuItems::opens).ifPresent(url -> page.openItem(url, TabPlacement.NEXT_TAB));
    }

    /** View Source: does nothing yet; plan 4b's Task 9 shows the page's source here. */
    private static void viewSource(Page page) { }

    /** Inspect Element at {@code at} on the page's canvas: does nothing yet; plan 4b's Task 10 opens DevTools here. */
    private static void inspectElement(Page page, Point at) { }
}
