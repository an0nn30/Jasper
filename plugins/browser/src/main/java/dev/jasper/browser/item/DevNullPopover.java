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
 * activity is not saved and where the mode is set. Escape and a click outside close it, as does a click on the indicator, which the host consumes
 * as it closes the popover, so that the click does not open it again.
 * UI thread.
 */
final class DevNullPopover {
    static final String CARD = "browser.devnull.card", TITLE = "browser.devnull.title", TEXT = "browser.devnull.text";
    /** The popover's heading and body. */
    static final String HEADING = "/dev/null Mode",
        BODY = "Your browsing activity is not being saved. No history, bookmarks, or other data will be stored. "
            + "This mode is set in Settings › Tools › Browser › Privacy & Data.";
    private final PluginPopover popover;
    private boolean closed;

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
            popover.onClosed(() -> shown.closed = true);
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

    /** Closes it. */
    void close() {
        popover.close();
    }

    private static ViewSpec spec() {
        return new ViewSpec(new Nodes.Stack(CARD, ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Text(TITLE, ControlState.READY, HEADING, Nodes.TextRole.BODY_STRONG),
            new Nodes.Text(TEXT, ControlState.READY, BODY, Nodes.TextRole.BODY))));
    }
}
