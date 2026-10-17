package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.app.testsupport.MockMatch;
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
     * which follows the UI face's glyphs and is asserted under the shipped IBM Plex ({@link MockCrops#fontsFloor}).
     */
    static void check(String name, MockMatch.Crop crop, BufferedImage render, int sampleX, String bands, int tolerance, double floor, String... colours) {
        var mock = crop.read();
        MockMatch.save(name, mock, render);
        assertThat(render.getWidth()).as(name + " width").isEqualTo(crop.width());
        assertThat(render.getHeight()).as(name + " height").isEqualTo(crop.height());
        assertThat(MockMatch.mismatch(MockMatch.parse(bands), MockMatch.bands(render, sampleX), tolerance)).as(name + " bands at x " + sampleX).isEmpty();
        for (String colour : colours) assertThat(MockMatch.present(render, Color.decode(colour))).as(name + " shows " + colour).isTrue();
        MockCrops.fontsFloor(name + " edge agreement", MockMatch.agreement(mock, render), floor);
    }

    /** Runs {@code body} under the installed Enterprise look in {@code dark}, then restores the default look. */
    static void under(boolean dark, ThrowingRunnable body) throws Exception {
        var controller = onEdtValue(() -> EnterpriseLook.controller(dark));
        try { body.run(); } finally { onEdt(() -> { controller.close(); EnterpriseLook.restoreDefault(); }); }
    }

    interface ThrowingRunnable { void run() throws Exception; }

    static final String SESSIONS_LIGHT = "0+36 #F7F7F8, 36+1 #DDE0E4, 37+20 #F0F1F3, 57+1 #D3D6DA, 58+44 #FFFFFF, 102+22 #2F65CA, "
        + "124+111 #FFFFFF, 235+1 #C4C7CC, 236+22 #F7F7F8, 258+1 #DDE0E4, 259+22 #F7F7F8, 281+1 #E6E8EB, 282+22 #F7F7F8, 304+1 #E6E8EB, "
        + "305+22 #F7F7F8, 327+1 #E6E8EB, 328+22 #F7F7F8, 350+1 #E6E8EB, 351+40 #F7F7F8";
    static final String SESSIONS_DARK = "0+36 #35383D, 36+1 #45484E, 37+20 #393C41, 57+1 #4A4D53, 58+44 #2E3135, 102+22 #3D74D6, "
        + "124+111 #2E3135, 235+1 #2A2C30, 236+22 #35383D, 258+1 #45484E, 259+22 #35383D, 281+1 #3F4247, 282+22 #35383D, 304+1 #3F4247, "
        + "305+22 #35383D, 327+1 #3F4247, 328+22 #35383D, 350+1 #3F4247, 351+40 #35383D";
    /**
     * The Dark mock's rule above the Properties is {@code border.window} (#2A2C30) at 1.19:1 on {@code surface.panel}; spec
     * 7.3's structural guard lifts it toward the ink to its 1.3:1 floor (#4E5054), a residual the guard decides.
     */
    static final String SESSIONS_DARK_GUARDED = SESSIONS_DARK.replace("235+1 #2A2C30", "235+1 #4E5054");
    /** The Disconnected mock stripes its second host (#F7F8FA); classic trees are not striped (Rulings 14), so the render reads white. */
    static final String SESSIONS_DISCONNECTED = "0+36 #F7F7F8, 36+1 #DDE0E4, 37+20 #F0F1F3, 57+1 #D3D6DA, 58+22 #FFFFFF, 80+22 #2F65CA, "
        + "102+133 #FFFFFF, 235+1 #C4C7CC, 236+22 #F7F7F8, 258+1 #DDE0E4, 259+22 #F7F7F8, 281+1 #E6E8EB, 282+22 #F7F7F8, 304+1 #E6E8EB, "
        + "305+22 #F7F7F8, 327+1 #E6E8EB, 328+22 #F7F7F8, 350+1 #E6E8EB, 351+40 #F7F7F8";

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theSessionsTreeAndPropertiesMatchTheWorkstationMocks() throws Exception {
        for (boolean dark : new boolean[] {false, true}) under(dark, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("sessions-" + dark), EnterpriseLook.resolved(dark), PanelSurfaceFixture.SESSIONS, true)) {
                var ids = fixture.hosts(new String[][] {{"Mac", "192.168.1.119", "dustin", "Local Network"},
                    {"minecraft-local", "192.168.1.124", "dustin", "Local Network", "favorite"}});
                fixture.selectSession(ids.get(1));
                var crop = new MockMatch.Crop(dark ? DARK : CONNECTED, 2136, 230, 700, 782);
                check((dark ? "dark" : "light") + "/sessions", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 4,
                    dark ? SESSIONS_DARK_GUARDED : SESSIONS_LIGHT, 1, 0.55,
                    dark ? new String[] {"#3D74D6", "#393C41", "#35383D"} : new String[] {"#2F65CA", "#F0F1F3", "#F7F7F8", "#E6E8EB"});
            }
        });
        under(false, () -> {
            try (var fixture = new PanelSurfaceFixture(root.resolve("sessions-locked"), EnterpriseLook.resolved(false), PanelSurfaceFixture.SESSIONS, true)) {
                var ids = fixture.hosts(new String[][] {{"Mac", "192.168.1.119", "dustin", "Local Network", "favorite", "vault"},
                    {"minecraft-local", "192.168.1.124", "dustin", "Local Network"}});
                fixture.selectSession(ids.get(0));
                var crop = new MockMatch.Crop(DISCONNECTED, 2136, 230, 700, 782);
                check("light/sessions-locked", crop, fixture.render(crop.logicalWidth(), crop.logicalHeight()), 4, SESSIONS_DISCONNECTED, 1, 0.55,
                    "#2F65CA", "#8A5A00");
            }
        });
    }
}
