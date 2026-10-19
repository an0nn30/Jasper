package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.util.SystemInfo;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeSelection;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.presentation.ChromeSpec;
import dev.jasper.app.presentation.DesktopFlags;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.testsupport.KitFixtures;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Spec 16.1.4, as JUnit and headless: a window's chrome under the default, Enterprise and complement kits, the fallback
 * combination and a strength-2 covering array of the nine chrome points, mounted as TerminalWindow mounts it and printed
 * at 2× into jasper-app/build/chrome-renders/. Plans 6b to 9 extend {@link #check}.
 */
class ChromeRenderTest {
    static final Projections.Host HOST = new Projections.Host(SystemInfo.isMacOS, MacTitleBar.isSupported());
    static final String[] KEYS = {"title_bar", "menu_bar", "window_title", "tool_windows", "editor_tabs", "status_bar", "palette", "dialogs", "motion"};

    /**
     * One rendered look.
     *
     * @param name the image's name
     * @param themes makes the theme controller (and so the kit) the window runs under
     * @param projections the look's projections
     */
    record Case(String name, Supplier<ThemeController> themes, Projections projections) { }

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    static ThemeController slateCobalt(Kit kit) {
        return ThemeController.startup(false, ThemeSelection.slateCobalt(TerminalSchemeChoice.MATCH), kit,
            failure -> { throw new AssertionError("Slate Cobalt must install", failure); });
    }

    /** The covering array's rows, as chrome specs. */
    static List<ChromeSpec> covering() {
        try (InputStream in = Objects.requireNonNull(ChromeRenderTest.class.getResourceAsStream("chrome-covering-array.tsv"), "chrome-covering-array.tsv")) {
            var rows = new ArrayList<ChromeSpec>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] v = line.split("\t");
                rows.add(new ChromeSpec(ChromeSpec.TitleBar.valueOf(v[0].toUpperCase()), ChromeSpec.MenuBar.valueOf(v[1].toUpperCase()), v[2],
                    ChromeSpec.ToolWindows.valueOf(v[3].toUpperCase()), ChromeSpec.EditorTabs.valueOf(v[4].toUpperCase()),
                    ChromeSpec.StatusBar.valueOf(v[5].toUpperCase()), ChromeSpec.PaletteKind.valueOf(v[6].toUpperCase()),
                    ChromeSpec.DialogKind.valueOf(v[7].toUpperCase()), ChromeSpec.MotionKind.valueOf(v[8].toUpperCase())));
            }
            return rows;
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    /** Every case of spec 16.1.4 this plan renders. */
    static List<Case> cases() {
        var cases = new ArrayList<Case>();
        cases.add(new Case("default", () -> slateCobalt(Kit.DEFAULT), Projections.of(ChromeSpec.DEFAULT, false, false, "", HOST)));
        cases.add(new Case("enterprise", () -> EnterpriseLook.controller(false),
            Projections.of(EnterpriseLook.chrome(), true, true, "dev.jasper.enterprise", HOST)));
        cases.add(new Case("complement", () -> slateCobalt(KitFixtures.complement()), Projections.of(ChromeSpec.DEFAULT, true, true, "dev.example", HOST)));
        cases.add(new Case("fallback", () -> slateCobalt(Kit.DEFAULT), Projections.fallback(new DesktopFlags(false, true), HOST)));
        var rows = covering();
        for (int i = 0; i < rows.size(); i++)
            cases.add(new Case("covering-" + i, () -> EnterpriseLook.controller(false), Projections.of(rows.get(i), true, true, "dev.jasper.enterprise", HOST)));
        return cases;
    }

    /** The structure every look keeps; plans 6b to 9 add their surfaces' checks here. */
    static void check(Case look, WindowContent owner, JRootPane root, MacTitleBar bar) {
        // Plan 7 (spec 10.7): the dock the look names.
        boolean labelled = look.projections().workspace().stripes() == dev.jasper.app.presentation.WorkspaceLook.Stripes.LABELLED;
        assertThat(owner.stripes() instanceof StripeSet.LabelledStripes).as(look.name() + ": labelled stripes where the look asks for them").isEqualTo(labelled);
        assertThat(owner.bottomStrip().isPresent()).as(look.name() + ": the bottom strip").isEqualTo(labelled);
        assertThat(owner.area().flush()).as(look.name() + ": flush frames")
            .isEqualTo(look.projections().workspace().frames() == dev.jasper.app.presentation.WorkspaceLook.FrameStyle.FLUSH);
        var window = WindowLook.of(look.projections());
        assertThat(bar != null).as(look.name() + ": the unified bar where the look asks for it").isEqualTo(window.unifiedTitle());
        if (bar == null) {
            assertThat(root.getJMenuBar()).as(look.name() + ": the menus are a row of the window").isSameAs(owner.menuBar());
            int contentTop = SwingUtilities.convertPoint(owner, 0, 0, root).y;
            assertThat(contentTop).as(look.name() + ": the content meets the menu row flush").isEqualTo(root.getJMenuBar().getHeight());
            var header = owner.chrome().header();
            if (header.getParent() == owner) assertThat(header.getY()).as(look.name() + ": no gutter above the header").isZero();
        }
        var titles = WindowChromeMenusTest.titles(owner);
        if (look.projections().workspace().menus() == WorkspaceLook.Menus.CLASSIC)
            assertThat(titles).as(look.name()).contains("Window").doesNotContain("Tab");
        else assertThat(titles).as(look.name()).contains("Tab").doesNotContain("Window");
        assertThat(titles.getLast()).as(look.name() + ": Help is last").isEqualTo("Help");
        // Plan 6b: the header, tabs, status cells and run item the look names.
        var workspace = look.projections().workspace();
        assertThat(owner.chrome().composition() instanceof ClassicHeader).as(look.name() + ": the header")
            .isEqualTo(workspace.header() == WorkspaceLook.Header.CLASSIC);
        assertThat(owner.tabBar() instanceof ClassicTabs).as(look.name() + ": the tabs").isEqualTo(workspace.tabs() == WorkspaceLook.Tabs.CLASSIC);
        assertThat(owner.tabBar().component().isVisible()).as(look.name() + ": the strip always shows").isTrue();
        assertThat(owner.status().cells() instanceof RuledCells).as(look.name() + ": the status cells")
            .isEqualTo(workspace.status() == WorkspaceLook.Status.RULED);
        assertThat(owner.status().runItem() != null).as(look.name() + ": the run item").isEqualTo(workspace.runItem());
        // Spec 16.1.4: the run item and the ruled cells paint their declared pairs as this look renders them.
        var installed = dev.jasper.ui.swing.FlatLafInstaller.installedTheme();
        if (owner.status().runItem() != null)
            assertThat(WindowPairsTest.paints(owner.status().runItem(), dev.jasper.ui.theme.SurfaceContrast.painted(installed,
                WindowPairs.run(false, owner.status().cells() instanceof RuledCells))))
                .as(look.name() + ": the run item's declared pair").isTrue();
        if (owner.status().cells() instanceof RuledCells)
            assertThat(WindowPairsTest.paints(owner.status(), dev.jasper.ui.theme.SurfaceContrast.painted(installed, WindowPairs.RULE)))
                .as(look.name() + ": the ruled cells' rule pair").isTrue();
        // Plan 8b (spec 11.5 and 16.1.4): the palette the look names opens at its home; the Run window inside the dock work
        // area, clear of the bottom strip and the status bar. Its image joins the others.
        owner.installRootBindings(root);
        var palette = owner.commandPalette();
        // The card's reveal starts at no height; with animations off the print shows it settled (the Run window has none).
        palette.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 0, 0, 0));
        palette.toggle();
        dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(root);
        boolean runWindow = look.projections().palette().view() == dev.jasper.app.presentation.PaletteLook.View.RUN_WINDOW;
        assertThat(palette.isOpen()).as(look.name() + ": the palette opens").isTrue();
        assertThat(palette.view() instanceof dev.jasper.app.palette.RunWindowView).as(look.name() + ": the palette's view").isEqualTo(runWindow);
        if (runWindow) {
            var shown = palette.component();
            var overlay = (javax.swing.JComponent) shown.getParent();
            var bounds = SwingUtilities.convertRectangle(overlay, shown.getBounds(), root);
            var work = SwingUtilities.convertRectangle(overlay, owner.paletteWorkArea(overlay), root);
            assertThat(work.contains(bounds)).as(look.name() + ": the Run window " + bounds + " inside the dock work area " + work).isTrue();
            var status = owner.status();
            if (status.isVisible() && status.getHeight() > 0)
                assertThat(bounds.intersects(SwingUtilities.convertRectangle(status.getParent(), status.getBounds(), root)))
                    .as(look.name() + ": clear of the status bar").isFalse();
            owner.bottomStrip().ifPresent(strip -> assertThat(bounds.intersects(SwingUtilities.convertRectangle(strip.getParent(), strip.getBounds(), root)))
                .as(look.name() + ": clear of the bottom strip").isFalse());
        }
        MockProfile.write(MockProfile.print(root, 1200, 700), "chrome-" + look.name() + "-palette");
        palette.dismiss();
    }

    @Test void everyLookBuildsItsChromeFlush() throws Exception {
        for (Case look : cases()) {
            edt(() -> {
                ThemeController themes = look.themes().get();
                MacTitleBar bar = null;
                try {
                    var window = WindowLook.of(look.projections());
                    var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, window.chrome());
                    owner.connectContributions(WindowChromeMenusTest.model(false));
                    var root = new JRootPane();
                    bar = WindowContent.mount(root, owner, window, title -> { });
                    MockProfile.write(MockProfile.print(root, 1200, 700), "chrome-" + look.name());
                    check(look, owner, root, bar);
                } finally {
                    if (bar != null) bar.close();
                    DesktopTestSupport.closeOwnersNow();
                    themes.close();
                    EnterpriseLook.restoreDefault();
                }
            });
        }
    }

    /** Plan 9a (spec 12.1 and 16.1.4): the real Settings dialog under each look's kit and projections, its frame checked and printed. */
    @Test void everyLookBuildsItsSettingsFrame() throws Exception {
        for (Case look : cases()) {
            edt(() -> {
                ThemeController themes = look.themes().get();
                try {
                    var image = dev.jasper.app.settingsui.SettingsFrameRenders.check(look.name(),
                        () -> themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME), look.projections());
                    MockProfile.write(image, "settings-" + look.name());
                } finally {
                    DesktopTestSupport.closeOwnersNow();
                    themes.close();
                    EnterpriseLook.restoreDefault();
                }
            });
        }
    }

    @Test void theCoveringArrayHasStrengthTwoOverTheChromePoints() {
        var rows = covering();
        assertThat(rows).hasSize(8);
        assertThat(rows.getFirst()).as("row 0 is the default look").isEqualTo(ChromeSpec.DEFAULT);
        var seen = new HashSet<String>();
        for (var row : rows) {
            boolean[] values = flags(row);
            for (int i = 0; i < KEYS.length; i++) for (int j = i + 1; j < KEYS.length; j++) seen.add(i + ":" + j + ":" + values[i] + ":" + values[j]);
        }
        for (int i = 0; i < KEYS.length; i++) for (int j = i + 1; j < KEYS.length; j++) for (boolean a : new boolean[] {false, true}) for (boolean b : new boolean[] {false, true}) {
            if (i == 0 && j == 1 && !a && b) continue; // spec 5.3: window menus need a native title
            assertThat(seen).as(KEYS[i] + "=" + a + " with " + KEYS[j] + "=" + b).contains(i + ":" + j + ":" + a + ":" + b);
        }
    }

    /** A row's points as "is the Enterprise value". */
    static boolean[] flags(ChromeSpec row) {
        return new boolean[] {row.titleBar() == ChromeSpec.TitleBar.NATIVE, row.menuBar() == ChromeSpec.MenuBar.WINDOW,
            !row.windowTitle().equals("{context}"), row.toolWindows() == ChromeSpec.ToolWindows.STRIPES, row.editorTabs() == ChromeSpec.EditorTabs.CLASSIC,
            row.statusBar() == ChromeSpec.StatusBar.CELLS, row.palette() == ChromeSpec.PaletteKind.RUN_WINDOW,
            row.dialogs() == ChromeSpec.DialogKind.CLASSIC, row.motion() == ChromeSpec.MotionKind.NONE};
    }
}
