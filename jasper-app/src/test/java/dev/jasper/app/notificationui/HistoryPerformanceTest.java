package dev.jasper.app.notificationui;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.notifications.*;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import static org.assertj.core.api.Assertions.assertThat;

/** Bounded actual-owner measurement; timings are evidence, never machine-dependent pass/fail limits. */
class HistoryPerformanceTest {
    @Test void fiveHundredEntriesRetainRowsThroughLiveThemeChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var controller = new ThemeController()) {
                var center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
                center.register(new NotificationGroup("perf", "Performance fixture", "jasper", DisplayMode.CARD));
                for (int i = 0; i < 500; i++) center.post(NotificationRequest.of("perf", Severity.INFO, "Fixture notification " + i)
                    .withContent("Completed fixture operation with details").withAction(NotificationAction.of("Review", () -> {})));
                assertThat(center.history()).hasSize(500);
                var site = new PanelSite(UUID.randomUUID(), () -> {}, () -> {}, () -> true);
                long started = System.nanoTime();
                var panel = new HistoryPanel(controller.appearance().context(ThemeRoute.CHROME), center, site, false);
                long mounted = System.nanoTime();
                try {
                    panel.setSize(400, 600); paint(panel);
                    long painted = System.nanoTime();
                    var retained = center.history().stream().map(n -> panel.row(n.id())).toList();
                    int views = count(panel);
                    assertThat(views).isGreaterThanOrEqualTo(1500);
                    var timings = new java.util.ArrayList<Double>();
                    for (var theme : List.of(Theme.JASPER_LIGHT, Theme.DARK, Theme.JASPER_LIGHT, Theme.DARK)) {
                        long start = System.nanoTime(); controller.configure(theme); paint(panel);
                        timings.add((System.nanoTime() - start) / 1_000_000.0);
                        assertThat(center.history().stream().map(n -> panel.row(n.id())).toList()).containsExactlyElementsOf(retained);
                    }
                    System.out.printf(java.util.Locale.ROOT,
                        "HISTORY_PERF runtime=%s vendor=%s entries=500 kitViews=%d mountMs=%.3f layoutPaintMs=%.3f themeLayoutPaintMs=%s%n",
                        System.getProperty("java.runtime.version"), System.getProperty("java.vendor"), views,
                        (mounted-started)/1_000_000.0, (painted-mounted)/1_000_000.0, timings);
                } finally { site.notifyClosed(); }
            }
        });
    }
    private static void paint(Container panel) {
        for (int i=0;i<3;i++) LayoutTestSupport.layoutTree(panel);
        var image=new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_ARGB);
        var graphics=image.createGraphics(); try { panel.paint(graphics); } finally { graphics.dispose(); }
    }
    private static int count(Component component) {
        int count=component instanceof KitView ? 1 : 0;
        if(component instanceof Container container) for(var child:container.getComponents()) count+=count(child);
        return count;
    }
}
