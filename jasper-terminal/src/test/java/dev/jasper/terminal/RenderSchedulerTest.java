package dev.jasper.terminal;
import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
class RenderSchedulerTest {
@Test void anOldPublicationCannotUseANewAttachmentsToken() throws Exception {
    AtomicInteger paints = new AtomicInteger();
    AtomicReference<RenderScheduler> owner = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
        RenderScheduler scheduler = new RenderScheduler(() -> {}, paints::incrementAndGet,
            () -> {}, () -> false);
        owner.set(scheduler);
        long oldGeneration = scheduler.attach();
        scheduler.showing(true);
        AtomicBoolean oldToken = scheduler.pendingToken();
        scheduler.detach();
        scheduler.attach();
        scheduler.showing(true);
        scheduler.frameTimerFinished();
        paints.set(0);
        scheduler.publishDirty(oldGeneration,oldToken);
    });
    SwingUtilities.invokeAndWait(() -> {
        RenderScheduler scheduler = owner.get();
        assertThat(paints).hasValue(0);
        scheduler.markDirty(scheduler.generation());
    });
    SwingUtilities.invokeAndWait(() -> {
        RenderScheduler scheduler = owner.get();
        scheduler.frameTimerFinished();
        assertThat(paints.get()).isPositive();
        scheduler.detach();
    });
}
}
