package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.image.BufferedImage;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 8.2's {@code IconSize.SWATCH}: a solid square of the icon's hue at caption size. */
@SuppressWarnings("try")
class SwatchesTest {
    @Test void aSwatchIsASolidSquareOfItsHueAtCaptionSize() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            var spec = new ViewSpec(new Nodes.Icon("swatch", ControlState.READY, "file", Hue.AMBER, IconSize.SWATCH, "FTP"));
            try (var view = new KitView(context, spec, e -> {})) {
                KitFixtures.render(view, 40, 40, 1);
                var label = find(view, "swatch", JLabel.class);
                int extent = UIScale.scale(context.metrics().value(DesignMetrics.Role.ICON_CAPTION));
                assertThat(label.getIcon().getIconWidth()).isEqualTo(extent);
                assertThat(label.getIcon().getIconHeight()).isEqualTo(extent);
                var image = new BufferedImage(extent, extent, BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                try { label.getIcon().paintIcon(label, g, 0, 0); } finally { g.dispose(); }
                int amber = KitFixtures.marker(SemanticColor.HUE_AMBER).getRGB();
                for (int[] at : new int[][] {{0, 0}, {extent - 1, 0}, {0, extent - 1}, {extent - 1, extent - 1}, {extent / 2, extent / 2}})
                    assertThat(image.getRGB(at[0], at[1])).isEqualTo(amber);
                assertThat(label.getAccessibleContext().getAccessibleName()).isEqualTo("FTP");
            } finally { release(context); }
        });
    }

    @Test void aSwatchKeepsItsSquareWhenTheIconChangesSize() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(new Nodes.Icon("icon", ControlState.READY, "file", Hue.BLUE, IconSize.CONTROL, "File")), e -> {})) {
                var label = find(view, "icon", JLabel.class);
                assertThat(label.getIcon()).isNotInstanceOf(Labels.Swatch.class);
                view.update(new ViewSpec(new Nodes.Icon("icon", ControlState.READY, "file", Hue.BLUE, IconSize.SWATCH, "File")));
                assertThat(label.getIcon()).isInstanceOf(Labels.Swatch.class);
                view.update(new ViewSpec(new Nodes.Icon("icon", ControlState.READY, "file", Hue.BLUE, IconSize.CONTROL, "File")));
                assertThat(label.getIcon()).isNotInstanceOf(Labels.Swatch.class);
            } finally { release(context); }
        });
    }
}
