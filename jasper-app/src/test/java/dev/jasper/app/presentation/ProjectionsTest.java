package dev.jasper.app.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.jasper.app.dock.Slot;
import dev.jasper.ui.kit.Kit;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 9.1: each consumer's projection, the default look's by the same rules, the fallback and the landing gate. */
class ProjectionsTest {
    static final Projections.Host MAC = new Projections.Host(true, true), LINUX = new Projections.Host(false, false);
    static final Set<Presentation.Landed> ALL = EnumSet.allOf(Presentation.Landed.class);

    @Test void theClassicLookMapsByTheSpecTableOnceEveryPlanLanded() {
        var classic = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC, ALL);
        assertThat(classic.desktop()).isEqualTo(new DesktopFlags(false, true));
        assertThat(classic.workspace()).isEqualTo(new WorkspaceLook(WorkspaceLook.Menus.CLASSIC, WorkspaceLook.Header.CLASSIC,
            WorkspaceLook.Stripes.LABELLED, WorkspaceLook.Tabs.CLASSIC, WorkspaceLook.Status.RULED, true, WorkspaceLook.FrameStyle.FLUSH));
        assertThat(classic.shell()).isEqualTo(new ShellLook(false, true, true));
        assertThat(classic.palette()).isEqualTo(new PaletteLook(PaletteLook.View.RUN_WINDOW, PaletteLook.Home.CORNER));
        assertThat(classic.settings()).isEqualTo(new SettingsChrome(false, true, false,
            Set.of("window.native_decorations", "ui.animations.enabled"), "Use the default font size"));
        assertThat(classic.composition()).isEqualTo(Composition.CLASSIC);
        assertThat(classic.dialogs()).isEqualTo(new DialogMapping(DialogMapping.Kind.CLASSIC, true));
        assertThat(classic.motion()).isEqualTo(Motion.NONE);
        assertThat(classic.title()).isEqualTo(new TitleFormat("{context}"));
        assertThat(classic.dockLayout()).isEqualTo(new DockLayoutKey("dev.jasper.enterprise"));
        assertThat(classic.panels()).isEqualTo(new AppPanels("Event Log", Slot.BOTTOM_LEFT));
    }

    @Test void theDefaultLookFollowsTheSameRules() {
        var mac = Projections.of(ChromeSpec.DEFAULT, false, false, "", MAC, ALL);
        assertThat(mac.desktop()).isEqualTo(DesktopFlags.STANDARD);
        assertThat(mac.workspace()).isEqualTo(new WorkspaceLook(WorkspaceLook.Menus.STANDARD, WorkspaceLook.Header.UNIFIED,
            WorkspaceLook.Stripes.RAILS, WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD));
        assertThat(mac.shell()).isEqualTo(new ShellLook(true, false, false));
        assertThat(mac.settings()).isEqualTo(new SettingsChrome(true, false, true, Set.of("window.native_decorations"), "Use platform font size"));
        assertThat(mac.dialogs()).isEqualTo(new DialogMapping(DialogMapping.Kind.AS_REQUESTED, false));
        assertThat(mac.dockLayout().key()).isEmpty();
        assertThat(mac.panels()).isEqualTo(new AppPanels("Notifications", Slot.RIGHT_TOP));
        var linux = Projections.of(ChromeSpec.DEFAULT, false, false, "", LINUX, ALL);
        assertThat(linux.shell().unifiedTitle()).isFalse();
        assertThat(linux.settings().hiddenKeys()).isEmpty();
    }

    @Test void untilItsPlanLandsAChromeValueMapsToTheDefaultProjection() {
        assertThat(Presentation.LANDED).as("plan 6a lands the window chrome; 6b, 7, 8 and 9 add theirs")
            .contains(Presentation.Landed.WINDOW_CHROME).doesNotContain(Presentation.Landed.WINDOW_CONTENT);
        var shipped = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC, EnumSet.of(Presentation.Landed.WINDOW_CHROME));
        assertThat(shipped.desktop()).isEqualTo(new DesktopFlags(false, true));
        assertThat(shipped.workspace().menus()).isEqualTo(WorkspaceLook.Menus.CLASSIC);
        assertThat(shipped.shell()).isEqualTo(new ShellLook(false, true, true));
        assertThat(shipped.motion()).isEqualTo(Motion.NONE);
        assertThat(shipped.title()).isEqualTo(new TitleFormat("{context}"));
        assertThat(shipped.workspace().header()).as("spec 9.1's fallback row: the unified header under the native title")
            .isEqualTo(WorkspaceLook.Header.UNIFIED);
        assertThat(shipped.workspace().tabs()).isEqualTo(WorkspaceLook.Tabs.EQUAL);
        assertThat(shipped.workspace().status()).isEqualTo(WorkspaceLook.Status.SLASH);
        assertThat(shipped.workspace().runItem()).isFalse();
        assertThat(shipped.dialogs()).as("dialogs are window content; the native title still titles them")
            .isEqualTo(new DialogMapping(DialogMapping.Kind.AS_REQUESTED, true));
        var content = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC,
            EnumSet.of(Presentation.Landed.WINDOW_CHROME, Presentation.Landed.WINDOW_CONTENT));
        assertThat(content.workspace().header()).isEqualTo(WorkspaceLook.Header.CLASSIC);
        assertThat(content.workspace().tabs()).isEqualTo(WorkspaceLook.Tabs.CLASSIC);
        assertThat(content.workspace().status()).isEqualTo(WorkspaceLook.Status.RULED);
        assertThat(content.workspace().runItem()).isTrue();
        assertThat(content.dialogs()).isEqualTo(new DialogMapping(DialogMapping.Kind.CLASSIC, true));
        var none = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC, Set.of());
        var standard = Projections.of(ChromeSpec.DEFAULT, false, false, "", MAC, Set.of());
        assertThat(none.desktop()).as("always computed from the file").isEqualTo(new DesktopFlags(false, true));
        assertThat(new Projections(standard.desktop(), none.workspace(), none.shell(), none.palette(), none.settings(),
            none.composition(), none.dialogs(), none.motion(), none.title(), none.dockLayout(), none.panels())).isEqualTo(standard);
        var windowOnly = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC, EnumSet.of(Presentation.Landed.WINDOW_CHROME));
        assertThat(windowOnly.workspace().stripes()).as("the dock group has not landed").isEqualTo(WorkspaceLook.Stripes.RAILS);
    }

    @Test void theFallbackIsSpecFourSevensValue() {
        var fallback = Projections.fallback(new DesktopFlags(false, true), MAC);
        assertThat(fallback.desktop()).isEqualTo(new DesktopFlags(false, true));
        assertThat(fallback.workspace().header()).isEqualTo(WorkspaceLook.Header.UNIFIED);
        assertThat(fallback.shell()).isEqualTo(new ShellLook(false, true, true));
        assertThat(fallback.settings()).isEqualTo(new SettingsChrome(false, false, true, Set.of("window.native_decorations"), "Use platform font size"));
        assertThat(fallback.composition()).isEqualTo(Composition.ROWS);
        assertThat(fallback.dialogs()).isEqualTo(new DialogMapping(DialogMapping.Kind.AS_REQUESTED, true));
        assertThat(fallback.motion()).isEqualTo(Motion.STANDARD);
        assertThat(fallback.dockLayout().key()).isEmpty();
        assertThat(fallback.panels()).isEqualTo(new AppPanels("Notifications", Slot.RIGHT_TOP));
        assertThat(Projections.fallback(new DesktopFlags(true, true), LINUX).settings().hiddenKeys())
            .as("a native title hides the decorations switch on every platform").containsExactly("window.native_decorations");
        for (var host : new Projections.Host[] {MAC, LINUX})
            assertThat(Projections.fallback(DesktopFlags.STANDARD, host)).as("a unified title was committed: the default look's values")
                .isEqualTo(Projections.of(ChromeSpec.DEFAULT, false, false, "", host, Presentation.LANDED));
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
            for (var host : new Projections.Host[] {MAC, LINUX}) for (var landed : java.util.List.of(Presentation.LANDED, ALL))
                assertThatCode(() -> Projections.of(chrome, true, true, "p", host, landed)).doesNotThrowAnyException();
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
}
