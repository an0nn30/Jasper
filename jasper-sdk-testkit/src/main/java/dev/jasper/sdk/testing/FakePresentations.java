package dev.jasper.sdk.testing;

import dev.jasper.sdk.ui.Presentation;
import java.util.Optional;

/**
 * Ready presentations for {@link FakePluginHost#setPresentation}.
 *
 * @since 0.18.2
 */
public final class FakePresentations {
    /** The default look as on Windows and Linux; a {@link FakePluginHost} reports it until told otherwise. */
    public static final Presentation STANDARD = new Presentation(false, Presentation.Icons.NEW_UI, Presentation.ToolWindows.RAILS,
        Presentation.TitleBar.UNIFIED, Presentation.Menus.TITLE_BAR, Presentation.ToolbarSearch.FIELD, Optional.empty(), Optional.empty(), 13, 30);

    /** Enterprise's on Linux: the classic catalog, classic icons, stripes, the native title bar and the window's menu bar. */
    public static final Presentation CLASSIC = new Presentation(true, Presentation.Icons.CLASSIC, Presentation.ToolWindows.STRIPES,
        Presentation.TitleBar.NATIVE, Presentation.Menus.WINDOW, Presentation.ToolbarSearch.FIELD, Optional.of("IBM Plex Sans"),
        Optional.of("IBM Plex Mono"), 12, 28);

    private FakePresentations() { }
}
