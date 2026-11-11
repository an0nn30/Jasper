package dev.jasper.browser.item;

import dev.jasper.browser.logins.FieldButtons;
import dev.jasper.browser.logins.VaultLogins;
import dev.jasper.browser.page.Page;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.UiView;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * One browser item's password manager (plan 8, F18), made only with the Credential Vault, as {@link PageToolsHost}
 * holds the item's tools: its page's login fields show the Vault's buttons, whose picks the item's {@link FieldButtons}
 * answer, the picker anchored on the field's area of the page's canvas, at the zoom the item shows its page at, over
 * the window the item's tab is in, once the click that asked lies in that area (L20). The host sees where the canvas
 * is, not what Chromium draws on it, so the picker closes as the page goes elsewhere (a main frame's load start, a
 * committed address, Jasper sending it elsewhere) and as the item's zoom changes ({@link #zoomChanged}); the page's
 * script closes it as the page scrolls, and its anchor closes it as the canvas is resized (L6). A /dev/null item has
 * the buttons too, as Chrome's Incognito fills saved passwords, and nothing here writes to the Vault (F24). It closes
 * with the item. UI thread.
 */
final class PageLoginsHost implements AutoCloseable {
    private final FieldButtons buttons;
    private final List<Subscription> heard;

    /**
     * The password manager of {@code page}, which from now on runs the field buttons' script in its http and https
     * documents.
     *
     * @param page the item's page
     * @param vault the Credential Vault's logins
     * @param view the item's view, whose {@code page} node holds the page's canvas
     * @param tab the item's tab, whose window the Vault's prompts open over
     * @param zoom the zoom the page shows at, in percent, as the item shows it
     */
    PageLoginsHost(Page page, VaultLogins vault, UiView view, ContentTab tab, IntSupplier zoom) {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(tab, "tab");
        Objects.requireNonNull(zoom, "zoom");
        buttons = new FieldButtons(vault);
        page.handleLogins(FieldButtons.SCRIPT, query -> buttons.query(query, tab.window(),
            css -> FieldButtons.area(css, zoom.getAsInt() / 100.0, page.view().getSize()), area -> view.anchorAt(BrowserItem.PAGE, area)));
        heard = List.of(page.onLoadStart(address -> buttons.closePicker()), page.onAddressChange(address -> buttons.closePicker()),
            page.onNavigate(buttons::closePicker));
    }

    /** The item's zoom changed, so the page's fields moved under the picker: it closes. */
    void zoomChanged() {
        buttons.closePicker();
    }

    /** The item closes: the page is heard no more, and an open picker closes. */
    @Override public void close() {
        heard.forEach(Subscription::close);
        buttons.closePicker();
    }
}
