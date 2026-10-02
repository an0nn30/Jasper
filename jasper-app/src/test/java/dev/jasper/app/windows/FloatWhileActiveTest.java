package dev.jasper.app.windows;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class FloatWhileActiveTest {
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final List<Boolean> applied = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final FloatWhileActive floating = new FloatWhileActive(changed -> {
        listeners.add(changed);
        return new Subscription(() -> listeners.remove(changed));
    }, active::get, applied::add);

    @Test void floatsWhileJasperIsActiveAndDropsWhileAnotherApplicationIs() {
        floating.update();
        assertThat(applied).as("shown while a Jasper window is active").containsExactly(true);
        active.set(false);
        listeners.getFirst().run();
        assertThat(applied).as("another application is active").containsExactly(true, false);
        active.set(true);
        listeners.getFirst().run();
        assertThat(applied).as("back in Jasper, above the window it blocks").containsExactly(true, false, true);
    }

    @Test void anUnchangedStateIsNotReappliedAndClosingStopsListening() {
        floating.update();
        listeners.getFirst().run();
        assertThat(applied).as("switching between Jasper windows changes nothing").containsExactly(true);
        floating.close();
        assertThat(listeners).isEmpty();
        active.set(false);
        floating.update();
        assertThat(applied).as("a closed dialog is left alone").containsExactly(true);
    }
}
