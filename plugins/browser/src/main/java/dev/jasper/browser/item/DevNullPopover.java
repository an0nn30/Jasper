package dev.jasper.browser.item;

import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PluginPopover;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.List;
import java.util.Objects;

/**
 * What the /dev/null indicator opens (spec section 12): a popover from the indicator that says the item's browsing
 * activity is not saved and where the mode is set. Escape, a click outside and a second click on the indicator close it.
 * UI thread.
 */
final class DevNullPopover {
    static final String CARD = "browser.devnull.card", TITLE = "browser.devnull.title", TEXT = "browser.devnull.text";
    /** The popover's heading and body. */
    static final String HEADING = "/dev/null Mode",
        BODY = "Your browsing activity is not being saved. No history, bookmarks, or other data will be stored. "
            + "This mode is set in Settings › Tools › Browser › Privacy & Data.";
    // How long after it closed a click on the indicator is that same click: an outside click closes a popover as it is
    // pressed, and the indicator then reports its action.
    private static final long SAME_CLICK_NANOS = 300_000_000L;
    private final PluginPopover popover;
    private boolean closed;
    private long closedAt;

    private DevNullPopover(PluginPopover popover) {
        this.popover = popover;
    }

    /**
     * Shows the popover below the indicator of {@code toolbar}.
     *
     * @param context the plugin's context
     * @param toolbar the item's view, whose {@link BrowserToolbar#DEVNULL} indicator anchors it
     * @return the popover, shown
     * @throws IllegalArgumentException when the view has no indicator to anchor it
     */
    static DevNullPopover show(PluginContext context, UiView toolbar) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(toolbar, "toolbar");
        PluginPopover popover = context.windows().popover(toolbar.anchor(BrowserToolbar.DEVNULL));
        var shown = new DevNullPopover(popover);
        UiView content = null;
        try {
            content = context.kit().create(spec(), event -> { });
            popover.setContent(content);
            content = null; // the popover's now, which disposes it as it closes
            popover.onClosed(shown::ended);
            popover.show();
        } catch (RuntimeException | Error failure) {
            if (content != null) content.close();
            popover.close();
            throw failure;
        }
        return shown;
    }

    /** Whether it is still open. */
    boolean open() {
        return !closed;
    }

    /** Whether it closed just now, so that the click on the indicator which closed it is not one to open it again. */
    boolean justClosed() {
        return closed && System.nanoTime() - closedAt < SAME_CLICK_NANOS;
    }

    private void ended() {
        closed = true;
        closedAt = System.nanoTime();
    }

    /** Closes it, as a second click on the indicator does. */
    void close() {
        popover.close();
    }

    private static ViewSpec spec() {
        return new ViewSpec(new Nodes.Stack(CARD, ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Text(TITLE, ControlState.READY, HEADING, Nodes.TextRole.BODY_STRONG),
            new Nodes.Text(TEXT, ControlState.READY, BODY, Nodes.TextRole.BODY))));
    }
}
