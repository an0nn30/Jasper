package dev.jasper.app.application;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/** Application-owned management dispatcher; the independent supervisor survives its disposal. */
public final class CygwinFeature implements AutoCloseable {
    private final Executor worker, ui;
    private final Callable<String> manage;
    private final Runnable refresh;
    private final BiConsumer<Boolean,String> notify;
    private boolean running, closed;
    public CygwinFeature(Executor worker, Executor ui, Callable<String> manage, Runnable refresh, BiConsumer<Boolean,String> notify) {
        this.worker = Objects.requireNonNull(worker); this.ui = Objects.requireNonNull(ui); this.manage = Objects.requireNonNull(manage);
        this.refresh = Objects.requireNonNull(refresh); this.notify = Objects.requireNonNull(notify);
    }
    public void manage() {
        if (closed || running) return;
        running = true;
        try { worker.execute(() -> {
            String result; boolean failed;
            try { result = manage.call(); failed = false; }
            catch (Exception error) { result = Objects.toString(error.getMessage(), error.toString()); failed = true; }
            String message = result; boolean error = failed;
            ui.execute(() -> {
                running = false;
                if (closed) return;
                refresh.run(); notify.accept(error, message);
            });
        }); } catch (RuntimeException rejected) { running = false; notify.accept(true, "Could not start Cygwin package management: " + rejected.getMessage()); }
    }
    @Override public void close() { closed = true; }
}
