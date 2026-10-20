package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeSelection;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.platform.PresentationIcon;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Spec 16.1.4 for the dock: a window under the default, the Enterprise fixture and the complement kits, with icon rails
 * and today's frames or labelled stripes and flush frames, builds and docks; the structure holds (the stripe kind and
 * labels, the bottom strip, the dividers' width, no gutter where the catalog has none, the header strips' height), and
 * each render is written under {@code jasper-app/build/dock-renders/} for inspection.
 */
@ExtendWith(EdtTestExtension.class)
class DockChromeMatrixTest {
    static final WorkspaceLook RAILS = new WorkspaceLook(WorkspaceLook.Menus.STANDARD, WorkspaceLook.Header.UNIFIED,
        WorkspaceLook.Stripes.RAILS, WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD,
        WorkspaceLook.Icons.NEW_UI);

    @Test void everyKitDocksUnderBothDockStyles() throws Exception {
        try {
            for (var kit : Map.of("default", Kit.DEFAULT, "enterprise", KitFixtures.enterprise(), "complement", KitFixtures.complement()).entrySet())
                for (var dock : List.of(RAILS, DockChromeTest.CLASSIC_DOCK)) {
                    boolean labelled = dock.stripes() == WorkspaceLook.Stripes.LABELLED;
                    String where = kit.getKey() + (labelled ? " labelled" : " rails");
                    ThemeController themes = ThemeController.startup(false, ThemeSelection.slateCobalt(TerminalSchemeChoice.MATCH), kit.getValue(),
                        failure -> { throw new AssertionError(failure); });
                    var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, WorkspaceChrome.of(dock, new dev.jasper.app.presentation.TitleFormat("{context}")));
                    try {
                        var model = new Contributions();
                        owner.connectContributions(model, UiState.inMemory());
                        model.addPanel("test.left", "Left", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("left"));
                        model.addPanel("test.right", "Right", new ImageIcon(), Slot.RIGHT_TOP, site -> new JLabel("right"));
                        model.addPanel("test.log", "Log", new ImageIcon(), Slot.BOTTOM_LEFT, site -> new JLabel("log"));
                        owner.leftStripe().buttons().getFirst().doClick(0);
                        owner.rightStripe().buttons().getFirst().doClick(0);
                        var image = MockCrops.render(owner, 1200, 800, 2);
                        Files.createDirectories(MockCrops.OUT);
                        ImageIO.write(image, "png", MockCrops.OUT.resolve("matrix-" + where.replace(' ', '-') + ".png").toFile());
                        var metrics = kit.getValue().metrics();
                        assertThat(owner.stripes() instanceof StripeSet.LabelledStripes).as(where).isEqualTo(labelled);
                        assertThat(owner.bottomStrip().isPresent()).as(where + " bottom strip").isEqualTo(labelled);
                        assertThat(owner.area().metrics().divider()).as(where + " dividers")
                            .isEqualTo(labelled ? metrics.value(DesignMetrics.Role.DOCK_RULE) : 6);
                        var frame = owner.area().content(Slot.LEFT_TOP).getParent();
                        if (metrics.value(DesignMetrics.Role.WORKSPACE_GUTTER) == 0)
                            assertThat(frame.getInsets()).as(where + " no gutter").isEqualTo(new java.awt.Insets(0, 0, 0, 0));
                        int header = metrics.value(DesignMetrics.Role.NATIVE_TOOL_HEADER_HEIGHT) + (labelled ? metrics.value(DesignMetrics.Role.DOCK_RULE) : 0);
                        assertThat(owner.area().headerOf(Slot.LEFT_TOP).getHeight()).as(where + " header").isEqualTo(com.formdev.flatlaf.util.UIScale.scale(header));
                        if (labelled) assertThat(owner.bottomStrip().orElseThrow().buttons()).as(where + " bottom labels").hasSize(1);
                        else assertThat(owner.leftStripe().buttons()).as(where + " the foot group on the rail").hasSize(2);
                    } finally { owner.close(); themes.close(); }
                }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /**
     * Plan 13 Rulings 10 on the path users see: a window whose look has labelled stripes and New UI artwork hands that
     * artwork to its stripes, so its left label leads with the New UI folder, not the classic tool-window file.
     */
    @Test void labelledStripesUnderNewUiArtworkDrawTheNewUiIcon() throws Exception {
        try {
            ThemeController themes = ThemeController.startup(false, ThemeSelection.slateCobalt(TerminalSchemeChoice.MATCH), KitFixtures.enterprise(),
                failure -> { throw new AssertionError(failure); });
            var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes,
                WorkspaceChrome.of(DockChromeTest.CLASSIC_DOCK, new dev.jasper.app.presentation.TitleFormat("{context}")));
            try {
                var model = new Contributions();
                owner.connectContributions(model, UiState.inMemory());
                model.addPanel("test.left", "Left", new PresentationIcon("folder", null), Slot.LEFT_TOP, site -> new JLabel("left"));
                var label = (StripeLabels.Label) owner.leftStripe().buttons().getFirst();
                assertThat(label.icon).isNotNull();
                assertThat(StripeIconsTest.reference(label.icon, label, Color.WHITE, 2)).isEqualTo(StripeIconsTest.reference(
                    new IconRegistry().chromeIcon("folder", 13).icon(ThemeZone.theme(label), SemanticColor.TEXT_SECONDARY), label, Color.WHITE, 2));
            } finally { owner.close(); themes.close(); }
        } finally { EnterpriseLook.restoreDefault(); }
    }
}
