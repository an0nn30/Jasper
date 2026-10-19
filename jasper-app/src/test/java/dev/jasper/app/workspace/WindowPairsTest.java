package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.swing.ClassicDialog;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.MatteBorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Spec 16.1.2 for the classic window chrome (plan 6b Rulings 17), the window part of {@code PaintedContrastTest}: each
 * pair the classic menu row, toolbar row, tab strip, ruled status cells, run item and dialog declare is painted as its
 * guard makes it, over the theme set and the eight degenerate palettes, under the default kit and the shipped
 * Enterprise kit, and the {@code border.divider} rules are painted as the role. It sits beside the painters, which are
 * package-private.
 */
class WindowPairsTest {
    static final WorkspaceChrome CLASSIC = WorkspaceChrome.of(new WorkspaceLook(WorkspaceLook.Menus.CLASSIC, WorkspaceLook.Header.CLASSIC,
        WorkspaceLook.Stripes.RAILS, WorkspaceLook.Tabs.CLASSIC, WorkspaceLook.Status.RULED, true, WorkspaceLook.FrameStyle.STANDARD,
        WorkspaceLook.Icons.NEW_UI), new TitleFormat("{context}"));

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    /** Whether the painter painted {@code expected} where its pair is painted, under the installed palette. */
    @FunctionalInterface interface Sampler { boolean paints(GeneratedTheme theme, Color expected); }

    /** A declared pair and the painter that paints it. */
    record Declared(String name, PaintPair pair, Sampler painter) { }

    static List<GeneratedTheme> palettes() {
        var palettes = new ArrayList<>(KitFixtures.themes());
        palettes.addAll(KitFixtures.degenerate());
        return palettes;
    }

    static String name(GeneratedTheme theme) { return theme.name() + (theme.dark() ? " Dark" : " Light"); }

    /** {@code component} printed at 4x, so a text's or a line's colour has fully covered pixels. */
    static BufferedImage print(JComponent component) {
        var image = new BufferedImage(Math.max(1, 4 * component.getWidth()), Math.max(1, 4 * component.getHeight()), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { graphics.scale(4, 4); component.printAll(graphics); } finally { graphics.dispose(); }
        return image;
    }

    static boolean has(BufferedImage image, Color color) {
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) if (image.getRGB(x, y) == color.getRGB()) return true;
        return false;
    }

    /** Whether {@code component}, printed at 4x, paints {@code expected} anywhere; {@code ChromeRenderTest} uses it too. */
    static boolean paints(JComponent component, Color expected) { return has(print(component), expected); }

    /** The classic chrome one kit's samplers paint, built once and repainted under each installed palette. EDT. */
    static final class Fixture {
        final ClassicTabs tabs;
        final ClassicTabs.Entry first, second;
        final JMenuBar menus = new JMenuBar();
        final ClassicToolbarButton button = new ClassicToolbarButton(new AbstractAction("Go") {
            @Override public void actionPerformed(ActionEvent event) { } }, "Go", ClassicToolbarButton.Glyph.PLUS, true, null);
        final ClassicHeader header = new ClassicHeader();
        final TitleSearchField field;
        final RunItem run, activeRun, slashRun, slashActiveRun;
        final WindowStatusBar warning, danger, idle;

        Fixture(ThemeController themes) {
            var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, CLASSIC);
            owner.currentTab().rename("Local: ~");
            owner.newTab(DesktopTestSupport.HOME);
            owner.currentTab().rename("Mac: ~");
            owner.update();
            tabs = ClassicTabsTest.tabs(owner, 400);
            first = tabs.entries().get(0); second = tabs.entries().get(1);
            field = new TitleSearchField(() -> { }, () -> "⌘K", header);
            field.setSize(300, 22);
            var open = new AbstractAction("Command Palette") { @Override public void actionPerformed(ActionEvent event) { } };
            run = new RunItem(open, () -> false);
            activeRun = new RunItem(open, () -> true);
            slashRun = new WindowStatusBar(new SlashSegments(), new RunItem(open, () -> false)).runItem();
            slashActiveRun = new WindowStatusBar(new SlashSegments(), new RunItem(open, () -> true)).runItem();
            warning = bar(ConfigDiagnostic.Severity.WARNING, true);
            danger = bar(ConfigDiagnostic.Severity.ERROR, true);
            idle = bar(null, false);
            restyle();
        }

