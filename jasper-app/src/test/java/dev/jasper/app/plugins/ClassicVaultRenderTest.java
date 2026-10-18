package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockMatch;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Plan 10b's mock matching (spec 16.1.4): the staged Vault's classic composition in the real host, under the shipped
 * Enterprise kit, Plex fonts and theme (Light: the 07 mocks have no Dark), against the 07 Credential Vault mocks. Images
 * go to jasper-app/build/enterprise-compositions/light/, paired in tools/visual/pairs.json.
 */
class ClassicVaultRenderTest {
    static final String V = "07 Credential Vault/Credential Vault ";
    static final MockMatch.Crop SELECTED = new MockMatch.Crop(V + "3 - Item selected.png", 2, 60, 1640, 1142),
        NO_SELECTION = new MockMatch.Crop(V + "2 - New menu.png", 2, 60, 1640, 1142);
    static final String[][] LOGINS = {{"amp", "ubuntu", "synthetic-password-1"}, {"Proxmox", "root", "synthetic-password-2"}};
    static final String TOOLBAR = "0+6 #ECEDEF, 6+1 #B4B8BF, 7+20 #FFFFFF, 27+1 #B4B8BF, 28+6 #ECEDEF, 34+1 #C4C7CC";
    static final String STATUS = "0+1 #C4C7CC, 1+6 #E6E7EA, 7+1 #2655AE, 8+22 #2F65CA, 30+1 #2655AE, 31+6 #E6E7EA";
    @TempDir Path root;

    /**
     * The staged Vault in Enterprise mode, created (unbound) and unlocked, its manager open. Its views take the Enterprise
     * Light theme through the appearance fixture (without one the host falls back to the bundled default theme).
     */
    static StagedVaultHost vault(Path data) throws Exception {
        var vault = new StagedVaultHost(data, false, onEdtValue(() -> new WorkspaceAppearanceFixture(EnterpriseLook.resolved(false))), true);
        try { vault.createVault("synthetic-host-pass"); } catch (Throwable failure) { vault.close(); throw failure; }
        return vault;
    }

    /**
     * The surface's kit view and everything below it in its holder, {@code width} by {@code height} logical pixels,
     * painted at 2x over {@code surface.dialog}: what the mock crops show under a title bar or a dialog's header strip.
     * The headless windows run no shell, so a dialog's holder and view first take what the Enterprise host gives the
     * dialogs it draws in their window (plan 6b's {@code NativeShells.classicContent}: a transparent holder and
     * {@code CLASSIC_FLAGS}, {@code hostTitled}, {@code dialogBand} and {@code dialogCard}); the manager window's view
     * takes the band its frame gives it ({@code NativeShells.frame}, spec 10.8).
     */
    static BufferedImage render(AuxiliarySurface surface, int width, int height) {
        return onEdtValue(() -> {
            var holder = surface.holder();
            var content = StagedVaultHost.kitView(holder);
            if (surface.kind() != AuxiliarySurface.Kind.WINDOW) {
                holder.setOpaque(false);
                content.hostTitled(true); content.dialogBand(true); content.dialogCard(true);
            } else content.dialogBand(true);
            holder.setSize(width, height);
            for (int i = 0; i < 3; i++) SessionsSurfaceFixture.layout(holder);
            var view = StagedVaultHost.kitView(holder);
            int above = SwingUtilities.convertPoint(view.getParent(), view.getLocation(), holder).y;
            holder.setSize(width, height + above);
            for (int i = 0; i < 3; i++) SessionsSurfaceFixture.layout(holder);
            var image = new BufferedImage(width * 2, (height + above) * 2, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try { graphics.scale(2, 2); holder.printAll(graphics); } finally { graphics.dispose(); }
            Color under = EnterpriseLook.resolved(false).workspace().semantic().color(SemanticColor.SURFACE_DIALOG);
            return MockMatch.onto(image.getSubimage(0, above * 2, width * 2, height * 2), under);
        });
    }

    /** The render's bands down device column {@code x} between device rows {@code top} and {@code bottom} match {@code expected} (±1 px). */
    static void bands(String name, BufferedImage render, int x, int top, int bottom, String expected) {
        assertThat(MockMatch.mismatch(MockMatch.parse(expected), MockMatch.bands(render, x, top, bottom), 1))
            .as(name + " at x " + x + ", rows " + top + ".." + bottom).isEmpty();
    }

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theManagerHasTheMocksToolbarAndStatusRows() throws Exception {
        ClassicCompositionRenderTest.under(false, () -> {
            try (var vault = vault(root.resolve("frame"))) {
                vault.seedLogins(LOGINS);
                var render = render(vault.surface("Credential Vault"), NO_SELECTION.logicalWidth(), NO_SELECTION.logicalHeight());
                MockMatch.save("light/vault-frame", NO_SELECTION.read(), render);
                bands("the toolbar row", render, 1600, 0, 70, TOOLBAR);
                bands("the status row and Done", render, 1600, 1068, 1142, STATUS);
                assertThat(MockMatch.present(render, Color.decode("#C4C7CC"))).as("the classic rules").isTrue();
                assertThat(vault.problems).isEmpty();
            }
        });
    }

    static final String CATEGORIES = "0+34 #ECEDEF, 34+1 #C4C7CC, 35+24 #E1E3E6, 59+1 #C4C7CC, 60+4 #F7F7F8, 64+22 #2F65CA, 86+448 #F7F7F8, 534+1 #C4C7CC";
    static final String ITEMS = "0+20 #F0F1F3, 20+1 #D3D6DA, 21+22 #2F65CA, 43+22 #F7F8FA, 65+413 #FFFFFF, 478+1 #DDE0E4, 479+20 #F7F8FA, 499+1 #C4C7CC";

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theCategoriesAndItemsMatchTheItemSelectedMock() throws Exception {
        ClassicCompositionRenderTest.under(false, () -> {
            try (var vault = vault(root.resolve("columns"))) {
                var ids = vault.seedLogins(LOGINS);
                vault.dispatchToManager(new UiEvent.Selected("rows", Set.of(ids.getFirst().toString())));
                var render = render(vault.surface("Credential Vault"), SELECTED.logicalWidth(), SELECTED.logicalHeight());
                MockMatch.save("light/vault-columns", SELECTED.read(), render);
                bands("the Categories column", render, 6, 0, 1070, CATEGORIES);
                bands("the Items column", render, 854, 70, 1070, ITEMS);
                assertThat(MockMatch.present(render, Color.decode("#F0F1F3"))).as("the table's header band").isTrue();
                assertThat(MockMatch.present(render, Color.decode("#E1E3E6"))).as("the pane header strip").isTrue();
                assertThat(vault.problems).isEmpty();
            }
        });
    }
}
