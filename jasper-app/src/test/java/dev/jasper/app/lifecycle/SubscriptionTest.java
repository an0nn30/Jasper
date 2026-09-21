package dev.jasper.app.lifecycle;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SubscriptionTest {
@Test void reentrantCloseRemovesOnlyOnce() {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var holder = new Subscription[1];
    holder[0] = new Subscription(() -> { calls.incrementAndGet(); holder[0].close(); });
    holder[0].close(); holder[0].close();
    assertThat(calls).hasValue(1);
}
}
