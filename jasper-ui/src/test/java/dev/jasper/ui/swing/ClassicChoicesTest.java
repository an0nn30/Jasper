package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 5.2's exclusive point: the classic face is a row of radios with hue swatches; PREVIEWS put a thumbnail over each. */
@SuppressWarnings("try")
class ClassicChoicesTest {
    static Nodes.Choice protocol(String selected) {
        return new Nodes.Choice("protocol", ControlState.READY, Nodes.ChoiceKind.EXCLUSIVE, List.of(
            new Option("http", "HTTP", Optional.of(Nodes.Hue.BLUE)), new Option("ftp", "FTP", Optional.of(Nodes.Hue.AMBER)),
            new Option("sftp", "SFTP", Optional.of(Nodes.Hue.GREEN)), new Option("tftp", "TFTP", Optional.of(Nodes.Hue.PURPLE))),
            selected, "Protocol", "protocol");
    }

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    static int rgb(BufferedImage image, int x, int y) { return image.getRGB(x, y) & 0xFFFFFF; }

    /**
     * Whether {@code image}'s pixel at ({@code x}, {@code y}) is {@code color} within 2 a channel. A ring's outermost pixel
     * on its middle row is about 96% covered and painted on a transparent image, so Java2D stores its colour rounded by
     * one (for example 0x1F52A6 for 0x1F52A7); a wrong role is far more than 2 away.
     */
    static boolean near(BufferedImage image, int x, int y, Color color) {
        var painted = new Color(image.getRGB(x, y), true);
        return Math.abs(painted.getRed() - color.getRed()) <= 2 && Math.abs(painted.getGreen() - color.getGreen()) <= 2
            && Math.abs(painted.getBlue() - color.getBlue()) <= 2;
    }

