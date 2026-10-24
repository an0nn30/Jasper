package dev.jasper.sdk.ui;
import dev.jasper.sdk.Subscription;
/** Nonblocking, owner-attached popup. Escape/outside dismissal closes it; closure disposes content. UI thread only. */
public interface PluginPopover extends Subscription {
    /**
     * Mount this producer's owned, unmounted view.
     *
     * @param content owned content
     */
    void setContent(UiView content);
    /** Show without a modal event loop. */
    void show();
    /**
     * Register final closure; runs once.
     *
     * @param handler cleanup
     * @return registration
     */
    Subscription onClosed(Runnable handler);
}
