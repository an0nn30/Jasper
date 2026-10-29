package dev.jasper.app.bootstrap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LoginItemUpdatesTest {
    @Test void theLatestPendingValueWinsAndOnlyOneWorkerRuns() {
        var workers = new ArrayDeque<Runnable>();
        var applied = new ArrayList<Boolean>();
        LoginItemUpdates[] updates = new LoginItemUpdates[1];
        updates[0] = new LoginItemUpdates(value -> {
            applied.add(value);
            if (applied.size() == 1) {
                updates[0].accept(false);
                updates[0].accept(true);
                updates[0].accept(false);
                assertThat(workers).isEmpty();
            }
        }, workers::add);
        updates[0].accept(true);
        updates[0].accept(true);
        assertThat(workers).hasSize(1);
        workers.remove().run();
        assertThat(applied).containsExactly(true, false);
        updates[0].accept(false);
        assertThat(workers).isEmpty();
        updates[0].accept(true);
        assertThat(workers).hasSize(1);
        workers.remove().run();
        assertThat(applied).containsExactly(true, false, true);
    }

    @Test void aFailureDoesNotLoseANewerChoice() {
        var workers = new ArrayDeque<Runnable>();
        var applied = new ArrayList<Boolean>();
        LoginItemUpdates[] updates = new LoginItemUpdates[1];
        updates[0] = new LoginItemUpdates(value -> {
            applied.add(value);
            if (value) {
                updates[0].accept(false);
                throw new IllegalStateException("write failed");
            }
        }, workers::add);
        updates[0].accept(true);
        workers.remove().run();
        assertThat(applied).isEqualTo(List.of(true, false));
        assertThat(workers).isEmpty();
    }
}
