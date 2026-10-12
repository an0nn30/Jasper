package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ToolWindowHeaderTest {
    @org.junit.jupiter.api.BeforeEach void installModuleTheme() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> dev.jasper.app.appearance.ThemeManager.install(dev.jasper.app.appearance.Theme.DARK));
    }

    /**
     * Main's connected-status dot, on the branch's module roles: the success status, held to the indicator floor on
     * the tool window surface, in a dark and a light theme (it replaces main's fixed 0x38ba54/0x248943 reference).
     */
    @Test void connectedHeaderDotIsTheReadableSuccessStatusInDarkAndLight() {
        for (var theme : List.of(dev.jasper.app.appearance.Theme.DARK, dev.jasper.app.appearance.Theme.JASPER_LIGHT)) {
            assertThat(dev.jasper.app.appearance.ThemeManager.install(theme)).isTrue();
            var header = new ToolWindowHeader("SFTP", JPopupMenu::new, () -> {});
            header.presentation(new dev.jasper.app.contributions.PanelHeaderState("prod", true, List.of()));
            var detail = descendants(header).stream().filter(c -> c instanceof javax.swing.JLabel label && "prod".equals(label.getText()))
                .map(c -> (javax.swing.JLabel) c).findFirst().orElseThrow();
            var image = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try { detail.getIcon().paintIcon(detail, graphics, 0, 0); } finally { graphics.dispose(); }
            int middle = detail.getIcon().getIconWidth() / 2;
            var dot = new java.awt.Color(image.getRGB(middle, middle), true);
            var palette = dev.jasper.app.platform.ThemeZone.theme(header).semantic();
            var surface = palette.color(dev.jasper.ui.theme.SemanticColor.SURFACE_SIDEBAR);
            assertThat(dot).as(theme.id()).isEqualTo(dev.jasper.ui.theme.SurfaceContrast.on(palette,
                dev.jasper.ui.theme.SemanticColor.STATUS_SUCCESS, dev.jasper.ui.theme.SemanticColor.SURFACE_SIDEBAR,
                dev.jasper.ui.theme.SurfaceContrast.INDICATOR));
            assertThat(dev.jasper.ui.theme.ColorMath.contrast(dot, surface)).as(theme.id()).isGreaterThanOrEqualTo(3);
            assertThat(dot.getGreen()).as("%s: a green status", theme.id()).isGreaterThan(Math.max(dot.getRed(), dot.getBlue()));
            assertThat(detail.getForeground()).as("detail uses the kit header's count colour")
                .isEqualTo(palette.color(dev.jasper.ui.theme.SemanticColor.TEXT_SECONDARY));
        }
    }

    @Test void contributedActionsLiveInTheHeaderAndSurviveMetadataRefresh() {
        var header = new ToolWindowHeader("Sessions", JPopupMenu::new, () -> { });
        var icon = dev.jasper.app.platform.AppIcons.named("ADD");
        header.presentation(new dev.jasper.app.contributions.PanelHeaderState("5", false, List.of(
            new dev.jasper.app.contributions.PanelHeaderState.Action("Import sessions", icon, () -> events.add("import")))));
        var actions = descendants(header).stream().filter(c -> c instanceof javax.swing.JButton button
            && "Import sessions".equals(button.getAccessibleContext().getAccessibleName())).toList();
        assertThat(actions).hasSize(1);
        var action = (javax.swing.JButton) actions.getFirst();
        assertThat(action.getText()).isNullOrEmpty();
        assertThat(action.isFocusable()).isTrue();
        action.doClick();
        header.presentation(new dev.jasper.app.contributions.PanelHeaderState("6", false, List.of(
            new dev.jasper.app.contributions.PanelHeaderState.Action("Import sessions", icon, () -> events.add("updated")))));
        assertThat(descendants(header)).contains(action);
        action.doClick();
        assertThat(events).containsExactly("import", "updated");
        assertThat(descendants(header).stream().filter(c -> c instanceof javax.swing.JLabel label && "6".equals(label.getText()))).hasSize(1);
    }

    private static List<java.awt.Component> descendants(java.awt.Container parent) {
        var result = new ArrayList<java.awt.Component>();
        for (var child : parent.getComponents()) {
            result.add(child);
            if (child instanceof java.awt.Container container) result.addAll(descendants(container));
        }
        return result;
    }

    private final List<String> events = new ArrayList<>();

    private JPopupMenu menu(Slot current, boolean visible) {
        return ToolWindowMenu.create(current, visible, slot -> events.add("move:" + slot), () -> events.add("hide"));
    }

    static JMenuItem item(JPopupMenu menu, String text) {
        for (var child : menu.getComponents()) {
            if (child instanceof JMenu submenu) for (var nested : submenu.getMenuComponents())
                if (nested instanceof JMenuItem entry && entry.getText().equals(text)) return entry;
            if (child instanceof JMenuItem entry && entry.getText().equals(text)) return entry;
        }
        throw new AssertionError("No menu item " + text);
    }

    @Test void theMenuMovesToAnyOfTheSixSlotsAndHides() {
        JPopupMenu menu = menu(Slot.LEFT_BOTTOM, true);
        assertThat(item(menu, "Hide").getFont()).isEqualTo(dev.jasper.app.platform.ThemeZone.font(dev.jasper.ui.metrics.Typography.Role.LABEL));
        var moveTo = (JMenu) menu.getComponent(0);
        assertThat(moveTo.getText()).isEqualTo("Move to");
        assertThat(Arrays.stream(moveTo.getMenuComponents()).map(item -> ((JMenuItem) item).getText()))
            .containsExactly("Left Top", "Left Bottom", "Bottom Left", "Right Top", "Right Bottom", "Bottom Right");
        assertThat(Arrays.stream(moveTo.getMenuComponents()).filter(item -> ((JCheckBoxMenuItem) item).isSelected())
            .map(item -> ((JMenuItem) item).getText())).as("the current slot is checked").containsExactly("Left Bottom");
        item(menu, "Bottom Right").doClick();
        item(menu, "Left Bottom").doClick();
        assertThat(((JCheckBoxMenuItem) item(menu, "Left Bottom")).isSelected()).as("clicking the current slot keeps it checked").isTrue();
        item(menu, "Hide").doClick();
        assertThat(events).as("choosing the current slot does nothing").containsExactly("move:BOTTOM_RIGHT", "hide");
        assertThat(item(menu(Slot.RIGHT_TOP, false), "Hide").isEnabled()).as("nothing to hide").isFalse();
    }

    @Test void theHeaderShowsThePlainTitleAndHidesOrOpensItsMenu() {
        var header = new ToolWindowHeader("<html><b>Sessions</b>", () -> menu(Slot.LEFT_TOP, true), () -> events.add("hidden"));
        assertThat(header.titleLabel().getText()).isEqualTo("<html><b>Sessions</b>");
        assertThat(header.titleLabel().getClientProperty("html.disable")).as("a plugin title is never HTML").isEqualTo(Boolean.TRUE);
        assertThat(header.titleLabel().getFont()).isEqualTo(dev.jasper.app.platform.ThemeZone.font(dev.jasper.ui.metrics.Typography.Role.PANEL_TITLE));
        header.updateUI();
        SwingUtilities.updateComponentTreeUI(header);
        assertThat(header.getInsets()).as("padding survives a look-and-feel update").isEqualTo(new Insets(0, UIScale.scale(10), 0, UIScale.scale(6)));
        assertThat(header.titleLabel().getFont()).as("module title role survives a look-and-feel change").isEqualTo(dev.jasper.app.platform.ThemeZone.font(dev.jasper.ui.metrics.Typography.Role.PANEL_TITLE));
        assertThat(header.optionsButton().getAccessibleContext().getAccessibleName()).isEqualTo("Options");
        assertThat(header.optionsButton().getToolTipText()).isEqualTo("Options");
        assertThat(header.hideButton().getAccessibleContext().getAccessibleName()).isEqualTo("Hide");
        assertThat(header.optionsButton().isFocusable()).isFalse();
        assertThat(header.getPreferredSize().height).isEqualTo(UIScale.scale(ToolWindowHeader.HEIGHT));

        header.hideButton().doClick();
        header.optionsButton().doClick();
        assertThat(events).as("a header that is not showing opens no popup").containsExactly("hidden");
        assertThat(header.menu().getComponentCount()).isEqualTo(2);
    }

    @Test void theButtonsSitAtTheRightAndAreCenteredVertically() {
        var header = new ToolWindowHeader("Sessions", () -> menu(Slot.LEFT_TOP, true), () -> { });
        header.setSize(260, header.getPreferredSize().height);
        LayoutTestSupport.layoutTree(header);
        var hide = SwingUtilities.convertRectangle(header.hideButton().getParent(), header.hideButton().getBounds(), header);
        var options = SwingUtilities.convertRectangle(header.optionsButton().getParent(), header.optionsButton().getBounds(), header);
        assertThat(hide.x + hide.width).isEqualTo(260 - UIScale.scale(6));
        assertThat(options.x + options.width).isLessThan(hide.x);
        assertThat(Math.abs(hide.y - (header.getHeight() - hide.y - hide.height))).isLessThanOrEqualTo(1);
        var title = SwingUtilities.convertRectangle(header.titleLabel().getParent(), header.titleLabel().getBounds(), header);
        assertThat(title.x).isEqualTo(UIScale.scale(10));
    }

    /** The class runs on the EDT (EdtTestExtension) with the module theme installed. */
    @Test void toolWindowsSitOnThePanelSurface() {
        var header = new ToolWindowHeader("Sessions", javax.swing.JPopupMenu::new, () -> { });
        assertThat(header.getBackground()).isEqualTo(dev.jasper.app.platform.ThemeZone.color(header, dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL));
        var root = new javax.swing.JPanel();
        ToolWindowSurface.apply(root);
        assertThat(root.getBackground()).isEqualTo(dev.jasper.app.platform.ThemeZone.color(root, dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL));
        assertThat(dev.jasper.app.platform.ThemeZone.color(root, dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL))
            .as("the module theme tells panel from sidebar").isNotEqualTo(dev.jasper.app.platform.ThemeZone.color(root, dev.jasper.ui.theme.SemanticColor.SURFACE_SIDEBAR));
    }
}
