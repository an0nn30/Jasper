package dev.jasper.app.appearance;

import static dev.jasper.ui.theme.SemanticColor.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Spec 16.1.2 for the default look: each pair it repairs that public API reaches is declared with its guard and painted
 * as the guard makes it, over the theme set and the eight degenerate palettes; the three repairs it does not reach
 * (the connected dot in {@code PanelHeaders}, the status bar's configuration text and notification counts) keep
 * {@code PanelHostHeaderTest}, {@code ConfigurationStatusTest} and {@code NotificationCardTest}. The off switch, which
 * no guard repairs, is painted as authored. The default look declares no other pair for the controls it had (spec 7.3),
 * so they stay pixel-identical; SDK 0.18's Notice and Properties declare their own.
 */
@ExtendWith(EdtTestExtension.class)
class PaintedContrastTest {
    private static final ChromePainter PAINTER = new ChromePainter(Kit.DEFAULT);
    private static final DesignMetrics METRICS = Kit.DEFAULT.metrics();

    /** {@code role} as the painters lay it out: the default catalog's value at the UI scale. */
    private static int scaled(DesignMetrics.Role role) { return com.formdev.flatlaf.util.UIScale.scale(METRICS.value(role)); }

    /** Whether the painter painted {@code expected} where its pair is painted, in {@code theme}. */
    @FunctionalInterface private interface Sampler { boolean paints(GeneratedTheme theme, Color expected); }

    /** A pair the default look repairs today and the painter that paints it. */
    private record Declared(String name, PaintPair pair, Sampler painter) { }

    private static final List<Declared> DEFAULT_PAIRS = List.of(
        new Declared("title search hint", new PaintPair(TEXT_PLACEHOLDER, SURFACE_FIELD_RAISED, SurfaceContrast.Guard.INDICATOR),
            PaintedContrastTest::searchHint),
        new Declared("inactive tab", new PaintPair(SURFACE_TABSTRIP, SURFACE_TERMINAL, SurfaceContrast.Guard.apart(SurfaceContrast.TAB_LIGHTNESS)),
            PaintedContrastTest::inactiveTab),
        new Declared("running status dot", new PaintPair(STATUS_SUCCESS, SURFACE_STATUS, SurfaceContrast.Guard.TEXT),
            PaintedContrastTest::runningDot),
        new Declared("rail badge", new PaintPair(ACCENT, SURFACE_APP, SurfaceContrast.Guard.INDICATOR),
            (theme, expected) -> badge(theme, expected, false)),
        new Declared("rail error badge", new PaintPair(STATUS_DANGER, SURFACE_APP, SurfaceContrast.Guard.INDICATOR),
            (theme, expected) -> badge(theme, expected, true)),
        new Declared("connected dot", new PaintPair(STATUS_SUCCESS, SURFACE_PANEL, SurfaceContrast.Guard.INDICATOR),
            PaintedContrastTest::connectedDot),
        new Declared("selected segment label", faces().get(0), PaintedContrastTest::selectedSegment),
        new Declared("selected preview caption", faces().get(1), PaintedContrastTest::selectedPreview),
        // Plan 3b: a Notice's text on its severity's ground, and its border held apart from that ground.
        new Declared("information notice text", new PaintPair(TEXT_PRIMARY, DerivedColor.INFO_GROUND, SurfaceContrast.Guard.TEXT),
            (theme, expected) -> noticeText(theme, expected, Nodes.Severity.INFO)),
        new Declared("warning notice text", new PaintPair(TEXT_PRIMARY, DerivedColor.WARNING_GROUND, SurfaceContrast.Guard.TEXT),
            (theme, expected) -> noticeText(theme, expected, Nodes.Severity.WARNING)),
        new Declared("error notice text", new PaintPair(TEXT_PRIMARY, DerivedColor.DANGER_GROUND, SurfaceContrast.Guard.TEXT),
            (theme, expected) -> noticeText(theme, expected, Nodes.Severity.DANGER)),
        new Declared("information notice border", new PaintPair(DerivedColor.INFO_BORDER, DerivedColor.INFO_GROUND, SurfaceContrast.Guard.STRUCTURAL),
            (theme, expected) -> noticeBorder(theme, expected, Nodes.Severity.INFO)),
        new Declared("warning notice border", new PaintPair(DerivedColor.WARNING_BORDER, DerivedColor.WARNING_GROUND, SurfaceContrast.Guard.STRUCTURAL),
            (theme, expected) -> noticeBorder(theme, expected, Nodes.Severity.WARNING)),
        new Declared("error notice border", new PaintPair(DerivedColor.DANGER_BORDER, DerivedColor.DANGER_GROUND, SurfaceContrast.Guard.STRUCTURAL),
            (theme, expected) -> noticeBorder(theme, expected, Nodes.Severity.DANGER)),
        new Declared("information notice link", new PaintPair(TEXT_LINK, DerivedColor.INFO_GROUND, SurfaceContrast.Guard.TEXT),
            (theme, expected) -> noticeLink(theme, expected, Nodes.Severity.INFO)),
        new Declared("warning notice link", new PaintPair(TEXT_LINK, DerivedColor.WARNING_GROUND, SurfaceContrast.Guard.TEXT),
            (theme, expected) -> noticeLink(theme, expected, Nodes.Severity.WARNING)),
        new Declared("error notice link", new PaintPair(TEXT_LINK, DerivedColor.DANGER_GROUND, SurfaceContrast.Guard.TEXT),
            (theme, expected) -> noticeLink(theme, expected, Nodes.Severity.DANGER)),
        // Plan 3b: a property value coloured by its severity, held readable on the Properties card.
        new Declared("information property value", new PaintPair(ACCENT, SURFACE_CARD, SurfaceContrast.Guard.TEXT),
            (theme, expected) -> propertyValue(theme, expected, Nodes.Severity.INFO)),
        new Declared("warning property value", new PaintPair(STATUS_WARNING, SURFACE_CARD, SurfaceContrast.Guard.TEXT),
            (theme, expected) -> propertyValue(theme, expected, Nodes.Severity.WARNING)),
        new Declared("error property value", new PaintPair(STATUS_DANGER, SURFACE_CARD, SurfaceContrast.Guard.TEXT),
            (theme, expected) -> propertyValue(theme, expected, Nodes.Severity.DANGER)));

    /** The default look's face declarations (plan 4b): the selected segment label, then the selected preview caption. */
    private static List<PaintPair> faces() { return KitFaces.declared(Kit.DEFAULT).stream().distinct().toList(); }

    @Test void theDefaultFacesDeclareExactlyTheTwoRepairsTheDefaultLookMakes() {
        assertThat(faces()).containsExactly(new PaintPair(TEXT_PRIMARY, SEGMENT_SELECTED_BG, SurfaceContrast.Guard.TEXT),
            new PaintPair(TEXT_PRIMARY, SURFACE_CARD, SurfaceContrast.Guard.TEXT));
    }

    /** The theme set, then the degenerate palettes. */
    private static List<GeneratedTheme> palettes() {
        var palettes = new ArrayList<>(KitFixtures.themes());
        palettes.addAll(KitFixtures.degenerate());
        return palettes;
    }

    private static String name(GeneratedTheme theme) { return theme.name() + (theme.dark() ? " Dark" : " Light"); }

    @Test void eachDeclaredPairIsPaintedAsItsGuardMakesItOverEveryPalette() {
        for (var theme : palettes()) for (var declared : DEFAULT_PAIRS) {
            String where = name(theme) + ", " + declared.name();
            Color expected = SurfaceContrast.painted(theme, declared.pair());
            assertThat(declared.pair().guard().holds(expected, declared.pair().against().color(theme))).as(where + " meets its guard").isTrue();
            assertThat(declared.painter().paints(theme, expected)).as(where + " is painted " + ColorMath.hex(expected)).isTrue();
        }
    }

    @Test void theOffSwitchWhichNoGuardRepairsIsPaintedAsAuthoredOverEveryPalette() throws Exception {
        var initial = ThemeTestSupport.generated(true);
        var original = UIManager.getLookAndFeel();
        try (var binding = new ThemeBinding(new ThemePair(initial, initial), failure -> { throw new AssertionError(failure); });
             var icons = new IconRegistry()) {
            var kit = KitContext.of(Kit.DEFAULT, binding, ThemeRoute.CHROME, icons, () -> false, action -> "");
            for (boolean compact : List.of(false, true)) {
                var events = new ArrayList<UiEvent>();
                try (var view = new KitView(kit, new ViewSpec(new Nodes.Toggle("toggle", ControlState.READY, "Enabled", false, "toggle", compact)), events::add)) {
                    var button = KitTestSupport.require(view, "toggle", JButton.class);
                    for (var theme : palettes()) {
                        FlatLafInstaller.install(theme, Kit.DEFAULT); binding.publish(new ThemePair(theme, theme)); SwingUtilities.updateComponentTreeUI(view);
                        button.setSize(button.getPreferredSize()); var image = paint(button);
                        // Centers of the left knob and right track avoid antialiasing and the shadow.
                        Color knob = new Color(image.getRGB(button.getWidth() / 4, button.getHeight() / 2), true);
                        Color track = new Color(image.getRGB(button.getWidth() * 3 / 4, button.getHeight() / 2), true);
                        String where = name(theme) + " compact=" + compact;
                        assertThat(knob).as(where).isEqualTo(theme.semantic().color(TOGGLE_KNOB));
                        assertThat(track).as(where).isEqualTo(theme.semantic().color(TOGGLE_TRACK_OFF));
                        // Slate Cobalt Dark separates knob and track by more than 3:1; Light's white knob on its #C3CAD3 track is
                        // 1.65:1, and the knob's shadow carries the separation.
                        if (theme == ThemeTestSupport.generated(true)) assertThat(ColorMath.contrast(knob, track)).isGreaterThanOrEqualTo(3);
                        assertThat(button.isEnabled()).isTrue(); assertThat(button.isSelected()).isFalse();
                    }
                    assertThat(events).isEmpty(); button.doClick(0); assertThat(events).containsExactly(new UiEvent.Toggled("toggle", true));
                }
            }
        } finally { UIManager.setLookAndFeel(original); }
    }

    /**
     * Plan 8a and spec 16.1.2: the Run window paints each pair RunWindowPresentation declares as its guard makes it, on
     * the surfaces the pairs name, over Enterprise Light, the theme set and the degenerate palettes: its texts, the help
     * glyph and tile edge, the field and list box edges and the hue squares, which keep their fill and take a ring in the
     * guarded colour where the fill misses its guard. Enterprise Light paints each text as authored but the muted ones.
     */
    @Test void theRunWindowPaintsEachPairAsItsGuardMakesItOverEveryPalette() throws Exception {
        record Sampled(String name, KitView view, String id) { }
        var enterprise = KitFixtures.enterprise();
        var initial = KitFixtures.enterpriseLight();
        var original = UIManager.getLookAndFeel();
        try (var binding = new ThemeBinding(new ThemePair(initial, initial), failure -> { throw new AssertionError(failure); });
             var icons = new IconRegistry()) {
            var kit = KitContext.of(enterprise, binding, ThemeRoute.CHROME, icons, () -> false, action -> "");
            var texts = new ViewSpec(new Nodes.Stack("run", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
                runText("run.title", "Run", Nodes.TextRole.WINDOW_TITLE), runText("run.shortcut", "\u21e7\u2318P", Nodes.TextRole.CAPTION),
                runText("run.label", "Open:", Nodes.TextRole.LABEL), runText("run.value", "dustin@192.168.1.119:22", Nodes.TextRole.MONO)), ""));
            var list = new ViewSpec(new Nodes.ItemList("results", ControlState.READY, Nodes.ListKind.RESULTS, List.of(
                new ListItem("h", Optional.empty(), List.of(runText("h.title", "Matches", Nodes.TextRole.CAPTION),
                    runText("h.count", "1 of 2", Nodes.TextRole.CAPTION)), List.of(), List.of(), true),
                new ListItem("a", Optional.empty(), List.of(runText("a.title", "Mac", Nodes.TextRole.BODY)),
                    List.of(runText("a.detail", "dustin@host", Nodes.TextRole.MONO)), List.of(runText("a.tag", "K", Nodes.TextRole.CAPTION))),
                new ListItem("b", Optional.empty(), List.of(runText("b.title", "gcloud auth", Nodes.TextRole.BODY)),
                    List.of(runText("b.detail", "gcloud auth login", Nodes.TextRole.MONO)), List.of(runText("b.tag", "K", Nodes.TextRole.CAPTION))),
                new ListItem("m", Optional.empty(), List.of(runText("m.title", "More in SSH", Nodes.TextRole.DESCRIPTION)), List.of(), List.of())),
                java.util.Set.of("a"), "run", List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(), java.util.OptionalInt.of(4)));
            var help = new ViewSpec(new Nodes.Stack("run.help", ControlState.READY, Nodes.LayoutRole.PROMPT_HEADER, List.of(
                new Nodes.Icon("run.help.icon", ControlState.READY, "play.filled", Nodes.Hue.BLUE, Nodes.IconSize.PROMPT, "Run"),
                runText("run.help.text", "Type the name of a session.", Nodes.TextRole.DESCRIPTION)), ""));
            try (var textView = new KitView(kit, texts, event -> { }); var listView = new KitView(kit, list, event -> { });
                 var helpView = new KitView(kit, help, event -> { })) {
                var guarded = List.of(new Sampled("title", textView, "run.title"), new Sampled("shortcut", textView, "run.shortcut"),
                    new Sampled("label", textView, "run.label"), new Sampled("detail value", textView, "run.value"),
                    new Sampled("heading", listView, "h.title"), new Sampled("selected title", listView, "a.title"),
                    new Sampled("list text", listView, "m.title"), new Sampled("row detail", listView, "b.detail"),
                    new Sampled("count", listView, "h.count"), new Sampled("selected detail", listView, "a.detail"));
                var pairs = RunWindowPresentation.pairs(enterprise);
                assertThat(guarded).as("every declared Run window text pair is sampled").hasSameSizeAs(pairs);
                // The muted texts the mocks draw under 4.5:1 in Enterprise Light; every other text pair holds there as authored.
                var mutedInEnterprise = java.util.Set.of(RunWindowPresentation.ROW_DETAIL, RunWindowPresentation.COUNT, RunWindowPresentation.SELECTED_DETAIL);
                var window = new SurfacePanel(kit, SurfacePanel.Kind.RUN_WINDOW, () -> 24, () -> 40);
                var box = new SurfacePanel(kit, SurfacePanel.Kind.BOX);
                var field = new SurfacePanel(kit, SurfacePanel.Kind.FIELD);
                field.focusShown(false);
                var themes = new ArrayList<GeneratedTheme>(List.of(initial));
                themes.addAll(palettes());
                for (var theme : themes) {
                    FlatLafInstaller.install(theme, enterprise); binding.publish(new ThemePair(theme, theme));
                    for (var view : List.of(textView, listView, helpView)) {
                        SwingUtilities.updateComponentTreeUI(view);
                        // Re-reconciles under this theme, so every control refreshes its colours.
                        view.runWindow(false); view.runWindow(true);
                    }
                    var rendered = KitFixtures.render(listView, 436, 100, 1);
                    String where = name(theme);
                    // The surfaces the pairs name are the ones painted under them.
                    var frame = surface(window, 462, 427);
                    assertThat(frame.getRGB(200, 12)).as(where + ", title band").isEqualTo(DerivedColor.SURFACE_HEADER_ACTIVE.color(theme).getRGB());
                    assertThat(frame.getRGB(200, 200)).as(where + ", dialog surface").isEqualTo(enterprise.colors().dialogSurface().color(theme).getRGB());
                    var boxImage = surface(box, 436, 208);
                    assertThat(boxImage.getRGB(100, 100)).as(where + ", list box").isEqualTo(theme.semantic().color(SURFACE_CARD).getRGB());
                    var fieldImage = surface(field, 392, 26);
                    assertThat(fieldImage.getRGB(100, 13)).as(where + ", field box").isEqualTo(theme.semantic().color(SURFACE_INPUT).getRGB());
                    // The edges, control boundaries on what they stand on.
                    var fieldEdge = RunWindowPresentation.FIELD_EDGE;
                    var boxEdge = RunWindowPresentation.boxEdge(enterprise);
                    assertThat(RunWindowPresentation.edges(enterprise)).containsExactly(fieldEdge, boxEdge);
                    for (var line : List.of(fieldEdge, boxEdge))
                        assertThat(line.guard().holds(SurfaceContrast.painted(theme, line), line.against().color(theme))).as(where + ", " + line + " meets its guard").isTrue();
                    int ring = com.formdev.flatlaf.util.UIScale.scale(enterprise.metrics().value(DesignMetrics.Role.FOCUS_RING));
                    assertThat(fieldImage.getRGB(200, ring)).as(where + ", the field box's edge").isEqualTo(SurfaceContrast.painted(theme, fieldEdge).getRGB());
                    assertThat(boxImage.getRGB(0, 100)).as(where + ", the list box's edge").isEqualTo(SurfaceContrast.painted(theme, boxEdge).getRGB());
                    assertThat(rendered.getRGB(200, 5)).as(where + ", heading band").isEqualTo(DerivedColor.SURFACE_TABLE_HEADER.color(theme).getRGB());
                    assertThat(rendered.getRGB(300, 19 + 10)).as(where + ", selected row").isEqualTo(theme.semantic().color(ACCENT).getRGB());
                    // The help tile's edge, an indicator on the dialog surface.
                    var helpImage = KitFixtures.render(helpView, 436, 40, 1);
                    var tile = KitTestSupport.require(helpView, "run.help.tile", JComponent.class);
                    var tileAt = SwingUtilities.convertRectangle(tile.getParent(), tile.getBounds(), helpView);
                    var edge = RunWindowPresentation.tileEdge(enterprise);
                    Color painted = SurfaceContrast.painted(theme, edge);
                    assertThat(edge.guard().holds(painted, edge.against().color(theme))).as(where + ", the help tile's edge meets its guard").isTrue();
                    assertThat(helpImage.getRGB(tileAt.x + tileAt.width / 2, tileAt.y)).as(where + ", the help tile's edge").isEqualTo(painted.getRGB());
                    // The help glyph, an indicator on the accent.
                    var glyph = RunWindowPresentation.HELP_GLYPH;
                    Color ink = SurfaceContrast.painted(theme, glyph);
                    assertThat(glyph.guard().holds(ink, glyph.against().color(theme))).as(where + ", the help glyph meets its guard").isTrue();
                    assertThat(hasColor(helpImage.getSubimage(tileAt.x, tileAt.y, tileAt.width, tileAt.height), ink)).as(where + ", the help glyph is inked " + ColorMath.hex(ink)).isTrue();
                    swatches(kit, theme, where);
                    for (int i = 0; i < guarded.size(); i++) {
                        var pair = pairs.get(i);
                        var sample = guarded.get(i);
                        Color expected = SurfaceContrast.painted(theme, pair);
                        assertThat(pair.guard().holds(expected, pair.against().color(theme))).as(where + ", " + sample.name() + " meets its guard").isTrue();
                        assertThat(KitTestSupport.require(sample.view(), sample.id(), JLabel.class).getForeground())
                            .as(where + ", " + sample.name() + " is painted " + ColorMath.hex(expected)).isEqualTo(expected);
                        if (theme == initial && !mutedInEnterprise.contains(pair))
                            assertThat(expected).as(where + ", " + sample.name() + " as authored").isEqualTo(pair.slot().color(theme));
                    }
                    if (theme == initial)
                        for (var pair : List.of(fieldEdge, boxEdge, glyph, edge))
                            assertThat(SurfaceContrast.painted(theme, pair)).as(where + ", " + pair + " as authored").isEqualTo(pair.slot().color(theme));
                }
            }
        } finally { UIManager.setLookAndFeel(original); }
    }

    private static Nodes.Text runText(String id, String value, Nodes.TextRole role) { return new Nodes.Text(id, ControlState.READY, value, role); }

    /**
     * The Run window's hue squares under {@code theme}, one row per hue with each selected in turn: a square keeps its
     * fill and, where the fill misses {@link RunWindowPresentation#swatch}'s guard, takes a ring in the guarded colour.
     */
    private static void swatches(KitContext kit, GeneratedTheme theme, String where) {
        var hues = List.of(Nodes.Hue.BLUE, Nodes.Hue.GREEN, Nodes.Hue.AMBER, Nodes.Hue.PURPLE, Nodes.Hue.NEUTRAL);
        int side = com.formdev.flatlaf.util.UIScale.scale(kit.metrics().value(DesignMetrics.Role.RUN_WINDOW_ROW_SIDE));
        int square = com.formdev.flatlaf.util.UIScale.scale(kit.metrics().value(DesignMetrics.Role.RUN_WINDOW_SWATCH));
        int row = com.formdev.flatlaf.util.UIScale.scale(kit.metrics().value(DesignMetrics.Role.RUN_WINDOW_ROW_HEIGHT));
        var sampled = new java.util.HashSet<PaintPair>();
        for (int chosen = 0; chosen < hues.size(); chosen++) {
            var items = new ArrayList<ListItem>();
            for (int i = 0; i < hues.size(); i++)
                items.add(new ListItem("r" + i, Optional.of(new Nodes.Icon("r" + i + ".swatch", ControlState.READY, "file", hues.get(i), Nodes.IconSize.SWATCH, "Scope")),
                    List.of(runText("r" + i + ".title", "Row", Nodes.TextRole.BODY)), List.of(), List.of()));
            var spec = new ViewSpec(new Nodes.ItemList("swatches", ControlState.READY, Nodes.ListKind.RESULTS, items, java.util.Set.of("r" + chosen), "run",
                List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(), java.util.OptionalInt.of(hues.size())));
            try (var view = new KitView(kit, spec, event -> { })) {
                view.runWindow(true);
                var image = KitFixtures.render(view, 436, row * hues.size(), 1);
                for (int i = 0; i < hues.size(); i++) {
                    boolean selected = i == chosen;
                    var pair = RunWindowPresentation.swatch(hues.get(i), selected);
                    sampled.add(pair);
                    Color fill = pair.slot().color(theme), guarded = SurfaceContrast.painted(theme, pair);
                    assertThat(pair.guard().holds(guarded, pair.against().color(theme))).as(where + ", " + pair + " meets its guard").isTrue();
                    int ground = pair.against().color(theme).getRGB(), x = side + square / 2, top = i * row;
                    // Off the selection the list is transparent: the list box under it paints surface.card.
                    while (top < (i + 1) * row && (image.getRGB(x, top) == ground || image.getRGB(x, top) >>> 24 == 0)) top++;
                    String name = where + ", the " + hues.get(i) + (selected ? " selected" : "") + " square";
                    assertThat(new Color(image.getRGB(x, top + square / 2))).as(name + "'s fill").isEqualTo(fill);
                    assertThat(new Color(image.getRGB(x, top))).as(name + "'s edge").isEqualTo(guarded);
                }
            }
        }
        assertThat(sampled).as(where + ", every hue square pair is sampled").containsExactlyInAnyOrderElementsOf(RunWindowPresentation.swatches());
    }

    /** {@code panel} painted at {@code width} by {@code height}. */
    private static BufferedImage surface(SurfacePanel panel, int width, int height) {
        panel.setSize(width, height);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { panel.paint(graphics); } finally { graphics.dispose(); }
        return image;
    }

    /** Spec 10.7 and 16.1.2: the classic dock chrome paints each declared pair as its guard makes it, over every palette. */
    @Test void theClassicDockPaintsEachPairAsItsGuardMakesItOverEveryPalette() {
        var painter = new ChromePainter(Kit.CLASSIC);
        var pairs = ChromePainter.DOCK_PAIRS;
        var declared = List.of(
            new Declared("dock rule", pairs.get(0), (theme, expected) -> painter.dockRule(theme).equals(expected)
                && strip(painter, theme, false).getRGB(5, 24) == expected.getRGB() && strip(painter, theme, true).getRGB(5, 24) == expected.getRGB()),
            new Declared("stripe label", pairs.get(1), (theme, expected) -> painter.stripeLabel(theme, false).equals(expected)),
            new Declared("open stripe label", pairs.get(2), (theme, expected) -> painter.stripeLabel(theme, true).equals(expected)),
            new Declared("accent count pill", pairs.get(3), (theme, expected) -> pill(painter, theme, expected, false)),
            new Declared("error count pill", pairs.get(4), (theme, expected) -> pill(painter, theme, expected, true)),
            new Declared("header title", pairs.get(5), (theme, expected) -> painter.headerTitle(theme, false).equals(expected)),
            new Declared("focused header title", pairs.get(6), (theme, expected) -> painter.headerTitle(theme, true).equals(expected)),
            new Declared("header detail", pairs.get(7), (theme, expected) -> painter.headerDetail(theme, false, false).equals(expected)),
            new Declared("focused header detail", pairs.get(8), (theme, expected) -> painter.headerDetail(theme, true, false).equals(expected)),
            new Declared("header count", pairs.get(9), (theme, expected) -> painter.headerDetail(theme, false, true).equals(expected)),
            new Declared("focused header count", pairs.get(10), (theme, expected) -> painter.headerDetail(theme, true, true).equals(expected)),
            new Declared("header connected dot", pairs.get(11), (theme, expected) -> dot(painter, theme, false).getRGB(9, 9) == expected.getRGB()),
            new Declared("focused header connected dot", pairs.get(12), (theme, expected) -> dot(painter, theme, true).getRGB(9, 9) == expected.getRGB()));
        assertThat(declared).extracting(Declared::pair).as("every declared dock pair is sampled").containsExactlyElementsOf(pairs);
        for (var theme : palettes()) {
            assertThat(strip(painter, theme, true).getRGB(5, 5)).as(name(theme) + " focused strip").isEqualTo(painter.headerSurface(theme, true).getRGB());
            for (var each : declared) {
                String where = name(theme) + ", " + each.name();
                Color expected = SurfaceContrast.painted(theme, each.pair());
                assertThat(each.pair().guard().holds(expected, each.pair().against().color(theme))).as(where + " meets its guard").isTrue();
                assertThat(each.painter().paints(theme, expected)).as(where + " is painted " + ColorMath.hex(expected)).isTrue();
            }
        }
    }

    /** A 40 x 25 header strip: 24 px of surface over its 1 px rule. */
    private static BufferedImage strip(ChromePainter painter, GeneratedTheme theme, boolean focused) {
        var image = new BufferedImage(40, 25, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { painter.headerStrip(graphics, 40, 25, theme, focused); } finally { graphics.dispose(); }
        return image;
    }

    /** A count pill at four times its size, so its text has fully covered pixels. */
    private static boolean pill(ChromePainter painter, GeneratedTheme theme, Color expected, boolean error) {
        var image = new BufferedImage(160, 80, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { graphics.scale(4, 4); painter.countPill(graphics, 2, 2, "8", theme, error); } finally { graphics.dispose(); }
        return anywhere(image, expected);
    }

    /** Whether any pixel of {@code image} is exactly {@code color}; {@code hasColor} scans only the right half, the off switch's. */
    private static boolean anywhere(BufferedImage image, Color color) {
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) if (image.getRGB(x, y) == color.getRGB()) return true;
        return false;
    }

    /** A 14 px connected dot at (2, 2). */
    private static BufferedImage dot(ChromePainter painter, GeneratedTheme theme, boolean focused) {
        var image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { painter.headerDot(graphics, 2, 2, 14, theme, focused); } finally { graphics.dispose(); }
        return image;
    }

    private static boolean searchHint(GeneratedTheme theme, Color expected) {
        var blank = new Icon() {
            public int getIconWidth() { return 16; } public int getIconHeight() { return 16; }
            public void paintIcon(Component c, Graphics g, int x, int y) { }
        };
        for (boolean hover : List.of(false, true)) {
            var component = new JPanel(); component.setSize(300, 32);
            var image = new BufferedImage(300, 32, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try { PAINTER.search(component, graphics, theme, hover, false, "", "MMMM", blank, null); } finally { graphics.dispose(); }
            if (!hasColor(image, expected)) return false;
        }
        return true;
    }

    private static boolean inactiveTab(GeneratedTheme theme, Color expected) {
        var strip = new JPanel(); strip.setSize(300, 28);
        var image = new BufferedImage(300, 28, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { PAINTER.tabStrip(strip, graphics, theme, Color.BLACK, List.of(new Rectangle(0, 0, 100, 28), new Rectangle(100, 0, 100, 28)), 1, -1, true); }
        finally { graphics.dispose(); }
        return image.getRGB(50, 10) == expected.getRGB();
    }

    private static boolean runningDot(GeneratedTheme theme, Color expected) {
        var bar = new JPanel(); bar.setSize(120, 24);
        var image = new BufferedImage(120, 24, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { PAINTER.statusDot(bar, graphics, theme, true); } finally { graphics.dispose(); }
        int size = scaled(DesignMetrics.Role.STATUS_RUNNING_DOT);
        return image.getRGB(scaled(DesignMetrics.Role.STATUS_GAP) + size / 2, (24 - size) / 2 + size / 2) == expected.getRGB();
    }

    private static boolean badge(GeneratedTheme theme, Color expected, boolean error) {
        var image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { PAINTER.badge(graphics, 10, 10, 16, theme, error); } finally { graphics.dispose(); }
        int dot = scaled(DesignMetrics.Role.RAIL_BADGE_DOT);
        return image.getRGB(10 + 16 - dot + dot / 2, 10 + dot / 2) == expected.getRGB();
    }

    private static boolean connectedDot(GeneratedTheme theme, Color expected) {
        var image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { PAINTER.connectedDot(graphics, 4, 4, 8, theme); } finally { graphics.dispose(); }
        return image.getRGB(8, 8) == expected.getRGB();
    }

    private static boolean selectedSegment(GeneratedTheme theme, Color expected) {
        var context = KitFixtures.context(theme);
        var choice = new Nodes.Choice("choice", ControlState.READY, Nodes.ChoiceKind.EXCLUSIVE,
            List.of(new Option("a", "Alpha", Optional.empty()), new Option("b", "Beta", Optional.empty())), "b", "Protocol", "choose");
        try (var view = new KitView(context, new ViewSpec(choice), event -> { })) {
            KitFixtures.render(view, 400, 80, 1);
            return KitTestSupport.require(view, "choice.option.b", JButton.class).getForeground().equals(expected);
        } finally { context.icons().close(); context.binding().close(); }
    }

    /** The preview tiles' selected caption: its most opaque ink below the tile, painted over nothing. */
    private static boolean selectedPreview(GeneratedTheme theme, Color expected) {
        var context = KitFixtures.context(theme);
        var choice = new Nodes.Choice("theme", ControlState.READY, Nodes.ChoiceKind.PREVIEWS,
            List.of(new Option("a", "Alpha", Optional.empty()), new Option("b", "Beta", Optional.empty())), "a", "Theme", "choose");
        try (var view = new KitView(context, new ViewSpec(choice), event -> { })) {
            KitFixtures.render(view, 400, 200, 1);
            var image = paint(KitTestSupport.require(view, "theme.option.a", JButton.class));
            int strongest = 0, ink = 0;
            for (int y = Math.min(scaled(DesignMetrics.Role.THEME_PREVIEW_HEIGHT), image.getHeight()); y < image.getHeight(); y++)
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    if (argb >>> 24 > strongest) { strongest = argb >>> 24; ink = argb & 0xFFFFFF; }
                }
            return strongest > 0 && ink == (expected.getRGB() & 0xFFFFFF);
        } finally { context.icons().close(); context.binding().close(); }
    }

    /** A notice of {@code severity}: its text label's colour. */
    private static boolean noticeText(GeneratedTheme theme, Color expected, Nodes.Severity severity) {
        return withNotice(theme, severity, view -> KitTestSupport.require(view, "notice.text", JLabel.class).getForeground().equals(expected));
    }

    /** The middle of a notice's top edge, where its border is drawn over its ground. */
    private static boolean noticeBorder(GeneratedTheme theme, Color expected, Nodes.Severity severity) {
        return withNotice(theme, severity, view -> {
            var surface = KitTestSupport.require(view, "notice", JComponent.class);
            return paint(surface).getRGB(surface.getWidth() / 2, 0) == expected.getRGB();
        });
    }

    /** A notice of {@code severity} with a LINK action: the link's resting colour. */
    private static boolean noticeLink(GeneratedTheme theme, Color expected, Nodes.Severity severity) {
        var details = new Nodes.Button("details", ControlState.READY, "Details", "", Nodes.ButtonKind.LINK, "details", "");
        return withNotice(theme, severity, List.of(details), view -> KitTestSupport.require(view, "details", JButton.class).getForeground().equals(expected));
    }

    private static boolean withNotice(GeneratedTheme theme, Nodes.Severity severity, java.util.function.Predicate<KitView> check) {
        return withNotice(theme, severity, List.of(), check);
    }

    private static boolean withNotice(GeneratedTheme theme, Nodes.Severity severity, List<Nodes.Button> actions, java.util.function.Predicate<KitView> check) {
        var context = KitFixtures.context(theme);
        var spec = new ViewSpec(new Nodes.Stack("root", ControlState.READY, Nodes.LayoutRole.VERTICAL,
            List.of(new Nodes.Notice("notice", ControlState.READY, severity, "Plain FTP sends the password unencrypted", actions))));
        try (var view = new KitView(context, spec, event -> { })) {
            KitFixtures.render(view, 400, 80, 1);
            return check.test(view);
        } finally { context.icons().close(); context.binding().close(); }
    }

    /** A Properties row whose value has {@code severity}: the value label's colour. */
    private static boolean propertyValue(GeneratedTheme theme, Color expected, Nodes.Severity severity) {
        var context = KitFixtures.context(theme);
        var spec = new ViewSpec(new Nodes.Properties("properties", ControlState.READY, "",
            List.of(new Nodes.PropertyRow("Credentials", "Vault locked", false, Optional.of(severity)))));
        try (var view = new KitView(context, spec, event -> { })) {
            KitFixtures.render(view, 320, 80, 1);
            return KitTestSupport.require(view, "properties.value.0", JLabel.class).getForeground().equals(expected);
        } finally { context.icons().close(); context.binding().close(); }
    }

    private static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics(); try { component.paint(graphics); } finally { graphics.dispose(); } return image;
    }

    private static boolean hasColor(BufferedImage image, Color color) {
        for (int y = 1; y < image.getHeight() - 1; y++) for (int x = image.getWidth() / 2; x < image.getWidth() - 2; x++)
            if (image.getRGB(x, y) == color.getRGB()) return true;
        return false;
    }

    /** Spec 16.1.2 for the classic faces (plan 4c Rulings 17): every pair they declare meets its guard as painted, everywhere. */
    @Test void everyClassicPairMeetsItsGuardOverEveryPaletteAndEnterpriseLight() {
        var palettes = palettes();
        palettes.add(KitFixtures.enterpriseLight());
        for (var kit : List.of(Kit.CLASSIC, KitFixtures.enterprise()))
            for (var pair : KitFaces.declared(kit).stream().distinct().toList())
                for (var theme : palettes) {
                    Color painted = SurfaceContrast.painted(theme, pair);
                    assertThat(pair.guard().holds(painted, pair.against().color(theme)))
                        .as("%s, %s: %s on %s", kit.id(), name(theme), pair.slot().key(), pair.against().key()).isTrue();
                }
    }
}