        static WindowStatusBar bar(ConfigDiagnostic.Severity severity, boolean running) {
            var bar = new WindowStatusBar(new RuledCells(), null);
            bar.setMetadata(RuledCellsTest.local("zsh"), "80 × 24", running);
            bar.setConfiguration(RuledCellsTest.state(severity));
            return bar;
        }

        /** Every component again under the installed palette and kit. */
        void restyle() {
            new ClassicMenuBar().style(menus);
            tabs.refresh(); LayoutTestSupport.layoutTree(tabs);
            for (var bar : List.of(warning, danger, idle)) { bar.refreshTheme(); bar.setSize(600, 24); LayoutTestSupport.layoutTree(bar); }
            for (JComponent each : List.of(button, run, activeRun, slashRun, slashActiveRun)) { Dimension size = each.getPreferredSize(); each.setSize(size); }
        }

        BufferedImage search(boolean hover) {
            var image = new BufferedImage(4 * field.getWidth(), 4 * field.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try { graphics.scale(4, 4); header.paintSearch(field, graphics, hover, false); } finally { graphics.dispose(); }
            return image;
        }

        BufferedImage hovered() {
            button.getModel().setRollover(true);
            try { return print(button); } finally { button.getModel().setRollover(false); }
        }

        BufferedImage pressed() {
            button.getModel().setArmed(true); button.getModel().setPressed(true);
            try { return print(button); } finally { button.getModel().setPressed(false); button.getModel().setArmed(false); }
        }

        BufferedImage inactiveTabs() {
            tabs.setActive(false);
            try { LayoutTestSupport.layoutTree(tabs); return print(tabs); } finally { tabs.setActive(true); LayoutTestSupport.layoutTree(tabs); }
        }

        @SuppressWarnings("unchecked")
        Color menuStyle(String key) { return (Color) ((Map<String, Object>) menus.getClientProperty("FlatLaf.style")).get(key); }

        Color menuRule() { return ((MatteBorder) ((CompoundBorder) menus.getBorder()).getOutsideBorder()).getMatteColor(); }
    }

    /** The window pairs and their samplers; a tab dot's pair follows its pane's running state. */
    static List<Declared> declared(Fixture f) {
        return List.of(
            new Declared("open menu title", WindowPairs.OPEN_MENU, (theme, expected) -> f.menuStyle("selectionForeground").equals(expected)),
            new Declared("toolbar button label", WindowPairs.BUTTON_LABEL, (theme, expected) -> paints(f.button, expected)),
            new Declared("hovered toolbar button label", WindowPairs.HOVERED_BUTTON_LABEL, (theme, expected) -> has(f.hovered(), expected)),
            new Declared("pressed toolbar button label", WindowPairs.PRESSED_BUTTON_LABEL, (theme, expected) -> has(f.pressed(), expected)),
            new Declared("hovered toolbar button frame", WindowPairs.BUTTON_FRAME, (theme, expected) -> has(f.hovered(), expected)),
            new Declared("pressed toolbar button frame", WindowPairs.PRESSED_BUTTON_FRAME, (theme, expected) -> has(f.pressed(), expected)),
            new Declared("search edge", WindowPairs.SEARCH_EDGE, (theme, expected) -> has(f.search(false), expected)),
            new Declared("hovered search edge", WindowPairs.SEARCH_HOVER_EDGE, (theme, expected) -> has(f.search(true), expected)),
            new Declared("search placeholder", WindowPairs.PLACEHOLDER, (theme, expected) -> has(f.search(false), expected)),
            new Declared("keycap text", WindowPairs.KEYCAP_TEXT, (theme, expected) -> has(f.search(false), expected)),
            new Declared("tab label", WindowPairs.TAB_LABEL, (theme, expected) -> paints(f.first, expected)),
            new Declared("selected tab label", WindowPairs.SELECTED_TAB_LABEL, (theme, expected) -> paints(f.second, expected)),
            new Declared("inactive window's tab label", WindowPairs.INACTIVE_TAB_LABEL, (theme, expected) -> has(f.inactiveTabs(), expected)),
            new Declared("inactive window's selected tab label", WindowPairs.INACTIVE_SELECTED_TAB_LABEL, (theme, expected) -> has(f.inactiveTabs(), expected)),
            new Declared("tab dot", WindowPairs.tabDot(f.first.running(), false), (theme, expected) -> paints(f.first, expected)),
            new Declared("selected tab dot", WindowPairs.tabDot(f.second.running(), true), (theme, expected) -> paints(f.second, expected)),
            new Declared("tab close", WindowPairs.TAB_CLOSE, (theme, expected) -> paints(f.first, expected)),
            new Declared("selected tab close", WindowPairs.SELECTED_TAB_CLOSE, (theme, expected) -> paints(f.second, expected)),
            new Declared("tab indicator", WindowPairs.TAB_INDICATOR, (theme, expected) -> paints(f.tabs, expected)),
            new Declared("new tab plus", WindowPairs.TAB_PLUS, (theme, expected) -> paints(f.tabs.add(), expected)),
            new Declared("status rule", WindowPairs.RULE, (theme, expected) -> print(f.warning).getRGB(1200, 1) == expected.getRGB()),
            new Declared("status cell text", WindowPairs.CELL_TEXT, (theme, expected) -> paints(f.warning, expected)),
            new Declared("running session dot", WindowPairs.RUNNING_SESSION_DOT, (theme, expected) -> paints(f.warning, expected)),
            new Declared("idle session dot", WindowPairs.IDLE_SESSION_DOT, (theme, expected) -> paints(f.idle, expected)),
            new Declared("configuration warning", WindowPairs.WARNING_CELL, (theme, expected) -> f.warning.configButton().getForeground().equals(expected)
                && paints(f.warning, expected)),
            new Declared("configuration error", WindowPairs.DANGER_CELL, (theme, expected) -> f.danger.configButton().getForeground().equals(expected)
                && paints(f.danger, expected)),
            new Declared("run item", WindowPairs.RUN, (theme, expected) -> paints(f.run, expected)),
            new Declared("run item on slash segments", WindowPairs.SLASH_RUN, (theme, expected) -> paints(f.slashRun, expected)),
            new Declared("active run item", WindowPairs.ACTIVE_RUN, (theme, expected) -> paints(f.activeRun, expected)),
            new Declared("active run item on slash segments", WindowPairs.SLASH_ACTIVE_RUN, (theme, expected) -> paints(f.slashActiveRun, expected)));
    }

