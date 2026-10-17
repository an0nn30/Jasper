package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.testsupport.KitFixtures;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Spec 16.1.9 (plan 4c Rulings 23): every {@code HostedKitContractTest} case under the Enterprise fixture kit. A hosted
 * view's context is made over {@code FlatLafInstaller.installedKit()} ({@code HostedKitTest} proves it), so installing
 * the kit around each case runs the host's controls through the classic faces.
 */
class ClassicHostedKitContractTest extends HostedKitContractTest {
    private final Kit enterprise = KitFixtures.enterprise();
    private LookAndFeel prior;

    @BeforeEach void installTheEnterpriseKit() throws Exception {
        edt(() -> { prior = UIManager.getLookAndFeel(); FlatLafInstaller.install(ThemeTestSupport.generated(false), enterprise); });
    }

    @AfterEach void restoreTheLookAndFeel() throws Exception {
        edt(() -> {
            try { UIManager.setLookAndFeel(prior); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
        });
    }

    /** The premise of every inherited case: the Enterprise kit is the installed one while it runs. */
    @Test void everyCaseRunsWithTheEnterpriseKitInstalled() throws Exception {
        edt(() -> assertThat(FlatLafInstaller.installedKit()).isSameAs(enterprise));
    }
}
