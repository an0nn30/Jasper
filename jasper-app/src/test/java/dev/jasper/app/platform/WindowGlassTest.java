package dev.jasper.app.platform;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class WindowGlassTest {
    private final List<String> calls = new ArrayList<>();
    private final List<Double> published = new ArrayList<>();
    /** What each live watch runs when something else makes the window opaque again. */
    private final List<Runnable> watches = new ArrayList<>();
    private boolean refuse, noBlur;
    private final WindowGlass.Platform platform = new WindowGlass.Platform(true,
        root -> { calls.add("translucent"); return !refuse; },
        root -> calls.add("opaque"),
        (root, radius) -> { calls.add("blur " + radius); return !noBlur; },
        (root, reset) -> { watches.add(reset); return () -> watches.remove(reset); });
    private final JRootPane root = new JRootPane();
    private final JPanel host = new JPanel(new BorderLayout());

    private WindowGlass glass(WindowGlass.Platform chosen) {
        host.add(root);
        return new WindowGlass(root, chosen, published::add);
    }

    @Test void waitsForAPeerThenGoesTranslucentWithItsBlur() {
        var glass = glass(platform);
        glass.request(0.95, 0.5);
        assertThat(calls).isEmpty();
        assertThat(glass.opacity()).isEqualTo(1);

        host.addNotify();

        assertThat(calls).containsExactly("translucent", "blur 20");
        assertThat(glass.opacity()).isEqualTo(0.95);
        assertThat(published).containsExactly(0.95);
    }

    @Test void anOpaqueRequestTouchesNothingEvenWithABlur() {
        host.addNotify();
        var glass = glass(platform);
        glass.request(1, 0.5);
        assertThat(calls).isEmpty();
        assertThat(glass.opacity()).isEqualTo(1);
        assertThat(published).isEmpty();
    }

    @Test void returningToOpaqueRestoresTheWindowAndClearsTheBlur() {
        host.addNotify();
        var glass = glass(platform);
        glass.request(0.8, 0.25);
        glass.request(1, 0.25);
        assertThat(calls).containsExactly("translucent", "blur 10", "opaque", "blur 0");
        assertThat(published).containsExactly(0.8, 1.0);
    }

    @Test void aRefusedWindowStaysOpaqueAndIsNotAskedAgain() {
        refuse = true;
        host.addNotify();
        var glass = glass(platform);
        glass.request(0.5, 0);
        glass.request(0.6, 0);
        assertThat(calls).containsExactly("translucent");
        assertThat(glass.opacity()).isEqualTo(1);
        assertThat(published).isEmpty();
    }

    @Test void aMissingBlurBridgeKeepsTheTranslucency() {
        noBlur = true;
        host.addNotify();
        var glass = glass(platform);
        glass.request(0.9, 0.5);
        assertThat(glass.opacity()).isEqualTo(0.9);
        assertThat(calls).containsExactly("translucent", "blur 20");
    }

    @Test void unsupportedPlatformsReportOpaque() {
        host.addNotify();
        var glass = glass(WindowGlass.Platform.NONE);
        glass.request(0.5, 1);
        assertThat(glass.opacity()).isEqualTo(1);
        assertThat(published).isEmpty();
    }

    @Test void aNewPeerIsMadeTranslucentAgain() {
        host.addNotify();
        var glass = glass(platform);
        glass.request(0.9, 0);
        host.removeNotify();
        assertThat(glass.opacity()).isEqualTo(1);
        host.addNotify();
        assertThat(calls).containsExactly("translucent", "translucent");
        assertThat(published).containsExactly(0.9, 1.0, 0.9);
    }

    @Test void closedGlassIgnoresRequestsAndPeers() {
        host.addNotify();
        var glass = glass(platform);
        glass.close();
        glass.request(0.5, 0.5);
        host.removeNotify();
        host.addNotify();
        assertThat(calls).isEmpty();
        assertThat(published).isEmpty();
    }

    /** Runs what this EDT turn queued (the test method itself runs on the EDT). */
    private static void runQueuedEvents() {
        var loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        SwingUtilities.invokeLater(loop::exit);
        loop.enter();
    }

    /** Something else making the window opaque, as a look-and-feel switch's deferred frame background does. */
    private void resetElsewhere() {
        assertThat(watches).as("a translucent window is watched").hasSize(1);
        watches.getFirst().run();
    }

    @Test void anOutsideOpaqueResetReportsOpaqueThenGoesTranslucentAgain() {
        host.addNotify();
        var glass = glass(platform);
        glass.request(0.95, 0.5);
        assertThat(calls).containsExactly("translucent", "blur 20");

        resetElsewhere();
        assertThat(glass.opacity()).as("the window is opaque until the next turn").isEqualTo(1);
        assertThat(published).containsExactly(0.95, 1.0);
        assertThat(watches).isEmpty();

        runQueuedEvents();
        assertThat(calls).containsExactly("translucent", "blur 20", "translucent");
        assertThat(glass.opacity()).isEqualTo(0.95);
        assertThat(published).containsExactly(0.95, 1.0, 0.95);
        assertThat(watches).as("the new translucency is watched").hasSize(1);
    }

    @Test void anOpaqueRequestBeforeTheNextTurnWins() {
        host.addNotify();
        var glass = glass(platform);
        glass.request(0.95, 0.5);
        resetElsewhere();
        glass.request(1, 0.5);
        runQueuedEvents();
        assertThat(calls).containsExactly("translucent", "blur 20", "blur 0");
        assertThat(glass.opacity()).isEqualTo(1);
        assertThat(published).containsExactly(0.95, 1.0);
        assertThat(watches).isEmpty();
    }

    @Test void watchingStopsWithTheTranslucency() {
        host.addNotify();
        var glass = glass(platform);
        glass.request(0.8, 0);
        assertThat(watches).hasSize(1);
        glass.request(1, 0);
        assertThat(watches).as("opaque again").isEmpty();
        glass.request(0.8, 0);
        host.removeNotify();
        assertThat(watches).as("the native window is gone").isEmpty();
        host.addNotify();
        assertThat(watches).hasSize(1);
        glass.close();
        assertThat(watches).as("closed").isEmpty();
    }

    @Test void aResetAfterCloseIsIgnored() {
        host.addNotify();
        var glass = glass(platform);
        glass.request(0.9, 0);
        var reset = watches.getFirst();
        glass.close();
        reset.run();
        runQueuedEvents();
        assertThat(calls).containsExactly("translucent");
        assertThat(published).containsExactly(0.9);
    }

    @Test void blurMapsTerminalAppsScaleOntoARadius() {
        assertThat(WindowGlass.radius(0)).isZero();
        assertThat(WindowGlass.radius(0.5)).isEqualTo(20);
        assertThat(WindowGlass.radius(1)).isEqualTo(WindowGlass.MAX_BLUR_RADIUS);
        assertThat(WindowGlass.radius(2)).isEqualTo(WindowGlass.MAX_BLUR_RADIUS);
        assertThat(WindowGlass.radius(-1)).isZero();
    }
}
