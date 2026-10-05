package dev.jasper.app.appearance;

import dev.jasper.app.platform.ThemeZone;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.swing.FlatLafInstaller;
import java.util.ArrayList;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

/**
 * Start-up from a fresh process (no Jasper snapshot) whose saved theme cannot be installed: the failed attempt is rolled
 * back completely, and the Jasper Dark fallback is installed before start-up returns, so no ThemeZone or icon read can
 * precede an installed snapshot. JasperApplication constructs its ThemeController through this sequence first.
 */
@ExtendWith(EdtTestExtension.class)
class ThemeControllerStartupTest {
    @AfterEach void reset() throws Exception { javax.swing.SwingUtilities.invokeAndWait(ThemeController::new); }

    @Test void aSavedThemeThatCannotInstallFallsBackToJasperDarkBeforeTheSnapshotIsRead() throws Exception {
        UIManager.setLookAndFeel(new MetalLookAndFeel());
        assertThatIllegalStateException().as("a fresh process has no snapshot yet").isThrownBy(FlatLafInstaller::installedTheme);
        var broken = Theme.JASPER_LIGHT;
        var attempts = new ArrayList<Theme>();
        var reported = new ArrayList<ThemeController.InstallationFailure>();
        var themes = ThemeController.startup(broken, theme -> {
            attempts.add(theme);
            boolean installed = ThemeManager.install(theme);
            if (theme == broken) throw new IllegalStateException("corrupt selected theme");
            return installed;
        }, failure -> {
            reported.add(failure);
            assertThatIllegalStateException().as("the partial install of the broken theme was rolled back")
                .isThrownBy(FlatLafInstaller::installedTheme);
        });
        assertThat(attempts).containsExactly(broken, Theme.DARK);
        assertThat(reported).singleElement().satisfies(failure -> assertThat(failure).hasMessageContaining(broken.name()));
        assertThat(themes.choice()).isEqualTo(Theme.DARK);
        assertThat(FlatLafInstaller.installedTheme()).isSameAs(ThemeManager.generated(Theme.DARK));
        assertThat(ThemeZone.theme(null)).isSameAs(ThemeManager.generated(Theme.DARK));
        assertThat(UIManager.getLookAndFeel().getName()).isEqualTo(Theme.DARK.name());
    }

    @Test void anInstallableSavedThemeStartsWithoutFallback() throws Exception {
        UIManager.setLookAndFeel(new MetalLookAndFeel());
        var reported = new ArrayList<ThemeController.InstallationFailure>();
        var themes = ThemeController.startup(Theme.JASPER_LIGHT, ThemeManager::install, reported::add);
        assertThat(reported).isEmpty();
        assertThat(themes.choice()).isEqualTo(Theme.JASPER_LIGHT);
        assertThat(FlatLafInstaller.installedTheme()).isSameAs(ThemeManager.generated(Theme.JASPER_LIGHT));
    }
}
