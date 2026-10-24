package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.app.testsupport.MockMatch;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.theme.ColorMath;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Plan 10a's mock matching (spec 16.1.4): the staged plugins' classic compositions in the real host, under the shipped
 * Enterprise kit, Plex fonts and theme, against the Workstation, Dark Mode and dialog mocks. Images go to
 * jasper-app/build/enterprise-compositions/ (light/ and dark/), paired in tools/visual/pairs.json.
 */
class ClassicCompositionRenderTest {
    static final String CONNECTED = "01 Workstation/Workstation - Connected (plugins in use).png",
        DISCONNECTED = "01 Workstation/Workstation - Disconnected.png", DARK = "08 Dark Mode/Workstation Dark - Connected (plugins in use).png";
    @TempDir Path root;

    /**
     * Saves the pair, then checks the render: its size, its bands down {@code sampleX}, its colours and its edge agreement,
     * which follows the UI face's glyphs and is asserted under the shipped IBM Plex ({@link MockCrops#fontsFloor}). Bands
     * and colours name the shipped theme's roles (or {@code #RRGGBB}), resolved in the variant the name starts with.
     */
    static void check(String name, MockMatch.Crop crop, BufferedImage render, int sampleX, String bands, int tolerance, double floor, String... colours) {
        boolean dark = name.startsWith("dark/");
        var mock = crop.read();
        MockMatch.save(name, mock, render);
        assertThat(render.getWidth()).as(name + " width").isEqualTo(crop.width());
        assertThat(render.getHeight()).as(name + " height").isEqualTo(crop.height());
        assertThat(MockMatch.mismatch(MockMatch.parse(bands, EnterpriseLook.theme(dark)), MockMatch.bands(render, sampleX), tolerance))
            .as(name + " bands at x " + sampleX).isEmpty();
        for (String colour : colours)
            assertThat(MockMatch.present(render, colour.startsWith("#") ? Color.decode(colour) : MockProfile.role(dark, colour))).as(name + " shows " + colour).isTrue();
        MockCrops.fontsFloor(name + " edge agreement", MockMatch.agreement(mock, render), floor);
    }

    /** A classic panel's faint rule: {@code border.subtle} laid over {@code ground}, opaque (the faces' subtle rule), as hex. */
    static String rule(boolean dark, String ground) {
        Color subtle = MockProfile.role(dark, "border.subtle");
        return ColorMath.hex(ColorMath.mix(subtle, MockProfile.role(dark, ground), subtle.getAlpha() / 255.0));
    }

    /**
     * The panel's faint rule at logical row {@code at} and the 20 px column header under it: one band where the two paint
     * within the band reader's ΔE 1.5 of each other, as the New UI Dark port's do (the user's 2026-10-18 direction).
     */
    static String ruleAndHeader(boolean dark, int at) {
        String rule = rule(dark, "surface.panel");
        return MockMatch.deltaE(Color.decode(rule), MockProfile.role(dark, "surface.tableHeader")) <= 1.5
            ? at + "+21 " + rule + "~surface.tableHeader" : at + "+1 " + rule + ", " + (at + 1) + "+20 surface.tableHeader";
    }

    /** {@code pair} as the shipped theme's Light variant paints it, as hex: a guarded rule in a band. */
    static String painted(dev.jasper.ui.theme.PaintPair pair) { return ColorMath.hex(MockProfile.painted(false, pair)); }

    /** Runs {@code body} under the installed Enterprise look in {@code dark}, then restores the default look. */
    static void under(boolean dark, ThrowingRunnable body) throws Exception {
        var controller = onEdtValue(() -> EnterpriseLook.controller(dark));
        try { body.run(); } finally { onEdt(() -> { controller.close(); EnterpriseLook.restoreDefault(); }); }
    }

    interface ThrowingRunnable { void run() throws Exception; }

