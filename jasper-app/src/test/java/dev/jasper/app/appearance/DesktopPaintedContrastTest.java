package dev.jasper.app.appearance;

import dev.jasper.app.platform.DesktopEnvironment;
import dev.jasper.app.platform.DesktopObservation;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.model.*;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/** Real faces under FlatLaf, with glyph interiors sampled at 4x and their actual painted backgrounds. */
@ExtendWith(EdtTestExtension.class)
class DesktopPaintedContrastTest {
    @Test void enabledButtonsNoticesAndTagsMeetTargetsAsPaintedInBothKits() throws Exception {
        var previous = UIManager.getLookAndFeel();
        try {
            for (boolean dark : List.of(false, true)) for (boolean high : List.of(false, true))
                for (boolean nativePalette : List.of(false, true)) for (var kit : List.of(Kit.DEFAULT, Kit.CLASSIC)) {
                    var theme = theme(dark, high, nativePalette);
                    FlatLafInstaller.install(theme, kit);
                    var context = KitFixtures.context(theme, kit);
                    var nodes = new java.util.ArrayList<Nodes.Node>();
                    for (var kind : List.of(Nodes.ButtonKind.PRIMARY, Nodes.ButtonKind.SECONDARY))
                        nodes.add(new Nodes.Button(kind.name(), ControlState.READY, "MMMM", "", kind, "action", ""));
                    for (var hue : List.of(Nodes.Hue.BLUE, Nodes.Hue.GREEN, Nodes.Hue.AMBER, Nodes.Hue.PURPLE, Nodes.Hue.NEUTRAL))
                        nodes.add(new Nodes.Chip(hue.name(), ControlState.READY, "MMMM", hue));
                    for (var severity : List.of(Nodes.Severity.INFO, Nodes.Severity.WARNING, Nodes.Severity.DANGER))
                        nodes.add(new Nodes.Notice(severity.name(), ControlState.READY, severity, "MMMM", List.of()));
                    try (var view = new KitView(context, new ViewSpec(new Nodes.Stack("root", ControlState.READY,
                            Nodes.LayoutRole.VERTICAL, nodes)), ignored -> { })) {
                        KitFixtures.render(view, 400, 600, 1);
                        for (var kind : List.of(Nodes.ButtonKind.PRIMARY, Nodes.ButtonKind.SECONDARY)) {
                            var button = KitTestSupport.require(view, kind.name(), JButton.class);
                            button.setSize(160, 30);
                            for (boolean hover : List.of(false, true)) {
                                button.getModel().setRollover(hover);
                                var image = paint(button, theme.semantic().color(kit.colors().dialogSurface()));
                                // Button foreground is chosen by the face at paint time. Compare a labelled paint to an
                                // empty one to isolate fully covered glyph pixels from gradients, borders and shadows.
                                button.setText("");
                                var blank = paint(button, theme.semantic().color(kit.colors().dialogSurface()));
                                button.setText("MMMM");
                                assertText(image, blank, high ? 7 : 4.5, kit.id()+" "+kind+" hover="+hover);
                            }
                        }
                        for (var hue : List.of(Nodes.Hue.BLUE, Nodes.Hue.GREEN, Nodes.Hue.AMBER, Nodes.Hue.PURPLE, Nodes.Hue.NEUTRAL)) {
                            var label = KitTestSupport.require(view, hue.name(), JLabel.class);
                            checkLabel(label, theme.semantic().color(SemanticColor.SURFACE_CARD), high, kit.id()+" "+hue);
                        }
                        for (var severity : List.of(Nodes.Severity.INFO, Nodes.Severity.WARNING, Nodes.Severity.DANGER)) {
                            var label = KitTestSupport.require(view, severity.name()+".text", JLabel.class);
                            var surface = KitTestSupport.require(view, severity.name(), JComponent.class);
                            var painted = paint(surface, theme.semantic().color(SemanticColor.SURFACE_CARD));
                            Color ground = new Color(painted.getRGB(painted.getWidth()/2, 8));
                            checkLabel(label, ground, high, kit.id()+" "+severity);
                        }
                    } finally { context.icons().close(); context.binding().close(); }
                }
        } finally { UIManager.setLookAndFeel(previous); }
    }

