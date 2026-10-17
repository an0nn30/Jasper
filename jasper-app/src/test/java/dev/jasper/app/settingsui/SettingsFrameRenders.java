package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.SettingsShell;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Test bridge for plan 6a's ChromeRenderTest (spec 16.1.4, plan 9a Task 7): the real Settings dialog under a look's kit
 * and projections, its frame checked against what the look's SettingsChrome names, printed at 2x. EDT only.
 */
public final class SettingsFrameRenders {
    static final Path FILE = Path.of("/fixture/config.toml");
    static final int WIDTH = 1000, HEIGHT = 700;

    private SettingsFrameRenders() { }

    /** The 2x print's pixel at {@code (x, y)} of {@code part}, without alpha. */
    static int at(BufferedImage image, JComponent part, JComponent root, int x, int y) {
        Point point = SwingUtilities.convertPoint(part, x, y, root);
        return image.getRGB(2 * point.x, 2 * point.y) & 0xFFFFFF;
    }

    /**
     * Opens the dialog at Command Palette, with its visible rows modified, under {@code kit} and {@code projections};
     * checks the frame the look's SettingsChrome names (the title row, the classic frame's rules and footer band, the
     * section triangles, the inline Reset) and returns the dialog's 2x print. EDT.
     */
    public static BufferedImage check(String name, Supplier<KitContext> kit, Projections projections) {
        var chrome = projections.settings();
        var parsed = ConfigLoader.parse(FILE, "[palette]\nvisible_rows = 9\n", true);
        var state = new ConfigService.State(parsed.snapshot(), parsed.diagnostics(), FILE, true, parsed.legacyKeys(), parsed.deadTables(),
            parsed.values(), parsed.rejected());
        var holder = new JComponent[1];
        var windows = new AuxiliaryWindows(UiState.inMemory(), surface -> {
            holder[0] = surface.holder();
            return new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { }, () -> new Rectangle(0, 0, WIDTH, HEIGHT), (title, initial) -> Optional.empty());
        }, (choice, cancel) -> List.of(), projections.dialogs());
        var dialog = new SettingsDialog(windows, context -> CorePages.pages(AppearanceFixtures.CHOICES,
            new CorePages.SystemActions(() -> FILE, () -> { }, () -> { }, () -> { }, () -> { }, () -> false, Optional.empty()),
            page -> new GroupPage("keymap", "Keymap", Optional.empty(), false, List::of, page),
            page -> new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, page)).apply(context),
            new SettingsDialog.Hooks(edit -> CompletableFuture.completedFuture(state), () -> state, listener -> new Subscription(() -> { }),
                () -> { }, () -> { }, uri -> { }, (parent, count) -> true, kit, chrome, projections.composition()));
        try {
            dialog.open(null, "command-palette");
            var root = holder[0];
            var image = MockProfile.print(root, WIDTH, HEIGHT);
            var view = dialog.view();
            var context = kit.get();
            var theme = context.theme();
            boolean classicFrame = !chrome.integratedTitle();
            assertThat(view.search.getY() < view.titleRowHeight()).as(name + ": a native title gives the search its own strip")
                .isEqualTo(classicFrame);
            if (classicFrame) {
                var pairs = SettingsShell.framePairs(context);
                assertThat(at(image, view.navigation, root, view.navigation.getWidth(), 20)).as(name + ": the sidebar rule")
                    .isEqualTo(SurfaceContrast.painted(theme, pairs.get(0)).getRGB() & 0xFFFFFF);
                assertThat(at(image, view.body, root, 40, -1)).as(name + ": the header rule")
                    .isEqualTo(SurfaceContrast.painted(theme, pairs.get(1)).getRGB() & 0xFFFFFF);
                assertThat(at(image, view.footer, root, view.footer.getWidth() / 2, -1)).as(name + ": the footer rule")
                    .isEqualTo(SurfaceContrast.painted(theme, pairs.get(2)).getRGB() & 0xFFFFFF);
                assertThat(at(image, view.footer, root, -4, view.footer.getHeight() / 2)).as(name + ": the footer band")
                    .isEqualTo(DerivedColor.SURFACE_FOOTER.color(theme).getRGB() & 0xFFFFFF);
                for (var pair : pairs)
                    assertThat(pair.guard().holds(SurfaceContrast.painted(theme, pair), pair.against().color(theme)))
                        .as(name + ": " + pair.slot().key() + " on " + pair.against().key() + " meets its guard").isTrue();
            }
            assertThat(KitTestSupport.find(view, "@tree/pages/appearance-behavior/expand", AbstractButton.class) != null)
                .as(name + ": section triangles exactly when the navigation collapses").isEqualTo(chrome.collapsibleNav());
            assertThat(KitTestSupport.find(view, "palette.visible_rows:reset", JComponent.class) != null)
                .as(name + ": a Reset button exactly when Reset is inline").isEqualTo(chrome.inlineReset());
            // Plan 9b (spec 12.2): the composition the look names, on the Shell page's environment variables.
            boolean classic = projections.composition() == dev.jasper.app.presentation.Composition.CLASSIC;
            dialog.select("terminal.shell");
            var add = KitTestSupport.require(view, "terminal.env:add", AbstractButton.class);
            assertThat(add.getText()).as(name + ": the environment's add action").isEqualTo(classic ? "Add Variable…" : "Add variable");
            assertThat(KitTestSupport.find(view, "terminal.env:name-0", JComponent.class)).as(name + ": no field rows").isNull();
            return image;
        } finally {
            dialog.cancel();
            windows.close();
        }
    }
}