    /**
     * The sessions tree and its Properties down x 4: the panel, its search row's faint rule, the column header and its
     * divider, the tree on surface.card with the selected row, the split's rule, then the Properties rows and their rules.
     */
    static String sessions(boolean dark, int above, int below) {
        String panel = rule(dark, "surface.panel");
        return "0+36 surface.panel, " + ruleAndHeader(dark, 36) + ", 57+1 border.divider, 58+" + above + " surface.card, "
            + (58 + above) + "+22 selection.bg, " + (80 + above) + "+" + below + " surface.card, 235+1 border.window, 236+22 surface.panel, "
            + "258+1 " + panel + ", 259+22 surface.panel, 281+1 " + panel + ", 282+22 surface.panel, 304+1 " + panel + ", 305+22 surface.panel, "
            + "327+1 " + panel + ", 328+22 surface.panel, 350+1 " + panel + ", 351+40 surface.panel";
    }
    /** The Disconnected mock stripes its second host (#F7F8FA); classic trees are not striped (Rulings 14), so the render reads surface.card. */
    static final String SESSIONS_DISCONNECTED = sessions(false, 22, 133);

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theSessionsTreeAndPropertiesMatchTheWorkstationMocks() throws Exception {
        for (boolean dark : new boolean[] {false, true}) under(dark, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("sessions-" + dark), EnterpriseLook.resolved(dark), PanelSurfaceFixture.SESSIONS, true)) {
                var ids = fixture.hosts(new String[][] {{"Mac", "192.168.1.119", "dustin", "Local Network"},
                    {"minecraft-local", "192.168.1.124", "dustin", "Local Network", "favorite"}});
                fixture.selectSession(ids.get(1));
                var crop = new MockMatch.Crop(dark ? DARK : CONNECTED, 2136, 230, 700, 782);
                check((dark ? "dark" : "light") + "/sessions", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 4,
                    sessions(dark, 44, 111), 1, 0.55, dark ? new String[] {"selection.bg", "surface.tableHeader", "surface.panel"}
                        // The mock's Properties rules (#E6E8EB) are the faint rule the render paints (the user's 2026-10-18 direction).
                        : new String[] {"selection.bg", "surface.tableHeader", "surface.panel", rule(false, "surface.panel")});
            }
        });
        under(false, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("sessions-locked"), EnterpriseLook.resolved(false), PanelSurfaceFixture.SESSIONS, true)) {
                var ids = fixture.hosts(new String[][] {{"Mac", "192.168.1.119", "dustin", "Local Network", "favorite", "vault"},
                    {"minecraft-local", "192.168.1.124", "dustin", "Local Network"}});
                fixture.selectSession(ids.get(0));
                var crop = new MockMatch.Crop(DISCONNECTED, 2136, 230, 700, 782);
                // "Vault locked" is the warning text through the properties face's text guard, which moves the IntelliJ Light
                // port's status.warning (the user's 2026-10-18 direction).
                check("light/sessions-locked", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 4, SESSIONS_DISCONNECTED, 1, 0.55,
                    "selection.bg", painted(new dev.jasper.ui.theme.PaintPair(dev.jasper.ui.theme.SemanticColor.STATUS_WARNING,
                        dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL, dev.jasper.ui.theme.SurfaceContrast.Guard.TEXT)));
            }
        });
    }

    /** Down the session combo at x 130: the toolbar's surface.app, the combo's ring and its input box, in either variant. */
    static final String TOOLBAR = "0+6 surface.app, 6+1 border.control, 7+20 surface.input, 27+1 border.control, 28+6 surface.app";

    @Test @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void theSessionToolbarViewMatchesTheWorkstationMocks() throws Exception {
        for (boolean dark : new boolean[] {false, true}) under(dark, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("toolbar-" + dark), EnterpriseLook.resolved(dark), PanelSurfaceFixture.SESSIONS, true)) {
                var ids = fixture.hosts(new String[][] {{"Mac", "192.168.1.119", "dustin", "Local Network"},
                    {"minecraft-local", "192.168.1.124", "dustin", "Local Network", "favorite"}});
                fixture.selectSession(ids.get(1));
                var crop = new MockMatch.Crop(dark ? DARK : CONNECTED, 660, 110, 720, 68);
                check((dark ? "dark" : "light") + "/session-toolbar", crop, fixture.toolbarView(crop.logicalWidth(), crop.logicalHeight()), 130,
                    TOOLBAR, 1, 0.50, "surface.input", "border.control");
            }
        });
    }

    /** Down the tunnels table at x 4: the panel and its faint rule, the column header and its divider, a row and its faint rule. */
    static String tunnels(boolean dark) {
        return "0+36 surface.panel, " + ruleAndHeader(dark, 36) + ", 57+1 border.divider, 58+24 surface.card, "
            + "82+1 " + rule(dark, "surface.card") + ", 83+238 surface.card";
    }
    static final String TUNNELS_NONE = "0+20 surface.tableHeader, 20+1 border.divider, 21+300 surface.card";

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theTunnelsTableMatchesTheWorkstationMocks() throws Exception {
        for (boolean dark : new boolean[] {false, true}) under(dark, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("tunnels-" + dark), EnterpriseLook.resolved(dark), PanelSurfaceFixture.TUNNELS, true)) {
                var mac = fixture.hosts(new String[][] {{"Mac", "192.168.1.119", "dustin", "Local Network"}}).getFirst();
                fixture.tunnel("test", mac, "LOCAL", 8080, "localhost", 234);
                var crop = new MockMatch.Crop(dark ? DARK : CONNECTED, 2136, 1064, 700, 642);
                check((dark ? "dark" : "light") + "/tunnels", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 4,
                    tunnels(dark), 2, 0.55, dark ? new String[] {"surface.tableHeader", "surface.card"}
                        // The idle tunnel's dot is status.idle, where the mock draws #A3A8B0 (the user's 2026-10-18 direction).
                        : new String[] {"surface.tableHeader", "status.idle", "text.secondary"});
            }
        });
        under(false, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("tunnels-none"), EnterpriseLook.resolved(false), PanelSurfaceFixture.TUNNELS, true)) {
                var crop = new MockMatch.Crop(DISCONNECTED, 2136, 1064, 700, 642);
                check("light/tunnels-none", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 4, TUNNELS_NONE, 1, 0.55, "text.primary", "button.secondary.border");
            }
        });
    }

    /**
     * Down the SFTP table at x 8: the panel and its faint rule, the column header and its divider, the selected row, the
     * zebra rows, the faint rule over the status line and the panel. The New UI Dark port's zebra is its card (the user's
     * 2026-10-18 direction), so there the rows read as one band.
     */
    static String sftp(boolean dark) {
        var rows = new StringBuilder();
        if (MockProfile.role(dark, "surface.zebra").equals(MockProfile.role(dark, "surface.card"))) rows.append("105+222 surface.card, ");
        else {
            for (int i = 0; i < 10; i++) rows.append(105 + 22 * i).append("+22 ").append(i % 2 == 0 ? "surface.zebra" : "surface.card").append(", ");
            rows.append("325+2 surface.zebra, ");
        }
        return "0+61 surface.panel, " + ruleAndHeader(dark, 61) + ", 82+1 border.divider, 83+22 selection.bg, "
            + rows + "327+1 " + rule(dark, "surface.panel") + ", 328+28 surface.panel";
    }
    static final String SFTP_DISCONNECTED = "0+36 surface.panel, 36+1 " + rule(false, "surface.panel") + ", 37+20 surface.tableHeader, "
        + "57+1 border.divider, 58+269 surface.card, 327+1 " + rule(false, "surface.panel") + ", 328+28 surface.panel";

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theSftpRowAndTableMatchTheWorkstationMocks() throws Exception {
        String[] folders = {"Applications", "Applications (Parallels)", "bin", "Desktop", "Documents", "Downloads", "go", "IdeaProjects",
            "Library", "Movies", "Music", "Pictures"};
        for (boolean dark : new boolean[] {false, true}) under(dark, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("sftp-" + dark), EnterpriseLook.resolved(dark), PanelSurfaceFixture.SFTP, true)) {
                var entries = new java.util.ArrayList<Object>();
                for (String folder : folders) entries.add(fixture.fileEntry(folder, "DIRECTORY", 0));
                fixture.sftp("showPage", new Class<?>[] {String.class, String.class, java.util.List.class, long.class, long.class, boolean.class},
                    "Mac", "/Users/dustin", entries, 0L, (long) entries.size(), false);
                var crop = new MockMatch.Crop(dark ? DARK : CONNECTED, 48, 230, 600, 712);
                check((dark ? "dark" : "light") + "/sftp", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 8,
                    sftp(dark), 1, 0.55, dark ? new String[] {"selection.bg", "surface.zebra"} : new String[] {"selection.bg", "surface.zebra", "border.window"});
            }
        });
        under(false, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("sftp-off"), EnterpriseLook.resolved(false), PanelSurfaceFixture.SFTP, true)) {
                fixture.hosts(new String[][] {{"Mac", "192.168.1.119", "dustin", "Local Network"}});
                var crop = new MockMatch.Crop(DISCONNECTED, 48, 230, 600, 712);
                check("light/sftp-disconnected", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 8, SFTP_DISCONNECTED, 1, 0.55,
                    "text.primary", "surface.tableHeader", "button.secondary.border");
            }
        });
    }

    /** Down a classic dialog at x 18: its body to {@code body} on the kit's dialog surface (surface.app), the footer band's guarded rule and the band. */
    static String dialog(int body, int band) { return "0+" + body + " surface.app, " + band(body, band); }

    /** A classic dialog's footer from logical row {@code top}: the band's guarded rule and the band. */
    static String band(int top, int band) {
        return top + "+1 " + painted(dev.jasper.ui.swing.ClassicDialog.BAND_RULE) + ", " + (top + 1) + "+" + band + " surface.footer";
    }
    static final String ADD_HOST = dialog(476, 44);
    /**
     * The keys box's white run under 4c's ordered face: its side column (spec 14.4's Add Stored Key…, Remove, Move Up and
     * Move Down, which supersedes the mock's two buttons) sets it 106, taller than the mock's 56 (Execution notes R11).
     */
    static final int KEYS_BOX = 106;
    static final String ADD_HOST_KEYS = "0+3 surface.app, 3+1 border.control, 4+" + KEYS_BOX + " surface.card, " + (4 + KEYS_BOX) + "+1 border.control, "
        + (5 + KEYS_BOX) + "+4 surface.app";
    static final String NEW_TUNNEL = dialog(404, 44);
    /**
     * New Tunnel's edge floor, a recorded residual (plan 10a Execution deviations): spec 5.6 sets SUPPORTING and
     * SETTING_DESCRIPTION at 11 px where the mock draws its explanation and the checkbox's description at 12, so the
     * explanation keeps one line where the mock wraps it and the Ports group stands 16 px higher (0.535 measured; 0.637
     * with the mock's 12 px).
     */
    static final double NEW_TUNNEL_FLOOR = 0.53;

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void addHostAndNewTunnelMatchTheirMocks() throws Exception {
        under(false, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("dialogs"), EnterpriseLook.resolved(false), PanelSurfaceFixture.SESSIONS, true)) {
                fixture.hosts(new String[][] {{"Mac", "192.168.1.119", "dustin", "Local Network"}});
                fixture.invoke("dev.jasper.remote.hosts.new");
                var addHost = fixture.surface("Add Host");
                var hostCrop = new MockMatch.Crop("03 Add Host/Add Host - Default.png", 2, 60, 1040, 1042);
                var host = fixture.renderSurface(addHost, hostCrop.logicalWidth(), hostCrop.logicalHeight());
                check("light/add-host", hostCrop, host, 18, ADD_HOST, 1, 0.55, "surface.app", "surface.footer", "border.input", "accent");
                assertThat(MockMatch.mismatch(MockMatch.parse(ADD_HOST_KEYS, EnterpriseLook.theme(false)), MockMatch.bands(host, 298, 500, 500 + 2 * (KEYS_BOX + 9)), 1))
                    .as("the keys box").isEmpty();
                onEdt(addHost::requestClose);
                fixture.invoke("dev.jasper.remote.tunnels.new");
                var newTunnel = fixture.surface("New Tunnel");
                // The mock's ports, typed as a user does.
                onEdt(() -> { StagedVaultHost.type(newTunnel, "tunnel.listen.port.input", "8080"); StagedVaultHost.type(newTunnel, "tunnel.forward.port.input", "5432"); });
                StagedVaultHost.settle();
                var tunnelCrop = new MockMatch.Crop("04 New Tunnel/New Tunnel - Local.png", 2, 60, 1040, 898);
                check("light/new-tunnel", tunnelCrop, fixture.renderSurface(newTunnel, tunnelCrop.logicalWidth(), tunnelCrop.logicalHeight()),
                    // The route's target edge paints hue.green, which the IntelliJ Light port sets apart from status.success
                    // (the user's 2026-10-18 direction).
                    18, NEW_TUNNEL, 1, NEW_TUNNEL_FLOOR, "surface.app", "surface.footer", "accent", "hue.green");
            }
        });
    }

    static final String FILE_SERVERS = "0+20 surface.tableHeader, 20+1 border.divider, 21+335 surface.card";
    /**
     * The HTTP tag's tint and box. Its ink is 4c's hue through the text guard (plan 4c Rulings 11), where the mock draws
     * #2655AE, a recorded residual of the classic tag face. The tint is hue.blue.surface at the kit's Light tile share over
     * surface.card, and the box tag.border.blue, where the mock draws #E8EEFA and #B9C8EA (the user's 2026-10-18 direction).
     */
    static String[] fileServersTag() {
        double share = EnterpriseLook.kit().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.TILE_PERCENT_LIGHT) / 100.0;
        return new String[] {ColorMath.hex(ColorMath.mix(MockProfile.role(false, "hue.blue.surface"), MockProfile.role(false, "surface.card"), share)),
            "tag.border.blue"};
    }

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theFileServersTableMatchesTheWorkstationMocks() throws Exception {
        for (boolean dark : new boolean[] {false, true}) under(dark, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("servers-" + dark), EnterpriseLook.resolved(dark), PanelSurfaceFixture.FILE_SERVERS, true)) {
                fixture.fileServer("test", "HTTP");
                var crop = new MockMatch.Crop(dark ? DARK : CONNECTED, 48, 994, 600, 712);
                check((dark ? "dark" : "light") + "/file-servers", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 8,
                    FILE_SERVERS, 1, 0.55, dark ? new String[] {"surface.tableHeader", "surface.card"}
                        : new String[] {"surface.tableHeader", fileServersTag()[0], fileServersTag()[1]});
            }
        });
        under(false, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("servers-none"), EnterpriseLook.resolved(false), PanelSurfaceFixture.FILE_SERVERS, true)) {
                var crop = new MockMatch.Crop(DISCONNECTED, 48, 994, 600, 712);
                check("light/file-servers-none", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 8, FILE_SERVERS, 1, 0.55,
                    "text.primary", "text.secondary", "button.secondary.border");
            }
        });
    }

    /**
     * The keys table's empty body: 3c's empty line is one 22 px row holding "No keys added", where the mock centres the line
     * in a 44 px body; SDK 0.18's Table has no visible-row count, so the 22 is a recorded residual (plan 10a Execution
     * deviations).
     */
    static final int FS_KEYS_BODY = 22;
    static final String FS_KEYS = "0+2 surface.app, 2+1 border.control, 3+20 surface.tableHeader, 23+1 border.divider, 24+" + FS_KEYS_BODY + " surface.card, "
        + (24 + FS_KEYS_BODY) + "+1 border.control, " + (25 + FS_KEYS_BODY) + "+3 surface.app";
    /**
     * The keys band's device column: mock x 152, clear of the column titles. The mock's x 100 passes between the T and the y
     * of "Type", which 4c's classic table face sets 2 px left of the mock's (7 from the box's edge against 9, as in every
     * classic table), so its T crosses that column (a recorded residual).
     */
    static final int FS_KEYS_X = 150;

    @Test @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void newFileServerMatchesItsFourMocks() throws Exception {
        under(false, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("new-server"), EnterpriseLook.resolved(false), PanelSurfaceFixture.FILE_SERVERS, true)) {
                fixture.invoke("dev.jasper.fileserver.new");
                var editor = fixture.surface("New File Server");
                record Mock(String protocol, String file, int height, int body) { }
                for (var mock : java.util.List.of(new Mock("HTTP", "HTTP", 986, 448), new Mock("FTP", "FTP", 1210, 560),
                        new Mock("SFTP", "SFTP", 1172, 541), new Mock("TFTP", "TFTP", 862, 386))) {
                    fixture.dispatch("currentEditor", new dev.jasper.sdk.ui.UiEvent.Selected("server.protocol", java.util.Set.of(mock.protocol())));
                    var crop = new MockMatch.Crop("02 New File Server/New File Server - " + mock.file() + ".png", 2, 60, 1040, mock.height());
                    var render = fixture.renderSurface(editor, crop.logicalWidth(), crop.logicalHeight());
                    check("light/new-file-server-" + mock.file().toLowerCase(java.util.Locale.ROOT), crop, render, 18,
                        dialog(mock.body(), 44), 1, 0.55, "surface.app", "surface.footer", "border.input");
                    if (mock.protocol().equals("SFTP"))
                        assertThat(MockMatch.mismatch(MockMatch.parse(FS_KEYS, EnterpriseLook.theme(false)), MockMatch.bands(render, FS_KEYS_X, 595, 595 + 2 * (FS_KEYS_BODY + 28)), 1)).as("the keys table").isEmpty();
                }
            }
        });
    }
}
