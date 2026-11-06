package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.testsupport.KitFixtures;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every {@code HostedAnchorContractTest} case under the Enterprise fixture kit, as {@code ClassicHostedKitContractTest}
 * runs the kit cases: the controls anchors are taken from, a menu button's among them, are then the classic faces'.
 */
class ClassicHostedAnchorContractTest extends HostedAnchorContractTest {
    private final Kit enterprise = KitFixtures.enterprise();
    private LookAndFeel prior;

    @BeforeEach void installTheEnterpriseKit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            prior = UIManager.getLookAndFeel();
            FlatLafInstaller.install(ThemeTestSupport.generated(false), enterprise);
        });
    }

    @AfterEach void restoreTheLookAndFeel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try { UIManager.setLookAndFeel(prior); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
        });
    }

    /** The premise of every inherited case: the Enterprise kit is the installed one while it runs. */
    @Test void everyCaseRunsWithTheEnterpriseKitInstalled() throws Exception {
        SwingUtilities.invokeAndWait(() -> assertThat(FlatLafInstaller.installedKit()).isSameAs(enterprise));
    }
}
