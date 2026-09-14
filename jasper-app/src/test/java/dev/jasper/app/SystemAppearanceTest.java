package dev.jasper.app;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemAppearanceTest {
    @Test void serializesReadAndEventsAndDropsQueuedPublicationAfterClose() throws Exception {
        var callbacks = new CopyOnWriteArrayList<Consumer<Boolean>>();
        var queue = new ConcurrentLinkedQueue<Runnable>();
        var received = new CopyOnWriteArrayList<SystemAppearance.Reading>();
        var dark = new AtomicBoolean(false);
        var worker = Executors.newSingleThreadExecutor();
        var source = new SystemAppearance(() -> new SystemAppearance.Binding(dark::get,
            callbacks::add, callbacks::remove), worker, queue::add);
        try {
            source.start(received::add);
            until(() -> !queue.isEmpty());
            queue.remove().run();
            assertThat(received).extracting(SystemAppearance.Reading::theme)
                .containsExactly(BuiltinTheme.LIGHT);
            dark.set(true);
            callbacks.getFirst().accept(true);
            until(() -> !queue.isEmpty());
            source.close();
            queue.remove().run();
            assertThat(received).hasSize(1);
            until(callbacks::isEmpty);
        } finally {
            source.close();
        }
    }

    @Test void suppressesDuplicateHealthyReadings() throws Exception {
        var callbacks = new CopyOnWriteArrayList<Consumer<Boolean>>();
        var received = new CopyOnWriteArrayList<SystemAppearance.Reading>();
        var dark = new AtomicBoolean(true);
        var reads = new AtomicInteger();
        try (var source = source(() -> new SystemAppearance.Binding(() -> {
            reads.incrementAndGet();
            return dark.get();
        },
            callbacks::add, callbacks::remove), Runnable::run)) {
            source.start(received::add);
            until(() -> received.size() == 1);
            callbacks.getFirst().accept(false);
            callbacks.getFirst().accept(false);
            until(() -> reads.get() == 3);
            assertThat(received).containsExactly(new SystemAppearance.Reading(BuiltinTheme.DARK, ""));
        }
    }

    @Test void reportsUnsupportedAndInitializationFailuresAsDark() throws Exception {
        for (Throwable failure : new Throwable[] {
            new IllegalStateException("System appearance is unavailable on this desktop; using Dark."),
            new LinkageError("native library missing")
        }) {
            var received = new CopyOnWriteArrayList<SystemAppearance.Reading>();
            try (var source = source(() -> { throwFailure(failure); return null; }, Runnable::run)) {
                source.start(received::add);
                until(() -> received.size() == 1);
                assertThat(received.getFirst().theme()).isEqualTo(BuiltinTheme.DARK);
                assertThat(received.getFirst().warning()).contains("using Dark");
                assertThat(received.getFirst().warning()).contains(failure.getMessage());
            }
        }
    }

    @Test void registerBeforeReadRaceResamplesCurrentValue() throws Exception {
        var callbacks = new CopyOnWriteArrayList<Consumer<Boolean>>();
        var received = new CopyOnWriteArrayList<SystemAppearance.Reading>();
        var dark = new AtomicBoolean(false);
        var binding = new SystemAppearance.Binding(dark::get, callback -> {
            callbacks.add(callback);
            dark.set(true);
            callback.accept(false);
        }, callbacks::remove);
        try (var source = source(() -> binding, Runnable::run)) {
            source.start(received::add);
            until(() -> received.size() == 1);
            until(() -> callbacks.size() == 1);
            assertThat(received).containsExactly(new SystemAppearance.Reading(BuiltinTheme.DARK, ""));
        }
    }

    @Test void readFailureKeepsRegistrationAndLaterEventClearsWarning() throws Exception {
        var callbacks = new CopyOnWriteArrayList<Consumer<Boolean>>();
        var received = new CopyOnWriteArrayList<SystemAppearance.Reading>();
        var failRead = new AtomicBoolean(true);
        var binding = new SystemAppearance.Binding(() -> {
            if (failRead.get()) throw new IllegalStateException("appearance read failed");
            return false;
        }, callbacks::add, callbacks::remove);
        try (var source = source(() -> binding, Runnable::run)) {
            source.start(received::add);
            until(() -> received.size() == 1);
            assertThat(received.getFirst().warning()).contains("appearance read failed").contains("using Dark");
            failRead.set(false);
            callbacks.getFirst().accept(true);
            until(() -> received.size() == 2);
            assertThat(received.getLast()).isEqualTo(new SystemAppearance.Reading(BuiltinTheme.LIGHT, ""));
        }
    }

    @Test void callbackAfterCloseDoesNotReadOrPublish() throws Exception {
        var callbacks = new CopyOnWriteArrayList<Consumer<Boolean>>();
        var received = new CopyOnWriteArrayList<SystemAppearance.Reading>();
        var reads = new AtomicInteger();
        var source = source(() -> new SystemAppearance.Binding(() -> {
            reads.incrementAndGet();
            return false;
        }, callbacks::add, callbacks::remove), Runnable::run);
        try {
            source.start(received::add);
            until(() -> received.size() == 1);
            Consumer<Boolean> callback = callbacks.getFirst();
            source.close();
            until(callbacks::isEmpty);

            callback.accept(true);

            assertThat(reads).hasValue(1);
            assertThat(received).containsExactly(new SystemAppearance.Reading(BuiltinTheme.LIGHT, ""));
        } finally {
            source.close();
        }
    }

    @Test void closeBeforeInitializationPublishesNothing() throws Exception {
        var callbacks = new CopyOnWriteArrayList<Consumer<Boolean>>();
        var queue = new ConcurrentLinkedQueue<Runnable>();
        var received = new CopyOnWriteArrayList<SystemAppearance.Reading>();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var worker = Executors.newSingleThreadExecutor();
        var source = new SystemAppearance(() -> {
            entered.countDown();
            await(release);
            return new SystemAppearance.Binding(() -> false, callbacks::add, callbacks::remove);
        }, worker, queue::add);
        source.start(received::add);
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        source.close();
        release.countDown();
        until(worker::isTerminated);
        assertThat(queue).isEmpty();
        assertThat(callbacks).isEmpty();
        assertThat(received).isEmpty();
    }

    @Test void closeIsIdempotentAndRemovesPartiallyRegisteredCallback() throws Exception {
        var callbacks = new CopyOnWriteArrayList<Consumer<Boolean>>();
        var removals = new AtomicInteger();
        var registered = new CountDownLatch(1);
        var binding = new SystemAppearance.Binding(() -> false, callback -> {
            callbacks.add(callback);
            registered.countDown();
            throw new IllegalStateException("registration failed");
        }, callback -> {
            callbacks.remove(callback);
            removals.incrementAndGet();
        });
        var source = source(() -> binding, Runnable::run);
        source.start(ignored -> {});
        assertThat(registered.await(5, TimeUnit.SECONDS)).isTrue();
        source.close();
        source.close();
        until(() -> removals.get() == 1);
        assertThat(callbacks).isEmpty();
    }

    @Test void startMayOnlyBeCalledOnce() {
        try (var source = SystemAppearance.fixed(BuiltinTheme.LIGHT)) {
            source.start(ignored -> {});
            assertThatThrownBy(() -> source.start(ignored -> {}))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    private static SystemAppearance source(java.util.function.Supplier<SystemAppearance.Binding> factory,
                                           Consumer<Runnable> publisher) {
        return new SystemAppearance(factory, Executors.newSingleThreadExecutor(), publisher);
    }

    private static void until(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static void throwFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtime) throw runtime;
        throw (LinkageError) failure;
    }
}
