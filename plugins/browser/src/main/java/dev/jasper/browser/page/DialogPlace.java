package dev.jasper.browser.page;

import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.Windows;
import java.util.Objects;

/**
 * Where a page's dialogs and file choosers show, in plain SDK types: over its item's tab, which owns them, so they
 * close with it, built with its plugin's kit and shown through its plugin's windows. The page's item gives it.
 *
 * @param kit builds a dialog's content
 * @param windows shows the dialogs and choosers
 * @param owner the page's item's tab
 */
public record DialogPlace(UiKit kit, Windows windows, ContentTab owner) {
    /** Rejects nulls. */
    public DialogPlace {
        Objects.requireNonNull(kit, "kit");
        Objects.requireNonNull(windows, "windows");
        Objects.requireNonNull(owner, "owner");
    }
}
