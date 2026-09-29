package dev.jasper.editor.open;

import dev.jasper.editor.api.OpenRequest;
import dev.jasper.sdk.Subscription;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** The handlers {@code Editor.opener} registers, asked in registration order; one that throws is reported and counts as false. */
public final class Openers {
    private final List<Predicate<OpenRequest>> handlers = new CopyOnWriteArrayList<>();
    private final Consumer<RuntimeException> failed;

    /** @param failed told each failure of a handler that threw */
    public Openers(Consumer<RuntimeException> failed) { this.failed = Objects.requireNonNull(failed); }

    /**
     * @param handler answers a request; true when it handled it
     * @return removes the handler
     */
    public Subscription register(Predicate<OpenRequest> handler) {
        handlers.add(Objects.requireNonNull(handler));
        return () -> handlers.remove(handler);
    }

    /**
     * @param request what Open in Editor… was invoked on
     * @return whether a handler handled it
     */
    public boolean handle(OpenRequest request) {
        for (Predicate<OpenRequest> handler : handlers) {
            try { if (handler.test(request)) return true; }
            catch (RuntimeException failure) { failed.accept(failure); /* the next handler, or the local chooser, answers */ }
        }
        return false;
    }
}
