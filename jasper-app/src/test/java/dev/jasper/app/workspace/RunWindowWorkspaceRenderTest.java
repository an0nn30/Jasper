package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.palette.PaletteTestSupport;
import dev.jasper.app.palette.RunWindowRenderTest;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.presentation.PaletteLook;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.EnterpriseResources;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayDeque;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Spec 16.1.4: the Run window open in a whole Enterprise workspace, at its corner home, matching the 06 Run Dialog mock. */
class RunWindowWorkspaceRenderTest {
    @Test void theRunWindowInAnEnterpriseWorkspaceMatchesItsMock() throws Exception {
        EnterpriseResources.assumePlexResolves();
        var out = EnterpriseResources.ROOT.resolve("jasper-app/build/run-window/workspace");
        DesktopTestSupport.edt(() -> {
            var themes = EnterpriseLook.controller(false);
            MacTitleBar bar = null;
            try {
                // The Enterprise workspace as the application builds it: the shipped kit's chrome, every landed group.
                var projections = Projections.of(EnterpriseLook.chrome(), true, true, "dev.jasper.enterprise", ChromeRenderTest.HOST);
                var look = WindowLook.of(projections);
                assertThat(look.chrome().palette()).as("the classic chrome's palette is the Run window").isEqualTo(PaletteLook.RUN_WINDOW);
                var owner = DesktopTestSupport.track(new WindowContent(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), DesktopTestSupport.HOME,
                    false, path -> { }, () -> { }, () -> { }, themes, KeyBindings.defaults(true), new CommandHistory(), true, look.chrome()));
                // The mock's tool windows, so the left and right stripes and the bottom strip the home measures from are there.
                var model = new Contributions();
                owner.connectContributions(model, UiState.inMemory(projections.dockLayout().key()));
                model.addPanel("test.sftp", "SFTP", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("sftp"));
                model.addPanel("test.servers", "File Servers", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("servers"));
                model.addPanel("test.sessions", "Sessions", new ImageIcon(), Slot.RIGHT_TOP, site -> new JLabel("sessions"));
                model.addPanel("test.tunnels", "Tunnels", new ImageIcon(), Slot.RIGHT_TOP, site -> new JLabel("tunnels"));
                model.addPanel("test.log", projections.panels().notificationsTitle(), new ImageIcon(), projections.panels().notificationsSlot(),
                    site -> new JLabel("log"));
                model.addPanel("test.transfers", "Transfers", new ImageIcon(), Slot.BOTTOM_LEFT, site -> new JLabel("transfers"));
                var root = new JRootPane();
                bar = WindowContent.mount(root, owner, look, title -> { });
                owner.installRootBindings(root);
                root.setSize(1442, 902); LayoutTestSupport.layoutTree(root);
                var palette = owner.commandPalette();
                palette.toggle();
                palette.setVisibleRows(8);
                PaletteTestSupport.showRunWindowState(palette.view(), "just-opened");
                PaletteTestSupport.focusRunWindowField(palette.view());
                for (int i = 0; i < 3; i++) LayoutTestSupport.layoutTree(root);
                var window = palette.component();
                var overlay = (JComponent) window.getParent();
                var work = owner.paletteWorkArea(overlay);
                var context = themes.appearance().context(ThemeRoute.CHROME);
                assertThat(window.getX()).as("at its corner home, the mocks' 6 right of the left stripe")
                    .isEqualTo(work.x + context.metrics().value(DesignMetrics.Role.RUN_WINDOW_HOME_SIDE));
                assertThat(window.getY() + window.getHeight()).as("and the mocks' 5 above the bottom strip")
                    .isEqualTo(work.y + work.height - context.metrics().value(DesignMetrics.Role.RUN_WINDOW_HOME_BOTTOM));
                var strip = owner.bottomStrip().orElseThrow();
                assertThat(strip.isVisible()).as("the bottom strip shows its tool windows").isTrue();
                var stripAt = SwingUtilities.convertRectangle(strip.getParent(), strip.getBounds(), root);
                var stripe = owner.leftStripe();
                var stripeAt = SwingUtilities.convertRectangle(stripe.getParent(), stripe.getBounds(), root);
                var placed = SwingUtilities.convertRectangle(window.getParent(), window.getBounds(), root);
                assertThat(stripeAt.width).as("the left stripe shows its tool windows").isPositive();
                assertThat(placed.x - (stripeAt.x + stripeAt.width)).as("the mocks' 6 right of the left stripe")
                    .isEqualTo(context.metrics().value(DesignMetrics.Role.RUN_WINDOW_HOME_SIDE));
                assertThat(stripAt.y - (placed.y + placed.height)).as("the mocks' 5 above the bottom strip")
                    .isEqualTo(context.metrics().value(DesignMetrics.Role.RUN_WINDOW_HOME_BOTTOM));
                // Against the mock's frame at 2x (Verified facts 8: frame x 60-61, frame bottom y 1694-1695, strip rule y 1706-1707): the
                // mock's 2 px window border is not drawn here, and the left stripe is the mock's 23 thick (DockMockTest).
                assertThat(2 * placed.x).as("the frame's left edge, the mock's x 60 less its border plus the stripe's extra thickness")
                    .isEqualTo(60 - 2 + 2 * (stripeAt.width - 23));
                assertThat(2 * stripAt.y).as("the bottom strip's rule, the mock's y 1706").isEqualTo(1706);
                assertThat(2 * (placed.y + placed.height)).as("the frame's bottom edge, the mock's y 1696").isEqualTo(1696);
                var image = new BufferedImage(root.getWidth() * 2, root.getHeight() * 2, BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                try { g.scale(2, 2); root.paint(g); } finally { g.dispose(); }
                Files.createDirectories(out);
                ImageIO.write(image, "png", out.resolve("enterprise-light@2x.png").toFile());
                var crop = image.getSubimage(placed.x * 2, placed.y * 2, placed.width * 2, placed.height * 2);
                ImageIO.write(crop, "png", out.resolve("just-opened@2x.png").toFile());
                RunWindowRenderTest.check(crop, "just-opened", context, out);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            } finally {
                if (bar != null) bar.close();
                DesktopTestSupport.closeOwnersNow();
                themes.close();
                EnterpriseLook.restoreDefault();
            }
        });
    }
}
