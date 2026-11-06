package dev.jasper.browser.palette;

import dev.jasper.browser.item.AddressInput;
import dev.jasper.browser.item.BrowserItem;
import dev.jasper.browser.item.BrowserItems;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.TabPlacement;
import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Opens a palette row's address. The address goes through the check the address field makes of typed text, so a row
 * reaches nothing the field could not (a {@code javascript:} address is no address there). It loads in the browser item
 * the window has focused, which is the page's item when the focus is in its DevTools, and otherwise opens in a new tab of
 * the window. When Chromium cannot serve pages it posts the notification of why instead. UI thread.
 */
public final class PaletteOpener {
    private final BrowserItems items;
    private final Supplier<Optional<Throwable>> unavailable;
    private final Consumer<Throwable> tell;

    /**
     * An opener.
     *
     * @param items the plugin's browser items
     * @param unavailable why Chromium cannot serve pages, when it cannot
     * @param tell posts the notification that the browser could not start
     */
    public PaletteOpener(BrowserItems items, Supplier<Optional<Throwable>> unavailable, Consumer<Throwable> tell) {
        this.items = items;
        this.unavailable = unavailable;
        this.tell = tell;
    }

    /**
     * Opens {@code address} for {@code window}.
     *
     * @param address a stored or typed address
     * @param window the window the palette is in
     */
    public void open(URI address, WindowHandle window) {
        Optional<URI> checked = AddressInput.address(address.toString());
        if (checked.isEmpty()) return;
        Optional<Throwable> failed = unavailable.get();
        if (failed.isPresent()) {
            tell.accept(failed.get());
            return;
        }
        Optional<BrowserItem> focused = items.focused(window);
        if (focused.isPresent()) focused.get().load(checked.get());
        else items.open(checked.get(), TabPlacement.NEW_TAB, Optional.empty(), Optional.of(window));
    }
}
