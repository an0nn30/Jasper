package dev.jasper.app.splash;

import dev.jasper.app.startup.StartupSteps;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SplashQueueTest {
    @Test void elapsedTransitionRetargetsWithoutDebtAndNeverRegresses() {
        var q = new SplashQueue(List.of("one", "two", "three", "four", "日本語 🚀"));
        assertThat(q.frame(0)).isEqualTo(new SplashQueue.Frame(1, 0, 1));
        q.show(new StartupSteps.Step("two", 2, 5), 1_000_000_000L, true);
        assertThat(q.frame(1_000_000_000L)).isEqualTo(new SplashQueue.Frame(2, 1, 0));
        assertThat(q.frame(1_120_000_000L).progress()).isBetween(0.0, 1.0);
        q.show(new StartupSteps.Step("five", 5, 5), 1_120_000_000L, true);
        assertThat(q.frame(1_120_000_000L)).isEqualTo(new SplashQueue.Frame(5, 2, 0));
        assertThat(q.frame(1_360_000_000L)).isEqualTo(new SplashQueue.Frame(5, 0, 1));
        q.show(new StartupSteps.Step("two", 2, 5), 2_000_000_000L, true);
        assertThat(q.frame(2_000_000_000L).index()).isEqualTo(5);
    }

    @Test void disabledMotionSettlesImmediately() {
        var q = new SplashQueue(List.of("one", "two"));
        q.show(new StartupSteps.Step("two", 2, 2), 1, false);
        assertThat(q.frame(1)).isEqualTo(new SplashQueue.Frame(2, 0, 1));
    }
}
