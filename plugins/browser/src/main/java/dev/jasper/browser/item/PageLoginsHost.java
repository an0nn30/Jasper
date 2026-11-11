package dev.jasper.browser.item;

import dev.jasper.browser.logins.FieldButtons;
import dev.jasper.browser.logins.HttpSignIns;
import dev.jasper.browser.logins.VaultLogins;
import dev.jasper.browser.page.DialogPlace;
import dev.jasper.browser.page.Page;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.UiView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;

/**
 * One browser item's logins (plan 8, F18), as {@link PageToolsHost} holds the item's tools. Its page's HTTP sign-ins go
 * to the plugin's {@link HttpSignIns}, with or without the Credential Vault, whose dialog shows over the item's tab,
 * and so do the page's final main-frame responses. With the Vault, its page's login fields
 * show the Vault's buttons too, whose picks the item's {@link FieldButtons} answer, the picker anchored on the field's
 * area of the page's canvas, at the zoom the item shows its page at, over the window the item's tab is in, once the
 * click that asked lies in that area (L20). The host sees where the canvas is, not what Chromium draws on it, so the
 * picker closes as the page goes elsewhere (a main frame's load start, a committed address, Jasper sending it
 * elsewhere) and as the item's zoom changes ({@link #zoomChanged}); the page's script closes it as the page scrolls,
 * and its anchor closes it as the canvas is resized (L6). A /dev/null item has the buttons too, as Chrome's Incognito
 * fills saved passwords, and nothing here writes to the Vault (F24); its sign-in dialog offers no Vault. It closes with
 * the item, which cancels the page's sign-ins, shown or waiting. UI thread.
 */
final class PageLoginsHost implements AutoCloseable {
    private final Page page;
    private final HttpSignIns signIns;
    // The page's field buttons, with the Credential Vault; null without it.
    private final FieldButtons buttons;
    private final List<Subscription> heard = new ArrayList<>();

    /**
     * The logins of {@code page}, whose sign-ins from now on go to {@code signIns}, and which, with the Vault, from now
     * on runs the field buttons' script in its http and https documents.
     *
     * @param page the item's page
     * @param vault the Credential Vault's logins; empty without the Vault
     * @param signIns the plugin's HTTP sign-ins
     * @param place where the page's dialogs show: over the item's tab
     * @param devNull whether the item is in /dev/null mode
     * @param view the item's view, whose {@code page} node holds the page's canvas
     * @param tab the item's tab, whose window the Vault's prompts open over
     * @param zoom the zoom the page shows at, in percent, as the item shows it
     */
    PageLoginsHost(Page page, Optional<VaultLogins> vault, HttpSignIns signIns, DialogPlace place, boolean devNull, UiView view,
                   ContentTab tab, IntSupplier zoom) {
        this.page = Objects.requireNonNull(page, "page");
        this.signIns = Objects.requireNonNull(signIns, "signIns");
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(place, "place");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(tab, "tab");
        Objects.requireNonNull(zoom, "zoom");
        heard.add(page.onSignIn(signIn -> signIns.challenged(page, place, devNull, signIn)));
        heard.add(page.onMainFrameResponse((url, status) -> signIns.responded(page, url, status)));
        buttons = vault.map(FieldButtons::new).orElse(null);
        if (buttons == null) return;
        page.handleLogins(FieldButtons.SCRIPT, query -> buttons.query(query, tab.window(),
            css -> FieldButtons.area(css, zoom.getAsInt() / 100.0, page.view().getSize()), area -> view.anchorAt(BrowserItem.PAGE, area)));
        heard.addAll(List.of(page.onLoadStart(address -> buttons.closePicker()), page.onAddressChange(address -> buttons.closePicker()),
            page.onNavigate(buttons::closePicker)));
    }

    /** The item's zoom changed, so the page's fields moved under the picker: it closes. */
    void zoomChanged() {
        if (buttons != null) buttons.closePicker();
    }

    /** The item closes: the page is heard no more, an open picker closes, and the page's sign-ins are cancelled. */
    @Override public void close() {
        heard.forEach(Subscription::close);
        if (buttons != null) buttons.closePicker();
        signIns.closed(page);
    }
}