    /** A classic dialog over a 400 x 300 layer, at 4x, in {@code theme} under {@code kit}. */
    static BufferedImage dialog(GeneratedTheme theme, Kit kit, Nodes.Hue hue) {
        var context = KitFixtures.context(theme, kit);
        try (var dialog = new ClassicDialog(context, sized(new JPanel(), 240, 120), "Unlock Vault", hue, ClassicDialog.Placement.CENTERED)) {
            dialog.setSize(400, 300); dialog.open(); dialog.doLayout(); dialog.card().doLayout();
            return print(dialog);
        } finally { context.icons().close(); context.binding().close(); }
    }

    /** A classic dialog around a band-flagged form, at 4x: its footer band and the band's rule. */
    static BufferedImage bandDialog(GeneratedTheme theme, Kit kit) {
        var context = KitFixtures.context(theme, kit);
        var ready = dev.jasper.ui.model.ControlState.READY;
        var form = new Nodes.Form("form", ready, Nodes.FormKind.SHEET, "Add Login", java.util.Optional.empty(),
            List.of(new Nodes.Text("note", ready, "A note", Nodes.TextRole.LABEL)), java.util.Optional.empty(),
            List.of(new Nodes.Button("ok", ready, "Save", "", Nodes.ButtonKind.PRIMARY, "ok", "")), "ok", Nodes.FormSubmission.PRIMARY, "");
        try (var view = new dev.jasper.ui.swing.KitView(context, new dev.jasper.ui.model.ViewSpec(form), event -> { })) {
            view.hostTitled(true); view.dialogBand(true); view.dialogCard(true);
            try (var dialog = new ClassicDialog(context, view, "Add Login", Nodes.Hue.NONE, ClassicDialog.Placement.CENTERED)) {
                dialog.setSize(800, 600); dialog.open(); LayoutTestSupport.layoutTree(dialog);
                return print(dialog);
            }
        } finally { context.icons().close(); context.binding().close(); }
    }

    static JComponent sized(JComponent component, int width, int height) { component.setPreferredSize(new Dimension(width, height)); component.setOpaque(false); return component; }