    @Test void theRadiosAreALeftAlignedRowWithTheirSwatches() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise()); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(protocol("ftp")), events::add)) {
                KitFixtures.capture(context, view.spec(), 420, 40, "radios");
                KitFixtures.render(view, 420, 40, 1);
                var http = find(view, "protocol.option.http", Choices.SegmentButton.class);
                var ftp = find(view, "protocol.option.ftp", Choices.SegmentButton.class);
                int ring = ThemePainting.metric(context, DesignMetrics.Role.RADIO_RING);
                assertThat(http.getX()).as("left-aligned").isZero();
                assertThat(ftp.getX()).isEqualTo(http.getWidth() + ThemePainting.metric(context, DesignMetrics.Role.RADIO_GAP));
                assertThat(ftp.getIcon().getIconWidth()).isEqualTo(ring + ThemePainting.metric(context, DesignMetrics.Role.RADIO_LABEL_GAP)
                    + ThemePainting.metric(context, DesignMetrics.Role.OPTION_SWATCH));
                var pairs = context.faces().exclusive.pairs();
                var image = paint(ftp);
                int y = (ftp.getHeight() - ring) / 2;
                // A ring's outermost pixel is antialiased against nothing: its colour is the ring's (within rounding), its alpha
                // the coverage. The dot's centre pixel is fully covered, so it is exact.
                assertThat(near(image, 0, y + ring / 2, ThemePainting.painted(context, pairs.get(1))))
                    .as("selected ring %06X", rgb(image, 0, y + ring / 2)).isTrue();
                assertThat(new Color(image.getRGB(ring / 2, y + ring / 2), true)).as("dot").isEqualTo(ThemePainting.painted(context, pairs.get(2)));
                var plain = paint(http);
                assertThat(near(plain, 0, y + ring / 2, ThemePainting.painted(context, pairs.get(3))))
                    .as("ring %06X", rgb(plain, 0, y + ring / 2)).isTrue();
                http.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Selected("protocol", Set.of("http")));
            } finally { release(context); }
        });
    }

    /** Review Focus 4: a row too narrow for its labels wraps to a second line instead of clipping one. */
    @Test void radiosWrapRatherThanClipALabel() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, new ViewSpec(protocol("ftp")), e -> {})) {
                KitFixtures.render(view, 150, 80, 1);
                for (var id : List.of("http", "ftp", "sftp", "tftp")) {
                    var option = find(view, "protocol.option." + id, Choices.SegmentButton.class);
                    assertThat(option.getWidth()).as(id).isEqualTo(option.getPreferredSize().width);
                }
                assertThat(find(view, "protocol.option.tftp", Choices.SegmentButton.class).getY()).as("a second line")
                    .isGreaterThan(find(view, "protocol.option.http", Choices.SegmentButton.class).getY());
            } finally { release(context); }
        });
    }

    static void press(JComponent option, String key) {
        var action = option.getActionMap().get("choice." + key);
        assertThat(action).as(key).isNotNull();
        action.actionPerformed(new java.awt.event.ActionEvent(option, 0, key));
    }

    static List<String> selected(List<UiEvent> events) {
        return events.stream().map(e -> ((UiEvent.Selected) e).ids().iterator().next()).toList();
    }

    /**
     * Review Focus 5 and Rulings 11: Left, Right, Home and End move and select as on the segment track; Up and Down move
     * to the same place on the next line, so on one line they stay on the focused radio.
     */
    @Test void theKeysMoveAlongTheRadios() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise()); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(protocol("ftp")), events::add)) {
                KitFixtures.render(view, 420, 40, 1);
                var ftp = find(view, "protocol.option.ftp", Choices.SegmentButton.class);
                for (String key : List.of("RIGHT", "LEFT", "UP", "DOWN", "HOME", "END")) press(ftp, key);
                assertThat(selected(events)).containsExactly("sftp", "http", "ftp", "ftp", "http", "tftp");
            } finally { release(context); }
        });
    }

    /** Rulings 11: on a wrapped row Up and Down move between the lines, the first and last wrapping round. */
    @Test void upAndDownMoveBetweenTheLinesOfAWrappedRow() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise()); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(protocol("ftp")), events::add)) {
                KitFixtures.render(view, 150, 80, 1);
                var http = find(view, "protocol.option.http", Choices.SegmentButton.class);
                var below = new ArrayList<String>();
                for (var id : List.of("ftp", "sftp", "tftp"))
                    if (find(view, "protocol.option." + id, Choices.SegmentButton.class).getY() > http.getY()
                        && find(view, "protocol.option." + id, Choices.SegmentButton.class).getX() == http.getX()) below.add(id);
                assertThat(below).as("the second line starts under the first").hasSize(1);
                var lines = new TreeSet<Integer>();
                for (var id : List.of("http", "ftp", "sftp", "tftp")) lines.add(find(view, "protocol.option." + id, Choices.SegmentButton.class).getY());
                assertThat(lines).as("two lines").hasSize(2);
                press(http, "DOWN"); press(http, "UP");
                press(find(view, "protocol.option." + below.getFirst(), Choices.SegmentButton.class), "DOWN");
                assertThat(selected(events)).containsExactly(below.getFirst(), below.getFirst(), "http");
            } finally { release(context); }
        });
    }

    @Test void theRadiosPaintTheirDeclaredPairsOverEveryPalette() {
        var palettes = new ArrayList<>(KitFixtures.themes()); palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) Edt.run(() -> {
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            try (var view = new KitView(context, new ViewSpec(protocol("ftp")), e -> {})) {
                KitFixtures.render(view, 420, 40, 1);
                var pairs = context.faces().exclusive.pairs();
                var ftp = find(view, "protocol.option.ftp", Choices.SegmentButton.class);
                int ring = ThemePainting.metric(context, DesignMetrics.Role.RADIO_RING), y = (ftp.getHeight() - ring) / 2;
                String where = theme.name() + (theme.dark() ? " Dark" : " Light");
                var image = paint(ftp);
                assertThat(near(image, 0, y + ring / 2, ThemePainting.painted(context, pairs.get(1))))
                    .as("%s ring %06X", where, rgb(image, 0, y + ring / 2)).isTrue();
                assertThat(ftp.getForeground()).as(where + " label").isEqualTo(ThemePainting.painted(context, pairs.get(0)));
            } finally { release(context); }
        });
    }

    /** Rulings 12: previews keep their size, left-aligned, the selected one in a 2 px accent frame over its radio. */
    @Test void previewsAreThumbnailsOverTheirRadios() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var palette = new Option.Palette(0xFF202124, 0xFF2A2D33, 0xFF33363C, 0xFFDFE1E5, 0xFF3D74D6);
            var choice = new Nodes.Choice("theme", ControlState.READY, Nodes.ChoiceKind.PREVIEWS, List.of(
                new Option("dark", "Dark", Optional.empty(), "", "", Optional.of(new Option.Preview(palette, Optional.empty()))),
                new Option("light", "Light", Optional.empty(), "", "", Optional.of(new Option.Preview(palette, Optional.empty())))),
                "light", "Theme", "theme");
            try (var view = new KitView(context, new ViewSpec(choice), e -> {})) {
                KitFixtures.capture(context, view.spec(), 520, 140, "previews");
                KitFixtures.render(view, 520, 140, 1);
                JButton dark = find(view, "theme.option.dark", JButton.class), light = find(view, "theme.option.light", JButton.class);
                assertThat(dark.getWidth()).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.THEME_PREVIEW_WIDTH));
                assertThat(light.getX() - dark.getX() - dark.getWidth()).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.THEME_PREVIEW_GAP));
                assertThat(new Color(paint(light).getRGB(0, 10), true)).isEqualTo(ThemePainting.painted(context, context.faces().exclusive.pairs().get(4)));
            } finally { release(context); }
        });
    }

    @Test void theDefaultFaceDeclaresTheDefaultLooksTwoRepairs() {
        assertThat(KitFaces.declared(dev.jasper.ui.kit.Kit.DEFAULT).stream().distinct().toList())
            .containsExactly(DefaultChoiceFace.SELECTED_LABEL, DefaultChoiceFace.SELECTED_CAPTION);
    }
}
