package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.PanelSite.Placement;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.dock.ToolWindowMode;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.EnterpriseResources;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Wide tool window layouts in the real host: the staged Remote and File Server panels, mounted through their real
 * factories on a site the test moves between a window of its own, a side and the bottom, laid out by the app's real kit
 * under the default look and under Enterprise. Each moved panel's list and detail columns (SFTP's four column headers)
 * are showing side by side, the list the narrower, and a panel docked on a side has no split. The renders go to
 * {@code jasper-app/build/wide-layouts/<look>/<panel>.png}, at 2x, without a window ever being shown.
 */
class WideLayoutRenderTest {
    static final Path OUT = EnterpriseResources.ROOT.resolve("jasper-app/build/wide-layouts");
    static final int WIDTH = 760, HEIGHT = 460;
    static final String[][] TWO_HOSTS = {{"Mac", "192.168.1.119", "dustin", "Local Network"},
        {"minecraft-local", "192.168.1.124", "dustin", "Local Network", "favorite"}};
    static final long MODIFIED = Instant.parse("2026-09-28T14:05:00Z").toEpochMilli();
    @TempDir Path root;

    /** A panel's place on its stripe in each look, and the id prefix of its wide nodes ({@code <prefix>.columns}). */
    record Wide(String panel, String name, String prefix, Slot rows, Slot classic) { }

    interface Populate { void run(PanelSurfaceFixture fixture) throws Exception; }

    interface Check { void run(PanelSurfaceFixture fixture, boolean wide, String look) throws Exception; }

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theSessionsToolWindowLaysItsListBesideTheHostsDetails() throws Exception {
        wide(new Wide(PanelSurfaceFixture.SESSIONS, "sessions", "sessions", Slot.LEFT_TOP, Slot.RIGHT_TOP), fixture -> {
            var ids = fixture.hosts(TWO_HOSTS);
            fixture.selectSession(ids.get(1));
        }, (fixture, wide, look) -> columns(fixture, "sessions", wide));
    }

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theSftpToolWindowAddsModifiedAndPermissionsColumns() throws Exception {
        wide(new Wide(PanelSurfaceFixture.SFTP, "sftp", "sftp", Slot.LEFT_BOTTOM, Slot.LEFT_TOP), fixture -> {
            fixture.hosts(TWO_HOSTS);
            fixture.sftp("showPage", new Class<?>[] {String.class, String.class, List.class, long.class, long.class, boolean.class},
                "prod", "/srv/app", List.of(fixture.fileEntry("src", "DIRECTORY", 0, MODIFIED), fixture.fileEntry("README.md", "FILE", 4_210, MODIFIED),
                    fixture.fileEntry("build.gradle.kts", "FILE", 18_400, MODIFIED)), 0L, 3L, false);
        }, (fixture, wide, look) -> {
            var headers = new ArrayList<Rectangle>();
            // The default look ends on Size at the trailing edge; Enterprise's table puts Size second.
            var order = look.equals("enterprise") ? List.of("name", "size", "modified", "permissions") : List.of("name", "modified", "permissions", "size");
            for (String column : order) {
                var bounds = shown(fixture, "@table/sftp.files/header/" + column);
                if (bounds != null) headers.add(bounds);
                if (wide) assertThat(bounds).as("the %s header is showing", column).isNotNull();
                else if (column.equals("permissions")) assertThat(bounds).as("docked on a side, no Permissions header").isNull();
            }
            if (wide) {
                assertThat(headers).as("the four headers").hasSize(4).allSatisfy(bounds -> assertThat(bounds.width).isPositive());
                for (int i = 1; i < headers.size(); i++)
                    assertThat(headers.get(i).x).as("header %d sits right of header %d", i, i - 1).isGreaterThan(headers.get(i - 1).x);
            }
        });
    }

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theTunnelsToolWindowLaysItsListBesideTheTunnelsDetail() throws Exception {
        wide(new Wide(PanelSurfaceFixture.TUNNELS, "tunnels", "tunnels", Slot.LEFT_TOP, Slot.RIGHT_BOTTOM), fixture -> {
            var hosts = fixture.hosts(TWO_HOSTS);
            fixture.tunnel("Postgres", hosts.get(0), "LOCAL", 5432, "localhost", 5432);
            var web = fixture.tunnel("Web preview", hosts.get(1), "LOCAL", 8080, "localhost", 80);
            fixture.tunnelStates(Map.of(web, "up"));
            fixture.select("tunnels.rows", "tunnel:" + web);
        }, (fixture, wide, look) -> columns(fixture, "tunnels", wide));
    }

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theFileServersToolWindowLaysItsListBesideTheServersDetails() throws Exception {
        wide(new Wide(PanelSurfaceFixture.FILE_SERVERS, "file-servers", "fileserver", Slot.LEFT_TOP, Slot.LEFT_BOTTOM), fixture -> {
            fixture.fileServer("Downloads", "HTTP");
            fixture.select("fileserver.rows", "server:" + fixture.fileServer("Shared drop", "SFTP"));
        }, (fixture, wide, look) -> columns(fixture, "fileserver", wide));
    }

