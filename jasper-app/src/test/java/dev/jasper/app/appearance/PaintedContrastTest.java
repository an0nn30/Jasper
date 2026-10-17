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
