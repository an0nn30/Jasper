package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.ThemeBinding;
import dev.jasper.ui.theme.ThemePair;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.BorderLayout;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Enterprise presentation plan 7's pixel rule for the default look: the icon rails, a borrowed kit header and a native
 * header in today's frames, rendered at 2x for Slate Cobalt Dark and Light into {@code jasper-app/build/dock-renders/}.
 * The plan compares these files with the base it captured before its first change.
 */
@ExtendWith(EdtTestExtension.class)
class DefaultDockPixelsTest {
    @Test void theDefaultLookDockRendersForThePixelRule() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            for (boolean dark : new boolean[] {true, false}) {
                ThemeTestSupport.install(dark);
                var theme = ThemeTestSupport.generated(dark);
                try (var binding = new ThemeBinding(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); });
                     var icons = new IconRegistry()) {
                    var context = KitContext.of(Kit.DEFAULT, binding, ThemeRoute.CHROME, icons, () -> false, action -> "");
                    var spec = new ViewSpec(new Nodes.Panel("hosts", ControlState.READY,
                        new Nodes.Header("hosts.header", ControlState.READY, "Hosts", OptionalInt.of(3), Optional.empty(), Optional.empty(), List.of()),
                        Optional.empty(), new Nodes.Text("hosts.body", ControlState.READY, "Body", Nodes.TextRole.BODY), Optional.empty()));
                    try (var view = new KitView(context, spec, event -> { })) {
                        var area = new ToolWindowArea(new JPanel(), new DockState());
                        DockTestSupport.show(area, Slot.LEFT_TOP, view, 260);
                        DockTestSupport.show(area, Slot.RIGHT_TOP, new JLabel("Notes"), 240);
                        area.setStripesVisible(true, true);
                        var left = new ToolStripe(Side.LEFT);
                        var right = new ToolStripe(Side.RIGHT);
                        var icon = AppIcons.semantic("folder");
                        left.render(List.of(new ToolStripe.Button(DockTestSupport.id(Slot.LEFT_TOP), "Hosts", icon, Slot.LEFT_TOP, true),
                            new ToolStripe.Button("test.files", "Files", icon, Slot.LEFT_BOTTOM, false)), List.of());
                        right.render(List.of(new ToolStripe.Button(DockTestSupport.id(Slot.RIGHT_TOP), "Notes", icon, Slot.RIGHT_TOP, true)), List.of());
                        var body = new JPanel(new BorderLayout());
                        body.add(left, BorderLayout.WEST);
                        body.add(area, BorderLayout.CENTER);
                        body.add(right, BorderLayout.EAST);
                        var image = MockCrops.render(body, 900, 420, 2);
                        Files.createDirectories(MockCrops.OUT);
                        ImageIO.write(image, "png", MockCrops.OUT.resolve("default-" + (dark ? "dark" : "light") + ".png").toFile());
                        assertThat(image.getWidth()).isEqualTo(1800);
                        area.close();
                    }
                }
            }
        } finally { UIManager.setLookAndFeel(original); }
    }
}