    void sample(Supplier<ThemeController> controller, Kit kit) {
        ThemeController themes = controller.get();
        try {
            var f = new Fixture(themes);
            var declared = declared(f);
            var unshown = new HashSet<>(List.of(WindowPairs.tabDot(!f.first.running(), false), WindowPairs.tabDot(!f.second.running(), true)));
            var expectedPairs = new ArrayList<>(WindowPairs.ALL);
            expectedPairs.removeAll(unshown);
            assertThat(declared).extracting(Declared::pair).as("every declared window pair the fixture shows is sampled").containsAll(expectedPairs);
            var dialogPairs = ClassicDialog.pairs(kit.colors().dialogSurface());
            for (var theme : palettes()) {
                FlatLafInstaller.install(theme, kit);
                f.restyle();
                String palette = name(theme) + " under " + (kit == Kit.DEFAULT ? "the default kit" : "Enterprise");
                for (var each : declared) {
                    Color expected = SurfaceContrast.painted(theme, each.pair());
                    assertThat(each.pair().guard().holds(expected, each.pair().against().color(theme))).as(palette + ", " + each.name() + " meets its guard").isTrue();
                    assertThat(each.painter().paints(theme, expected)).as(palette + ", " + each.name() + " is painted " + ColorMath.hex(expected)).isTrue();
                }
                // Rulings 17: the border.divider rules are the role as the theme gives it.
                Color divider = DerivedColor.BORDER_DIVIDER.color(theme);
                assertThat(f.menuRule()).as(palette + ", the menu row's rule").isEqualTo(divider);
                assertThat(paints(f.warning, divider)).as(palette + ", the status cell rules").isTrue();
                assertThat(has(f.search(false), divider)).as(palette + ", the keycap's edge").isTrue();
                assertThat(paints(f.tabs, theme.semantic().color(dev.jasper.ui.theme.SemanticColor.BORDER_WINDOW))).as(palette + ", the tab dividers and rule").isTrue();
                // The classic dialog's frame, strip rule, title and swatches.
                var blue = dialog(theme, kit, Nodes.Hue.BLUE);
                for (var pair : List.of(ClassicDialog.frame(kit.colors().dialogSurface()), ClassicDialog.stripRule(kit.colors().dialogSurface()), ClassicDialog.STRIP_TITLE, ClassicDialog.swatch(Nodes.Hue.BLUE))) {
                    Color expected = SurfaceContrast.painted(theme, pair);
                    assertThat(pair.guard().holds(expected, pair.against().color(theme))).as(palette + ", dialog " + pair.slot() + " meets its guard").isTrue();
                    assertThat(has(blue, expected)).as(palette + ", dialog " + pair.slot() + " is painted " + ColorMath.hex(expected)).isTrue();
                }
                Color bandRule = SurfaceContrast.painted(theme, ClassicDialog.BAND_RULE);
                assertThat(ClassicDialog.BAND_RULE.guard().holds(bandRule, ClassicDialog.BAND_RULE.against().color(theme))).as(palette + ", the band's rule meets its guard").isTrue();
                assertThat(has(bandDialog(theme, kit), bandRule)).as(palette + ", the band's rule is painted " + ColorMath.hex(bandRule)).isTrue();
                for (var hue : List.of(Nodes.Hue.GREEN, Nodes.Hue.AMBER, Nodes.Hue.PURPLE, Nodes.Hue.NEUTRAL))
                    assertThat(has(dialog(theme, kit, hue), SurfaceContrast.painted(theme, ClassicDialog.swatch(hue)))).as(palette + ", the " + hue + " swatch").isTrue();
                assertThat(dialogPairs).as("the dialog declares what it paints").contains(ClassicDialog.stripRule(kit.colors().dialogSurface()), ClassicDialog.STRIP_TITLE,
                    ClassicDialog.frame(kit.colors().dialogSurface()), ClassicDialog.swatch(Nodes.Hue.GREEN), ClassicDialog.BAND_RULE);
            }
        } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
    }

    @Test void theClassicWindowChromePaintsEachPairAsItsGuardMakesItOverEveryPaletteUnderBothKits() throws Exception {
        edt(() -> sample(() -> ThemeTestSupport.controller(true), Kit.DEFAULT));
        DesktopTestSupport.closeOwners();
        edt(() -> sample(() -> EnterpriseLook.controller(false), EnterpriseLook.kit()));
    }
}
