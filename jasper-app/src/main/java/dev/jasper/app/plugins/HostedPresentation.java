package dev.jasper.app.plugins;

import java.util.Optional;

/** The presentation every plugin's appearance reports: the look this process runs with. */
final class HostedPresentation {
    private HostedPresentation() { }

    /** The default look as on Windows and Linux, for a runtime that was given no presentation.
     * @return the default look's presentation
     */
    static dev.jasper.sdk.ui.Presentation standard() {
        return new dev.jasper.sdk.ui.Presentation(false, dev.jasper.sdk.ui.Presentation.Icons.NEW_UI,
            dev.jasper.sdk.ui.Presentation.ToolWindows.RAILS, dev.jasper.sdk.ui.Presentation.TitleBar.UNIFIED,
            dev.jasper.sdk.ui.Presentation.Menus.TITLE_BAR, dev.jasper.sdk.ui.Presentation.ToolbarSearch.FIELD,
            Optional.empty(), Optional.empty(), 13, 30);
    }
}
