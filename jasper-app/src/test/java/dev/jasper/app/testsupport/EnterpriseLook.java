package dev.jasper.app.testsupport;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.appearance.ThemeSelection;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.presentation.ChromeSpec;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.JasperTheme;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Enterprise as the chrome tests run it before plan 5b ships the plugin: plan 4a's Enterprise fixture kit (the classic
 * base with the kit file's tokens and 12 px body, in the platform faces), spec 5.1's chrome table, and a fixture theme
 * holding spec 7.6's named roles and override keys over Slate Cobalt (the roles the mocks show). Tests restore the
 * default look when they finish. Plan 5b replaces the kit, faces and theme with the shipped ones.
 */
public final class EnterpriseLook {
    /** Spec 5.1's {@code [chrome]} table, as the Enterprise kit file lists it. */
    public static final ChromeSpec CHROME = new ChromeSpec(ChromeSpec.TitleBar.NATIVE, ChromeSpec.MenuBar.WINDOW,
        "Workstation — {session} — {directory}", ChromeSpec.ToolWindows.STRIPES, ChromeSpec.EditorTabs.CLASSIC,
        ChromeSpec.StatusBar.CELLS, ChromeSpec.PaletteKind.RUN_WINDOW, ChromeSpec.DialogKind.CLASSIC, ChromeSpec.MotionKind.NONE);
    private static JasperTheme theme;

    private EnterpriseLook() { }

    /** Plan 4a's Enterprise fixture kit. */
    public static Kit kit() { return KitFixtures.enterprise(); }

    /** The Enterprise kit file's chrome points. */
    public static ChromeSpec chrome() { return CHROME; }

    /** The fixture theme: spec 7.6's named roles and overrides, Light and Dark, over Slate Cobalt's other roles. */
    public static synchronized JasperTheme theme() {
        if (theme != null) return theme;
        try (var in = EnterpriseLook.class.getResourceAsStream("enterprise-fixture.json")) {
            if (in == null) throw new AssertionError("enterprise-fixture.json is missing");
            theme = JasperTheme.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), "enterprise-fixture");
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
        return theme;
    }

    /** A controller showing the fixture theme's Light or Dark variant under the fixture kit. EDT. */
    public static ThemeController controller(boolean dark) {
        var shown = theme();
        return ThemeController.startup(dark, new ThemeSelection(ThemeLibrary.bundledOnly(), shown, shown, TerminalSchemeChoice.MATCH), kit(),
            failure -> { throw new AssertionError("the Enterprise fixture must install", failure); });
    }

    /** Installs Slate Cobalt under the default kit again, with today's fonts, for the tests that follow. EDT. */
    public static void restoreDefault() { ThemeTestSupport.controller(true).close(); }
}
