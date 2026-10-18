package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.app.testsupport.MockMatch;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
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

    static final String DETAILS = "0+24 #E1E3E6, 24+1 #C4C7CC, 25+77 #ECEDEF, 102+1 #D3D6DA, 103+26 #FFFFFF, 129+1 #E6E8EB, 130+26 #FFFFFF, "
        + "156+1 #D3D6DA, 157+8 #ECEDEF, 165+1 #C4C7CC, 166+61 #ECEDEF, 227+1 #B4B8BF, 228+22 #FEFEFE~#EEF0F2, 250+1 #B4B8BF, 251+248 #ECEDEF, "
        + "499+1 #C4C7CC";
    static final String DETAILS_NONE = "0+24 #E1E3E6, 24+1 #C4C7CC, 25+474 #ECEDEF, 499+1 #C4C7CC";

    @Test @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void theManagerMatchesItsMocksWithAndWithoutASelection() throws Exception {
        ClassicCompositionRenderTest.under(false, () -> {
            try (var vault = vault(root.resolve("details"))) {
                var ids = vault.seedLogins(LOGINS);
                var manager = vault.surface("Credential Vault");
                var none = render(manager, NO_SELECTION.logicalWidth(), NO_SELECTION.logicalHeight());
                MockMatch.save("light/vault-manager", NO_SELECTION.read(), none);
                bands("the Details pane without a selection", none, 1600, 70, 1070, DETAILS_NONE);
                MockCrops.fontsFloor("light/vault-manager edge agreement", MockMatch.agreement(NO_SELECTION.read(), none), 0.55);
                vault.dispatchToManager(new UiEvent.Selected("rows", Set.of(ids.getFirst().toString())));
                var selected = render(manager, SELECTED.logicalWidth(), SELECTED.logicalHeight());
                MockMatch.save("light/vault-manager-selected", SELECTED.read(), selected);
                bands("the Details pane of amp", selected, 1085, 70, 1070, DETAILS);
                bands("the Categories column", selected, 6, 0, 1070, CATEGORIES);
                bands("the Items column", selected, 854, 70, 1070, ITEMS);
                bands("the toolbar row", selected, 1600, 0, 70, TOOLBAR);
                bands("the status row and Done", selected, 1600, 1068, 1142, STATUS);
                for (String colour : List.of("#2F65CA", "#E1E3E6", "#F0F1F3", "#F7F8FA", "#E6E7EA", "#C4C7CC"))
                    assertThat(MockMatch.present(selected, Color.decode(colour))).as("shows " + colour).isTrue();
                MockCrops.fontsFloor("light/vault-manager-selected edge agreement", MockMatch.agreement(SELECTED.read(), selected), 0.55);
                assertThat(vault.problems).isEmpty();
            }
        });
    }
}
