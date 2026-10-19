package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import javax.swing.Icon;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Plan 12 Rulings 10: a rich dropdown in a toolbar strip is IntelliJ's run-configuration combo under the classic buttons. */
class ClassicToolbarComboTest {
    static ViewSpec bar(String name) {
        var ready = ControlState.READY;
        return new ViewSpec(new Nodes.Stack("bar", ready, Nodes.LayoutRole.INLINE, List.of(new Nodes.Choice("bar.host", ready, Nodes.ChoiceKind.DROPDOWN,
            List.of(new Option("mac", name, Optional.empty())), "mac", "Session", "", "", Optional.empty(),
            Optional.of(new Nodes.PickerFace(name, "", "application", Nodes.Hue.NONE, Nodes.PickerDensity.COMPACT))))));
    }
    static int s(int v) { return com.formdev.flatlaf.util.UIScale.scale(v); }

    /** The {@code side}-square region of {@code image} at ({@code x}, {@code y}), as ARGB values. */
    static int[] region(BufferedImage image, int x, int y, int side) { return image.getRGB(x, y, side, side, null, 0, side); }

    /** {@code icon} painted alone over {@code ground}, as ARGB values. */
    static int[] over(Icon icon, Color ground) {
        var image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { g.setColor(ground); g.fillRect(0, 0, image.getWidth(), image.getHeight()); icon.paintIcon(null, g, 0, 0); } finally { g.dispose(); }
        return region(image, 0, 0, icon.getIconWidth());
    }

    @Test void aRichDropdownInAToolbarStripIsIntelliJsRunConfigurationCombo() {
        Edt.run(() -> {
            var theme = KitFixtures.enterpriseLight();
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            try (var view = new KitView(context, bar("Mac (192.168.1.119)"), event -> { })) {
                view.toolbarStrip(true);
                var combo = KitViewTest.find(view, "bar.host.input", Choices.ChoiceCombo.class);
                assertThat(combo.toolbar).isTrue();
                var size = combo.getPreferredSize();
                int text = ThemePainting.measureText(ThemePainting.font(context, Typography.Role.BODY), "Mac (192.168.1.119)");
                assertThat(size.height).isEqualTo(s(24));
                assertThat(size.width).isCloseTo(s(10) + s(16) + s(4) + text + s(4) + s(16) + s(7), within(1));
                combo.setSize(size);
                var image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics(); try { combo.paint(g); } finally { g.dispose(); }
                var input = theme.semantic().color(SemanticColor.SURFACE_INPUT);
                assertThat(new Color(image.getRGB(size.width - s(7) - s(16) - s(2), s(4)), true))
                    .as("the white box").isEqualTo(input);
                assertThat(new Color(image.getRGB(size.width / 2, s(1)), true).getRGB() | 0xFF000000).as("the ring")
                    .isEqualTo(KitFixtures.painted(theme, new PaintPair(SemanticColor.BORDER_CONTROL, context.kit().colors().dialogSurface(), SurfaceContrast.Guard.CONTROL)).getRGB());
                var arrow = new Color(image.getRGB(size.width - s(7) - s(8), size.height / 2), true);
                assertThat(arrow.getAlpha()).as("the 9 x 5 triangle").isGreaterThan(200);
                var icon = over(context.icons().icon("application", theme, SemanticColor.TEXT_SECONDARY, s(16)), input);
                assertThat(region(image, s(10), (size.height - s(16)) / 2, s(16))).as("IntelliJ's application icon").isEqualTo(icon);
            } finally { context.binding().close(); context.icons().close(); }
        });
    }

    @Test void aLongNameIsElidedAtTheCap() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, bar("x".repeat(400)), event -> { })) {
                view.toolbarStrip(true);
                var size = KitViewTest.find(view, "bar.host.input", Choices.ChoiceCombo.class).getPreferredSize();
                assertThat(size.height).isEqualTo(s(24));
                assertThat(size.width).isCloseTo(s(57) + s(240), within(1));
            } finally { context.binding().close(); context.icons().close(); }
        });
    }

    @Test void outsideAToolbarStripOrUnderTheDefaultFaceTheDropdownIsUnchanged() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, bar("Mac (192.168.1.119)"), event -> { })) {
                var combo = KitViewTest.find(view, "bar.host.input", Choices.ChoiceCombo.class);
                assertThat(combo.toolbar).isFalse();
                assertThat(combo.getPreferredSize().height)
                    .isEqualTo(ThemePainting.height(context, DesignMetrics.Role.PICKER_DEFAULT_HEIGHT, Typography.Role.BODY)).isEqualTo(s(22));
            } finally { context.binding().close(); context.icons().close(); }
            var plain = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(plain, bar("Mac (192.168.1.119)"), event -> { })) {
                var combo = KitViewTest.find(view, "bar.host.input", Choices.ChoiceCombo.class);
                Dimension outside = combo.getPreferredSize();
                view.toolbarStrip(true);
                combo = KitViewTest.find(view, "bar.host.input", Choices.ChoiceCombo.class);
                assertThat(combo.toolbar).as("the flag follows the strip").isTrue();
                assertThat(combo.getPreferredSize()).as("Kit.DEFAULT's face keeps its size").isEqualTo(outside);
            } finally { plain.binding().close(); plain.icons().close(); }
        });
    }
}
