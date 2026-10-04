package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

/** Both stripes follow {@code window.rail_width}; the stripe layout itself is pinned by {@code ToolStripeTest}. */
class RailWidthTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private static int x(JComponent component, int at, WindowContent owner) { return SwingUtilities.convertPoint(component, at, 0, owner).x; }

    @Test void visibleStripesHaveBalancedSpacingToTheAdjacentSurfaces() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.leftStripe().render(List.of(new ToolStripe.Button("files", "Files",
                dev.jasper.app.platform.AppIcons.named("FOLDER"), Slot.LEFT_TOP, true)), List.of());
            owner.rightStripe().render(List.of(new ToolStripe.Button("log", "Log",
                dev.jasper.app.platform.AppIcons.named("FOLDER"), Slot.RIGHT_TOP, true)), List.of());
            owner.syncRailVisibility();
            var panel = new JPanel(); DockTestSupport.show(owner, Slot.LEFT_TOP, panel, 250);
            var right = new JPanel(); DockTestSupport.show(owner, Slot.RIGHT_TOP, right, 250);
            for (int width : new int[]{28, 36, 64}) {
                owner.setRailWidth(width); owner.setSize(1000, 600);
                LayoutTestSupport.layoutTree(owner);
                LayoutTestSupport.layoutTree(owner);
                int left = x(owner.leftStripe(), 0, owner);
                int surface = x(panel, 0, owner);
                assertThat(surface - left).as("no extra gutter on the icons' right").isEqualTo(owner.leftStripe().getWidth());
                assertThat(x(owner.rightStripe(), 0, owner)).as("the right stripe mirrors it").isEqualTo(x(right, right.getWidth(), owner));
                assertThat(owner.rightStripe().getWidth()).isEqualTo(owner.leftStripe().getWidth());
                var button = owner.leftStripe().buttons().getFirst();
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
            owner.setRailVisible(false); LayoutTestSupport.layoutTree(owner);
            assertThat(panel.getParent()).as("visibility does not reparent plugin content").isSameAs(originalParent);
            assertThat(x(panel, 0, owner)).isEqualTo(UIScale.scale(6));
            assertThat(x(right, right.getWidth(), owner)).isEqualTo(owner.getWidth() - UIScale.scale(6));
            owner.setRailVisible(true); LayoutTestSupport.layoutTree(owner);
            assertThat(x(panel, 0, owner)).isEqualTo(owner.leftStripe().getWidth());
            DockTestSupport.hide(owner, Slot.LEFT_TOP); DockTestSupport.hide(owner, Slot.RIGHT_TOP); LayoutTestSupport.layoutTree(owner);
            assertThat(x(owner.tabStrip(), 0, owner)).as("terminal follows the same stripe edge").isEqualTo(owner.leftStripe().getWidth());
            var bottom = new JPanel(); DockTestSupport.show(owner, Slot.BOTTOM_LEFT, bottom, 150);
            LayoutTestSupport.layoutTree(owner); LayoutTestSupport.layoutTree(owner);
            assertThat(x(bottom, 0, owner)).as("bottom panels use the same edge").isEqualTo(owner.leftStripe().getWidth());
            assertThat(x(bottom, bottom.getWidth(), owner)).as("and reach the right stripe").isEqualTo(x(owner.rightStripe(), 0, owner));
        });
    }

    @Test void theWindowAppliesALiveWidthToBothStripesAndRejectsValuesOutsideTheRange() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var changes = new AtomicInteger(); owner.onMinimumSizeChanged = changes::incrementAndGet;
            assertThat(owner.railWidth()).isEqualTo(28);
            owner.setRailWidth(48);
            assertThat(owner.railWidth()).isEqualTo(48);
            assertThat(owner.leftStripe().getPreferredSize().width).isEqualTo(UIScale.scale(48));
            assertThat(owner.rightStripe().getPreferredSize().width).isEqualTo(UIScale.scale(48));
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
