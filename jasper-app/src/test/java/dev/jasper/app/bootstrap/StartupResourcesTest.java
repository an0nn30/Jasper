package dev.jasper.app.bootstrap;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class StartupResourcesTest {
@Test void rollbackIsReverseOrderedOnceOnlyAndPreservesOriginalFailure() {
    var order = new java.util.ArrayList<String>();
    var resources = new StartupResources();
    resources.own((AutoCloseable) () -> order.add("first"));
    resources.own((AutoCloseable) () -> { order.add("second"); throw new IllegalStateException("cleanup"); });
    var original = new IllegalArgumentException("startup");
    resources.rollback(original); resources.close();
    assertThat(order).containsExactly("second", "first");
    assertThat(original.getSuppressed()).extracting(Throwable::getMessage).containsExactly("cleanup");
}
    @Test void transferredResourcesBelongOnlyToTheirReceivingOwner() {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var scope = new StartupResources();
        scope.own((AutoCloseable) count::incrementAndGet);
        scope.transfer(); scope.close();
        assertThat(count).hasValue(0);
    }
}
