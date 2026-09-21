package dev.jasper.terminal;
import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
class BellControllerTest {
    @Test void staleSoundIsRejectedAndNewDeliveryUsesTheCurrentSoundCallback() throws Exception {
        var oldSound = new AtomicInteger();
        var newSound = new AtomicInteger();
        var owner = new AtomicReference<BellController>();
        SwingUtilities.invokeAndWait(() -> {
            var bell = new BellController(() -> BellMode.SOUND, () -> {}, oldSound::incrementAndGet);
            owner.set(bell);
            bell.attach(7);
            bell.signal(7);
            bell.detach();
            bell.attach(8);
            bell.setSound(newSound::incrementAndGet);
            bell.signal(7);
            bell.signal(8);
            bell.signal(8);
        });
        SwingUtilities.invokeAndWait(() -> {
            assertThat(oldSound).hasValue(0);
            assertThat(newSound).hasValue(1);
            owner.get().detach();
        });
    }
}
