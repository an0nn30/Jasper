package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.contributions.PanelRegion;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class RailWidthTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void theRailLaysOutItsButtonsFromItsWidth() throws Exception {
        edt(() -> {
            var rail = new WindowRail();
            rail.render(List.of(new WindowRail.PanelButton("a", "A", null, PanelRegion.LEFT, false),
                new WindowRail.PanelButton("b", "B", null, PanelRegion.RIGHT, false)), List.of());
            for (int width : new int[]{28, 36, 64}) {
                rail.setRailWidth(width);
                assertThat(rail.getPreferredSize().width).as("width %d", width).isEqualTo(UIScale.scale(width));
                assertThat(rail.getMinimumSize().width).isEqualTo(UIScale.scale(width));
                rail.setSize(rail.getPreferredSize().width, UIScale.scale(400));
                rail.doLayout();
                int side = UIScale.scale(width - 6);
                var buttons = rail.buttons();
                assertThat(buttons.get(0).getBounds()).isEqualTo(new Rectangle(UIScale.scale(3), UIScale.scale(4), side, side));
                var separator = Arrays.stream(rail.getComponents()).filter(JSeparator.class::isInstance).findFirst().orElseThrow();
                assertThat(separator.getWidth()).isEqualTo(UIScale.scale(width - 14));
                assertThat(buttons.get(1).getY()).as("a button, then a separator").isEqualTo(UIScale.scale(4) + side + UIScale.scale(4) + UIScale.scale(8));
                assertThat(buttons.get(1).getWidth()).isEqualTo(side);
            }
        });
    }

    @Test void visibleRailHasBalancedSpacingToTheAdjacentSurface() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.rail().render(List.of(new WindowRail.PanelButton("files", "Files",
                dev.jasper.app.platform.AppIcons.named("FOLDER"), PanelRegion.LEFT, true)), List.of());
            owner.syncRailVisibility();
            var panel = new JPanel(); owner.regions().show(PanelRegion.LEFT, panel, 250);
            for (int width : new int[]{28, 36, 64}) {
                owner.setRailWidth(width); owner.setSize(1000, 600);
                dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
                dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
                int left = SwingUtilities.convertPoint(owner.rail(), 0, 0, owner).x;
                int surface = SwingUtilities.convertPoint(panel, 0, 0, owner).x;
                assertThat(surface - left).as("no extra gutter on the icons' right").isEqualTo(owner.rail().getWidth());
                var button = owner.rail().buttons().getFirst();
                var insets = button.getInsets();
                var view = new Rectangle(insets.left, insets.top, button.getWidth() - insets.left - insets.right,
                    button.getHeight() - insets.top - insets.bottom);
                var icon = new Rectangle(); var text = new Rectangle();
                SwingUtilities.layoutCompoundLabel(button, button.getFontMetrics(button.getFont()), button.getText(), button.getIcon(),
                    button.getVerticalAlignment(), button.getHorizontalAlignment(), button.getVerticalTextPosition(),
                    button.getHorizontalTextPosition(), view, icon, text, button.getIconTextGap());
                int iconLeft = SwingUtilities.convertPoint(button, icon.x, 0, owner).x;
                assertThat(Math.abs((iconLeft - left) - (surface - iconLeft - icon.width))).as("icon centered between window edge and panel").isLessThanOrEqualTo(1);
            }
            var originalParent = panel.getParent();
            owner.setRailVisible(false); dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
            assertThat(panel.getParent()).as("visibility does not reparent plugin content").isSameAs(originalParent);
            assertThat(SwingUtilities.convertPoint(panel, 0, 0, owner).x).isEqualTo(UIScale.scale(6));
            owner.setRailVisible(true); dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
            assertThat(SwingUtilities.convertPoint(panel, 0, 0, owner).x).isEqualTo(owner.rail().getWidth());
            owner.regions().hide(PanelRegion.LEFT); dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
            assertThat(SwingUtilities.convertPoint(owner.tabStrip(), 0, 0, owner).x).as("terminal follows the same rail edge").isEqualTo(owner.rail().getWidth());
            var bottom = new JPanel(); owner.regions().show(PanelRegion.BOTTOM, bottom, 150);
            dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner); dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
            assertThat(SwingUtilities.convertPoint(bottom, 0, 0, owner).x).as("bottom panels use the same edge").isEqualTo(owner.rail().getWidth());
        });
    }

    @Test void theWindowAppliesALiveWidthAndRejectsValuesOutsideTheRange() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var changes = new AtomicInteger(); owner.onMinimumSizeChanged = changes::incrementAndGet;
            assertThat(owner.railWidth()).isEqualTo(28);
            owner.setRailWidth(48);
            assertThat(owner.railWidth()).isEqualTo(48);
            assertThat(owner.rail().getPreferredSize().width).isEqualTo(UIScale.scale(48));
            assertThat(changes.get()).isPositive();
            for (int width : new int[]{27, 65, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                assertThatIllegalArgumentException().isThrownBy(() -> owner.setRailWidth(width));
                assertThat(owner.railWidth()).isEqualTo(48);
            }
            assertThat(content(launcher(new ArrayDeque<>())).railWidth()).isEqualTo(28);
        });
    }

    @Test void theSavedWidthAppliesOnEveryReload() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var saved = ConfigSnapshot.defaults().toBuilder().railWidth(44).build();
            owner.applyConfiguration(saved, true);
            assertThat(owner.railWidth()).isEqualTo(44);
            owner.setRailWidth(52);
            owner.applyConfiguration(saved.toBuilder().tabHeight(40).build(), true);
            assertThat(owner.railWidth()).as("the saved width is the only width").isEqualTo(44);
            owner.applyConfiguration(saved.toBuilder().tabHeight(40).railWidth(30).build(), true);
            assertThat(owner.railWidth()).isEqualTo(30);
        });
    }

    @Test void closingTheWindowIgnoresLaterWidthChanges() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setRailWidth(44);
            owner.close();
            owner.setRailWidth(60);
            assertThat(owner.railWidth()).isEqualTo(44);
        });
    }

}