    @Test void inheritedPickerSearchSelectionMeetsTheFinalPaintTarget() throws Exception {
        var previous = UIManager.getLookAndFeel();
        try {
            for (boolean dark : List.of(true, false)) for (boolean high : List.of(true, false))
                for (var kit : List.of(Kit.DEFAULT, Kit.CLASSIC)) {
                    var theme = theme(dark, high, false);
                    assertThat(DesktopPaintPairs.validate(theme, high)).isEmpty();
                    FlatLafInstaller.install(theme, kit);
                    var context = KitFixtures.context(theme, kit);
                    var menu = new Nodes.PickerMenu("menu", ControlState.READY, "MMMM", "Search", List.of(),
                        "", "", "No matches", "Create", "create", "cancel");
                    try (var view = new KitView(context, new ViewSpec(menu), ignored -> { })) {
                        KitFixtures.render(view, 300, 160, 1);
                        var search = KitTestSupport.require(view, "menu.search", JTextField.class);
                        search.setSize(200, 30); search.selectAll(); search.getCaret().setSelectionVisible(true);
                        Color ink = search.getSelectedTextColor(), fill = search.getSelectionColor();
                        assertThat(ColorMath.contrast(ink, fill)).as("%s dark=%s high=%s inherited selection", kit.id(), dark, high)
                            .isGreaterThanOrEqualTo(high ? 7 : 4.5);
                        var image = paint(search, theme.semantic().color(SemanticColor.SURFACE_INPUT));
                        var pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
                        assertThat(java.util.Arrays.stream(pixels).filter(pixel -> pixel == ink.getRGB()).count()).isGreaterThan(8);
                        assertThat(java.util.Arrays.stream(pixels).filter(pixel -> pixel == fill.getRGB()).count()).isGreaterThan(8);
                        var base = JasperTheme.bundled().generated(dark);
                        assertThat(theme.terminal()).isSameAs(base.terminal());
                        assertThat(theme.editor()).isSameAs(base.editor());
                        assertThat(theme.syntax()).isEqualTo(base.syntax());
                        assertThat(theme.glass()).isSameAs(base.glass());
                    } finally { context.icons().close(); context.binding().close(); }
                }
        } finally { UIManager.setLookAndFeel(previous); }
    }

    private static void checkLabel(JLabel label, Color parent, boolean high, String where) {
        label.setSize(160, 30);
        var image = paint(label, parent);
        String text = label.getText(); label.setText("");
        var blank = paint(label, parent); label.setText(text);
        assertText(image, blank, high ? 7 : 4.5, where);
    }

    private static void assertText(BufferedImage image, BufferedImage blank, double minimum, String where) {
        // The solid glyph interior is the most frequent changed colour; antialiased fringe pixels do not express
        // the foreground's contrast. Require several interiors and check every background they actually cover.
        var counts = new java.util.HashMap<Integer, Integer>();
        for (int y=8; y<image.getHeight()-8; y++) for (int x=8; x<image.getWidth()-8; x++)
            if (image.getRGB(x,y) != blank.getRGB(x,y)) counts.merge(image.getRGB(x,y), 1, Integer::sum);
        var ink = counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
        assertThat(ink.getValue()).as(where+" glyph interiors").isGreaterThan(8);
        for (int y=8; y<image.getHeight()-8; y++) for (int x=8; x<image.getWidth()-8; x++)
            if (image.getRGB(x,y) == ink.getKey() && image.getRGB(x,y) != blank.getRGB(x,y))
                assertThat(ColorMath.contrast(new Color(ink.getKey()), new Color(blank.getRGB(x,y))))
                    .as(where+" painted text").isGreaterThanOrEqualTo(minimum);
    }

    private static BufferedImage paint(JComponent component, Color background) {
        var image = new BufferedImage(component.getWidth()*4, component.getHeight()*4, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { graphics.setColor(background); graphics.fillRect(0,0,image.getWidth(),image.getHeight()); graphics.scale(4,4); component.paint(graphics); }
        finally { graphics.dispose(); }
        return image;
    }

    private static GeneratedTheme theme(boolean dark, boolean high, boolean nativePalette) {
        var foreground = new DesktopObservation.Rgba(dark ? 1 : 0, dark ? 1 : 0, dark ? 1 : 0, 1);
        var background = new DesktopObservation.Rgba(dark ? .08 : .96, dark ? .08 : .96, dark ? .08 : .96, 1);
        var palette = new DesktopObservation.Palette(0, DesktopObservation.Source.GTK3,
            dark ? DesktopObservation.Polarity.DARK : DesktopObservation.Polarity.LIGHT,
            Map.of(DesktopObservation.Pair.WINDOW, new DesktopObservation.Colors(background, foreground)), "test", "1", "");
        var input = new DesktopObservation(new DesktopObservation.Portal(0, DesktopObservation.Polarity.UNKNOWN,
            Optional.empty(), Optional.of(high), ""), nativePalette ? Map.of(DesktopObservation.Source.GTK3, palette) : Map.of());
        return DesktopPalette.resolve(dark, DesktopPalette.SourceMode.AUTO, DesktopEnvironment.from(Map.of()), input).theme();
    }
}
