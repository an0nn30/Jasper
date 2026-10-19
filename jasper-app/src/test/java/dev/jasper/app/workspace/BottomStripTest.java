package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.ui.icons.ChromeArtwork;
import dev.jasper.ui.swing.ChromePainter;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 10.7: the bottom strip's labels, dividers, groups and place in the window. */
@ExtendWith(EdtTestExtension.class)
class BottomStripTest {
    @Test void bottomLeftStartsAtTheStripesFillAndBottomRightEndsAtItWithDividers() throws Exception {
        try {
            EnterpriseLook.install(false);
            var kit = EnterpriseLook.kit();
            var strip = new BottomStrip(kit, ChromeArtwork.CLASSIC);
            strip.render(List.of(LabelledStripeTest.window("log", "Event Log", Slot.BOTTOM_LEFT, 0),
                LabelledStripeTest.window("transfers", "Transfers", Slot.BOTTOM_LEFT, 3),
                LabelledStripeTest.window("terminal", "Terminal", Slot.BOTTOM_RIGHT, 0),
                LabelledStripeTest.window("sftp", "SFTP", Slot.LEFT_TOP, 2)), List.of());
            int thickness = strip.getPreferredSize().height;
            assertThat(thickness).as("the mocks' 24 and the rule (Rulings 4 as ruled)").isEqualTo(25);
            int fill = new LabelledStripe(Side.LEFT, kit, ChromeArtwork.CLASSIC).getPreferredSize().width - 1;
            var image = MockCrops.render(strip, 900, thickness, 1);
            assertThat(strip.buttons()).extracting(AbstractButton::getToolTipText).containsExactly("Event Log", "3: Transfers", "Terminal");
            Rectangle log = strip.buttons().get(0).getBounds(), transfers = strip.buttons().get(1).getBounds(), terminal = strip.buttons().get(2).getBounds();
            assertThat(log.x).as("from the left stripe's fill").isEqualTo(fill);
            assertThat(log.y).as("under the top rule").isEqualTo(1);
            assertThat(transfers.x).as("after the divider").isEqualTo(log.x + log.width + 1);
            assertThat(terminal.x + terminal.width).as("to the right stripe's fill").isEqualTo(900 - fill);
            var painter = new ChromePainter(kit);
            var theme = EnterpriseLook.theme(false);
            assertThat(new Color(image.getRGB(log.x + log.width, thickness / 2))).as("a divider after each label").isEqualTo(painter.stripDivider(theme));
            assertThat(new Color(image.getRGB(terminal.x - 1, thickness / 2))).as("a divider before each right label").isEqualTo(painter.stripDivider(theme));
            assertThat(new Color(image.getRGB(450, 0))).as("the top rule").isEqualTo(painter.dockRule(theme));
            assertThat(new Color(image.getRGB(5, thickness / 2))).as("the strip's surface").isEqualTo(painter.stripeSurface(theme));
            var groups = strip.dropGroups();
            assertThat(groups).extracting(DockDropResolver.Group::slot).containsExactly(Slot.BOTTOM_LEFT, Slot.BOTTOM_RIGHT);
            assertThat(groups).extracting(DockDropResolver.Group::axis).containsOnly(DockDropResolver.Axis.HORIZONTAL);
            assertThat(groups.get(0).bounds().x + groups.get(0).bounds().width).isEqualTo(groups.get(1).bounds().x);
            assertThat(groups.get(0).buttons()).hasSize(2);
            // window.rail_width moves neither the strip's height nor its labels: the side stripes fit their labels.
            strip.setRailWidth(40);
            strip.setSize(900, strip.getPreferredSize().height);
            strip.doLayout();
            assertThat(strip.getPreferredSize().height).as("independent of window.rail_width").isEqualTo(25);
            assertThat(strip.buttons().get(0).getX()).as("still the stripes' fill").isEqualTo(fill);
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void theLabelledSetShowsTheBottomSlotsOnTheStrip() throws Exception {
        try {
            EnterpriseLook.install(false);
            var set = new StripeSet.LabelledStripes(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
            assertThat(set.of(Slot.LEFT_BOTTOM)).isSameAs(set.left());
            assertThat(set.of(Slot.RIGHT_TOP)).isSameAs(set.right());
            assertThat(set.of(Slot.BOTTOM_LEFT)).isSameAs(set.bottomStrip());
            assertThat(set.of(Slot.BOTTOM_RIGHT)).isSameAs(set.bottomStrip());
            assertThat(set.all()).containsExactly(set.left(), set.right(), set.bottomStrip());
            assertThat(new DockChrome(true, true).stripes(EnterpriseLook.kit(), ChromeArtwork.CLASSIC)).isInstanceOf(StripeSet.LabelledStripes.class);
            assertThat(DockChrome.STANDARD.stripes(EnterpriseLook.kit(), ChromeArtwork.CLASSIC)).isInstanceOf(StripeSet.IconRails.class);
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Review Focus 4: a window under labelled stripes counts the bottom strip in its height and shows it with a bottom window. */
    @Test void theStripCountsInTheWindowsMinimumSize() throws Exception {
        var themes = ThemeTestSupport.controller(false);
        var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes,
            DockChromeTest.classicDock());
        try {
            var model = new Contributions();
            owner.connectContributions(model, UiState.inMemory());
            var strip = owner.bottomStrip().orElseThrow();
            assertThat(strip.isVisible()).as("an empty strip hides").isFalse();
            int before = owner.getMinimumSize().height;
            model.addPanel("dev.x.log", "Event Log", new ImageIcon(), Slot.BOTTOM_LEFT, site -> new JLabel("log"));
            assertThat(strip.isVisible()).isTrue();
            assertThat(strip.buttons()).extracting(AbstractButton::getToolTipText).containsExactly("Event Log");
            assertThat(owner.leftStripe().buttons()).as("no foot group on the side stripes").isEmpty();
            assertThat(owner.getMinimumSize().height).isGreaterThanOrEqualTo(before + strip.getPreferredSize().height);
        } finally { owner.close(); }
    }
}
