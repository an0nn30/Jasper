package dev.jasper.sdk.ui;

import javax.swing.JComponent;

/** Builds a panel's content for one window. */
@FunctionalInterface
public interface PanelFactory {
    /** Transitional owned-view factory; becomes PanelFactory in the coordinated cutover. */
    @FunctionalInterface
    interface View {
        /** Builds this panel instance's owned view. */
        UiView createView(PanelHost host);
    }

    /**
     * Called on the UI thread once per window, the first time the panel is shown there. Build ordinary
     * Swing components; share your own model between the instances. A factory that throws yields an
     * error placeholder instead of breaking the window.
     *
     * @param host this instance's window and visibility
     * @return the content, which fills the panel's region
     */
    JComponent create(PanelHost host);
}
