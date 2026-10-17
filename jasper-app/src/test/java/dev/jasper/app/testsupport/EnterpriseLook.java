package dev.jasper.app.testsupport;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.appearance.ThemeSelection;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.presentation.ChromeSpec;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.theme.JasperTheme;

/**
 * Enterprise as the chrome tests run it: the shipped kit with its IBM Plex files registered, its chrome table and the
 * shipped Enterprise theme, read from the plugin's sources ({@link EnterpriseResources}). Tests restore the default look
 * when they finish.
 */
public final class EnterpriseLook {
    /** Spec 5.1's {@code [chrome]} table, which the shipped kit file lists ({@code EnterpriseFixtureTest} holds them equal). */
    public static final ChromeSpec CHROME = new ChromeSpec(ChromeSpec.TitleBar.NATIVE, ChromeSpec.MenuBar.WINDOW,
        "Workstation — {session} — {directory}", ChromeSpec.ToolWindows.STRIPES, ChromeSpec.EditorTabs.CLASSIC,
        ChromeSpec.StatusBar.CELLS, ChromeSpec.PaletteKind.RUN_WINDOW, ChromeSpec.DialogKind.CLASSIC, ChromeSpec.MotionKind.NONE);

    private EnterpriseLook() { }

    /** The shipped Enterprise kit; its IBM Plex files are registered first. */
    public static Kit kit() { return EnterpriseResources.kit(); }

    /** The shipped kit file's chrome points. */
    public static ChromeSpec chrome() { return EnterpriseResources.chrome(); }

    /**
     * The shipped Enterprise theme, Light and Dark, with plan 6b's chrome targets (the mocks' tab strip, tab indicator,
     * scrim and {@code border.divider}).
     */
    public static JasperTheme theme() { return EnterpriseResources.theme(); }

    /** A controller showing the Enterprise theme's Light or Dark variant under the shipped kit. EDT. */
    public static ThemeController controller(boolean dark) {
        var shown = theme();
        return ThemeController.startup(dark, new ThemeSelection(ThemeLibrary.bundledOnly(), shown, shown, TerminalSchemeChoice.MATCH), kit(),
            failure -> { throw new AssertionError("the Enterprise look must install", failure); });
    }

    /** The shipped theme's Light or Dark variant. */
    public static dev.jasper.ui.theme.GeneratedTheme theme(boolean dark) { return theme().generated(dark); }

    /** Installs the theme's variant under the kit for a component render, without a controller. EDT. */
    public static void install(boolean dark) { dev.jasper.ui.swing.FlatLafInstaller.install(theme(dark), kit()); }

    /** Installs Slate Cobalt under the default kit again, with today's fonts, for the tests that follow. EDT. */
    public static void restoreDefault() { ThemeTestSupport.controller(true).close(); }
}
