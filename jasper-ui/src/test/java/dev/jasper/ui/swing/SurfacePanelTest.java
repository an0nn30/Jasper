package dev.jasper.ui.swing;

import dev.jasper.ui.testsupport.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SurfacePanelTest {
    @Test void automaticSplitReservesTheBodyAndRestoresPreferredWidthUntilExplicitDrag() {
        Edt.run(()->{
            var context=KitFixtures.context();
            try {
            var leading=new JPanel();leading.setMinimumSize(new Dimension(140,0));
            var body=new JPanel();body.setMinimumSize(new Dimension(0,0));
            var split=new SurfacePanel.NativeSplit(context,leading,body,()->320,()->140);
            split.setBorder(BorderFactory.createEmptyBorder(0,8,0,8));split.setDividerSize(4);
            split.setSize(900,300);split.doLayout();assertThat(leading.getWidth()).isEqualTo(320);
            split.setSize(330,300);split.doLayout();assertThat(body.getWidth()).isEqualTo(140);
            assertThat(split.receivingWidth(330)).isEqualTo(body.getWidth());
            split.setSize(40,300);split.doLayout();assertThat(leading.getWidth()).isNotNegative();assertThat(body.getWidth()).isNotNegative();
            assertThat(body.getX()+body.getWidth()).isLessThanOrEqualTo(split.getWidth());
            split.setSize(900,300);split.doLayout();assertThat(leading.getWidth()).isEqualTo(320);
            split.setResizeWeight(.35);split.setSize(1000,300);split.doLayout();assertThat(leading.getWidth()).isEqualTo(355);
            split.setResizeWeight(0);
            split.setDividerLocation(208);split.doLayout();assertThat(leading.getWidth()).isEqualTo(200);
            split.doLayout();assertThat(leading.getWidth()).isEqualTo(200);
            split.setSize(950,300);split.doLayout();assertThat(leading.getWidth()).isEqualTo(200);
            } finally {KitViewTest.release(context);}
        });
    }

    @Test void concreteSurfacesKeepTheirDistinctHitAndOpaqueChildCornerPolicies() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try {
                for (var kind : SurfacePanel.Kind.values()) {
                    var surface = new SurfacePanel(context,kind,() -> 20);
                    surface.setBackground(Color.MAGENTA); surface.setSize(120,80);
                    var opaque = new JPanel(); opaque.setBackground(Color.GREEN);
                    surface.add(opaque); surface.doLayout();
                    assertThat(surface.isPaintingOrigin()).isTrue();
                    var image = new BufferedImage(120,80,BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics(); surface.printAll(graphics); graphics.dispose();
                    switch(kind) {
                        case POPUP -> { assertThat(surface.contains(0,0)).isFalse(); assertThat(image.getRGB(0,0)>>>24).isZero(); }
                        case CONTENT -> { assertThat(surface.contains(0,0)).isTrue(); assertThat(surface.contains(0,20)).isFalse(); assertThat(image.getRGB(0,20)).isEqualTo(Color.MAGENTA.getRGB()); }
                        case NOTICE -> { assertThat(surface.contains(0,0)).isTrue(); assertThat(image.getRGB(0,0)).isEqualTo(Color.GREEN.getRGB()); }
                    }
                    assertThat(surface.contains(60,40)).isTrue();
                    surface.remove(opaque);
                }
            } finally { KitViewTest.release(context); }
        });
    }
}
