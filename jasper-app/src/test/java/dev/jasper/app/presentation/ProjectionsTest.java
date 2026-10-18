package dev.jasper.app.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.jasper.app.dock.Slot;
import dev.jasper.ui.kit.Kit;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 9.1: each consumer's projection, the default look's by the same rules, and the fallback. */
class ProjectionsTest {
    static final Projections.Host MAC = new Projections.Host(true, true), LINUX = new Projections.Host(false, false);

    @Test void theClassicLookMapsByTheSpecTable() {
        var classic = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC);
        assertThat(classic.desktop()).isEqualTo(new DesktopFlags(false, true));
        assertThat(classic.workspace()).isEqualTo(new WorkspaceLook(WorkspaceLook.Menus.CLASSIC, WorkspaceLook.Header.CLASSIC,
            WorkspaceLook.Stripes.LABELLED, WorkspaceLook.Tabs.CLASSIC, WorkspaceLook.Status.RULED, true, WorkspaceLook.FrameStyle.FLUSH));
        assertThat(classic.shell()).isEqualTo(new ShellLook(false, true, true));
        assertThat(classic.palette()).isEqualTo(new PaletteLook(PaletteLook.View.RUN_WINDOW, PaletteLook.Home.CORNER));
        assertThat(classic.settings()).isEqualTo(new SettingsChrome(false, true, false,
            Set.of("window.native_decorations", "ui.animations.enabled", "window.rail_width"), true));
        assertThat(classic.composition()).isEqualTo(Composition.CLASSIC);
        assertThat(classic.dialogs()).isEqualTo(new DialogMapping(DialogMapping.Kind.CLASSIC, true));
        assertThat(classic.motion()).isEqualTo(Motion.NONE);
        assertThat(classic.title()).isEqualTo(new TitleFormat("{context}"));
        assertThat(classic.dockLayout()).isEqualTo(new DockLayoutKey("dev.jasper.enterprise"));
        assertThat(classic.panels()).isEqualTo(new AppPanels("Event Log", Slot.BOTTOM_LEFT));
    }

    @Test void theDefaultLookFollowsTheSameRules() {
        var mac = Projections.of(ChromeSpec.DEFAULT, false, false, "", MAC);
        assertThat(mac.desktop()).isEqualTo(DesktopFlags.STANDARD);
        assertThat(mac.workspace()).isEqualTo(new WorkspaceLook(WorkspaceLook.Menus.STANDARD, WorkspaceLook.Header.UNIFIED,
            WorkspaceLook.Stripes.RAILS, WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD));
        assertThat(mac.shell()).isEqualTo(new ShellLook(true, false, false));
        assertThat(mac.settings()).isEqualTo(new SettingsChrome(true, false, true, Set.of("window.native_decorations"), false));
        assertThat(mac.dialogs()).isEqualTo(new DialogMapping(DialogMapping.Kind.AS_REQUESTED, false));
        assertThat(mac.dockLayout().key()).isEmpty();
        assertThat(mac.panels()).isEqualTo(new AppPanels("Notifications", Slot.RIGHT_TOP));
        var linux = Projections.of(ChromeSpec.DEFAULT, false, false, "", LINUX);
        assertThat(linux.shell().unifiedTitle()).isFalse();
        assertThat(linux.settings().hiddenKeys()).isEmpty();
    }

    @Test void theFallbackIsSpecFourSevensValue() {
        var fallback = Projections.fallback(new DesktopFlags(false, true), MAC);
        assertThat(fallback.desktop()).isEqualTo(new DesktopFlags(false, true));
        assertThat(fallback.workspace().header()).isEqualTo(WorkspaceLook.Header.UNIFIED);
        assertThat(fallback.shell()).isEqualTo(new ShellLook(false, true, true));
        assertThat(fallback.settings()).isEqualTo(new SettingsChrome(false, false, true, Set.of("window.native_decorations"), false));
        assertThat(fallback.composition()).isEqualTo(Composition.ROWS);
        assertThat(fallback.dialogs()).isEqualTo(new DialogMapping(DialogMapping.Kind.AS_REQUESTED, true));
        assertThat(fallback.motion()).isEqualTo(Motion.STANDARD);
        assertThat(fallback.dockLayout().key()).isEmpty();
        assertThat(fallback.panels()).isEqualTo(new AppPanels("Notifications", Slot.RIGHT_TOP));
        assertThat(Projections.fallback(new DesktopFlags(true, true), LINUX).settings().hiddenKeys())
            .as("a native title hides the decorations switch on every platform").containsExactly("window.native_decorations");
        for (var host : new Projections.Host[] {MAC, LINUX})
            assertThat(Projections.fallback(DesktopFlags.STANDARD, host)).as("a unified title was committed: the default look's values")
                .isEqualTo(Projections.of(ChromeSpec.DEFAULT, false, false, "", host));
    }

    @Test void everyChromeSpecTheParserAcceptsBuildsEveryProjection() {
        int built = 0;
        for (var title : ChromeSpec.TitleBar.values()) for (var menu : ChromeSpec.MenuBar.values())
        for (var tools : ChromeSpec.ToolWindows.values()) for (var tabs : ChromeSpec.EditorTabs.values())
        for (var status : ChromeSpec.StatusBar.values()) for (var palette : ChromeSpec.PaletteKind.values())
        for (var dialogs : ChromeSpec.DialogKind.values()) for (var motion : ChromeSpec.MotionKind.values())
        for (String template : new String[] {"{context}", "Workstation — {session} — {directory}"}) {
            if (menu == ChromeSpec.MenuBar.WINDOW && title != ChromeSpec.TitleBar.NATIVE) continue;
            var chrome = new ChromeSpec(title, menu, template, tools, tabs, status, palette, dialogs, motion);
            for (var host : new Projections.Host[] {MAC, LINUX})
                assertThatCode(() -> Projections.of(chrome, true, true, "p", host)).doesNotThrowAnyException();
            built++;
        }
        assertThat(built).isEqualTo(3 * 2 * 2 * 2 * 2 * 2 * 2 * 2);
    }

    @Test void thePresentationOfAKitCarriesItAndFallsBackToTheDefaultKitKeepingItsDefaults() throws Exception {
        var standard = Presentation.standard(MAC);
        assertThat(standard.look()).isEqualTo("default");
        assertThat(standard.kit()).isSameAs(Kit.DEFAULT);
        assertThat(standard.provider()).isEmpty();
        var parsed = KitParserTest.parse(KitParserTest.FULL);
        var look = Presentation.of(new Presentation.Provider("dev.example.look", "Look", "0.1.0", "d1"), parsed, MAC);
        assertThat(look.look()).isEqualTo("dev.example.look");
        assertThat(look.kit()).isSameAs(parsed.kit());
        assertThat(look.defaults()).isSameAs(parsed.defaults());
        assertThat(look.faces()).isEqualTo(parsed.faces());
        assertThat(look.committedDesktop()).as("plan 6a applies the look's flags").isEqualTo(new DesktopFlags(false, true));
        var fallback = look.fallback(MAC);
        assertThat(fallback.kit()).isSameAs(Kit.DEFAULT);
        assertThat(fallback.provider()).isEmpty();
        assertThat(fallback.defaults()).as("the session keeps the defaults read at start").isSameAs(parsed.defaults());
        assertThat(fallback.projections()).isEqualTo(Projections.fallback(new DesktopFlags(false, true), MAC));
        assertThat(fallback.look()).isEqualTo("default");
    }

    /** Plan 7: the shipped kit's dock is labelled and flush, saved under its id, with Event Log. */
    @Test void theClassicDockIsLabelledAndFlush() {
        var shipped = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC);
        assertThat(shipped.workspace().stripes()).isEqualTo(WorkspaceLook.Stripes.LABELLED);
        assertThat(shipped.workspace().frames()).isEqualTo(WorkspaceLook.FrameStyle.FLUSH);
        assertThat(shipped.dockLayout()).isEqualTo(new DockLayoutKey("dev.jasper.enterprise"));
        assertThat(shipped.panels()).isEqualTo(new AppPanels("Event Log", Slot.BOTTOM_LEFT));
        var standard = Projections.of(ChromeSpec.DEFAULT, false, false, "", MAC);
        assertThat(standard.workspace().stripes()).isEqualTo(WorkspaceLook.Stripes.RAILS);
        assertThat(standard.workspace().frames()).isEqualTo(WorkspaceLook.FrameStyle.STANDARD);
        assertThat(standard.dockLayout().key()).isEmpty();
        assertThat(standard.panels()).isEqualTo(new AppPanels("Notifications", Slot.RIGHT_TOP));
        assertThat(Presentation.standard(MAC).fallback(MAC).projections().dockLayout().key()).as("the fallback").isEmpty();
    }

    /** The user's direction of 2026-10-18: labelled stripes fit their labels, so Settings hides the Panel rail width. */
    @Test void labelledStripesHideThePanelRailWidth() {
        var d = ChromeSpec.DEFAULT;
        var stripes = new ChromeSpec(d.titleBar(), d.menuBar(), d.windowTitle(), ChromeSpec.ToolWindows.STRIPES, d.editorTabs(),
            d.statusBar(), d.palette(), d.dialogs(), d.motion());
        for (var host : new Projections.Host[] {MAC, LINUX}) {
            assertThat(Projections.of(stripes, false, false, "p", host).settings().hiddenKeys()).contains("window.rail_width");
            assertThat(Projections.of(d, false, false, "", host).settings().hiddenKeys()).doesNotContain("window.rail_width");
            assertThat(Projections.fallback(DesktopFlags.STANDARD, host).settings().hiddenKeys()).doesNotContain("window.rail_width");
        }
    }

    /** Plan 9a: the kit file's Settings chrome and composition reach the projection. */
    @Test void theClassicSettingsChromeAndComposition() {
        var shipped = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC);
        assertThat(shipped.settings()).isEqualTo(new SettingsChrome(false, true, false,
            Set.of("window.native_decorations", "ui.animations.enabled", "window.rail_width"), true));
        assertThat(shipped.composition()).isEqualTo(Composition.CLASSIC);
    }

    /** Spec 9.1: the font row's copy follows from whether the kit names a UI face. */
    @Test void theFontRowCopyFollowsTheKitsUiFace() {
        assertThat(new SettingsChrome(true, false, true, Set.of(), true).fontRowCopy()).isEqualTo("Use the default font size");
        assertThat(new SettingsChrome(true, false, true, Set.of(), false).fontRowCopy()).isEqualTo("Use platform font size");
        assertThat(Projections.of(ChromeSpec.DEFAULT, false, true, "dev.example", MAC).settings().kitFontSize()).isTrue();
        assertThat(Projections.standard(MAC).settings().kitFontSize()).isFalse();
    }

    /** Plan 9a: the default look's projections, for pages and dialogs made without a presentation. */
    @Test void theStandardProjectionsAreTheDefaultLooks() {
        for (var host : new Projections.Host[] {MAC, LINUX})
            assertThat(Projections.standard(host)).isEqualTo(Projections.of(ChromeSpec.DEFAULT, false, false, "", host));
        assertThat(Projections.standard(LINUX).settings()).isEqualTo(new SettingsChrome(true, false, true, Set.of(), false));
        assertThat(Projections.standard(MAC).settings().hiddenKeys()).containsExactly("window.native_decorations");
        assertThat(Projections.standard(LINUX).composition()).isEqualTo(Composition.ROWS);
    }

    /** Plan 8b: the kit's run_window palette reaches the projection. */
    @Test void theRunWindowPaletteReachesTheProjection() {
        var classic = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC);
        assertThat(classic.palette()).isEqualTo(new PaletteLook(PaletteLook.View.RUN_WINDOW, PaletteLook.Home.CORNER));
        assertThat(Projections.of(ChromeSpec.DEFAULT, false, false, "", MAC).palette()).isEqualTo(PaletteLook.DEFAULT);
    }
}
