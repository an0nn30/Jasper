package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** App-owned document tab, also a dynamic dialog/picker owner. All methods except close use the UI thread.
 * Moving retains content, selection/undo state and this handle. Closed handles do not reattach content.
 * @since 0.8.5
 */
public interface ContentTab extends WindowOwner, AutoCloseable {
    /** @param value literal title, location and state */
    void setPresentation(ContentPresentation value);
    /** @param commands immutable snapshot of active-content commands */
    void setCommands(List<ContentCommand> commands);
    /** @param items immutable snapshot of status items */
    void setStatus(List<ContentStatus> items);
    /** @param focus UI-thread callback focusing the editable content */
    void setFocusTarget(Runnable focus);
    /** Selects and raises the current host, focusing its content. */
    void select();
    /** @param separate true moves to a new separate workspace; false moves to the last main workspace */
    void move(boolean separate);
    /** @return whether the content lives in a separate window */
    boolean isSeparate();
    /** @return whether the content is selected in its host */
    boolean isActive();
    /** @return whether the handle has been disposed */
    boolean isClosed();
    /** @return whether closing was accepted; false keeps content intact */
    CompletableFuture<Boolean> requestClose();
    /** @param listener activation callback
     * @return removable registration */
    Subscription onActive(Consumer<Boolean> listener);
    /** @param listener once-only disposal callback
     * @return removable registration */
    Subscription onClosed(Runnable listener);
    /** Disposes without asking, for plugin shutdown; user close uses requestClose. */
    @Override void close();
}
