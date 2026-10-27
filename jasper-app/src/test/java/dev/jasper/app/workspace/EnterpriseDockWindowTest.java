package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.persistence.ToolWindowState;
import dev.jasper.app.persistence.ToolWindows;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.platform.PresentationIcon;
import dev.jasper.app.presentation.ChromeSpec;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Spec 10.7 and 16.1.4: a real window under the Enterprise kit file's projection ({@code ChromeSpec.CLASSIC} with the
 * dock group landed), its kit and its theme (plan 6a's {@link EnterpriseLook} fixtures until plan 5b ships them) docks
 * the classic way.
 */
@ExtendWith(EdtTestExtension.class)
class EnterpriseDockWindowTest {
    @Test void aWindowUnderTheShippedLookDocksTheClassicWay() throws Exception {
        var kit = EnterpriseLook.kit();
        ThemeController themes = EnterpriseLook.controller(false);
        var look = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", ChromeRenderTest.LINUX);
        var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, WorkspaceChrome.of(look.workspace(), look.title()));
        try {
            var state = new ToolWindowState(ToolWindows.BUILT_IN, look.dockLayout().key());
            var model = new Contributions();
            owner.connectContributions(model, state);
            model.addAction("test.sftp.toggle", "Toggle SFTP", null, List.of("sftp"), Optional.of("alt+1"), invocation -> { });
            model.addPanel("test.sftp", "SFTP", new PresentationIcon("folder", null), Slot.LEFT_TOP, Optional.of("test.sftp.toggle"), site -> new JLabel("sftp"));
            model.addPanel("test.sessions", "Sessions", new ImageIcon(), Slot.RIGHT_TOP, site -> new JLabel("sessions"));
            // The application's Notifications panel takes the look's AppPanels (EventLogTest); a plain panel stands in here.
            model.addPanel("test.log", look.panels().notificationsTitle(), new ImageIcon(), look.panels().notificationsSlot(), site -> new JLabel("log"));
            owner.leftStripe().buttons().getFirst().doClick(0);

            assertThat(owner.stripes()).isInstanceOf(StripeSet.LabelledStripes.class);
            assertThat(owner.area().flush()).isTrue();
            assertThat(owner.leftStripe().buttons()).extracting(AbstractButton::getToolTipText).containsExactly("1: SFTP");
            assertThat(owner.bottomStrip().orElseThrow().buttons()).extracting(AbstractButton::getToolTipText).containsExactly("Event Log");
            assertThat(owner.bottomStrip().orElseThrow().isVisible()).isTrue();
            assertThat(state.look()).isEqualTo("dev.jasper.enterprise");
            assertThat(state.panel("test.sftp")).hasValueSatisfying(panel -> assertThat(panel.visible()).isTrue());
            var header = owner.area().headerOf(Slot.LEFT_TOP);
            int strip = kit.metrics().value(DesignMetrics.Role.NATIVE_TOOL_HEADER_HEIGHT) + kit.metrics().value(DesignMetrics.Role.DOCK_RULE);
            assertThat(header.getPreferredSize().height).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(strip));
            // Plan 13: the window hands the look's classic artwork to its stripes, so SFTP leads with Project's
            // toolWindowProject file.
            var label = (StripeLabels.Label) owner.leftStripe().buttons().getFirst();
            assertThat(label.icon).isNotNull();
            assertThat(StripeIconsTest.reference(label.icon, label, Color.WHITE, 2)).isEqualTo(StripeIconsTest.reference(
                new IconRegistry().stripeIcon("folder", 13).orElseThrow().icon(EnterpriseLook.theme(false), SemanticColor.TEXT_PRIMARY),
                label, Color.WHITE, 2));
            var image = MockCrops.render(owner, 1442, 902, 2);
            Files.createDirectories(MockCrops.OUT);
            ImageIO.write(image, "png", MockCrops.OUT.resolve("enterprise-window-light.png").toFile());
        } finally {
            owner.close();
            themes.close();
            EnterpriseLook.restoreDefault();
        }
    }
}
