package dev.moray.app;

import javax.swing.SwingUtilities;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Application-owned, serialized source of operating-system light/dark appearance. */
final class SystemAppearance implements AutoCloseable {
    record Binding(BooleanSupplier dark, Consumer<Consumer<Boolean>> register,
                   Consumer<Consumer<Boolean>> remove) {
        Binding {
            Objects.requireNonNull(dark, "dark");
            Objects.requireNonNull(register, "register");
            Objects.requireNonNull(remove, "remove");
        }
    }

    record Reading(BuiltinTheme theme, String warning) {
        Reading {
            Objects.requireNonNull(theme, "theme");
            Objects.requireNonNull(warning, "warning");
        }
    }

    private final Supplier<Binding> factory;
    private final ExecutorService worker;
    private final Consumer<Runnable> publisher;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Consumer<Boolean> callback = ignored -> enqueue(this::readAndPublish);

    private Consumer<Reading> listener = ignored -> {};
    private Binding binding;
    private boolean cleanupRequired;
    private Reading last;

    SystemAppearance(Supplier<Binding> factory, ExecutorService worker, Consumer<Runnable> publisher) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    static SystemAppearance production() {
        return new SystemAppearance(SystemAppearance::nativeBinding, daemonWorker(), SwingUtilities::invokeLater);
    }

    static SystemAppearance fixed(BuiltinTheme theme) {
        Objects.requireNonNull(theme, "theme");
        return new SystemAppearance(() -> new Binding(() -> theme == BuiltinTheme.DARK,
            ignored -> {}, ignored -> {}), daemonWorker(), SwingUtilities::invokeLater);
    }

    void start(Consumer<Reading> nextListener) {
        Objects.requireNonNull(nextListener, "listener");
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("System appearance source has already been started");
        }
        listener = nextListener;
        enqueue(this::initialize);
    }

    private void initialize() {
        try {
            Binding created = factory.get();
            if (closed.get()) return;
            binding = Objects.requireNonNull(created, "appearance binding");
            cleanupRequired = true;
            binding.register().accept(callback);
            readAndPublish();
        } catch (RuntimeException | LinkageError failure) {
            publishFailure(failure);
        }
    }

    private void readAndPublish() {
        if (binding == null) return;
        try {
            publish(reading(binding.dark().getAsBoolean()));
        } catch (RuntimeException | LinkageError failure) {
            publishFailure(failure);
        }
    }

    private void publishFailure(Throwable failure) {
        String detail = failure.getMessage();
        if (detail == null || detail.isBlank()) detail = failure.getClass().getSimpleName();
        publish(new Reading(BuiltinTheme.DARK,
            "Could not read system appearance (" + detail + "); using Dark."));
    }

    private static Reading reading(boolean dark) {
        return new Reading(dark ? BuiltinTheme.DARK : BuiltinTheme.LIGHT, "");
    }

    private void enqueue(Runnable action) {
        if (closed.get()) return;
        try {
            worker.execute(() -> {
                if (!closed.get()) action.run();
            });
        } catch (RejectedExecutionException failure) {
            if (!closed.get()) throw failure;
        }
    }

    private void publish(Reading reading) {
        if (reading.equals(last)) return;
        last = reading;
        publisher.accept(() -> {
            if (!closed.get()) listener.accept(reading);
        });
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        worker.execute(() -> {
            if (binding != null && cleanupRequired) {
                try {
                    binding.remove().accept(callback);
                } catch (RuntimeException | LinkageError ignored) {
                    // The owner is closed, so cleanup failures have no UI consumer.
                }
            }
            binding = null;
            cleanupRequired = false;
            listener = ignored -> {};
        });
        worker.shutdown();
    }

    private static ExecutorService daemonWorker() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "moray-appearance");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static Binding nativeBinding() {
        if (!com.jthemedetecor.OsThemeDetector.isSupported()) {
            throw new IllegalStateException("System appearance is unavailable on this desktop; using Dark.");
        }
        var detector = com.jthemedetecor.OsThemeDetector.getDetector();
        return new Binding(detector::isDark, detector::registerListener, detector::removeListener);
    }
}