    /** Runs {@code populate}, then the moves, under the default look and under Enterprise (Light), each with its own panel. */
    private void wide(Wide wide, Populate populate, Check check) throws Exception {
        try (var fixture = new PanelSurfaceFixture(root.resolve(wide.name() + "-default"), PanelSurfacePreview.theme("dark"), wide.panel())) {
            populate.run(fixture);
            moves(fixture, wide, wide.rows(), "default", check);
        }
        ClassicCompositionRenderTest.under(false, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve(wide.name() + "-enterprise"), EnterpriseLook.resolved(false), wide.panel(), true)) {
                populate.run(fixture);
                moves(fixture, wide, wide.classic(), "enterprise", check);
            }
        });
    }

    /** Docked on a side, in a window of its own (rendered), docked on a side again, docked at the bottom, and on a side once more. */
    private static void moves(PanelSurfaceFixture fixture, Wide wide, Slot slot, String look, Check check) throws Exception {
        String as = wide.name() + " in the " + look + " look";
        fixture.size(WIDTH, HEIGHT);
        check.run(fixture, false, look);

        fixture.place(new Placement(slot, ToolWindowMode.FLOAT));
        fixture.size(WIDTH, HEIGHT);
        check.run(fixture, true, look);
        var image = fixture.render(WIDTH, HEIGHT);
        save(look, wide.name(), image);
        assertThat(colours(image)).as(as + ": the render is not one colour").isGreaterThan(1);

        fixture.place(new Placement(slot, ToolWindowMode.DOCKED));
        fixture.size(WIDTH, HEIGHT);
        check.run(fixture, false, look);

        fixture.place(new Placement(Slot.BOTTOM_LEFT, ToolWindowMode.DOCKED));
        fixture.size(WIDTH, HEIGHT);
        check.run(fixture, true, look);

        fixture.place(new Placement(slot, ToolWindowMode.DOCKED));
        fixture.size(WIDTH, HEIGHT);
        check.run(fixture, false, look);
    }

    /**
     * With {@code wide}, {@code <prefix>.columns}, {@code .list} and {@code .detail} are all showing, the list left of the
     * detail, both with a size and the list the narrower; else the split is gone.
     */
    private static void columns(PanelSurfaceFixture fixture, String prefix, boolean wide) {
        var split = shown(fixture, prefix + ".columns");
        if (!wide) {
            assertThat(split).as(prefix + ".columns while docked on a side").isNull();
            return;
        }
        var list = shown(fixture, prefix + ".list");
        var detail = shown(fixture, prefix + ".detail");
        assertThat(split).as(prefix + ".columns is showing").isNotNull();
        assertThat(list).as(prefix + ".list is showing").isNotNull();
        assertThat(detail).as(prefix + ".detail is showing").isNotNull();
        assertThat(list.width).as("the list's width").isPositive();
        assertThat(list.height).as("the list's height").isPositive();
        assertThat(detail.width).as("the detail's width").isPositive();
        assertThat(detail.height).as("the detail's height").isPositive();
        assertThat(list.x + list.width).as("the list is left of the detail").isLessThanOrEqualTo(detail.x);
        assertThat(list.width).as("the list is the narrower column").isLessThan(detail.width);
        assertThat(split.contains(list) && split.contains(detail)).as("both columns lie in the split").isTrue();
    }

    /** The bounds in the panel's view of the component named {@code id}, or null when it is absent or one of its ancestors is hidden. */
    private static Rectangle shown(PanelSurfaceFixture fixture, String id) {
        return onEdtValue(() -> {
            var view = fixture.view();
            var found = StagedVaultHost.named(view, id);
            if (found == null) return null;
            for (Component c = found; c != view; c = c.getParent()) if (c == null || !c.isVisible()) return null;
            return SwingUtilities.convertRectangle(found.getParent(), found.getBounds(), view);
        });
    }

    private static int colours(BufferedImage image) {
        var seen = new HashSet<Integer>();
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) seen.add(image.getRGB(x, y));
        return seen.size();
    }

    private static void save(String look, String panel, BufferedImage image) {
        try {
            Path file = OUT.resolve(look).resolve(panel + ".png");
            Files.createDirectories(file.getParent());
            ImageIO.write(image, "png", file.toFile());
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
