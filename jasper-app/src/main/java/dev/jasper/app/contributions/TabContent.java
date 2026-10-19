package dev.jasper.app.contributions;

import javax.swing.JComponent;

/**
 * What one tab shows (workspace tabs spec 9.2): {@link HostedContent}, a plugin's view, or the workspace's terminal
 * content, one terminal pane. The registry holds it for the tab's whole life and never looks inside; a window mounts
 * {@link #component()} and moves it to another window without replacing it. EDT only.
 */
public interface TabContent {
    /**
     * The component a window shows for the tab, the same object for the tab's whole life.
     *
     * @return the component
     */
    JComponent component();

    /** Releases what the content holds once its tab has closed, as a terminal closes its session. Called once. */
    void dispose();
}
